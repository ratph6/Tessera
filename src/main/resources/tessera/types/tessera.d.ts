/**
 * Tessera — JavaScript In Minecraft — TypeScript API declarations.
 *
 * Point your editor at this file (it ships in the mod jar at `tessera/types/tessera.d.ts` and is written to
 * `.minecraft/tessera/tessera.d.ts` on first run) for completion and type-checking.
 *
 * Scripts look like this — no main(), no export, arrow callbacks against the Event enum:
 *
 *   import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';
 *
 *   Tessera.register(Event.CHAT, (message) => {
 *     ChatLib.chat("pong!");
 *     // Tessera.cancelEvent();   // cancel the event
 *   }).setContains().setCriteria("hello");
 *
 * Tessera has TWO engines, chosen per-module by `"engine"` in tessera.json:
 *
 *   • "graal" (DEFAULT) — TypeScript is transpiled to JavaScript (swc4j) and run on GraalJS, a real
 *     ECMAScript runtime. You get NORMAL JavaScript: real arrays with .map/.filter/.reduce, spread,
 *     closures over reassigned `let`s, objects, JSON, template strings. None of the constraints
 *     below apply. Best for general scripting. See examples/arraydemo.
 *
 *   • "bytecode" — TypeScript is compiled straight to JVM bytecode (swc4j ByteCodeCompiler). Native
 *     speed (no engine in the hot path), ideal for per-frame render hooks — but it is NOT a JS
 *     runtime, so the constraints below apply. Opt in with `"engine": "bytecode"`. See examples/cubed.
 *
 * Constraints that apply to the BYTECODE engine ONLY (the graal engine has none of these):
 *   • Callbacks take exactly ONE argument (the event's value). Cancel via Tessera.cancelEvent().
 *   • Annotate numeric function PARAMETERS as `number` (untyped params become non-primitive).
 *   • Annotate fractional VARIABLES as `number` too: `let s = 1` infers int and truncates;
 *     write `let s: number = 1` to get a double.
 *   • A user function cannot call another user function yet — inline shared logic.
 *   • Module-level mutable state captured by a callback misbehaves; use Store/Num/Args helpers.
 *   • No async/await; use Tessera.setTimeout / Tessera.setInterval.
 *
 * The GRAAL engine supports real `async`/`await`, Promises, and the global `setTimeout`/`setInterval`/
 * `clearTimeout`/`clearInterval`/`sleep` (declared below). They run on the JS thread, driven by Tessera's
 * timers, e.g. `await sleep(1000)` waits a second then resumes.
 */

// Async / timer globals (graal engine), backed by Tessera's tick-thread timers.
declare function setTimeout(handler: () => void, ms?: number): number;
declare function setInterval(handler: () => void, ms?: number): number;
declare function clearTimeout(id: number): void;
declare function clearInterval(id: number): void;
/** Resolve after `ms` milliseconds — `await sleep(500)`. */
declare function sleep(ms: number): Promise<void>;

declare module 'ratph6.tessera.api' {
  type Callback = (value: any) => void;

