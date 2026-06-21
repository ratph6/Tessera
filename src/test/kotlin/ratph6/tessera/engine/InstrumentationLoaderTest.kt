package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// test JVM has no allowAttachSelf flag, so this exercises the external-attach fallback
class InstrumentationLoaderTest {

    @Test fun `attaches and yields a retransform-capable Instrumentation`() {
        val inst = InstrumentationLoader.instrumentation()
        assertTrue(inst.isRetransformClassesSupported, "instrumentation should support retransform")
    }
}
