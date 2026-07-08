# ai.md — writing Tessera mods with an LLM

This file is for AI assistants (Claude, GPT, Cursor, etc.) asked to write or debug Tessera
modules. Read it fully before generating code. Humans: see [README.md](README.md).

## What Tessera is

Tessera is a Fabric client mod for **Minecraft 26.2** that runs **TypeScript** mods ("modules") at
runtime — the spiritual successor to ChatTriggers. A module is a folder under
`.minecraft/tessera/modules/<name>/` containing:

```
modules/
  my-mod/
    index.ts        # entry point (top-level code runs on load)
    tessera.json    # optional manifest: { "name", "version", "entry", "priority", "engine" }
```

No build step, no jar, no `main()`, no `export`. Drop the folder in, run `/te reload` in game.

## How it works (architecture)

Two execution engines; the TypeScript source is identical either way, `tessera.json`'s `"engine"`
field picks one:

- **`graal`** (default) — swc4j transpiles TS → JS, GraalJS (a full ECMAScript runtime on the JVM)
  executes it. Normal JavaScript semantics: arrays, closures, spread, JSON, `parseFloat`, functions
  calling functions. **Use this unless profiling says otherwise.**
- **`bytecode`** — swc4j compiles TS **directly to JVM bytecode**; scripts load as real JVM classes
  and trigger dispatch is a cached `MethodHandle`. Near-native speed (within ~1% of hand-written
  Kotlin on compute; ~1000× faster than reflective bridges on interop), but a restricted language
  subset (see constraints below). Only worth it for per-frame hot paths like `RENDER_ENTITY`.

Around the engines:

- **Triggers** — `Tessera.register(Event.X, cb)` returns a chainable handle
  (`setCriteria`, `setContains`/`setStart`/`setEnd`/`setExact`, `setPriority`, `setName`,
  `setDelay`/`setFps`, `filterClass`, `unregister`). Events are dispatched from Fabric hooks and a
  small set of mixins into Minecraft's chat/render/network/sound paths.
- **Custom event bus** — `Tessera.on("name", cb)` / `Tessera.emit("name", data)` plus built-in
  `tessera:*` events (e.g. `tessera:tick`).
- **Java interop** — `import { BlockPos } from 'net.minecraft.core'`-style imports bind mapped
  Minecraft classes (Mojang mappings). Common classes are pre-bound as globals; the full surface is
  in the generated `tessera.d.ts` / `minecraft.d.ts`.
- **Runtime mixins & access widening** — the `Mixin` API injects into Minecraft methods from
  TypeScript at runtime (ASM + a self-attached instrumentation agent); `AccessWidener` opens
  private members. Powerful and dangerous — prefer the `Event` catalogue when it covers the need.
- **Scheduling** — `Tessera.setTimeout(fn, ms)`, `Tessera.setInterval(fn, ms)`,
  `Tessera.millis()`. There is no `async`/`await` on the bytecode engine.

## API quick reference

Import from `'ratph6.tessera.api'`:

| Global | Purpose |
|---|---|
| `Tessera` | register triggers, event bus, timers, `cancelEvent()` |
| `Event` | trigger catalogue (`CHAT`, `TICK`, `COMMAND`, `RENDER_OVERLAY`, `RENDER_ENTITY`, `BLOCK_BREAK`, `SOUND_PLAY`, `GUI_OPENED`, `PACKET_RECEIVED`, …) |
| `ChatLib` | `chat()` (client-side print), `say()`, `command()`, `clearChat()`, `addColor()`, `removeFormatting()` |
| `Player` | position/rotation/health of the local player |
| `World` | world queries, entities, blocks |
| `Renderer` | 2-D overlay drawing (HUD) |
| `Tessellator` | world-space matrix ops; bound to the entity pose stack inside `RENDER_ENTITY` |
| `Num` / `Args` | numeric parsing and command-arg helpers (needed on bytecode engine) |
| `Store` | persistent key/value storage per module |
| `Display` | ChatTriggers-style text displays |
| `Mixin` / `AccessWidener` | runtime bytecode injection / private-member access |

Full signatures: `tessera.d.ts` (written into the modules dir on first launch) and the API docs at
<https://tessera-5d7.pages.dev/>.

## Canonical patterns