  /**
   * Event ids (string constants), in the spirit of ChatTriggers' TriggerType.
   *
   * What your callback RECEIVES per event — `Tessera.register(Event.X, (…args) => {})`:
   *
   *   CHAT            (message: string)                       the chat line (supports setCriteria/setContains)
   *   COMMAND         (args: string[])                        words after the command; name via .setName("x")
   *   MESSAGE_SENT    (message: string)                       outgoing chat — cancel to block it
   *   ACTION_BAR      (text: string)                          overlay message — cancel to hide
   *   TICK/GAME_TICK  (tick: number)                          client tick count
   *   STEP            (dt: number)                            seconds since last step (set rate via .setFps()/.setDelay())
   *   GAME_LOAD       ()                                       client finished starting
   *   WORLD_LOAD      ()  /  WORLD_UNLOAD ()                   joined / left a world
   *   SERVER_CONNECT  (ip: string, port: number)
   *   SERVER_DISCONNECT (reason: string)
   *   RENDER_OVERLAY  ()                                       draw HUD via the Renderer API
   *   RENDER_ENTITY / POST_RENDER_ENTITY  (entity: EntityWrapper)   entity.getName()/getX()/isPlayer()/...
   *   ENTITY_DEATH    (entity: EntityWrapper)
   *   BLOCK_BREAK     (block: BlockWrapper)                    block.getX()/getType()/isAir() — cancel to veto
   *   SOUND_PLAY      (name: string)                           sound id, e.g. "minecraft:block.note_block.harp"
   *   SPAWN_PARTICLE  (name: string, x: number, y: number, z: number)
   *   GUI_OPENED / GUI_CLOSED / GUI_DRAW_BACKGROUND  (screenClassName: string)
   *   INVENTORY_OPEN / INVENTORY_CLOSE  (screenClassName: string)
   *   GUI_KEY         (keyEvent)                               net.minecraft KeyEvent — .getKey() etc.
   *   GUI_MOUSE_CLICK (mouseEvent)                             net.minecraft MouseButtonEvent
   *   PACKET_RECEIVED / PACKET_SENT  (packet, name: string)    raw net.minecraft Packet + its class name (observe-only)
   *
   * Custom bus: `Tessera.on("myEvent", (payload) => {})` ← `Tessera.emit("myEvent", payload)`.
   * Events not listed above have no source hook on this build — registering them logs a warning.
   */
  namespace Event {
    const CHAT: string; const ACTION_BAR: string; const MESSAGE_SENT: string; const COMMAND: string;
    const TICK: string; const STEP: string; const GAME_LOAD: string; const GAME_UNLOAD: string;
    const WORLD_LOAD: string; const WORLD_UNLOAD: string; const SERVER_CONNECT: string; const SERVER_DISCONNECT: string;
    const BLOCK_BREAK: string; const SPAWN_PARTICLE: string; const PLAYER_JOIN: string; const PLAYER_LEAVE: string;
    const ENTITY_DAMAGE: string; const ENTITY_DEATH: string;
    const SOUND_PLAY: string; const NOTE_BLOCK_PLAY: string; const NOTE_BLOCK_CHANGE: string;
    const CLICKED: string; const SCROLLED: string; const DRAGGED: string; const KEY_DOWN: string; const KEY_UP: string;
    const PICKUP_ITEM: string; const DROP_ITEM: string; const SLOT_CLICK: string; const INVENTORY_OPEN: string; const INVENTORY_CLOSE: string;
    const GUI_OPENED: string; const GUI_CLOSED: string; const GUI_KEY: string; const GUI_MOUSE_CLICK: string;
    const GUI_MOUSE_RELEASE: string; const GUI_DRAW_BACKGROUND: string; const POST_GUI_RENDER: string;
    const RENDER_OVERLAY: string; const RENDER_CROSSHAIR: string; const RENDER_HOTBAR: string; const RENDER_HEALTH: string;
    const RENDER_FOOD: string; const RENDER_ARMOR: string; const RENDER_EXPERIENCE: string; const RENDER_AIR: string;
    const RENDER_PORTAL: string; const RENDER_JUMP_BAR: string; const RENDER_CHAT: string; const RENDER_HELMET: string;
    const RENDER_HAND: string; const RENDER_MOUNT_HEALTH: string; const RENDER_BOSS_HEALTH: string; const RENDER_SCOREBOARD: string;
    const RENDER_TITLE: string; const RENDER_DEBUG: string; const RENDER_PLAYER_LIST: string;
    const RENDER_WORLD: string; const RENDER_ENTITY: string; const POST_RENDER_ENTITY: string;
    const RENDER_TILE_ENTITY: string; const POST_RENDER_TILE_ENTITY: string; const BLOCK_HIGHLIGHT: string;
    const PACKET_SENT: string; const PACKET_RECEIVED: string;
  }

