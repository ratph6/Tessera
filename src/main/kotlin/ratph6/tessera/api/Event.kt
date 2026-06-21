package ratph6.tessera.api

/**
 * The catalogue of events you can register a trigger for, in the spirit of ChatTriggers'
 * `TriggerType`. Pass one to [Tessera.register]:
 *
 * ```ts
 * import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';
 * Tessera.register(Event.CHAT, (message, event) => ChatLib.chat("pong!"));
 * ```
 *
 * Implemented as string constants (not a JVM enum) — `Event.CHAT` is the string `"chat"`. This is
 * deliberate: the swc4j bytecode compiler miscompiles passing an enum constant alongside a lambda
 * argument, whereas string constants compile cleanly. The script-facing syntax is identical.
 *
 * ## What your callback receives
 *
 * `Tessera.register(Event.X, (...args) => { })` — the arguments per event:
 *
 * | Event | Callback arguments | Notes |
 * |---|---|---|
 * | `CHAT` | `(message: string)` | the chat line; supports `setCriteria`/`setContains` |
 * | `COMMAND` | `(args: string[])` | words after the command; name via `.setName("x")` |
 * | `MESSAGE_SENT` | `(message: string)` | outgoing chat — cancel to block it |
 * | `ACTION_BAR` | `(text: string)` | overlay message — cancel to hide |
 * | `TICK` / `GAME_TICK` | `(tick: number)` | client tick count |
 * | `STEP` | `(dt: number)` | seconds since last step; rate via `.setFps()`/`.setDelay()` |
 * | `GAME_LOAD` | `()` | client finished starting |
 * | `WORLD_LOAD` / `WORLD_UNLOAD` | `()` | joined / left a world |
 * | `SERVER_CONNECT` | `(ip: string, port: number)` | |
 * | `SERVER_DISCONNECT` | `(reason: string)` | |
 * | `RENDER_OVERLAY` | `()` | draw the HUD via the `Renderer` API |
 * | `RENDER_ENTITY` / `POST_RENDER_ENTITY` | `(entity: EntityWrapper)` | `getName()`/`getX()`/`isPlayer()`/… |
 * | `ENTITY_DEATH` | `(entity: EntityWrapper)` | |
 * | `BLOCK_BREAK` | `(block: BlockWrapper)` | `getX()`/`getType()`/`isAir()` — cancel to veto |
 * | `SOUND_PLAY` | `(name: string)` | sound id, e.g. `minecraft:block.note_block.harp` |
 * | `SPAWN_PARTICLE` | `(name: string, x: number, y: number, z: number)` | |
 * | `GUI_OPENED` / `GUI_CLOSED` / `GUI_DRAW_BACKGROUND` | `(screenClassName: string)` | |
 * | `INVENTORY_OPEN` / `INVENTORY_CLOSE` | `(screenClassName: string)` | |
 * | `GUI_KEY` | `(keyEvent)` | net.minecraft `KeyEvent` |
 * | `GUI_MOUSE_CLICK` | `(mouseEvent)` | net.minecraft `MouseButtonEvent` |
 * | `PACKET_RECEIVED` / `PACKET_SENT` | `(packet, name: string)` | raw net.minecraft `Packet` + class name; observe-only |
 *
 * Custom bus: `Tessera.on("myEvent", (payload) => {})` receives whatever `Tessera.emit("myEvent", payload)` sent.
 *
 * Events not listed above have no source hook in this build — registering them is accepted but logs a
 * warning and the callback never fires (global key/mouse and per-element HUD renders aren't hookable
 * on this Minecraft version; the rest are added incrementally).
 */
object Event {
    // chat & messages
    const val CHAT = "chat"
    const val ACTION_BAR = "actionBar"
    const val MESSAGE_SENT = "messageSent"
    const val COMMAND = "command"

    // ticking
    const val TICK = "tick"
    const val STEP = "step"
    const val GAME_LOAD = "gameLoad"
    const val GAME_UNLOAD = "gameUnload"

    // world & server
    const val WORLD_LOAD = "worldLoad"
    const val WORLD_UNLOAD = "worldUnload"
    const val SERVER_CONNECT = "serverConnect"
    const val SERVER_DISCONNECT = "serverDisconnect"
    const val BLOCK_BREAK = "blockBreak"
    const val SPAWN_PARTICLE = "spawnParticle"
    const val PLAYER_JOIN = "playerJoin"
    const val PLAYER_LEAVE = "playerLeave"

    // entities
    const val ENTITY_DAMAGE = "entityDamage"
    const val ENTITY_DEATH = "entityDeath"

    // sound
    const val SOUND_PLAY = "soundPlay"
    const val NOTE_BLOCK_PLAY = "noteBlockPlay"
    const val NOTE_BLOCK_CHANGE = "noteBlockChange"

    // input
    const val CLICKED = "mouseClick"
    const val SCROLLED = "mouseScrolled"
    const val DRAGGED = "mouseDrag"
    const val KEY_DOWN = "keyDown"
    const val KEY_UP = "keyUp"

    // items / inventory
    const val PICKUP_ITEM = "pickupItem"
    const val DROP_ITEM = "dropItem"
    const val SLOT_CLICK = "slotClick"
    const val INVENTORY_OPEN = "inventoryOpen"
    const val INVENTORY_CLOSE = "inventoryClose"

    // gui
    const val GUI_OPENED = "guiOpen"
    const val GUI_CLOSED = "guiClose"
    const val GUI_KEY = "guiKey"
    const val GUI_MOUSE_CLICK = "guiMouseClick"
    const val GUI_MOUSE_RELEASE = "guiMouseRelease"
    const val GUI_DRAW_BACKGROUND = "guiDrawBackground"
    const val POST_GUI_RENDER = "postGuiRender"

    // HUD rendering
    const val RENDER_OVERLAY = "renderOverlay"
    const val RENDER_CROSSHAIR = "renderCrosshair"
    const val RENDER_HOTBAR = "renderHotbar"
    const val RENDER_HEALTH = "renderHealth"
    const val RENDER_FOOD = "renderFood"
    const val RENDER_ARMOR = "renderArmor"
    const val RENDER_EXPERIENCE = "renderExperience"
    const val RENDER_AIR = "renderAir"
    const val RENDER_PORTAL = "renderPortal"
    const val RENDER_JUMP_BAR = "renderJumpBar"
    const val RENDER_CHAT = "renderChat"
    const val RENDER_HELMET = "renderHelmet"
    const val RENDER_HAND = "renderHand"
    const val RENDER_MOUNT_HEALTH = "renderMountHealth"
    const val RENDER_BOSS_HEALTH = "renderBossHealth"
    const val RENDER_SCOREBOARD = "renderScoreboard"
    const val RENDER_TITLE = "renderTitle"
    const val RENDER_DEBUG = "renderDebug"
    const val RENDER_PLAYER_LIST = "renderPlayerList"

    // world rendering
    const val RENDER_WORLD = "renderWorld"
    const val RENDER_ENTITY = "renderEntity"
    const val POST_RENDER_ENTITY = "postRenderEntity"
    const val RENDER_TILE_ENTITY = "renderTileEntity"
    const val POST_RENDER_TILE_ENTITY = "postRenderTileEntity"
    const val BLOCK_HIGHLIGHT = "blockHighlight"

    // network
    const val PACKET_SENT = "packetSent"
    const val PACKET_RECEIVED = "packetReceived"
}