```ts
import { Tessera, Event, ChatLib, Player } from 'ratph6.tessera.api';

ChatLib.chat("§a[my-mod] loaded");                 // top-level runs once on load

Tessera.register(Event.CHAT, (message) => {        // one argument: the event's value
  ChatLib.chat("§epong!");
  // Tessera.cancelEvent();                        // hide the original message
}).setContains().setCriteria("ping");

Tessera.register(Event.COMMAND, (args) => {        // registers /coords
  ChatLib.chat("XYZ: " + Player.getX() + ", " + Player.getY() + ", " + Player.getZ());
}).setName("coords");

Tessera.register(Event.TICK, (t) => { /* every client tick */ });
Tessera.on("tessera:tick", (e) => { /* same, via the event bus */ });
Tessera.setTimeout(() => ChatLib.chat("3s later"), 3000);
```

Beware the echo loop: a `CHAT` trigger that prints a message containing its own criteria
re-triggers itself. Never echo the matched word.

## `bytecode` engine constraints (only when `"engine": "bytecode"`)

The graal engine has **none** of these. On bytecode:

1. Callbacks take exactly **one argument** (the event value). Cancel with `Tessera.cancelEvent()`.
2. **Annotate every numeric parameter and fractional variable as `number`** —
   `function f(n: number)`, `let s: number = 1`. An unannotated `let s = 1` infers `int` and
   silently truncates: `s * 1.5` becomes `1`.
3. Read command args with `Args.count(args)` / `Args.get(args, i)` and parse with `Num.parse(...)`
   — `args[0]` and `parseFloat` are not guaranteed.
4. A user function **cannot call another user function** — inline shared logic.
5. No `async`/`await` — use `Tessera.setTimeout` / `Tessera.setInterval`.

If a task needs real arrays, closures, or helper functions, put the module on the graal engine —
that is the default and the right call almost every time.

## Verify loop (do this, don't guess)

Every claim you make about a mod working should be checkable in game:

1. Write the module folder, then `/te reload` (or `/te load <name>`).
2. `/te errors` — one line per recent script error; full stacks in `/te console`.
3. `/te eval <code>` — run a TS snippet live with APIs auto-imported; test an expression before
   building a trigger around it.
4. `/te list` — confirm the module loaded and how many triggers it registered.
5. The `events-test` module (`/events`) lights up each event as it fires — use it to confirm an
   event actually has a live hook before relying on it. Some catalogue entries (global
   `keyDown`/`mouse*`, per-element HUD renders) have **no source hook on MC 26.2**; registering
   one logs a warning rather than failing silently.

## Guidelines for AI assistants

Adapted from [andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills)
(guidelines distilled from Andrej Karpathy's observations on common LLM coding mistakes — MIT
licensed). Follow all four when writing Tessera modules:

### 1. Think before coding

Don't assume; don't hide confusion; surface tradeoffs.
- State assumptions explicitly (which engine? which events? client-only?). If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists (an existing `Event` instead of a runtime `Mixin`), say so.

### 2. Simplicity first

Minimum code that solves the problem; nothing speculative.
- No features beyond what was asked. No config systems, no abstractions for single-use code.
- One module folder, one `index.ts`, the default graal engine — until something measurable says
  otherwise.
- If you wrote 200 lines and it could be 50, rewrite it.

### 3. Surgical changes

Touch only what you must.
- When editing an existing module, don't "improve" adjacent triggers, comments, or formatting.
- Match the existing style. Remove only imports/variables **your** change orphaned.
- Every changed line should trace directly to the user's request.

### 4. Goal-driven execution

Define success criteria; loop until verified.
- "Add a /waypoint command" → "after `/te reload`, `/waypoint 10 64 10` prints the waypoint and
  `/te errors` is clean."
- Use the verify loop above (`/te reload` → `/te errors` → `/te eval`) instead of declaring
  untested code done.
- Strong criteria let you iterate independently; weak ones ("make it work") just defer the bugs.

## Common failure modes to avoid

- Registering `Event.KEY_DOWN` or per-element HUD render events and assuming they fire — they have
  no hook on 26.2; check the events list in README.md.
- Forgetting `setName` on a `COMMAND` trigger (the command never registers).
- On bytecode: unannotated numeric `let` truncating fractions — the classic "my scale is stuck
  at 1" bug.
- Chat echo loops (see above).
- Reaching for `Mixin`/`AccessWidener` when a built-in event or API already exposes the data.
- Blocking the client thread: triggers run on the render/main thread — no busy loops, no
  synchronous I/O in callbacks; use `Store` for persistence and timers for delays.
