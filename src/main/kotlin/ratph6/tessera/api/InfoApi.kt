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

// Sidebar scoreboard — stubbed; the 26.1.2 read path is non-trivial under the new render model.
object Scoreboard {
    @JvmStatic fun getTitle(): String = ""
    @JvmStatic fun getUnformattedTitle(): String = ""
    @JvmStatic fun getLines(): Array<String> = emptyArray()
}

// Raw key state.
object KeyBind {
    // true while the GLFW key is held
    @JvmStatic fun isKeyDown(keyCode: Int): Boolean = runCatching {
        InputConstants.isKeyDown(Mc.client.window, keyCode)
    }.getOrDefault(false)
}
