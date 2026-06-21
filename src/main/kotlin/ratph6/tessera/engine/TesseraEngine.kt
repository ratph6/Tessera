package ratph6.tessera.engine

import ratph6.tessera.api.CancellableEvent
import ratph6.tessera.api.ChatLib
import ratph6.tessera.triggers.TriggerMeta
import ratph6.tessera.triggers.TriggerRegistry
import ratph6.tessera.triggers.TriggerType
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** A captured script error, surfaced by `/te errors` and the console. [stack] is the full chain. */
data class TesseraError(val where: String, val detail: String, val tick: Long, val stack: String? = null)

/** One line for the Tessera console: a level (info/warn/error/debug), origin, message and optional detail. */
data class TesseraLogLine(val level: String, val where: String, val message: String, val detail: String?, val tick: Long)

/**
 * The heart of Tessera. Compiles each module's TypeScript to JVM bytecode (via [TesseraCompiler]), invokes
 * its `main()` entry point, and dispatches triggers by invoking the named exported function through
 * a cached MethodHandle. No V8, no JNI on the dispatch path.
 *
 * Minecraft-free on purpose, so the whole pipeline can be exercised from a plain JVM test.
 */
object TesseraEngine {
    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")

    @Volatile var jsThread: Thread? = null
        private set
    @Volatile var booted = false
        private set
    @Volatile var chatSink: (String) -> Unit = { println("[Tessera] $it") }

    /** Receives every console line (chat, Tessera.log, errors). The console window registers here. */
    @Volatile var consoleSink: (TesseraLogLine) -> Unit = {}

    /** Recent console lines, replayed when the console window opens. */
    private val logBuffer = ArrayDeque<TesseraLogLine>()
    private const val MAX_LOG = 500

    /** Class loader scripts compile against (so `import { ChatLib } from 'ratph6.tessera.api'` resolves). */
    @Volatile var scriptClassLoader: ClassLoader = TesseraEngine::class.java.classLoader

    private var modulesDir: Path? = null
    private val loadedModules = ConcurrentHashMap<String, TesseraModule>()

    /** The module whose code is currently running — read by the API when a script registers things. */
    private val currentModuleTL = ThreadLocal<TesseraModule?>()
    fun currentModule(): TesseraModule? = currentModuleTL.get()

    /** The cancellable event currently being dispatched (for Tessera.cancelEvent()). */
    private val currentEventTL = ThreadLocal<CancellableEvent?>()
    fun cancelCurrentEvent() { currentEventTL.get()?.cancel() }

    private val queue = ConcurrentLinkedQueue<Runnable>()

    private class Timer(val id: Int, var dueNanos: Long, val intervalMs: Long, val module: TesseraModule?, val callback: TesseraCallback)
    private val timers = ConcurrentHashMap<Int, Timer>()
    private val timerIds = AtomicInteger(1)

    /** Tick-based one-shot/repeat tasks (fired from [pump], counted in client ticks not wall-clock). */
    private class TickTask(val id: Int, var dueTick: Long, val repeatTicks: Long, val module: TesseraModule?, val callback: TesseraCallback)
    private val tickTasks = ConcurrentHashMap<Int, TickTask>()

    /** Callbacks run on reload/shutdown so MC-side state (e.g. HUD displays) can be cleared. */
    val resetHooks = java.util.concurrent.CopyOnWriteArrayList<Runnable>()

    /** Invoked (on the JS/render thread) after modules load or reload, so the client can re-register
     *  script commands with brigadier (commands from post-startup loads are otherwise "unknown"). */
    @Volatile var modulesChangedSink: () -> Unit = {}

    private val errors = ArrayDeque<TesseraError>()
    private const val MAX_ERRORS = 50
    @Volatile var tickCount: Long = 0
        private set

    // ----------------------------------------------------------------------------------------------
    // lifecycle
    // ----------------------------------------------------------------------------------------------

