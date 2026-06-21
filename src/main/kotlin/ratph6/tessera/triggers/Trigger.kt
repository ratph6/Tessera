package ratph6.tessera.triggers

import ratph6.tessera.engine.TesseraCallback
import ratph6.tessera.engine.TesseraModule

/** How a chat-like trigger's [TriggerMeta.criteria] is compared against an incoming message. */
enum class MatchMode { EXACT, CONTAINS, START, END }

/**
 * One registered trigger. The [callback] (an arrow function, or a named function via convention) is
 * stored as a uniform [TesseraCallback], so the engine invokes bytecode lambdas and GraalJS guest
 * functions the same way. [module] is nullable (null for `/te eval` snippets) and is only used to
 * unload a module's hooks.
 */
class TriggerMeta(
    val id: Int,
    @Volatile var type: String,
    val module: TesseraModule?,
    val callback: TesseraCallback,
) {
    @Volatile var enabled: Boolean = true
    @Volatile var priority: Int = 0
    @Volatile var cancelable: Boolean = TriggerType.isCancellable(type)

    // chat-like matching
    @Volatile var criteria: String? = null
    @Volatile var matchMode: MatchMode = MatchMode.EXACT

    // step timing
    @Volatile var delayMs: Int = 1000
    @Volatile var lastStepNanos: Long = 0L

    // misc filters
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
