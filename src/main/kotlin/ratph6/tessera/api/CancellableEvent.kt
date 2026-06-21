package ratph6.tessera.api

/**
 * Passed as the final argument to cancellable trigger callbacks. Call [cancel] to stop the
 * underlying game action (e.g. hide a chat message). Scripts:
 *
 * ```ts
 * import { CancellableEvent } from 'ratph6.tessera.api';
 * export function onChat(message: string, event: CancellableEvent): void {
 *   if (message.includes("spam")) event.cancel();
 * }
 * ```
 */
class CancellableEvent {
    @JvmField var cancelled: Boolean = false

    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled
    fun setCancelled(value: Boolean) { cancelled = value }
}
