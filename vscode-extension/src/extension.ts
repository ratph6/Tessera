import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

// Marker so we only ever overwrite a tsconfig.json we created ourselves (never a user's own).
const MANAGED_KEY = '_tesseraManaged';

/**
 * The Tessera IntelliSense extension. Tessera scripts are TypeScript; completion comes entirely from two
 * ambient-declaration files bundled with the extension:
 *   - tessera.d.ts       — the ratph6.tessera.api surface (Tessera, ChatLib, Player, Event, ...)
 *   - minecraft.d.ts — the full Mojang-mapped Minecraft API (net.minecraft.*), generated from the jar
 *
 * On activation we copy those files into the extension's global storage (one stable location, kept
 * in sync with the extension version) and drop a managed tsconfig.json into each Tessera module folder
 * (anything containing a tessera.json) that points the TypeScript language service at them. From there,
 * VS Code's built-in TS IntelliSense does the rest: completion, hover, go-to-def, signature help.
 */
export async function activate(context: vscode.ExtensionContext) {
  const typeFiles = await syncBundledTypes(context);

  context.subscriptions.push(
    vscode.commands.registerCommand('tessera.setupIntelliSense', async () => {
      const folder = await pickFolder();
      if (folder) {
        await writeTsconfig(folder, typeFiles, true);
        vscode.window.showInformationMessage(`Tessera IntelliSense set up in ${folder.fsPath}`);
      }
    }),
  );

  // Auto-configure existing + newly added workspace folders.
  await configureWorkspace(typeFiles);
  context.subscriptions.push(
    vscode.workspace.onDidChangeWorkspaceFolders(() => configureWorkspace(typeFiles)),
  );

  // Smart completion for the string arguments of Mixin.inject(...) — class, method, injection point.
  registerMixinCompletions(context);
}

export function deactivate() {}

// ----------------------------------------------------------------------------------------------
// Mixin argument completion
//
// Mixin.inject("<class>", "<method>", "<at>", cb) takes its target/method/injection-point as string
// literals — which the TypeScript service can't complete. This provider parses the bundled Minecraft
// declarations to suggest fully-qualified class names (arg 0), that class's methods (arg 1), and the
// injection points "HEAD"/"RETURN"/"TAIL" (the `at` arg). All single-line calls.
// ----------------------------------------------------------------------------------------------

/** fully-qualified class name → its declared method names (lazily parsed from minecraft.d.ts). */
let mcApi: Map<string, string[]> | undefined;
/** fully-qualified class name → its declared field names. */
let mcFieldApi: Map<string, string[]> | undefined;
/** sorted list of every fully-qualified Minecraft class name. */
let mcClasses: string[] | undefined;

function registerMixinCompletions(context: vscode.ExtensionContext) {
  const extPath = context.extensionPath;
  const provider: vscode.CompletionItemProvider = {
    provideCompletionItems(document, position) {
      const prefix = document.lineAt(position.line).text.slice(0, position.character);
      const ctx = injectionCallContext(prefix);
      if (!ctx || !ctx.inString) return undefined;

      const range = currentStringRange(document, position);
      // arg 0 of every supported call is a class name.
      if (ctx.argIndex === 0) return classCompletions(extPath, range);
      // arg 1: a method name (inject / injectExact / widenMethod) or a field name (widenField).
      if (ctx.argIndex === 1) {
        if (ctx.call === 'widenField') return fieldCompletions(extPath, ctx.args[0], range);
        if (ctx.call !== 'makeExtendable') return methodCompletions(extPath, ctx.args[0], range);
      }
      // the injection-point ("at") arg.
      if (ctx.call === 'inject' && ctx.argIndex === 2) return atCompletions(range);
      if (ctx.call === 'injectExact' && ctx.argIndex === 3) return atCompletions(range);
      return undefined;
    },
  };
  context.subscriptions.push(
    vscode.languages.registerCompletionItemProvider(
      [{ language: 'typescript' }, { language: 'javascript' }],
      provider,
      '"',
      "'",
      '.',
    ),
  );
}

type CallName = 'inject' | 'injectExact' | 'widenField' | 'widenMethod' | 'makeExtendable';

interface InjectionCtx {
  call: CallName;
  argIndex: number;
  inString: boolean;
  args: string[];
}

