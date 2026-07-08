package ratph6.tessera.api

import ratph6.tessera.engine.TesseraEngine
import java.util.function.Consumer

// Primary scripting entry point. Register triggers with an Event and an arrow-function callback
// taking the event's primary value.
object Tessera {
    @JvmStatic
    fun register(type: String, callback: Consumer<Any?>): TriggerHandle =
        TriggerHandle(TesseraEngine.registerTrigger(type, callback))

    // every Event catalogue value — used to catch on()/register() mixups
    private val BUILTIN_EVENTS: Set<String> by lazy {
        Event::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .mapNotNull { runCatching { it.apply { isAccessible = true }.get(null) as String }.getOrNull() }
            .toSet()
    }

    // listen for a custom event (see emit) or a built-in tessera:* event
    @JvmStatic
    fun on(eventName: String, callback: Consumer<Any?>): TriggerHandle {
        // on(Event.CHAT) registers "@evt:chat", which no built-in hook ever emits — flag it
        if (eventName in BUILTIN_EVENTS) {
            TesseraEngine.recordError(
                "Tessera.on",
                "'$eventName' is a built-in Event — use Tessera.register(Event....); on() is only for emit() events and tessera:* bus events")
        }
        return TriggerHandle(TesseraEngine.registerTrigger("@evt:$eventName", callback))
    }

    // fire a custom event; every on() handler runs with the payload
    @JvmStatic
    fun emit(eventName: String, payload: Any?) = TesseraEngine.emitEvent(eventName, arrayOf(payload))

    // call from inside a callback to cancel the event being dispatched
    @JvmStatic
    fun cancelEvent() = TesseraEngine.cancelCurrentEvent()

    // resolution ~1 tick
    @JvmStatic
    fun setTimeout(callback: Runnable, ms: Int): Int = TesseraEngine.scheduleTimer(callback, ms, false)

    @JvmStatic
    fun setInterval(callback: Runnable, ms: Int): Int = TesseraEngine.scheduleTimer(callback, ms, true)

    // tick-based; 20 ticks ≈ 1s. id for clearTimer
    @JvmStatic
    fun scheduleTask(callback: Runnable, ticks: Int): Int = TesseraEngine.scheduleTickTask(callback, ticks, false)

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

    // prints to chat prefixed with [Tessera]
    @JvmStatic
    fun log(message: String) = TesseraEngine.consoleLog("info", message)

    // high-res monotonic ms — for measuring deltas only; NOT wall-clock, origin is arbitrary
    @JvmStatic
    fun millis(): Double = System.nanoTime() / 1_000_000.0
}