    fun bootstrap(modulesDir: Path, scriptClassLoader: ClassLoader) {
        check(!booted) { "TesseraEngine already booted" }
        this.modulesDir = modulesDir
        this.scriptClassLoader = scriptClassLoader
        jsThread = Thread.currentThread()
        booted = true
        log.info("booting (modules dir: {})", modulesDir)
        loadAllModules()
        log.info("booted: {} module(s), {} trigger(s) registered", loadedModules.size, TriggerRegistry.count())
        emitEvent("tessera:ready", loadedModuleNames())
    }

    private fun loadAllModules() {
        val dir = modulesDir ?: return
        val modules = runCatching { TesseraLoader.load(dir, scriptClassLoader) }.getOrElse {
            recordError("loader", it); emptyList()
        }
        for (m in modules) {
            loadedModules[m.name] = m
            activateModule(m)
        }
        runCatching { modulesChangedSink() }
    }

    /** Run a module's entry point (or auto-wire convention functions) with [currentModule] set. */
    private fun activateModule(module: TesseraModule) {
        currentModuleTL.set(module)
        try {
            val entry = when {
                module.hasFunction("__tesseraEntry") -> "__tesseraEntry"   // generated from top-level statements (bytecode path)
                module.hasFunction("main") -> "main"
                module.hasFunction("init") -> "init"
                else -> null
            }
            if (entry != null) {
                invokeFunction(module, entry, emptyList())
            } else {
                // Convention modules: auto-register exported functions named exactly like a trigger.
                for (fn in module.exportedFunctions) {
                    if (!isKnownTrigger(fn)) continue
                    val cb = module.callbackFor(fn) ?: continue
                    TriggerRegistry.register(fn, module, cb)
                }
            }
            emitEvent("tessera:moduleLoaded", arrayOf(module.name))
        } catch (e: Throwable) {
            recordError(module.name, e)
            emitEvent("tessera:scriptError", arrayOf(module.name, rootMessage(e)))
        } finally {
            currentModuleTL.set(null)
        }
    }

    private fun isKnownTrigger(name: String): Boolean = name in TriggerType.ALL

    fun reload() = onJsThread {
        for (name in loadedModules.keys.toList()) purge(name)
        loadedModules.clear()
        timers.clear()
        tickTasks.clear()
        TriggerRegistry.clear()
        MixinManager.clear()
        InstrumentationLoader.resetFailureForRetry()
        resetHooks.forEach { runCatching { it.run() } }
        GraalRuntime.reset()
        loadAllModules()
        chat("§areloaded ${loadedModules.size} module(s)")
        emitEvent("tessera:ready", loadedModuleNames())
    }

    fun loadModuleByName(name: String) = onJsThread {
        if (loadedModules.containsKey(name)) return@onJsThread
        val dir = modulesDir?.resolve(name) ?: return@onJsThread
        val module = runCatching { TesseraLoader.loadModule(dir, scriptClassLoader) }.getOrElse {
            recordError(name, it); return@onJsThread
        }
        loadedModules[module.name] = module
        activateModule(module)
        runCatching { modulesChangedSink() }
    }

    fun unloadModule(name: String) = onJsThread { purge(name) }

    private fun purge(name: String) {
        TriggerRegistry.removeModule(name)
        MixinManager.removeModule(name)
        timers.values.filter { it.module?.name == name }.forEach { timers.remove(it.id) }
        tickTasks.values.filter { it.module?.name == name }.forEach { tickTasks.remove(it.id) }
        loadedModules.remove(name)
        emitEvent("tessera:moduleUnloaded", arrayOf(name))
    }

    fun loadedModuleNames(): Array<String> = loadedModules.keys.toTypedArray()
    fun loadedModuleList(): List<TesseraModule> = loadedModules.values.toList()

    /** Root directory holding every module's folder (`.minecraft/tessera/modules`), or null pre-boot. */
    fun modulesDir(): Path? = modulesDir
    fun shutdown() { timers.clear(); tickTasks.clear(); booted = false }

    // ----------------------------------------------------------------------------------------------
    // threading + per-tick pump
    // ----------------------------------------------------------------------------------------------

