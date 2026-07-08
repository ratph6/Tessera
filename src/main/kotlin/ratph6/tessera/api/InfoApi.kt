package ratph6.tessera.api

import com.mojang.blaze3d.platform.InputConstants

// Current server info.
object Server {
    @JvmStatic fun isOnline(): Boolean = runCatching { Mc.client.currentServer != null }.getOrDefault(false)
    @JvmStatic fun getIP(): String = runCatching { Mc.client.currentServer?.ip ?: "" }.getOrDefault("")
    @JvmStatic fun getName(): String = runCatching { Mc.client.currentServer?.name ?: "" }.getOrDefault("")
    @JvmStatic fun getMotd(): String = runCatching { Mc.client.currentServer?.motd?.string ?: "" }.getOrDefault("")
    @JvmStatic fun getPlayerCount(): Int = runCatching { Mc.connection?.onlinePlayers?.size ?: 0 }.getOrDefault(0)
    @JvmStatic fun getPlayers(): Array<String> = runCatching {
        Mc.connection?.onlinePlayers?.map { it.profile.name }?.toTypedArray() ?: emptyArray()
    }.getOrDefault(emptyArray())
}

// Player-list (tab) info.
object TabList {
    @JvmStatic fun getNames(): Array<String> = runCatching {
        Mc.connection?.onlinePlayers?.map { it.profile.name }?.toTypedArray() ?: emptyArray()
    }.getOrDefault(emptyArray())

    @JvmStatic fun getUnformattedNames(): Array<String> = getNames()
}

// Sidebar scoreboard — stubbed; the 26.2 read path is non-trivial under the new render model.
object Scoreboard {
    // stub returns empty — warn once so scripts don't parse nothing forever without a clue
    private var warned = false
    private fun warnStub() {
        if (warned) return
        warned = true
        ratph6.tessera.engine.TesseraEngine.recordError(
            "Scoreboard", "Scoreboard API is stubbed on MC 26.2 — getTitle/getLines always return empty")
    }

    @JvmStatic fun getTitle(): String { warnStub(); return "" }
    @JvmStatic fun getUnformattedTitle(): String { warnStub(); return "" }
    @JvmStatic fun getLines(): Array<String> { warnStub(); return emptyArray() }
}

// Raw key state.
object KeyBind {
    // true while the GLFW key is held
    @JvmStatic fun isKeyDown(keyCode: Int): Boolean = runCatching {
        InputConstants.isKeyDown(Mc.client.window, keyCode)
    }.getOrDefault(false)
}