  class TriggerHandle {
    setPriority(priority: number): TriggerHandle;
    setCriteria(pattern: string): TriggerHandle;
    setContains(): TriggerHandle;
    setStart(): TriggerHandle;
    setEnd(): TriggerHandle;
    setExact(): TriggerHandle;
    setCancelable(value: boolean): TriggerHandle;
    setDelay(ms: number): TriggerHandle;
    setFps(fps: number): TriggerHandle;
    setName(name: string): TriggerHandle;     // command name
    setSound(sound: string): TriggerHandle;
    filterClass(className: string): TriggerHandle;
    /** Only fire when the event's value is an instance of this class (simple or full name, incl.
     *  superclasses). e.g. PACKET_RECEIVED + setFilteredClass("ClientboundSetHealthPacket"). */
    setFilteredClass(className: string): TriggerHandle;
    unregister(): TriggerHandle;
  }

  namespace Tessera {
    /** Register a callback for an Event (or custom type id). Callback receives the event's value. */
    function register(type: string, callback: Callback): TriggerHandle;
    /** Listen for a custom or built-in (`tessera:*`) event. */
    function on(eventName: string, callback: Callback): TriggerHandle;
    /** Fire a custom event with a payload. */
    function emit(eventName: string, payload: any): void;
    /** Cancel the cancellable event currently being dispatched. */
    function cancelEvent(): void;
    function setTimeout(callback: () => void, ms: number): number;
    function setInterval(callback: () => void, ms: number): number;
    /** Run `callback` once after `ticks` client ticks (20 ticks ≈ 1s). Returns an id for clearTimer. */
    function scheduleTask(callback: () => void, ticks: number): number;
    /** Run `callback` every `ticks` client ticks. Returns an id for clearTimer. */
    function scheduleInterval(callback: () => void, ticks: number): number;
    function clearTimer(id: number): void;
    function loadModule(name: string): void;
    function unloadModule(name: string): void;
    function reload(): void;
    function getLoadedModules(): string[];
    function log(message: string): void;
    /** Milliseconds timestamp for benchmarking (use instead of System.*). */
    function millis(): number;
  }

  namespace ChatLib {
    function chat(message: string): void;
    function say(message: string): void;
    function command(command: string): void;
    function clearChat(): void;
    function removeFormatting(text: string): string;
    function addColor(text: string): string;
    function isPlayer(name: string): boolean;
    function simulateChat(message: string): void;
  }

  namespace Player {
    function getX(): number; function getY(): number; function getZ(): number;
    function getMotionX(): number; function getMotionY(): number; function getMotionZ(): number;
    function getYaw(): number; function getPitch(): number;
    function getName(): string; function getUUID(): string;
    function getHealth(): number; function getMaxHealth(): number;
    function getHunger(): number; function getSaturation(): number;
    function getXPLevel(): number; function getXPProgress(): number; function getArmor(): number;
    function isOnGround(): boolean; function isSprinting(): boolean;
    function isSneaking(): boolean; function isFlying(): boolean;
    function getHeldItem(): ItemWrapper | null;
    function distanceTo(entity: EntityWrapper): number;
  }

  namespace World {
    function isLoaded(): boolean;
    function getDimension(): string;
    function getTime(): number; function getDayTime(): number;
    function isRaining(): boolean;
    function getBlock(x: number, y: number, z: number): BlockWrapper | null;
    function getAllEntities(): EntityWrapper[];
    function getEntitiesOfType(type: string): EntityWrapper[];
    function getNearestEntity(type: string, radius: number): EntityWrapper | null;
  }

  namespace Renderer {
    function drawRect(color: number, x: number, y: number, width: number, height: number): void;
    function drawString(text: string, x: number, y: number, color: number): void;
    function drawStringWithShadow(text: string, x: number, y: number, color: number): void;
    function drawLine(color: number, x1: number, y1: number, x2: number, y2: number, width: number): void;
    function color(r: number, g: number, b: number, a?: number): number;
    function getStringWidth(text: string): number;
    function getFontHeight(): number;
    function getScreenWidth(): number;
    function getScreenHeight(): number;
  }

