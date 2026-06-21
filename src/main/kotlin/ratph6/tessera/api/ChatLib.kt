package ratph6.tessera.api

/**
 * Chat helpers. `import { ChatLib } from 'ratph6.tessera.api'`.
 *
 * Legacy `§` colour codes render as normal; use [addColor] to convert friendlier `&` codes.
 */
object ChatLib {
    private val FORMATTING = Regex("§[0-9a-fk-orA-FK-OR]")

    /** Display a client-side message in chat (only you see it). */
    @JvmStatic
    fun chat(message: String) {
        runCatching { Mc.client.gui.chat.addClientSystemMessage(Mc.literal(message)) }
    }

    /** Send [message] as if the player typed it into chat. */
    @JvmStatic
    fun say(message: String) {
        runCatching { Mc.connection?.sendChat(message) }
    }

    /** Run [command] (leading slash optional). */
    @JvmStatic
    fun command(command: String) {
        runCatching { Mc.connection?.sendCommand(command.removePrefix("/")) }
    }

    @JvmStatic
    fun clearChat() {
        runCatching { Mc.client.gui.chat.clearMessages(true) }
    }

    @JvmStatic
    fun removeFormatting(text: String): String = FORMATTING.replace(text, "")

    /** Convert `&`-style colour codes to `§`. */
    @JvmStatic
    fun addColor(text: String): String = text.replace('&', '§')

    @JvmStatic
    fun isPlayer(name: String): Boolean = runCatching {
        Mc.connection?.onlinePlayers?.any { it.profile.name.equals(name, ignoreCase = true) } ?: false
    }.getOrDefault(false)

    /** Display a fake incoming message client-side. */
    @JvmStatic
    fun simulateChat(message: String) = chat(message)
}
