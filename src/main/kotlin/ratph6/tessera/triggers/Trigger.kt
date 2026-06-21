package ratph6.tessera.triggers

import ratph6.tessera.engine.TesseraCallback
import ratph6.tessera.engine.TesseraModule

enum class MatchMode { EXACT, CONTAINS, START, END }

// one registered trigger. module is null for /te eval snippets.
class TriggerMeta(
    val id: Int,
    @Volatile var type: String,
    val module: TesseraModule?,
    val callback: TesseraCallback,
) {
    @Volatile var enabled: Boolean = true
    @Volatile var priority: Int = 0
    @Volatile var cancelable: Boolean = TriggerType.isCancellable(type)

    @Volatile var criteria: String? = null
    @Volatile var matchMode: MatchMode = MatchMode.EXACT

    @Volatile var delayMs: Int = 1000
    @Volatile var lastStepNanos: Long = 0L

    @Volatile var name: String? = null
    @Volatile var sound: String? = null
    @Volatile var filterClass: String? = null
    @Volatile var eventClass: String? = null

    fun matchesChat(unformatted: String): Boolean {
        val c = criteria ?: return true
        return when (matchMode) {
            MatchMode.EXACT -> unformatted == c
            MatchMode.CONTAINS -> unformatted.contains(c)
            MatchMode.START -> unformatted.startsWith(c)
            MatchMode.END -> unformatted.endsWith(c)
        }
    }
}
