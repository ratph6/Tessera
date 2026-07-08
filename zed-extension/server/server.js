#!/usr/bin/env node
'use strict';

// Tessera Mixin language server.
//
// A dependency-free LSP server (raw JSON-RPC over stdio) that ports the two pieces of the VS Code
// extension that a sandboxed Zed extension can't do itself:
//
//   1. String-literal completion for Mixin.inject / AccessWidener calls — fully-qualified Minecraft
//      class names (arg 0), that class's methods/fields (arg 1), and injection points HEAD/RETURN/TAIL.
//      Zed only exposes custom completions through a language server, so this logic moved here.
//
//   2. Writing a managed tsconfig.json into every Tessera module folder so the editor's *own*
//      TypeScript server picks up the bundled .d.ts declarations (tessera.d.ts, minecraft.d.ts,
//      minecraft-globals.d.ts). The VS Code extension wrote these from JS; here it happens on
//      `initialize`, once per workspace.
//
// The parsing/loading logic mirrors vscode-extension/src/extension.ts so the two stay in lockstep.

const fs = require('fs');
const path = require('path');

const TYPES_DIR = path.join(__dirname, 'types');
const TYPE_FILES = ['tessera.d.ts', 'minecraft.d.ts', 'minecraft-globals.d.ts'];
const MANAGED_KEY = '_tesseraManaged';

// LSP CompletionItemKind values.
const KIND = { Class: 7, Method: 2, Field: 5, EnumMember: 20 };

// ------------------------------------------------------------------------------------------------
// JSON-RPC / stdio transport
// ------------------------------------------------------------------------------------------------

let buffer = Buffer.alloc(0);
process.stdin.on('data', (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  drain();
});
process.stdin.on('end', () => process.exit(0));

function drain() {
  while (true) {
    const headerEnd = buffer.indexOf('\r\n\r\n');
    if (headerEnd < 0) return;
    const header = buffer.slice(0, headerEnd).toString('ascii');
    const m = /Content-Length:\s*(\d+)/i.exec(header);
    if (!m) {
      // Malformed header — drop it and resync.
      buffer = buffer.slice(headerEnd + 4);
      continue;
    }
    const len = parseInt(m[1], 10);
    const start = headerEnd + 4;
    if (buffer.length < start + len) return; // wait for the rest of the body
    const body = buffer.slice(start, start + len).toString('utf8');
    buffer = buffer.slice(start + len);
    let msg;
    try {
      msg = JSON.parse(body);
    } catch {
      continue;
    }
    handle(msg);
  }
}

function send(msg) {
  const json = JSON.stringify(msg);
  const payload = Buffer.from(json, 'utf8');
  process.stdout.write(`Content-Length: ${payload.length}\r\n\r\n`);
  process.stdout.write(payload);
}

function reply(id, result) {
  send({ jsonrpc: '2.0', id, result });
}

// ------------------------------------------------------------------------------------------------
// Message dispatch
// ------------------------------------------------------------------------------------------------

const docs = new Map(); // uri -> full text (TextDocumentSyncKind.Full)

function handle(msg) {
  switch (msg.method) {
    case 'initialize':
      onInitialize(msg);
      break;
    case 'initialized':
      break;
    case 'shutdown':
      reply(msg.id, null);
      break;
    case 'exit':
      process.exit(0);
      break;
    case 'textDocument/didOpen':
      docs.set(msg.params.textDocument.uri, msg.params.textDocument.text);
      break;
    case 'textDocument/didChange': {
      const changes = msg.params.contentChanges;
      if (changes && changes.length) {
        // Full sync: the last change carries the entire document.
        docs.set(msg.params.textDocument.uri, changes[changes.length - 1].text);
      }
      break;
    }
    case 'textDocument/didClose':
      docs.delete(msg.params.textDocument.uri);
      break;
    case 'textDocument/completion':
      reply(msg.id, onCompletion(msg.params));
      break;
    default:
      // Requests we don't implement still need a response so the client doesn't hang.
      if (msg.id !== undefined) reply(msg.id, null);
  }
}

