# Tessera for Zed

A [Zed](https://zed.dev) port of the Tessera VS Code extension — IntelliSense, Mixin/AccessWidener
string-literal completion, and snippets for Tessera scripts (Minecraft modding in TypeScript/JavaScript).

## What it does

| Feature | How it works in Zed |
| --- | --- |
| **TypeScript IntelliSense** for the Tessera + Minecraft APIs | Zed's built-in TypeScript server (vtsls) reads the bundled `.d.ts` files via a `tsconfig.json` the language server drops into each Tessera module folder. |
| **Mixin/AccessWidener string completion** — class names, methods, fields, injection points (`HEAD`/`RETURN`/`TAIL`) | A small bundled Node language server (`server/server.js`) parses `minecraft.d.ts` and answers completion requests inside `Mixin.inject(...)` / `AccessWidener.*(...)` string arguments. |
| **Snippets** (`mixin`, `mixincancel`, `widenfield`, …) | `snippets/typescript.json` and `snippets/javascript.json`. |

## Why the architecture differs from the VS Code extension

Zed extensions run as sandboxed WebAssembly: no arbitrary completion API and no project file access.
So the two things the VS Code extension did from JavaScript — the custom completion provider and writing
`tsconfig.json` — were moved into a real **language server** process (Node), which Zed launches and which
runs natively with full filesystem access. The completion parsing and tsconfig logic are ported 1:1 from
`vscode-extension/src/extension.ts`.

Zed runs this server *alongside* its own TypeScript server; ours only contributes completions inside Mixin
string literals and stays silent everywhere else.

## Layout

```
zed-extension/
  extension.toml          registers the language server + snippets
  Cargo.toml              Rust/WASM glue
  src/lib.rs              launches server/server.js with Zed's bundled Node
  snippets/*.json         the 9 Mixin/AccessWidener snippets
  server/
    server.js             dependency-free LSP (completion + tsconfig management)
    types/                bundled .d.ts (synced from vscode-extension/types)
```

## Build & install (development)

1. Sync the type declarations into the server (the two large generated files are gitignored):
   ```sh
   ./scripts/sync-zed-types.sh         # or scripts\sync-zed-types.ps1 on Windows
   ```
2. In Zed: **Extensions → Install Dev Extension…** and pick the `zed-extension/` folder.
   Zed compiles `src/lib.rs` to WebAssembly automatically (needs the Rust toolchain installed).
3. Open a folder containing a `tessera.json`. The server writes a managed `tsconfig.json` into each
   module root; IntelliSense and Mixin completion come up on the next `.ts`/`.js` you open.

A managed `tsconfig.json` carries `"_tesseraManaged": true` — the server only ever overwrites those, never
your own. Run `sync-zed-types` again whenever the Minecraft mappings are regenerated.

## Requirements

- Rust toolchain (Zed uses it to build the extension).
- Node is provided by Zed for the language server — no separate install needed.
