package ratph6.tessera.triggers

// trigger-type strings a script passes to register(type, cb)
object TriggerType {
    const val CHAT = "chat"
    const val ACTION_BAR = "actionBar"
    const val COMMAND = "command"
    const val MESSAGE_SENT = "messageSent"

    const val RENDER_OVERLAY = "renderOverlay"
    const val RENDER_CROSSHAIR = "renderCrosshair"
    const val RENDER_HOTBAR = "renderHotbar"
    const val RENDER_HEALTH = "renderHealth"
    const val RENDER_ARMOR = "renderArmor"
    const val RENDER_FOOD = "renderFood"
    const val RENDER_MOUNT_HEALTH = "renderMountHealth"
    const val RENDER_EXPERIENCE = "renderExperience"
    const val RENDER_AIR = "renderAir"
    const val RENDER_PORTAL = "renderPortal"
    const val RENDER_JUMP_BAR = "renderJumpBar"
    const val RENDER_CHAT = "renderChat"
    const val RENDER_HELMET = "renderHelmet"
    const val RENDER_HAND = "renderHand"
    const val RENDER_SCOREBOARD = "renderScoreboard"
    const val RENDER_TITLE = "renderTitle"
    const val RENDER_DEBUG = "renderDebug"
    const val RENDER_BOSS_HEALTH = "renderBossHealth"
    const val RENDER_PLAYER_LIST = "renderPlayerList"

    const val RENDER_WORLD = "renderWorld"
    const val RENDER_ENTITY = "renderEntity"
    const val POST_RENDER_ENTITY = "postRenderEntity"
    const val RENDER_TILE_ENTITY = "renderTileEntity"
    const val POST_RENDER_TILE_ENTITY = "postRenderTileEntity"
    const val BLOCK_HIGHLIGHT = "blockHighlight"

    const val WORLD_LOAD = "worldLoad"
    const val WORLD_UNLOAD = "worldUnload"
    const val BLOCK_BREAK = "blockBreak"
    const val TICK = "tick"
    const val GAME_TICK = "gameTick"
    const val STEP = "step"
    const val GAME_LOAD = "gameLoad"

    const val ENTITY_DAMAGE = "entityDamage"
    const val ENTITY_DEATH = "entityDeath"
    const val SPAWN_PARTICLE = "spawnParticle"

    const val PLAYER_JOIN = "playerJoin"
    const val PLAYER_LEAVE = "playerLeave"

    const val SOUND_PLAY = "soundPlay"
    const val NOTE_BLOCK_PLAY = "noteBlockPlay"
    const val NOTE_BLOCK_CHANGE = "noteBlockChange"

    const val GUI_OPEN = "guiOpen"
    const val GUI_CLOSE = "guiClose"
    const val GUI_KEY = "guiKey"
    const val GUI_MOUSE_CLICK = "guiMouseClick"
    const val GUI_DRAW_BACKGROUND = "guiDrawBackground"
    const val POST_GUI_RENDER = "postGuiRender"
    const val INVENTORY_OPEN = "inventoryOpen"
    const val INVENTORY_CLOSE = "inventoryClose"
    const val SLOT_CLICK = "slotClick"
    const val PICKUP_ITEM = "pickupItem"
    const val DROP_ITEM = "dropItem"

    const val KEY_DOWN = "keyDown"
    const val KEY_UP = "keyUp"
    const val MOUSE_CLICK = "mouseClick"
    const val MOUSE_RELEASE = "mouseRelease"
    const val MOUSE_SCROLLED = "mouseScrolled"
    const val MOUSE_MOVE = "mouseMove"

    const val PACKET_RECEIVED = "packetReceived"
    const val PACKET_SENT = "packetSent"
    const val SERVER_CONNECT = "serverConnect"
    const val SERVER_DISCONNECT = "serverDisconnect"

