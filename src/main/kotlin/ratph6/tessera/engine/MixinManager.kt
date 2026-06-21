package ratph6.tessera.engine

import java.util.function.Consumer

// Front door for TS mixins: attaches the agent + installs MixinTransformer on first injection, registers
// hooks, and retransforms so the change takes effect now (even for already-loaded classes). All calls
// expected on the JS/render thread.
object MixinManager {

    @Volatile private var transformerInstalled = false

    fun register(target: String, method: String, descriptor: String?, at: String, callback: Consumer<Any?>): MixinRegistry.Hook {
        ensureTransformer()
        val normalizedAt = normalizeAt(at)
        val cb = Callbacks.resolve(callback)
        val hook = MixinRegistry.add(target, method, descriptor, normalizedAt, TesseraEngine.currentModule(), cb)
        retransform(hook.targetBinary)
        return hook
    }

    fun remove(hook: MixinRegistry.Hook) {
        val binary = MixinRegistry.remove(hook) ?: return
        retransform(binary)
    }

    // make a class/method/field public, non-final
    fun widen(target: String, kind: AccessRegistry.Kind, member: String?, descriptor: String?) {
        ensureTransformer()
        AccessRegistry.add(target, kind, member, descriptor, TesseraEngine.currentModule())
        // JVM forbids modifier changes on a loaded class — if already loaded, widening only applies on a
        // later load, so warn instead of silently doing nothing.
        val alreadyLoaded = runCatching {
            Class.forName(target, false, TesseraEngine.scriptClassLoader)
        }.getOrNull() != null
        if (alreadyLoaded) {
            TesseraEngine.recordError(
                "accessWidener",
                "$target is already loaded — access widening only applies to classes not yet loaded this " +
                    "session. Widen it before the class is first used, or restart.",
            )
        }
    }

    fun removeModule(moduleName: String) {
        (MixinRegistry.removeModule(moduleName) + AccessRegistry.removeModule(moduleName)).forEach { retransform(it) }
    }

    fun clear() {
        (MixinRegistry.clear() + AccessRegistry.clear()).forEach { retransform(it) }
    }

    private fun normalizeAt(at: String): String = when (at.trim().uppercase()) {
        "HEAD" -> MixinAt.HEAD
        "RETURN", "TAIL" -> MixinAt.RETURN
        else -> throw IllegalArgumentException("unknown injection point '$at' (use 'HEAD' or 'RETURN')")
    }

    private fun ensureTransformer() {
        if (transformerInstalled) return
        synchronized(this) {
            if (transformerInstalled) return
            val inst = InstrumentationLoader.instrumentation()
            // Fully define every type transform() reaches BEFORE the transformer goes live, else the JVM
            // calls transform() while one is mid-definition -> duplicate class def.
            warmup()
            inst.addTransformer(MixinTransformer, true)
            transformerInstalled = true
        }
    }

    // touch every class transform() references so none is mid-definition when it runs
    private fun warmup() {
        MixinRegistry.isEmpty()
        AccessRegistry.isEmpty()
        AccessRegistry.Kind.CLASS
        MixinHooks.hashCode()
        MixinTransformer.hashCode()
        MixinAt.HEAD
        ratph6.tessera.api.MixinContext::class.java.name
    }

    // re-apply the transformer to binary's already-loaded class so registry changes take effect now
    private fun retransform(binary: String) {
        val inst = InstrumentationLoader.instrumentationOrNull() ?: return
        val cls = runCatching { Class.forName(binary, false, TesseraEngine.scriptClassLoader) }.getOrNull()
            ?: return // not loaded yet — transformer catches it on first load
        if (!inst.isModifiableClass(cls)) {
            TesseraEngine.recordError("mixin", "$binary cannot be modified by instrumentation")
            return
        }
        runCatching { inst.retransformClasses(cls) }
            .onFailure { TesseraEngine.recordError("mixin:retransform:$binary", it) }
    }
}