function onInitialize(msg) {
  reply(msg.id, {
    capabilities: {
      textDocumentSync: 1, // Full
      completionProvider: { triggerCharacters: ['"', "'", '`', '.'] },
    },
    serverInfo: { name: 'tessera-mixin', version: '0.1.0' },
  });

  // Configure every Tessera folder the workspace exposes.
  const roots = [];
  const params = msg.params || {};
  if (Array.isArray(params.workspaceFolders)) {
    for (const f of params.workspaceFolders) {
      const p = uriToPath(f.uri);
      if (p) roots.push(p);
    }
  } else if (params.rootUri) {
    const p = uriToPath(params.rootUri);
    if (p) roots.push(p);
  } else if (params.rootPath) {
    roots.push(params.rootPath);
  }
  const typeFiles = TYPE_FILES.map((n) => path.join(TYPES_DIR, n)).filter((p) => fs.existsSync(p));
  for (const root of roots) {
    try {
      configureWorkspace(root, typeFiles);
    } catch {
      /* best-effort */
    }
  }
}

// ------------------------------------------------------------------------------------------------
// Completion (ported from extension.ts)
// ------------------------------------------------------------------------------------------------

function onCompletion(params) {
  const text = docs.get(params.textDocument.uri);
  if (text === undefined) return null;
  const line = lineAt(text, params.position.line);
  const prefix = line.slice(0, params.position.character);

  const ctx = injectionCallContext(prefix);
  if (!ctx || !ctx.inString) return null;

  const range = currentStringRange(prefix, params.position);
  let items = null;
  if (ctx.argIndex === 0) {
    items = classCompletions(range);
  } else if (ctx.argIndex === 1) {
    if (ctx.call === 'widenField') items = fieldCompletions(ctx.args[0], range);
    else if (ctx.call !== 'makeExtendable') items = methodCompletions(ctx.args[0], range);
  } else if (ctx.call === 'inject' && ctx.argIndex === 2) {
    items = atCompletions(range);
  } else if (ctx.call === 'injectExact' && ctx.argIndex === 3) {
    items = atCompletions(range);
  }
  if (!items) return null;
  // Class list is huge; tell the client to keep re-querying as the user types.
  return { isIncomplete: ctx.argIndex === 0, items };
}

const CALL_PREFIXES = [
  { prefix: 'Mixin.injectExact(', name: 'injectExact' },
  { prefix: 'Mixin.inject(', name: 'inject' },
  { prefix: 'AccessWidener.widenField(', name: 'widenField' },
  { prefix: 'AccessWidener.widenMethod(', name: 'widenMethod' },
  { prefix: 'AccessWidener.makeExtendable(', name: 'makeExtendable' },
];