    // types whose callback gets a cancellable event as its final arg
    val CANCELLABLE: Set<String> = setOf(
        CHAT, ACTION_BAR, MESSAGE_SENT,
        RENDER_CROSSHAIR, RENDER_HOTBAR, RENDER_HEALTH, RENDER_ARMOR, RENDER_FOOD, RENDER_MOUNT_HEALTH,
        RENDER_EXPERIENCE, RENDER_AIR, RENDER_PORTAL, RENDER_JUMP_BAR, RENDER_CHAT, RENDER_HELMET,
        RENDER_HAND, RENDER_SCOREBOARD, RENDER_TITLE, RENDER_DEBUG, RENDER_BOSS_HEALTH, RENDER_PLAYER_LIST,
        RENDER_ENTITY, POST_RENDER_ENTITY, RENDER_TILE_ENTITY, POST_RENDER_TILE_ENTITY, BLOCK_HIGHLIGHT,
        BLOCK_BREAK, ENTITY_DAMAGE, SPAWN_PARTICLE, SOUND_PLAY, NOTE_BLOCK_PLAY,
        GUI_OPEN, GUI_KEY, GUI_MOUSE_CLICK, SLOT_CLICK, PICKUP_ITEM, DROP_ITEM,
        KEY_DOWN, MOUSE_CLICK, MOUSE_SCROLLED, PACKET_RECEIVED, PACKET_SENT,
    )

    // types that honour .setCriteria() and the match-mode setters
    val CHAT_LIKE: Set<String> = setOf(CHAT, ACTION_BAR, MESSAGE_SENT)

    val ALL: Set<String> = setOf(
        CHAT, ACTION_BAR, COMMAND, MESSAGE_SENT,
        RENDER_OVERLAY, RENDER_CROSSHAIR, RENDER_HOTBAR, RENDER_HEALTH, RENDER_ARMOR, RENDER_FOOD,
        RENDER_MOUNT_HEALTH, RENDER_EXPERIENCE, RENDER_AIR, RENDER_PORTAL, RENDER_JUMP_BAR, RENDER_CHAT,
        RENDER_HELMET, RENDER_HAND, RENDER_SCOREBOARD, RENDER_TITLE, RENDER_DEBUG, RENDER_BOSS_HEALTH,
        RENDER_PLAYER_LIST, RENDER_WORLD, RENDER_ENTITY, POST_RENDER_ENTITY, RENDER_TILE_ENTITY,
        POST_RENDER_TILE_ENTITY, BLOCK_HIGHLIGHT,
        WORLD_LOAD, WORLD_UNLOAD, BLOCK_BREAK, TICK, GAME_TICK, STEP, GAME_LOAD,
        ENTITY_DAMAGE, ENTITY_DEATH, SPAWN_PARTICLE, PLAYER_JOIN, PLAYER_LEAVE,
        SOUND_PLAY, NOTE_BLOCK_PLAY, NOTE_BLOCK_CHANGE,
        GUI_OPEN, GUI_CLOSE, GUI_KEY, GUI_MOUSE_CLICK, GUI_DRAW_BACKGROUND, POST_GUI_RENDER,
        INVENTORY_OPEN, INVENTORY_CLOSE, SLOT_CLICK, PICKUP_ITEM, DROP_ITEM,
        KEY_DOWN, KEY_UP, MOUSE_CLICK, MOUSE_RELEASE, MOUSE_SCROLLED, MOUSE_MOVE,
        PACKET_RECEIVED, PACKET_SENT, SERVER_CONNECT, SERVER_DISCONNECT,
    )

    fun isCancellable(type: String): Boolean = type in CANCELLABLE

    // types that actually have a source hook in this build and so fire; others warn on register.
    // input + per-element HUD renders aren't hookable on this MC version.
    val WIRED: Set<String> = setOf(
        CHAT, COMMAND, TICK, GAME_TICK, STEP, GAME_LOAD,
        WORLD_LOAD, WORLD_UNLOAD, SERVER_CONNECT, SERVER_DISCONNECT,
        RENDER_OVERLAY, RENDER_WORLD, RENDER_ENTITY, POST_RENDER_ENTITY,
        PACKET_RECEIVED, PACKET_SENT,
        SOUND_PLAY, SPAWN_PARTICLE, ENTITY_DEATH, MESSAGE_SENT, ACTION_BAR, BLOCK_BREAK,
        GUI_OPEN, GUI_CLOSE, GUI_KEY, GUI_MOUSE_CLICK, GUI_DRAW_BACKGROUND,
        INVENTORY_OPEN, INVENTORY_CLOSE,
    )

    // known type with no source hook: registrable, but won't fire
    fun isUnwired(type: String): Boolean = type in ALL && type !in WIRED
}