    fun isOnJsThread(): Boolean = Thread.currentThread() === jsThread

    inline fun onJsThread(crossinline block: () -> Unit) {
        if (isOnJsThread()) block() else enqueue(Runnable { block() })
    }

    fun enqueue(r: Runnable) = queue.add(r)

    fun pump() {
        if (!booted) return
        tickCount++
        while (true) {
            val r = queue.poll() ?: break
            runCatching { r.run() }.onFailure { recordError("queue", it) }
        }
        if (timers.isNotEmpty()) {
            val now = System.nanoTime()
            for (t in timers.values) if (now >= t.dueNanos) {
                fireTimer(t)
                if (t.intervalMs > 0) t.dueNanos = now + t.intervalMs * 1_000_000 else timers.remove(t.id)
            }
        }
        if (tickTasks.isNotEmpty()) {
            for (t in tickTasks.values) if (tickCount >= t.dueTick) {
                fireTickTask(t)
                if (t.repeatTicks > 0) t.dueTick = tickCount + t.repeatTicks else tickTasks.remove(t.id)
            }
        }
        val steps = TriggerRegistry.byType(TriggerType.STEP)
        if (steps.isNotEmpty()) {
            val now = System.nanoTime()
            for (meta in steps) {
                val due = meta.lastStepNanos + meta.delayMs * 1_000_000L
                if (meta.lastStepNanos == 0L || now >= due) {
                    val elapsed = if (meta.lastStepNanos == 0L) 0.0 else (now - meta.lastStepNanos) / 1e9
                    meta.lastStepNanos = now
                    invokeTrigger(meta, listOf(elapsed))
                }
            }
        }
        emitEvent("tessera:tick", arrayOf(tickCount))
    }

    // ----------------------------------------------------------------------------------------------
    // registration (called by the api.Tessera facade while a module is loading)
    // ----------------------------------------------------------------------------------------------

    private val warnedUnwired = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun registerTrigger(type: String, callback: Any): TriggerMeta {
        if (TriggerType.isUnwired(type) && warnedUnwired.add(type)) {
            recordError(currentModule()?.name ?: "register", "event '$type' has no source hook in this build — its callback will never fire")
        }
        return TriggerRegistry.register(type, currentModule(), Callbacks.resolve(callback))
    }

    fun scheduleTimer(callback: Any, ms: Int, repeat: Boolean): Int {
        val cb = Callbacks.resolve(callback)
        val id = timerIds.getAndIncrement()
        val now = System.nanoTime()
        timers[id] = Timer(id, now + ms.coerceAtLeast(0) * 1_000_000L, if (repeat) ms.coerceAtLeast(1).toLong() else 0L, currentModule(), cb)
        return id
    }

    /** Schedule [callback] to run after [ticks] client ticks (repeating every [ticks] if [repeat]). */
    fun scheduleTickTask(callback: Any, ticks: Int, repeat: Boolean): Int {
        val cb = Callbacks.resolve(callback)
        val id = timerIds.getAndIncrement()
        val delay = ticks.coerceAtLeast(0).toLong()
        tickTasks[id] = TickTask(id, tickCount + delay, if (repeat) delay.coerceAtLeast(1) else 0L, currentModule(), cb)
        return id
    }

    fun clearTimer(id: Int) { timers.remove(id); tickTasks.remove(id) }

    // ----------------------------------------------------------------------------------------------
    // dispatch
    // ----------------------------------------------------------------------------------------------

    /** Fire all triggers of [type]. Returns true if any callback cancelled the event. JS thread only. */
    fun dispatch(type: String, vararg args: Any?): Boolean {
        if (!booted || !isOnJsThread()) return false
        var cancelled = false
        for (meta in TriggerRegistry.byType(type)) {
            if (invokeTrigger(meta, args.asList())) cancelled = true
        }
        return cancelled
    }

    /** Chat path: match each chat trigger's criteria before invoking. */
    // Guards against a chat handler that itself chats with text re-matching its criteria.
    private var chatDepth = 0

