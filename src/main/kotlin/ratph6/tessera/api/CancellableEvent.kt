package ratph6.tessera.api

// Passed last to cancellable callbacks; cancel() stops the underlying game action.
class CancellableEvent {
    @JvmField var cancelled: Boolean = false

    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled
    fun setCancelled(value: Boolean) { cancelled = value }
}
