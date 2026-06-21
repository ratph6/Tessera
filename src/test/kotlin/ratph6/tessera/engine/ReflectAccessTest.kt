package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ratph6.mixintest.MixinAccessTarget

/**
 * Reflection accessors must reach private members of an already-instantiated (loaded) object — the
 * case bytecode widening can't handle. Also checks JS-style numeric coercion (a `Double` written into
 * an `int` field).
 */
class ReflectAccessTest {

    @Test fun `reads, writes (with coercion) and invokes private members`() {
        val obj = MixinAccessTarget()

        assertEquals(42, ReflectAccess.getField(obj, "hidden"), "read private field")

        // JS numbers arrive as Double; must coerce into the int field.
        ReflectAccess.setField(obj, "hidden", 100.0)
        assertEquals(100, ReflectAccess.getField(obj, "hidden"), "write private field with coercion")

        assertEquals("shh-100", ReflectAccess.invoke(obj, "secret", emptyArray()), "invoke private method")
    }
}
