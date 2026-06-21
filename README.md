# Tessera

TypeScript scripting for Minecraft 26.1.2 (Fabric, Kotlin). Write a `.ts` file, drop it in a
folder, reload in game. In the spirit of ChatTriggers, rebuilt on a modern toolchain.

API docs: [tessera-1w4.pages.dev](https://tessera-1w4.pages.dev) · runnable examples in [`examples/`](examples)

## Two engines

Every module picks its engine in `tessera.json`. The TypeScript source is the same either way; only
the runtime underneath changes.

- `graal` (default) — swc4j transpiles your TypeScript to JavaScript, GraalJS runs it. A full
  ECMAScript runtime, so you write ordinary TypeScript: real arrays with `.map`/`.filter`/`.reduce`,
  spread, closures over `let`, objects, JSON, template strings. Use this unless you have a reason not
  to.
- `bytecode` — swc4j compiles your TypeScript
  [straight to JVM bytecode](https://github.com/caoccao/swc4j/blob/main/docs/typescript_to_jvm_bytecode.md).
  Scripts load as ordinary JVM classes, so there's no interpreter in the hot path and no per-call
  JNI: trigger dispatch is a cached `MethodHandle`, and interop like `new BlockPos(...)` is a plain
  JVM allocation. Fast enough for per-frame render hooks, but the language has limits (see
  [Constraints](#bytecode-engine-constraints)). Opt in with `"engine": "bytecode"`.

```
graal:     TypeScript ──swc4j──▶ JavaScript ──GraalJS──▶ ECMAScript (arrays, closures, …)
bytecode:  TypeScript ──swc4j──▶ JVM bytecode ──MethodHandle──▶ runs as a native client class
```

## Benchmarks

Measured in client (`./gradlew runClient`, then `/te bench`). *Native* is the same workload written
by hand in Kotlin and compiled by kotlinc — the JVM floor Tessera is trying to reach. The Akutz and
ChatTriggers columns are published reference figures for two other Minecraft scripting runtimes.

| Benchmark | Tessera | Native (Kotlin) | Akutz | ChatTriggers |
|---|---|---|---|---|
| 1,000,000 × isPrime | ~71 ms | ~70 ms | ~60 ms | ~4,500 ms |
| 27,000 × `new BlockPos` (interop) | ~1.5 ms | ~1.4 ms | ~1,773 ms | ~16 ms |

Two things to read off this. On raw compute (`isPrime`) Tessera lands within a millisecond of
hand-written Kotlin, because swc4j emits JVM bytecode with no engine in the loop. On interop
(constructing Minecraft objects) it's roughly 1000× faster than Akutz, because each `new` compiles to
a direct JVM allocation instead of going through a reflective bridge.

The baseline lives in `BenchNative.kt`; the script versions are in `examples/bench-native` and
`examples/bench-interop`.

## Requirements

| | |
|---|---|
| Minecraft | 26.1.2 (Fabric) |
| Dependencies | Fabric API, [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) |
| Java | 25 |

swc4j ships inside the mod jar, including its small Rust native (used only at compile time) for macOS
(arm64/x64), Linux (x64) and Windows (x64). Nothing else to install.

## Build & install

```bash
./gradlew build              # -> build/libs/tessera-1.0.0.jar
```

Drop the jar in `.minecraft/mods/` next to Fabric API and Fabric Language Kotlin. On first launch
Tessera creates `.minecraft/tessera/modules/` with a `hello-tessera` example, and writes
`tessera.d.ts` there for editor autocompletion.

## Writing a script

A module is a folder under `.minecraft/tessera/modules/<name>/` containing an `index.ts` (and an
optional `tessera.json`). Scripts are plain TypeScript: no `main()`, no `export`, top-level code runs
once on load, and you register callbacks against `Event`.

```ts
import { Tessera, Event, ChatLib, Player } from 'ratph6.tessera.api';

ChatLib.chat("loaded!");                       // runs once, on load

Tessera.register(Event.CHAT, (message) => {
  ChatLib.chat("pong!");
  // Tessera.cancelEvent();                     // cancel the event (e.g. hide the message)
}).setContains().setCriteria("ping");

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("XYZ: " + Player.getX());
}).setName("coords");                           // registers /coords

Tessera.on("tessera:tick", (e) => { /* custom + built-in event bus */ });
Tessera.setTimeout(() => ChatLib.chat("3s later"), 3000);
```

Copy [`examples/hello-tessera`](examples/hello-tessera) to start. [`examples/cubed`](examples/cubed)
is a real port (scale and spin the player model through render hooks);
[`examples/bench-native`](examples/bench-native) and
[`examples/bench-interop`](examples/bench-interop) are the benchmark scripts. Point your editor at
`tessera.d.ts` for completion and type-checking.

### Events

`Tessera.register(Event.X, callback)` returns a chainable handle: `setPriority`, `setCriteria`,
`setContains`/`setStart`/`setEnd`/`setExact`, `setCancelable`, `setDelay`/`setFps` (for `Event.STEP`),
`setName` (for `Event.COMMAND`), `setSound`, `filterClass`, `unregister`. The `Event` catalogue
mirrors ChatTriggers (`Event.CHAT`, `Event.TICK`, `Event.RENDER_OVERLAY`, …).

Events with a live source hook in this build:

- **Chat & text** — `CHAT`, `ACTION_BAR`, `MESSAGE_SENT`
- **Lifecycle** — `COMMAND`, `TICK`/`GAME_TICK`, `STEP`, `GAME_LOAD`, `WORLD_LOAD`/`WORLD_UNLOAD`,
  `SERVER_CONNECT`/`SERVER_DISCONNECT`
- **Render** — `RENDER_OVERLAY`, `RENDER_ENTITY`/`POST_RENDER_ENTITY`
- **World & entities** — `BLOCK_BREAK`, `SOUND_PLAY`, `SPAWN_PARTICLE`, `ENTITY_DEATH`
- **GUI & inventory** — `GUI_OPENED`/`GUI_CLOSED`, `GUI_KEY`, `GUI_MOUSE_CLICK`, `GUI_DRAW_BACKGROUND`,
  `INVENTORY_OPEN`/`INVENTORY_CLOSE`
- **Network** — `PACKET_RECEIVED`/`PACKET_SENT` (observe-only)
- The custom bus (`Tessera.on` / `Tessera.emit`) and the built-in `tessera:*` events

Some catalogue entries have no source hook on MC 26.1.2 — global `keyDown`/`mouse*` and the
per-element HUD renders, because of the reworked input system and layered HUD, plus a few obscure
ones. Registering one is allowed but logs a warning, so a typo never fails silently. The
`run/tessera/modules/events-test` module (run `/events`) lights up each event as it fires, handy for
checking what's actually wired.

Inside a `RENDER_ENTITY` callback, `Tessellator` is bound to the entity's pose stack, so you can
`pushMatrix`, `scale`, `rotate` and `translate` the model. Tessera pops anything left on the stack
when the entity finishes rendering, so a stray `pushMatrix` can't corrupt Minecraft's matrices. See
[`examples/cubed`](examples/cubed).

### Choosing an engine

Set `"engine"` in `tessera.json`:

```json
{ "name": "mymod", "engine": "graal" }
```

Reach for `graal` (the default) for almost everything — it's normal JavaScript. Use `bytecode` only
for hot per-frame work, like a `RENDER_ENTITY` hook that fires for every entity every frame, where
the native dispatch and direct interop pay off. [`examples/arraydemo`](examples/arraydemo) runs on
graal; [`examples/cubed`](examples/cubed) pins `"engine": "bytecode"`.

### `bytecode` engine constraints

These apply **only** to `"engine": "bytecode"`. The graal engine is a real JS runtime and has none of
them — `args[0]`, `parseFloat`, `[...args].map(Number)`, module-level `let` closed over by callbacks,
and user functions calling each other all just work.

- **Callbacks take one argument** — the event's value (message, tick count, command args). Cancel a
  cancellable event with `Tessera.cancelEvent()` inside the callback.
- **Annotate numeric params and any fractional variable as `number`.** Use `function f(n: number)`
  (untyped params become boxed objects) and `let s: number = 1` for variables. An unannotated
  `let s = 1` infers `int` and silently truncates fractions, so `s * 1.5` becomes `1`.
- **Read command args through helpers** — `Args.count(args)` / `Args.get(args, i)`, then `Num.parse(...)`.
  JS-style `args[0]` and `parseFloat` aren't guaranteed.
- **A user function can't call another user function yet.** Inline shared logic, or push it through
  the API.
- **No `async`/`await`.** Use `Tessera.setTimeout` / `Tessera.setInterval`, and `Tessera.millis()`
  for timing.

## API (`ratph6.tessera.api`)

`Tessera` · `Event` · `ChatLib` · `Player` · `World` · `Renderer` · `Tessellator` (world-render
matrix) · `PlayerScales` (remote per-player scale table) · `Num`/`Args` (parse helpers) · `Server` ·
`TabList` · `Scoreboard` · `KeyBind` · `Display` · `EntityWrapper`/`ItemWrapper`/`BlockWrapper`. Full
signatures are in `tessera.d.ts` and the generated docs.

## In-game commands

```
/te reload              reload all modules
/te list                list loaded modules + trigger counts
/te load <module>       load one module
/te unload <module>     unload one module
/te eval <code>         run a TypeScript snippet live (APIs auto-imported)
/te errors              show recent script errors, one line each (full stacks in the console)
/te console             open the console window
/te bench               run the native JVM baseline for the benchmarks
```

`/te console` opens a standalone window, like ChatTriggers' console. It mirrors all Tessera output
(chat, `Tessera.log`, errors) with level colours, captures the full cause chain and stack rather than
just the message, and has an input box that evaluates a line of TypeScript.

## API docs site

The site at [tessera-1w4.pages.dev](https://tessera-1w4.pages.dev) is the Dokka HTML, committed to
[`docs/`](docs) and served statically (Cloudflare Pages: framework preset *none*, output directory
`docs`; or GitHub Pages via [`.github/workflows/docs.yml`](.github/workflows/docs.yml)).

Regenerate after API changes:

```bash
./gradlew dokkaGenerate && cp -r build/dokka/html/. docs/
```

Minecraft 26.1.2 isn't on public Maven, so the docs are committed as static HTML rather than built in
CI — `build.yml` only passes on a runner that can resolve MC 26.1.2.