    fun fireChat(type: String, formatted: String, unformatted: String): Boolean {
        if (!booted || !isOnJsThread()) return false
        if (chatDepth >= 8) return false // runaway recursion safety net
        chatDepth++
        try {
            var cancelled = false
            for (meta in TriggerRegistry.byType(type)) {
                if (!meta.matchesChat(unformatted)) continue
                if (invokeTrigger(meta, listOf(formatted))) cancelled = true
            }
            return cancelled
        } finally {
            chatDepth--
        }
    }

    /** True if [obj]'s class (or any superclass) matches [name] by simple or fully-qualified name. */
    private fun classMatches(name: String, raw: Any?): Boolean {
        // Entity-bearing events dispatch an EntityWrapper, not the raw entity — match against the
        // wrapped entity so setFilteredClass("Bat") / full-name filters work as scripts expect.
        val obj = (raw as? ratph6.tessera.api.EntityWrapper)?.handle ?: raw
        if (obj == null) return false
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            if (c.simpleName == name || c.name == name || c.name.endsWith(".$name")) return true
            c = c.superclass
        }
        return false
    }

    /** Invoke one trigger's callback; returns whether its event was cancelled. */
    private fun invokeTrigger(meta: TriggerMeta, args: List<Any?>): Boolean {
        // setFilteredClass: only fire when the event's primary value is (a subtype of) the named class.
        val filter = meta.filterClass
        if (filter != null && !classMatches(filter, args.firstOrNull())) return false

        val event = if (meta.cancelable) CancellableEvent() else null
        currentModuleTL.set(meta.module)
        currentEventTL.set(event)
        try {
            meta.callback.invoke(args)
        } catch (e: Throwable) {
            recordError("trigger:${meta.type}", e)
        } finally {
            currentEventTL.set(null)
            currentModuleTL.set(null)
        }
        return event?.cancelled == true
    }

    /** Invoke a TS-mixin callback (called from [MixinHooks] on whatever thread the target method runs). */
    fun invokeMixin(hook: MixinRegistry.Hook, ctx: ratph6.tessera.api.MixinContext) {
        val prev = currentModuleTL.get()
        currentModuleTL.set(hook.module)
        try {
            hook.callback.invoke(listOf(ctx))
        } catch (e: Throwable) {
            recordError("mixin:${hook.targetBinary}.${hook.method}", e)
        } finally {
            currentModuleTL.set(prev)
        }
    }

    private fun fireTimer(t: Timer) {
        currentModuleTL.set(t.module)
        try {
            t.callback.invoke(emptyList())
        } catch (e: Throwable) {
            recordError("timer", e)
        } finally {
            currentModuleTL.set(null)
        }
    }

    private fun fireTickTask(t: TickTask) {
        currentModuleTL.set(t.module)
        try {
            t.callback.invoke(emptyList())
        } catch (e: Throwable) {
            recordError("scheduleTask", e)
        } finally {
            currentModuleTL.set(null)
        }
    }

    private fun invokeFunction(module: TesseraModule, functionName: String, args: List<Any?>) {
        // Arity is reconciled inside the callback (HandleCallback pads/truncates; JS ignores surplus).
        module.callbackFor(functionName)?.invoke(args)
    }

    /** Run [block] with [module] as the current module (so registrations inside it attach correctly). */
    internal fun <T> withCurrentModule(module: TesseraModule?, block: () -> T): T {
        val prev = currentModuleTL.get()
        currentModuleTL.set(module)
        try {
            return block()
        } finally {
            currentModuleTL.set(prev)
        }
    }

    /**
     * Run a one-off TypeScript snippet (used by `/te eval`) on GraalJS, so it gets real ECMAScript
     * (arrays, closures, `let`, JSON). The Tessera API objects (`Tessera`, `Event`, `ChatLib`, ...) are bound
     * as globals by [GraalRuntime], so no import line is needed.
     */
    fun evaluate(code: String) = onJsThread {
        runCatching {
            GraalRuntime.evalSnippet(code)
            chat("§aeval ok")
        }.onFailure { recordError("eval", it); chat("§ceval error: ${rootMessage(it)}") }
    }

    /** Run a client command registered by a script (by name), passing the raw args array. */
    fun dispatchCommand(name: String, args: Array<String>): Boolean {
        if (!booted || !isOnJsThread()) return false
        val meta = TriggerRegistry.commandByName(name) ?: return false
        invokeTrigger(meta, listOf<Any?>(args))
        return true
    }

    /** Emit a custom event to scripts listening via `Tessera.on(name, fn)`. */
    fun emitEvent(name: String, args: Array<out Any?>) {
        onJsThread { dispatch("@evt:$name", *args) }
    }

    /**
     * Dispatch from any thread (e.g. a mixin on the netty/packet thread): marshalled onto the JS
     * thread, so it's observe-only (the originating call has already proceeded — no inline cancel).
     * Cheaply skips work when no script listens for [type], so high-volume sources (packets) are free
     * until something registers.
     */
    fun dispatchAsync(type: String, vararg args: Any?) {
        if (!booted || !TriggerRegistry.hasType(type)) return
        onJsThread { dispatch(type, *args) }
    }

    // ----------------------------------------------------------------------------------------------
    // logging / errors
    // ----------------------------------------------------------------------------------------------

    fun chat(message: String) {
        chatSink("§7[§bTessera§7]§r $message")
        emitConsole("info", "chat", ChatLib.removeFormatting(message), null)
    }

    fun consoleLog(level: String, message: String) {
        val color = when (level) { "warn" -> "§e"; "error" -> "§c"; "debug" -> "§8"; else -> "§r" }
        chatSink("§7[§bTessera§7]§r $color$message")
        emitConsole(level, "log", message, null)
    }

    /** Record a script error from a string detail (origin already known). */
    fun recordError(where: String, detail: String) = recordError(where, detail, null)

    /** Record a script error from a [Throwable], capturing the full cause chain and stack trace. */
    fun recordError(where: String, t: Throwable) = recordError(where, describe(t), stackString(t))

    private fun recordError(where: String, detail: String, stack: String?) {
        synchronized(errors) {
            errors.addLast(TesseraError(where, detail, tickCount, stack))
            while (errors.size > MAX_ERRORS) errors.removeFirst()
        }
        log.warn("error in {}: {}", where, detail)
        emitConsole("error", where, detail, stack)
    }

    fun recentErrors(n: Int = 10): List<TesseraError> = synchronized(errors) { errors.toList().takeLast(n) }

    /** A snapshot of recent console lines (for replay when the console opens). */
    fun recentLog(): List<TesseraLogLine> = synchronized(logBuffer) { logBuffer.toList() }

    private fun emitConsole(level: String, where: String, message: String, detail: String?) {
        val line = TesseraLogLine(level, where, message, detail, tickCount)
        synchronized(logBuffer) {
            logBuffer.addLast(line)
            while (logBuffer.size > MAX_LOG) logBuffer.removeFirst()
        }
        runCatching { consoleSink(line) }
    }

    /** Deepest cause as `Type: message` — the human-readable headline for an error. */
    private fun rootMessage(t: Throwable): String {
        val cur = rootCause(t)
        return "${cur::class.simpleName}: ${cur.message ?: ""}"
    }

    /** The full cause chain, newest first, for richer error descriptions. */
    private fun describe(t: Throwable): String = buildString {
        var cur: Throwable? = t
        var first = true
        while (cur != null) {
            append(if (first) "" else "caused by: ")
            append(cur::class.simpleName).append(": ").append(cur.message ?: "(no message)")
            first = false
            val next = cur.cause
            cur = if (next === cur) null else next
            if (cur != null) append('\n')
        }
    }

    private fun rootCause(t: Throwable): Throwable {
        var cur: Throwable = t
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }

    /** Full stack trace of the deepest cause, trimmed to the most relevant frames. */
    private fun stackString(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }
}
