# Tessera — JavaScript In Minecraft (IntelliSense)

Autocomplete, hover docs, signature help and go-to-definition for Tessera scripts. Two things get
wired up the moment you start typing:

- the Tessera API — `Tessera`, `Event`, `ChatLib`, `Player`, `World`, `Renderer`, `Tessellator`,
  `Store`, `Mixin`, … from `ratph6.tessera.api`
- the **whole** Mojang-mapped Minecraft API — every `net.minecraft.*` class (6000+, generated
  straight from the game jar)

No language server to babysit, no separate process. It's just `.d.ts` files pointed at VS Code's
built-in TypeScript service, so it's fast and it's the same engine you already trust.

## Install

There's no Marketplace listing — you install the `.vsix` directly. Two steps, both from the command
palette (`Ctrl+Shift+P`, or `Cmd+Shift+P` on macOS).

**1. Build the `.vsix`** (skip if someone handed you one):

```bash
cd vscode-extension
npm install
npm run package        # -> tessera-intellisense-<version>.vsix
```

**2. Install it.** Open the palette with `Ctrl+Shift+P`, type **`Install from VSIX`**, pick
**Extensions: Install from VSIX…**, and choose the file you just built
(`tessera-intellisense-1.3.2.vsix` or whatever the version is).

**3. Turn it on for your folder.** Open the palette again — same `Ctrl+Shift+P` — and run
**`Tessera: Set up IntelliSense in this folder`**. That's the switch. It drops a managed
`tsconfig.json` next to your scripts and points the TypeScript service at the bundled type
definitions. Until you run it (or open a folder that already has a `tessera.json` in it, which
triggers setup automatically), completion stays dark.

> Prefer the terminal? `code --install-extension tessera-intellisense-1.3.2.vsix` does the install
> step, but you'll still want the **Set up IntelliSense in this folder** command for any folder that
> doesn't already contain a `tessera.json`.

Now open `.minecraft/tessera/modules/` (or the project's `run/tessera/modules/`) and start writing.

## Minecraft classes need no import

Every uniquely-named `net.minecraft.*` class is a bare global. Type the name, get full completion —
no import line:

```ts
import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';

Tessera.register(Event.COMMAND, () => {
  const p = new BlockPos(0, 64, 0);   // no import; completion on BlockPos and p.getX(), p.getY()…
  ChatLib.chat("y=" + p.getY());
});
```

The ~50 classes whose simple name collides (`Block`, `Item`, …) still need an explicit
`import { Block } from 'net.minecraft.world.level.block'` — VS Code's auto-import offers it. These
are Mojang names, so they resolve against the dev client (`runClient`); a remapped production jar
uses the bytecode engine for Minecraft interop.

## Mixins, with completion inside the strings

`Mixin.inject(...)` patches a callback straight into a Minecraft method. The target, method and
injection point are string literals, and the extension completes inside them:

```ts
Mixin.inject('net.minecraft.client.Minecraft', 'tick', (ctx) => { /* … */ });
//            └─ arg 0: class names         └─ arg 1: that class's methods
Mixin.inject('net.minecraft...', 'tick', 'HEAD', (ctx) => {});   // arg 2: HEAD / RETURN / TAIL
```

- **arg 0** — any fully-qualified `net.minecraft.*` class.
- **arg 1** — methods declared on that class. Inherited ones aren't listed; retarget the declaring
  superclass if a method's missing.
- **the `at` arg** — `HEAD`, `RETURN`, `TAIL`.

`AccessWidener.widenField/widenMethod/makeExtendable(...)` get the same string completion (arg 0 =
class, arg 1 = field/method). Classes that are **already loaded** (Minecraft, KeyMapping, …) can't be
bytecode-widened — reach them with the reflection accessors `AccessWidener.getField/setField/invoke`
(plus the `*Static` variants), which hit private members on any object with no restart.

Snippets to save the typing: `mixin`, `mixincancel`, `mixinreturn`, `widenfield`, `widenmethod`,
`makeextendable`, `getfield`, `setfield`, `reflectinvoke`.

## How it actually works

Tessera scripts are TypeScript, so all the completion comes from two bundled declaration files
(`tessera.d.ts`, `minecraft.d.ts`). On activation the extension copies them into its global storage
(refreshed when the version changes) and drops a **managed** `tsconfig.json` into every folder that
contains a `tessera.json`, aiming the built-in TypeScript service at them.

It won't clobber a `tsconfig.json` you wrote — it only touches ones tagged `"_tesseraManaged": true`.
Run **Tessera: Set up IntelliSense in this folder** any time you want to configure a folder by hand.

## Updating the Minecraft declarations

`types/minecraft.d.ts` is generated from the mapped Minecraft jar. After a Minecraft or mapping bump,
regenerate it and repackage:

```bash
./gradlew genMinecraftDts        # from the Tessera project root -> vscode-extension/types/minecraft.d.ts
cp src/main/resources/tessera/types/tessera.d.ts vscode-extension/types/   # only if the Tessera API changed
cd vscode-extension && npm run package
```

Reinstall the fresh `.vsix` the same way you did the first time — palette → **Install from VSIX…**.
