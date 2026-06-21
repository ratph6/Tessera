# Tessera — IntelliSense for JavaScript In Minecraft

Full autocompletion, hover docs, signature help and go-to-definition for **Tessera** scripts:

- the Tessera API — `Tessera`, `Event`, `ChatLib`, `Player`, `World`, `Renderer`, `Tessellator`, `Store`, `Mixin`, … (`ratph6.tessera.api`)
- the **entire Mojang-mapped Minecraft API** — `net.minecraft.*` (6000+ classes, generated from the game jar)

## TS mixins

`Mixin.inject(...)` injects a callback straight into a Minecraft method. Its target/method/injection-point
are string literals, so the extension adds **smart completion inside those strings**:

```ts
Mixin.inject('net.minecraft.client.Minecraft', 'tick', (ctx) => { /* ... */ });
//            └─ arg 0: class names      └─ arg 1: that class's methods
Mixin.inject('net.minecraft...', 'tick', 'HEAD', (ctx) => {});   // arg 2: HEAD / RETURN / TAIL
```

- **arg 0** — every `net.minecraft.*` class (fully-qualified).
- **arg 1** — the methods declared on the class named in arg 0 (inherited methods aren't listed —
  retarget the declaring superclass if a method is missing).
- **the `at` arg** — `HEAD`, `RETURN`, `TAIL`.

`AccessWidener.widenField/widenMethod/makeExtendable(...)` get the same string completion (arg 0 = class,
arg 1 = field/method). For **already-loaded** classes (Minecraft, KeyMapping, …), bytecode widening is
impossible — use the reflection accessors `AccessWidener.getField/setField/invoke(...)` (and `*Static`
variants), which reach private members on any object with no restart.

Snippets: `mixin`, `mixincancel`, `mixinreturn`, `widenfield`, `widenmethod`, `makeextendable`,
`getfield`, `setfield`, `reflectinvoke`.

```ts
import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';

Tessera.register(Event.COMMAND, () => {
  const p = new BlockPos(0, 64, 0);   // ← no import needed; full completion on BlockPos, p.getX(), etc.
  ChatLib.chat("y=" + p.getY());
});
```

**Minecraft classes need no import.** Every uniquely-named `net.minecraft.*` class (6000+: `BlockPos`,
`Vec3`, `Entity`, `ItemStack`, `Component`, ...) is a bare global — just type the name. The ~50 classes
whose simple name is ambiguous (e.g. `Block`, `Item`) still use `import { Block } from 'net.minecraft.world.level.block'`
(VS Code auto-import offers it). This resolves Mojang names, which exist in the **dev client**
(`runClient`); a remapped production jar uses the bytecode engine for Minecraft interop.

## How it works

Tessera scripts are TypeScript, so completion is driven entirely by two bundled declaration files
(`tessera.d.ts`, `minecraft.d.ts`). On activation the extension:

1. copies those files into its global storage (refreshed on version change), and
2. drops a **managed** `tsconfig.json` into each folder that contains a `tessera.json`, pointing the
   built-in TypeScript language service at them.

It never overwrites a `tsconfig.json` you wrote yourself (it only touches ones marked
`"_tesseraManaged": true`). Run **“Tessera: Set up IntelliSense in this folder”** from the command palette to
configure a folder manually.

## Install

```bash
cd vscode-extension
npm install
npm run package        # produces tessera-intellisense-<version>.vsix
code --install-extension tessera-intellisense-1.3.0.vsix
```

Then open your `.minecraft/tessera/modules/` folder (or the project's `run/tessera/modules/`) in VS Code.

## Updating the Minecraft declarations

`types/minecraft.d.ts` is generated from the mapped Minecraft jar. After a Minecraft/mapping bump,
regenerate and re-copy:

```bash
./gradlew genMinecraftDts        # from the Tessera project root — writes vscode-extension/types/minecraft.d.ts
cp src/main/resources/tessera/types/tessera.d.ts vscode-extension/types/   # only if the Tessera API changed
cd vscode-extension && npm run package
```
