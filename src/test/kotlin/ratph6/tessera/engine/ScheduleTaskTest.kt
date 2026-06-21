package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ratph6.tessera.api.Tessera
import ratph6.tessera.triggers.TriggerRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** Verifies Tessera.scheduleTask fires after exactly N client-tick pumps (not before). */
class ScheduleTaskTest {

    @Test fun `scheduleTask fires after N pumps`() {
        TriggerRegistry.clear()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-sched").resolve("modules")
        modules.resolve("s").createDirectories()
        modules.resolve("s/tessera.json").writeText("""{"name":"s","engine":"graal"}""")
        modules.resolve("s/index.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            Tessera.scheduleTask(() => { Tessera.log("fired"); }, 3);
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            fun fired() = captured.any { it.contains("fired") }
            TesseraEngine.pump() // tick 1
            TesseraEngine.pump() // tick 2
            assertEquals(false, fired(), "must not fire before 3 ticks elapse")
            TesseraEngine.pump() // tick 3 → due
            assertEquals(true, fired(), "should fire on the 3rd tick")

            // one-shot: does not fire again
            captured.clear()
            TesseraEngine.pump()
            TesseraEngine.pump()
            assertEquals(false, captured.any { it.contains("fired") }, "one-shot must not repeat")
        } finally {
            TesseraEngine.shutdown()
        }
    }
}
