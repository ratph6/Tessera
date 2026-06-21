package ratph6.tessera.api

import ratph6.tessera.engine.TesseraEngine
import java.util.function.Consumer

/**
 * The primary scripting entry point. Register triggers with an [Event] and an arrow-function
 * callback that takes a single argument (the event's primary value):
 *
 * ```ts
 * import { Tessera, Event, ChatLib, Player } from 'ratph6.tessera.api';
 *
 * Tessera.register(Event.CHAT, (message) => {
 *   ChatLib.chat("pong!");
 *   // Tessera.cancelEvent();   // hide the original message
 * }).setContains().setCriteria("ping");
 *
 * Tessera.register(Event.COMMAND, (args) => {
 *   ChatLib.chat("XYZ: " + Player.getX());
 * }).setName("coords");
 * ```
 *
 * Callbacks receive the event's primary value (e.g. the chat message, the tick count, the command's
 * argument array). To cancel a cancellable event, call [cancelEvent] from inside the callback.
 */
object Tessera {
    /** Register an arrow-function [callback] for an [Event] (e.g. `Event.CHAT`) or custom type id. */
    @JvmStatic
    fun register(type: String, callback: Consumer<Any?>): TriggerHandle =
        TriggerHandle(TesseraEngine.registerTrigger(type, callback))

    /** Listen for a custom event (see [emit]) or a built-in `tessera:*` event. */
    @JvmStatic
    fun on(eventName: String, callback: Consumer<Any?>): TriggerHandle =
        TriggerHandle(TesseraEngine.registerTrigger("@evt:$eventName", callback))

    /** Fire a custom event; every [on] handler runs with the given payload. */
    @JvmStatic
    fun emit(eventName: String, payload: Any?) = TesseraEngine.emitEvent(eventName, arrayOf(payload))

    /** Cancel the cancellable event currently being dispatched (call from inside a callback). */
    @JvmStatic
    fun cancelEvent() = TesseraEngine.cancelCurrentEvent()

    /** Run [callback] once after [ms] (resolution ~1 tick). */
    @JvmStatic
    fun setTimeout(callback: Runnable, ms: Int): Int = TesseraEngine.scheduleTimer(callback, ms, false)

    /** Run [callback] repeatedly every [ms]. */
    @JvmStatic
    fun setInterval(callback: Runnable, ms: Int): Int = TesseraEngine.scheduleTimer(callback, ms, true)

    /** Run [callback] once after [ticks] client ticks (20 ticks ≈ 1s). Returns an id for [clearTimer]. */
    @JvmStatic
    fun scheduleTask(callback: Runnable, ticks: Int): Int = TesseraEngine.scheduleTickTask(callback, ticks, false)

    /** Run [callback] every [ticks] client ticks. Returns an id for [clearTimer]. */
    @JvmStatic
    fun scheduleInterval(callback: Runnable, ticks: Int): Int = TesseraEngine.scheduleTickTask(callback, ticks, true)

    @JvmStatic
    fun clearTimer(id: Int) = TesseraEngine.clearTimer(id)

    @JvmStatic
    fun loadModule(name: String) = TesseraEngine.loadModuleByName(name)

    @JvmStatic
    fun unloadModule(name: String) = TesseraEngine.unloadModule(name)

    @JvmStatic
    fun reload() = TesseraEngine.reload()

    @JvmStatic
    fun getLoadedModules(): Array<String> = TesseraEngine.loadedModuleNames()

    /** Print to the in-game chat, prefixed with `[Tessera]`. */
    @JvmStatic
    fun log(message: String) = TesseraEngine.consoleLog("info", message)

    /** High-resolution timestamp in milliseconds (for benchmarking). Use instead of `System.*`. */
    @JvmStatic
    fun millis(): Double = System.nanoTime() / 1_000_000.0
}
