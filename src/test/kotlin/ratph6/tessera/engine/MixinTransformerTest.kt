package ratph6.tessera.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.mixintest.MixinTarget
import ratph6.tessera.api.MixinContext
import java.util.function.Consumer

/**
 * Exercises [MixinTransformer] end to end against bytecode (HEAD cancel/override, RETURN override) by
 * transforming [MixinTarget] in memory, loading the rewritten class in a child loader, and invoking it.
 */
class MixinTransformerTest {

    private val targetBinary = MixinTarget::class.java.name
    private val targetInternal = targetBinary.replace('.', '/')

    @AfterEach fun cleanup() = MixinRegistry.clear().let { }

    /** Loads exactly [name] from [bytes]; everything else (MixinHooks, MixinContext, ...) delegates to the parent. */
    private class Rewriter(val target: String, val bytes: ByteArray, parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(n: String, resolve: Boolean): Class<*> {
            if (n == target) synchronized(getClassLoadingLock(n)) {
                (findLoadedClass(n) ?: defineClass(n, bytes, 0, bytes.size)).let {
                    if (resolve) resolveClass(it)
                    return it
                }
            }
            return super.loadClass(n, resolve)
        }
    }

    private fun rewriteAndLoad(): Any {
        val parent = MixinTarget::class.java.classLoader
        val original = parent.getResourceAsStream("$targetInternal.class")!!.use { it.readBytes() }
        val transformed = MixinTransformer.transform(parent, targetInternal, null, null, original)
            ?: error("transformer made no change — no hook matched")
        val cls = Rewriter(targetBinary, transformed, parent).loadClass(targetBinary)
        return cls.getDeclaredConstructor().newInstance()
    }

    private fun hook(method: String, at: String, body: (MixinContext) -> Unit) {
        MixinRegistry.add(targetBinary, method, null, at, null, Callbacks.resolve(Consumer<Any?> { body(it as MixinContext) }))
    }

    @Test fun `HEAD cancel skips the original body`() {
        hook("sideEffect", MixinAt.HEAD) { it.cancel() }
        val obj = rewriteAndLoad()
        obj.javaClass.getMethod("sideEffect").invoke(obj)
        assertEquals(0, obj.javaClass.getMethod("getCounter").invoke(obj), "cancelled void method ran its body")
    }

    @Test fun `HEAD passthrough still runs the original body`() {
        hook("sideEffect", MixinAt.HEAD) { /* observe only */ }
        val obj = rewriteAndLoad()
        obj.javaClass.getMethod("sideEffect").invoke(obj)
        assertEquals(1, obj.javaClass.getMethod("getCounter").invoke(obj))
    }

    @Test fun `HEAD setReturnValue overrides and receives boxed args`() {
        var seenArg: Any? = null
        hook("greet", MixinAt.HEAD) { ctx -> seenArg = ctx.getArg(0); ctx.setReturnValue("override") }
        val obj = rewriteAndLoad()
        val out = obj.javaClass.getMethod("greet", Int::class.javaPrimitiveType).invoke(obj, 7)
        assertEquals("override", out)
        assertEquals(7, seenArg, "int argument should arrive boxed")
    }

    @Test fun `RETURN override replaces a primitive return value`() {
        hook("flag", MixinAt.RETURN) { it.setReturnValue(true) }
        val obj = rewriteAndLoad()
        assertEquals(true, obj.javaClass.getMethod("flag").invoke(obj))
    }

    @Test fun `RETURN passthrough keeps the original return value`() {
        hook("flag", MixinAt.RETURN) { /* observe only */ }
        val obj = rewriteAndLoad()
        assertFalse(obj.javaClass.getMethod("flag").invoke(obj) as Boolean)
    }
}
