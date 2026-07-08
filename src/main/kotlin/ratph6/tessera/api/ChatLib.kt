package ratph6.tessera.api

// Chat helpers. § codes render as normal; addColor converts friendlier & codes.
object ChatLib {
    private val FORMATTING = Regex("§[0-9a-fk-orA-FK-OR]")

    // ChatComponent isn't thread-safe; scripts can call us from mixin hooks on other threads
    private fun onClientThread(body: () -> Unit) {
        val mc = Mc.client
        if (mc.isSameThread) body() else mc.execute(body)
    }

    // client-side only — just you see it
    @JvmStatic
    fun chat(message: String) {
        onClientThread { runCatching { Mc.client.gui.hud.chat.addClientSystemMessage(Mc.literal(message)) } }
    }

    // send as if typed into chat
    @JvmStatic
    fun say(message: String) {
        val conn = Mc.connection
            ?: return ratph6.tessera.engine.TesseraEngine.recordError("ChatLib.say", "not connected to a server")
        runCatching { conn.sendChat(message) }
            .onFailure { ratph6.tessera.engine.TesseraEngine.recordError("ChatLib.say", it) }
    }

    @JvmStatic
    fun command(command: String) {
        val conn = Mc.connection
            ?: return ratph6.tessera.engine.TesseraEngine.recordError("ChatLib.command", "not connected to a server")
        runCatching { conn.sendCommand(command.removePrefix("/")) } // leading slash optional
            .onFailure { ratph6.tessera.engine.TesseraEngine.recordError("ChatLib.command", it) }
    }

    @JvmStatic
    fun clearChat() {
        onClientThread { runCatching { Mc.client.gui.hud.chat.clearMessages(true) } }
    }

    @JvmStatic
    fun removeFormatting(text: String): String = FORMATTING.replace(text, "")

    // only convert & when it actually precedes a formatting code — leave literal ampersands alone
    private val AMP_CODE = Regex("&(?=[0-9a-fk-orA-FK-OR])")

    @JvmStatic
    fun addColor(text: String): String = AMP_CODE.replace(text, "§")

    @JvmStatic
    fun isPlayer(name: String): Boolean = runCatching {
        Mc.connection?.onlinePlayers?.any { it.profile.name.equals(name, ignoreCase = true) } ?: false
    }.getOrDefault(false)

    @JvmStatic
    fun simulateChat(message: String) = chat(message)
}
