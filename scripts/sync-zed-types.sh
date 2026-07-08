#!/usr/bin/env bash
# Copy the bundled TypeScript declarations from the VS Code extension into the Zed extension's
# language server so both editors share one source of truth. The two big files (minecraft.d.ts,
# minecraft-globals.d.ts) are gitignored in the Zed copy — run this before packaging the Zed extension.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/vscode-extension/types"
dst="$root/zed-extension/server/types"

mkdir -p "$dst"
for name in tessera.d.ts minecraft.d.ts minecraft-globals.d.ts; do
  if [ -f "$src/$name" ]; then
    cp -f "$src/$name" "$dst/$name"
    echo "copied $name"
  else
    echo "missing $src/$name" >&2
  fi
done
echo "Done -> $dst"
