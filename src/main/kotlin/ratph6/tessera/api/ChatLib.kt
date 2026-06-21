package ratph6.tessera.api

// Chat helpers. § codes render as normal; addColor converts friendlier & codes.
object ChatLib {
    private val FORMATTING = Regex("§[0-9a-fk-orA-FK-OR]")

    // client-side only — just you see it
    @JvmStatic
    fun chat(message: String) {
        runCatching { Mc.client.gui.chat.addClientSystemMessage(Mc.literal(message)) }
    }

    // send as if typed into chat
    @JvmStatic
    fun say(message: String) {
        runCatching { Mc.connection?.sendChat(message) }
    }

    @JvmStatic
    fun command(command: String) {
        runCatching { Mc.connection?.sendCommand(command.removePrefix("/")) } // leading slash optional
    }

    @JvmStatic
    fun clearChat() {
        runCatching { Mc.client.gui.chat.clearMessages(true) }
    }

    @JvmStatic
    fun removeFormatting(text: String): String = FORMATTING.replace(text, "")

    @JvmStatic
    fun addColor(text: String): String = text.replace('&', '§')

    @JvmStatic
    fun isPlayer(name: String): Boolean = runCatching {
        Mc.connection?.onlinePlayers?.any { it.profile.name.equals(name, ignoreCase = true) } ?: false
    }.getOrDefault(false)

    @JvmStatic
    fun simulateChat(message: String) = chat(message)
}