  /**
   * World-render matrix transforms (ChatTriggers-style). Valid only inside a renderEntity /
   * postRenderEntity callback, where the pose is positioned at the entity's origin. Tessera auto-pops
   * anything left on the stack when the entity finishes, so an unmatched pushMatrix is safe.
   */
  namespace Tessellator {
    function pushMatrix(): void;
    function popMatrix(): void;
    function scale(x: number, y: number, z: number): void;
    function translate(x: number, y: number, z: number): void;
    /** Rotate `angle` degrees about the axis (x, y, z); the axis is normalised for you. */
    function rotate(angle: number, x: number, y: number, z: number): void;
  }

  /** Remote, case-insensitive per-player scale table: `{ "Name": { x, y, z } }` fetched from a URL. */
  namespace PlayerScales {
    /** Download + parse the table off-thread, replacing the current one on success. */
    function fetch(url: string): void;
    function has(name: string): boolean;
    function getX(name: string): number;
    function getY(name: string): number;
    function getZ(name: string): number;
    function count(): number;
  }

  /** Numeric helpers (swc4j compiles to bytecode, so JS `parseFloat` isn't guaranteed). */
  namespace Num {
    function parse(s: string, fallback?: number): number;
  }

  /** Safe access to a command callback's `string[]` args (avoids JS-style indexing). */
  namespace Args {
    function count(args: any): number;
    function get(args: any, index: number): string;
  }

  /**
   * Global key→value store for script state. Prefer this over many module-level `let`s shared across
   * callbacks: swc4j boxes each reassigned captured variable and miscomputes the stack when one
   * callback captures many of them (→ VerifyError). Store keeps callbacks capture-free.
   */
  namespace Store {
    function getNum(key: string, fallback: number): number;
    function setNum(key: string, value: number): void;
    function getBool(key: string, fallback: boolean): boolean;
    function setBool(key: string, value: boolean): void;
    /** Flip a boolean (default false) and return the new value. */
    function toggle(key: string): boolean;
    function getStr(key: string, fallback: string): string;
    function setStr(key: string, value: string): void;
    function clear(): void;
  }

  namespace Server {
    function isOnline(): boolean;
    function getIP(): string; function getName(): string; function getMotd(): string;
    function getPlayerCount(): number; function getPlayers(): string[];
  }
  namespace TabList { function getNames(): string[]; function getUnformattedNames(): string[]; }
  namespace Scoreboard { function getTitle(): string; function getLines(): string[]; }
  namespace KeyBind { function isKeyDown(keyCode: number): boolean; }

  class Display {
    constructor();
    setLine(index: number, text: string): Display;
    addLine(text: string): Display;
    clearLines(): Display;
    setX(value: number): Display;
    setY(value: number): Display;
    setTextColor(color: number): Display;
    setBackgroundColor(color: number): Display;
    setAlign(value: string): Display;
    setShadow(value: boolean): Display;
    setVisible(value: boolean): Display;
    remove(): void;
  }

  class EntityWrapper {
    getX(): number; getY(): number; getZ(): number;
    getName(): string; getUUID(): string; getType(): string;
    distanceTo(other: EntityWrapper): number; isPlayer(): boolean;
    /** Body yaw in degrees. */
    getYaw(): number;
    /** True if this entity is the local player (matched by UUID). */
    isLocalPlayer(): boolean;
  }
  class ItemWrapper { getName(): string; getStackSize(): number; isEmpty(): boolean; getRawName(): string; }
  class BlockWrapper { getX(): number; getY(): number; getZ(): number; getType(): string; isAir(): boolean; }

