# Copy the bundled TypeScript declarations from the VS Code extension into the Zed extension's
# language server so both editors share one source of truth. The two big files (minecraft.d.ts,
# minecraft-globals.d.ts) are gitignored in the Zed copy — run this before packaging the Zed extension.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root "vscode-extension\types"
$dst = Join-Path $root "zed-extension\server\types"

New-Item -ItemType Directory -Force -Path $dst | Out-Null
foreach ($name in @("tessera.d.ts", "minecraft.d.ts", "minecraft-globals.d.ts")) {
    $from = Join-Path $src $name
    if (Test-Path $from) {
        Copy-Item -Force $from (Join-Path $dst $name)
        Write-Host "copied $name"
    } else {
        Write-Warning "missing $from"
    }
}
Write-Host "Done -> $dst"
