package ratph6.tessera.api

// Event ids for Tessera.register. String constants, not a JVM enum: swc4j miscompiles passing an
// enum constant alongside a lambda arg, strings compile cleanly. Events with no hook in this build
// register fine but never fire (logs a warning).
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