function injectionCallContext(prefix) {
  let best = null;
  for (const { prefix: p, name } of CALL_PREFIXES) {
    const idx = prefix.lastIndexOf(p);
    if (idx >= 0 && (!best || idx + p.length > best.open)) best = { name, open: idx + p.length };
  }
  if (!best) return null;

  const inner = prefix.slice(best.open);
  const args = [''];
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

function currentStringRange(prefix, position) {
  const q = Math.max(prefix.lastIndexOf('"'), prefix.lastIndexOf("'"), prefix.lastIndexOf('`'));
  const startChar = q >= 0 ? q + 1 : position.character;
  return {
    start: { line: position.line, character: startChar },
    end: { line: position.line, character: position.character },
  };
}

function item(label, kind, range, detail) {
  const it = {
    label,
    kind,
    filterText: label,
    textEdit: { range, newText: label },
  };
  if (detail) it.detail = detail;
  return it;
}

function classCompletions(range) {
  return loadClasses().map((fqcn) => item(fqcn, KIND.Class, range));
}

function methodCompletions(fqcn, range) {
  const methods = loadApi().get((fqcn || '').trim());
  if (!methods || methods.length === 0) return [];
  return methods.map((name) => item(name, KIND.Method, range, (fqcn || '').trim()));
}

function fieldCompletions(fqcn, range) {
  loadApi();
  const fields = mcFieldApi && mcFieldApi.get((fqcn || '').trim());
  if (!fields || fields.length === 0) return [];
  return fields.map((name) => item(name, KIND.Field, range, (fqcn || '').trim()));
}

function atCompletions(range) {
  const docsMap = {
    HEAD: 'Run before the method body. May cancel() or setReturnValue().',
    RETURN: 'Run at every return site. May override the value with setReturnValue().',
    TAIL: 'Alias of RETURN.',
  };
  return Object.entries(docsMap).map(([label, detail]) => item(label, KIND.EnumMember, range, detail));
}

// ------------------------------------------------------------------------------------------------
// minecraft.d.ts parsing (ported from extension.ts)
// ------------------------------------------------------------------------------------------------

let mcApi; // fqcn -> method names
let mcFieldApi; // fqcn -> field names
let mcClasses; // sorted fqcn list

function loadApi() {
  if (mcApi) return mcApi;
  const map = new Map();
  const fields = new Map();
  const file = path.join(TYPES_DIR, 'minecraft.d.ts');
  if (!fs.existsSync(file)) {
    mcApi = map;
    mcFieldApi = fields;
    mcClasses = [];
    return map;
  }
  const moduleRe = /declare module '([^']+)'/;
  const typeRe = /^\s*export (?:class|interface) (\w+)/;
  const methodRe = /^\s+(?:static\s+)?(\w+)\s*\(/;
  const fieldRe = /^\s+(?:static\s+)?(?:readonly\s+)?(\w+)\s*:/;

  let pkg = '';
  let cls = '';
  let methods = [];
  let clsFields = [];
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
      const mm = methodRe.exec(line);
      if (mm && mm[1] !== 'constructor' && !methods.includes(mm[1])) {
        methods.push(mm[1]);
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

function loadClasses() {
  loadApi();
  return mcClasses || [];
}

// ------------------------------------------------------------------------------------------------
// tsconfig management (ported from extension.ts)
// ------------------------------------------------------------------------------------------------

function configureWorkspace(root, typeFiles) {
  const roots = new Set();
  for (const j of findTesseraJson(root)) {
    // One tsconfig at the modules root (parent of each module dir) covers all modules — clamped
    // to the workspace root so a top-level tessera.json can't make us write above the workspace.
    const candidate = path.dirname(path.dirname(j));
    roots.add(candidate.startsWith(root) ? candidate : path.dirname(j));
  }
  for (const r of roots) {
    try {
      writeTsconfig(r, typeFiles, false);
    } catch {
      /* ignore folders we can't write to */
    }
  }
}

// Recursive walk for tessera.json, skipping node_modules/.git and capping work.
function findTesseraJson(root) {
  const found = [];
  const stack = [root];
  let visited = 0;
  while (stack.length && visited < 5000) {
    const dir = stack.pop();
    visited++;
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (e.isDirectory()) {
        if (e.name === 'node_modules' || e.name === '.git' || e.name === 'build') continue;
        stack.push(path.join(dir, e.name));
      } else if (e.name === 'tessera.json') {
        found.push(path.join(dir, e.name));
      }
    }
  }
  return found;
}

function writeTsconfig(folder, typeFiles, force) {
  const file = path.join(folder, 'tsconfig.json');
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
    include: ['**/*.ts', '**/*.js'],
  };
  fs.writeFileSync(file, JSON.stringify(tsconfig, null, 2) + '\n');
}

// ------------------------------------------------------------------------------------------------
// helpers
// ------------------------------------------------------------------------------------------------

function lineAt(text, lineNo) {
  // Avoid splitting the whole (possibly large) document: walk to the requested line.
  let start = 0;
  for (let i = 0; i < lineNo; i++) {
    const nl = text.indexOf('\n', start);
    if (nl < 0) return '';
    start = nl + 1;
  }
  let end = text.indexOf('\n', start);
  if (end < 0) end = text.length;
  if (end > start && text[end - 1] === '\r') end--;
  return text.slice(start, end);
}

function uriToPath(uri) {
  if (!uri) return null;
  if (!uri.startsWith('file://')) return null;
  let p = decodeURIComponent(uri.slice('file://'.length));
  // file:///C:/foo -> /C:/foo ; strip the leading slash before a Windows drive letter.
  if (/^\/[a-zA-Z]:/.test(p)) p = p.slice(1);
  return path.normalize(p);
}

function toPosix(p) {
  return p.replace(/\\/g, '/');
}

function stripJsonComments(s) {
  return s.replace(/\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '');
}