  /** Passed to a Mixin.inject callback: read/cancel/override the intercepted Minecraft method call. */
  class MixinContext {
    /** Binary name of the injected class, e.g. "net.minecraft.client.Minecraft". */
    readonly target: string;
    /** Name of the injected method. */
    readonly method: string;
    /** The receiver (`this`), or null for a static method. */
    readonly self: any;
    /** The method's arguments, boxed (an `int` arrives as a java.lang.Integer). Read-only. */
    readonly args: any[];
    /** Skip the rest of the original method (HEAD injection returns immediately). */
    cancel(): void;
    isCancelled(): boolean;
    /** The i-th argument, or null if out of range. */
    getArg(i: number): any;
    argCount(): number;
    /** Substitute the return value (at HEAD this also cancels the body; at RETURN it replaces the value). */
    setReturnValue(value: any): void;
  }

  /**
   * Inject TypeScript callbacks into Minecraft methods at runtime — a live, script-defined mixin.
   * Targets are Mojang-mapped binary class + method names. Requires `-Djdk.attach.allowAttachSelf=true`.
   *
   *   Mixin.inject("net.minecraft.client.Minecraft", "tick", (ctx) => { ... });          // HEAD (default)
   *   Mixin.inject(cls, method, "HEAD", (ctx) => ctx.cancel());                          // cancel the call
   *   Mixin.inject(cls, method, "RETURN", (ctx) => ctx.setReturnValue(true));            // override the result
   */
  namespace Mixin {
    /** Inject at the HEAD of `method` on `target`. Returns a handle whose `remove()` detaches it. */
    function inject(target: string, method: string, callback: (ctx: MixinContext) => void): MixinHandle;
    /** Inject at `at` ("HEAD" or "RETURN"/"TAIL") of `method` on `target`. */
    function inject(target: string, method: string, at: string, callback: (ctx: MixinContext) => void): MixinHandle;
    /** Inject into the single overload whose JVM `descriptor` matches (e.g. "(Lnet/minecraft/world/entity/Entity;)Z"). */
    function injectExact(target: string, method: string, descriptor: string, at: string, callback: (ctx: MixinContext) => void): MixinHandle;
  }

  /** Handle to a live injection; `remove()` reverts the target method to its original bytecode. */
  class MixinHandle { remove(): void; }

  /**
   * Open up private / protected / final Minecraft members at runtime (a live access widener). A widened
   * member becomes public (and loses final), so scripts can read/write/call it.
   *
   * TIMING: the JVM only allows access changes while a class is being defined, so a widening takes
   * effect only on a class NOT YET LOADED when you register it. Widen as early as possible (top of the
   * module). Targeting an already-loaded class (e.g. Minecraft) logs a note in /te errors and does
   * nothing this session. Reverted on /te reload.
   */
  namespace AccessWidener {
    // --- bytecode widening: transparent member access, but ONLY for classes not yet loaded ---
    /** Make `field` on `target` public and non-final (readable + writable from scripts). */
    function widenField(target: string, field: string): void;
    /** Make every overload of `method` on `target` public and non-final. */
    function widenMethod(target: string, method: string): void;
    /** Make the single overload whose JVM `descriptor` matches public and non-final. */
    function widenMethod(target: string, method: string, descriptor: string): void;
    /** Make `target` itself public and non-final, so it can be subclassed. */
    function makeExtendable(target: string): void;

    // --- reflection accessors: work on ALREADY-LOADED classes too (Minecraft, KeyMapping, ...) ---
    /** Read a private instance field by name (walks the class hierarchy). */
    function getField(target: any, field: string): any;
    /** Write a private instance field; numeric values are coerced to the field's type. */
    function setField(target: any, field: string, value: any): void;
    /** Read a private static field, e.g. getStaticField('net.minecraft.client.Minecraft', 'instance'). */
    function getStaticField(className: string, field: string): any;
    /** Write a private static field; numeric values are coerced to the field's type. */
    function setStaticField(className: string, field: string, value: any): void;
    /** Call a private instance method by name + arg count; numeric args are coerced to the param types. */
    function invoke(target: any, method: string, ...args: any[]): any;
    /** Call a private static method by name + arg count. */
    function invokeStatic(className: string, method: string, ...args: any[]): any;
  }
}
