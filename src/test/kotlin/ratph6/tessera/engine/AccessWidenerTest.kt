package ratph6.tessera.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.mixintest.MixinAccessTarget
import java.lang.reflect.Modifier

/**
 * Verifies [MixinTransformer] applies [AccessRegistry] widenings at initial class load: the target's
 * private field/method become public and the final class loses `final`. No Minecraft, no agent.
 */
class AccessWidenerTest {

    private val targetBinary = MixinAccessTarget::class.java.name
    private val targetInternal = targetBinary.replace('.', '/')

    @AfterEach fun cleanup() = AccessRegistry.clear().let { }

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

    private fun rewriteAndLoad(): Class<*> {
        val parent = MixinAccessTarget::class.java.classLoader
        val original = parent.getResourceAsStream("$targetInternal.class")!!.use { it.readBytes() }
        // classBeingRedefined = null → the initial-load path, where access widening is legal.
        val transformed = MixinTransformer.transform(parent, targetInternal, null, null, original)
            ?: error("transformer made no change — no widening matched")
        return Rewriter(targetBinary, transformed, parent).loadClass(targetBinary)
    }

    @Test fun `widens a private field, a private method, and drops final on the class`() {
        AccessRegistry.add(targetBinary, AccessRegistry.Kind.FIELD, "hidden", null, null)
        AccessRegistry.add(targetBinary, AccessRegistry.Kind.METHOD, "secret", null, null)
        AccessRegistry.add(targetBinary, AccessRegistry.Kind.CLASS, null, null, null)

        val cls = rewriteAndLoad()

        assertFalse(Modifier.isFinal(cls.modifiers), "class should no longer be final")
        assertTrue(Modifier.isPublic(cls.modifiers), "class should be public")

        val field = cls.getDeclaredField("hidden")
        assertTrue(Modifier.isPublic(field.modifiers), "field should be public")
        assertFalse(Modifier.isFinal(field.modifiers), "field should not be final")

        val method = cls.getDeclaredMethod("secret")
        assertTrue(Modifier.isPublic(method.modifiers), "method should be public")
    }
}
