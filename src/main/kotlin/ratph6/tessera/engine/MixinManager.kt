package ratph6.tessera.engine

import java.util.function.Consumer

/**
 * Front door for TypeScript mixins. Lazily self-attaches the instrumentation agent and installs
 * [MixinTransformer] on the first injection, registers each hook in [MixinRegistry], and triggers a
 * retransform so the change takes effect immediately — even for Minecraft classes that are already
 * loaded. Reverting (on `/te reload` or module unload) retransforms the affected classes back to their
 * original bytes.
 *
 * All calls are expected on the JS/render thread (where scripts register), matching the rest of the
 * engine.
 */
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

    /** Register a runtime access widening (make a class/method/field public, non-final). */
    fun widen(target: String, kind: AccessRegistry.Kind, member: String?, descriptor: String?) {
        ensureTransformer()
        AccessRegistry.add(target, kind, member, descriptor, TesseraEngine.currentModule())
        // Access widening can only be applied while a class is being defined (the JVM forbids modifier
        // changes on a loaded class). If the target is already loaded, the widening only takes effect on a
        // later load — tell the user instead of silently doing nothing.
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
            // Throws with a user-facing message if self-attach isn't available — surfaced to the script.
            val inst = InstrumentationLoader.instrumentation()
            // Fully define every type transform() reaches BEFORE the transformer goes live. Otherwise the
            // JVM invokes transform() while one of these is still mid-definition → duplicate class def.
            warmup()
            inst.addTransformer(MixinTransformer, true)
            transformerInstalled = true
        }
    }

    /** Touch every class [MixinTransformer.transform] references so none is mid-definition when it runs. */
    private fun warmup() {
        MixinRegistry.isEmpty()
        AccessRegistry.isEmpty()
        AccessRegistry.Kind.CLASS
        MixinHooks.hashCode()
        MixinTransformer.hashCode()
        MixinAt.HEAD
        ratph6.tessera.api.MixinContext::class.java.name
    }

    /** Re-apply the transformer to [binary]'s already-loaded class so registry changes take effect now. */
    private fun retransform(binary: String) {
        val inst = InstrumentationLoader.instrumentationOrNull() ?: return
        val cls = runCatching { Class.forName(binary, false, TesseraEngine.scriptClassLoader) }.getOrNull()
            ?: return // not loaded yet — the transformer will catch it when the class is first loaded
        if (!inst.isModifiableClass(cls)) {
            TesseraEngine.recordError("mixin", "$binary cannot be modified by instrumentation")
            return
        }
        runCatching { inst.retransformClasses(cls) }
            .onFailure { TesseraEngine.recordError("mixin:retransform:$binary", it) }
    }
}
