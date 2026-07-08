package ratph6.tessera.api

// Cancel-flag holder for the event currently being dispatched. Scripts don't receive this object —
// call Tessera.cancelEvent() inside a callback to cancel; the engine reads the flag afterwards.
class CancellableEvent {
    @JvmField var cancelled: Boolean = false

    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled
    fun setCancelled(value: Boolean) { cancelled = value }
}
