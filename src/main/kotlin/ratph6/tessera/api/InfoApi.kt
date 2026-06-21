package ratph6.tessera.api

import com.mojang.blaze3d.platform.InputConstants

/** Current server info. `import { Server } from 'ratph6.tessera.api'`. */
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

/** Player-list (tab) info. */
object TabList {
    @JvmStatic fun getNames(): Array<String> = runCatching {
        Mc.connection?.onlinePlayers?.map { it.profile.name }?.toTypedArray() ?: emptyArray()
    }.getOrDefault(emptyArray())

    @JvmStatic fun getUnformattedNames(): Array<String> = getNames()
}

/**
 * Sidebar scoreboard. The 26.1.2 scoreboard read path is non-trivial under the new render model;
 * these are best-effort and may be expanded later.
 */
object Scoreboard {
    @JvmStatic fun getTitle(): String = ""
    @JvmStatic fun getUnformattedTitle(): String = ""
    @JvmStatic fun getLines(): Array<String> = emptyArray()
}

/** Raw key state. `import { KeyBind } from 'ratph6.tessera.api'`. */
object KeyBind {
    /** True while the GLFW key with [keyCode] is held (use GLFW key constants). */
    @JvmStatic fun isKeyDown(keyCode: Int): Boolean = runCatching {
        InputConstants.isKeyDown(Mc.client.window, keyCode)
    }.getOrDefault(false)
}
