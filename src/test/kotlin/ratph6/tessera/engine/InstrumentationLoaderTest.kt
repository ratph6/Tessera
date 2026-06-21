package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Proves the attach pipeline works outside Minecraft. The gradle test JVM is not launched with
 * `-Djdk.attach.allowAttachSelf=true`, so this exercises the external-process fallback end to end.
 */
class InstrumentationLoaderTest {

    @Test fun `attaches and yields a retransform-capable Instrumentation`() {
        val inst = InstrumentationLoader.instrumentation()
        assertTrue(inst.isRetransformClassesSupported, "instrumentation should support retransform")
    }
}