const CALL_PREFIXES: Array<{ prefix: string; name: CallName }> = [
  { prefix: 'Mixin.injectExact(', name: 'injectExact' },
  { prefix: 'Mixin.inject(', name: 'inject' },
  { prefix: 'AccessWidener.widenField(', name: 'widenField' },
  { prefix: 'AccessWidener.widenMethod(', name: 'widenMethod' },
  { prefix: 'AccessWidener.makeExtendable(', name: 'makeExtendable' },
];

/** Parse the line prefix to find which Mixin/AccessWidener call + argument the cursor is in. */
function injectionCallContext(prefix: string): InjectionCtx | null {
  let best: { name: CallName; open: number } | null = null;
  for (const { prefix: p, name } of CALL_PREFIXES) {
    const idx = prefix.lastIndexOf(p);
    if (idx >= 0 && (!best || idx + p.length > best.open)) best = { name, open: idx + p.length };
  }
  if (!best) return null;

  const inner = prefix.slice(best.open);
  const args: string[] = [''];
  let inStr = false;
  let quote = '';
  for (const ch of inner) {
    if (inStr) {
      if (ch === quote) inStr = false;
      else args[args.length - 1] += ch;
    } else if (ch === '"' || ch === "'" || ch === '`') {
      inStr = true;
      quote = ch;
    } else if (ch === ',') {
      args.push('');
    } else if (ch === ')') {
      return null; // call already closed before the cursor
    }
  }
  return { call: best.name, argIndex: args.length - 1, inString: inStr, args };
}

/** The range covering the partial text already typed inside the current string literal. */
function currentStringRange(document: vscode.TextDocument, position: vscode.Position): vscode.Range {
  const prefix = document.lineAt(position.line).text.slice(0, position.character);
  const q = Math.max(prefix.lastIndexOf('"'), prefix.lastIndexOf("'"), prefix.lastIndexOf('`'));
  const start = q >= 0 ? q + 1 : position.character;
  return new vscode.Range(position.line, start, position.line, position.character);
}

function classCompletions(extPath: string, range: vscode.Range): vscode.CompletionItem[] {
  return loadClasses(extPath).map((fqcn) => {
    const item = new vscode.CompletionItem(fqcn, vscode.CompletionItemKind.Class);
    item.range = range;
    item.insertText = fqcn;
    item.filterText = fqcn;
    return item;
  });
}

function methodCompletions(extPath: string, fqcn: string, range: vscode.Range): vscode.CompletionItem[] {
  const methods = loadApi(extPath).get(fqcn.trim());
  if (!methods || methods.length === 0) return [];
  return methods.map((name) => {
    const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Method);
    item.range = range;
    item.insertText = name;
    item.detail = fqcn.trim();
    return item;
  });
}

function fieldCompletions(extPath: string, fqcn: string, range: vscode.Range): vscode.CompletionItem[] {
  loadApi(extPath);
  const fields = mcFieldApi?.get(fqcn.trim());
  if (!fields || fields.length === 0) return [];
  return fields.map((name) => {
    const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Field);
    item.range = range;
    item.insertText = name;
    item.detail = fqcn.trim();
    return item;
  });
}

function atCompletions(range: vscode.Range): vscode.CompletionItem[] {
  const docs: Record<string, string> = {
    HEAD: 'Run before the method body. May cancel() or setReturnValue().',
    RETURN: 'Run at every return site. May override the value with setReturnValue().',
    TAIL: 'Alias of RETURN.',
  };
  return Object.entries(docs).map(([label, detail]) => {
    const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.EnumMember);
    item.range = range;
    item.insertText = label;
    item.detail = detail;
    return item;
  });
}

/** Parse minecraft.d.ts once into a class → methods map (and the sorted class-name list). */
function loadApi(extPath: string): Map<string, string[]> {
  if (mcApi) return mcApi;
  const map = new Map<string, string[]>();
  const fields = new Map<string, string[]>();
  const file = path.join(extPath, 'types', 'minecraft.d.ts');
  if (!fs.existsSync(file)) {
    mcApi = map;
    mcFieldApi = fields;
    mcClasses = [];
    return map;
  }
  const moduleRe = /declare module '([^']+)'/;
  const typeRe = /^\s*export (?:class|interface) (\w+)/;
  const methodRe = /^\s+(?:static\s+)?(\w+)\s*\(/;
  // A field/property line: optional static/readonly, an identifier, then a colon (no call parens).
  const fieldRe = /^\s+(?:static\s+)?(?:readonly\s+)?(\w+)\s*:/;

  let pkg = '';
  let cls = '';
  let methods: string[] = [];
  let clsFields: string[] = [];
  const flush = () => {
    if (cls) {
      map.set(`${pkg}.${cls}`, methods);
      fields.set(`${pkg}.${cls}`, clsFields);
    }
    cls = '';
    methods = [];
    clsFields = [];
  };

  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    const mod = moduleRe.exec(line);
    if (mod) {
      flush();
      pkg = mod[1];
      continue;
    }
    const type = typeRe.exec(line);
    if (type) {
      flush();
      cls = type[1];
      continue;
    }
    if (cls) {
      const m = methodRe.exec(line);
      if (m && m[1] !== 'new' && !methods.includes(m[1])) {
        methods.push(m[1]);
        continue;
      }
      const f = fieldRe.exec(line);
      if (f && !clsFields.includes(f[1])) clsFields.push(f[1]);
    }
  }
  flush();

  mcApi = map;
  mcFieldApi = fields;
  mcClasses = [...map.keys()].sort();
  return map;
}

function loadClasses(extPath: string): string[] {
  loadApi(extPath);
  return mcClasses ?? [];
}

/** Copy bundled type declarations into global storage, refreshing when the extension version changes. */
async function syncBundledTypes(context: vscode.ExtensionContext): Promise<string[]> {
  const storage = context.globalStorageUri.fsPath;
  fs.mkdirSync(storage, { recursive: true });

  const version = (context.extension.packageJSON.version as string) ?? '0';
  const stamp = path.join(storage, '.version');
  const current = fs.existsSync(stamp) ? fs.readFileSync(stamp, 'utf8') : '';

  const names = ['tessera.d.ts', 'minecraft.d.ts', 'minecraft-globals.d.ts'];
  const targets = names.map((n) => path.join(storage, n));

  if (current !== version || targets.some((t) => !fs.existsSync(t))) {
    for (const n of names) {
      const src = path.join(context.extensionPath, 'types', n);
      if (fs.existsSync(src)) fs.copyFileSync(src, path.join(storage, n));
    }
    fs.writeFileSync(stamp, version);
  }
  return targets.filter((t) => fs.existsSync(t));
}

/** Find every Tessera folder in the workspace (one containing a tessera.json) and ensure a managed tsconfig. */
async function configureWorkspace(typeFiles: string[]) {
  const jsons = await vscode.workspace.findFiles('**/tessera.json', '**/node_modules/**', 2000);
  const roots = new Set<string>();
  for (const j of jsons) {
    // Put one tsconfig at the modules root (parent of each module dir) so it covers all modules.
    roots.add(path.dirname(path.dirname(j.fsPath)));
  }
  for (const root of roots) {
    try {
      await writeTsconfig(vscode.Uri.file(root), typeFiles, false);
    } catch {
      /* ignore individual folders we can't write to */
    }
  }
}

/** Write (or refresh) a managed tsconfig.json. Never clobbers a user's own tsconfig unless `force`. */
async function writeTsconfig(folder: vscode.Uri, typeFiles: string[], force: boolean) {
  const file = path.join(folder.fsPath, 'tsconfig.json');
  if (fs.existsSync(file) && !force) {
    try {
      const existing = JSON.parse(stripJsonComments(fs.readFileSync(file, 'utf8')));
      if (!existing[MANAGED_KEY]) return; // user's own tsconfig — leave it alone
    } catch {
      return; // unparseable — don't touch
    }
  }
  const tsconfig = {
    [MANAGED_KEY]: true,
    compilerOptions: {
      target: 'ES2022',
      module: 'ESNext',
      moduleResolution: 'bundler',
      lib: ['ES2022'],
      types: [],
      allowJs: true,
      checkJs: false,
      noEmit: true,
      skipLibCheck: true,
      strict: false,
    },
    files: typeFiles.map(toPosix),
    include: ['**/*.ts'],
  };
  fs.writeFileSync(file, JSON.stringify(tsconfig, null, 2) + '\n');
}

async function pickFolder(): Promise<vscode.Uri | undefined> {
  const folders = vscode.workspace.workspaceFolders;
  if (!folders || folders.length === 0) {
    vscode.window.showWarningMessage('Open your Tessera modules folder first.');
    return undefined;
  }
  if (folders.length === 1) return folders[0].uri;
  const pick = await vscode.window.showWorkspaceFolderPick();
  return pick?.uri;
}

/** TypeScript accepts forward slashes on every platform; backslashes in JSON are escapes. */
function toPosix(p: string): string {
  return p.replace(/\\/g, '/');
}

function stripJsonComments(s: string): string {
  return s.replace(/\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '');
}
