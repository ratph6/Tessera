package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.tessera.api.Tessera
import ratph6.tessera.triggers.MatchMode
import ratph6.tessera.triggers.TriggerRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// headless e2e for the bytecode engine: TS -> JVM bytecode, arrow callbacks via MethodHandle
class TesseraBytecodeEngineTest {

    @Test
    fun `arrow callbacks, Event enum and auto-wrapped top-level registration all work`() {
        TriggerRegistry.clear() // shared singleton
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val root = Files.createTempDirectory("tessera-bc-test")
        val modules = root.resolve("modules")

        modules.resolve("demo").createDirectories()
        modules.resolve("demo/tessera.json").writeText("""{"name":"demo","engine":"bytecode"}""")
        modules.resolve("demo/index.ts").writeText(
            """
            import { Tessera, Event } from 'ratph6.tessera.api';

            Tessera.register(Event.CHAT, (message) => { Tessera.log("heard:" + message); })
               .setContains().setCriteria("ping");
            Tessera.on("custom:hi", (payload) => { Tessera.log("hi-event"); });
            Tessera.setTimeout(() => { Tessera.log("timer-fired"); }, 0);
            """.trimIndent(),
        )

        // convention module: exported function named after a trigger
        modules.resolve("conv").createDirectories()
        modules.resolve("conv/tessera.json").writeText("""{"name":"conv","engine":"bytecode"}""")
        modules.resolve("conv/index.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            export function tick(): void { Tessera.log("conv-tick"); }
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            val chat = TriggerRegistry.byType("chat")
            assertEquals(1, chat.size, "one chat trigger")
            assertEquals(MatchMode.CONTAINS, chat[0].matchMode)
            assertEquals("ping", chat[0].criteria)
            assertEquals(1, TriggerRegistry.byType("tick").size, "convention auto-registered tick")

            // criteria gating
            captured.clear()
            TesseraEngine.fireChat("chat", "pong", "pong")
            assertTrue(captured.none { it.contains("heard:") }, "non-matching chat must not fire")
            TesseraEngine.fireChat("chat", "ping there", "ping there")
            assertTrue(captured.any { it.contains("heard:ping there") }, "matching chat should fire arrow")

            // arity adaptation: tick() takes no args, we pass a count
            captured.clear()
            TesseraEngine.dispatch("tick", 1L)
            assertTrue(captured.any { it.contains("conv-tick") }, "tick dispatch should run convention fn")

            captured.clear()
            TesseraEngine.emitEvent("custom:hi", emptyArray())
            TesseraEngine.pump()
            assertTrue(captured.any { it.contains("hi-event") }, "custom event should reach Tessera.on arrow")

            assertTrue(captured.any { it.contains("timer-fired") }, "setTimeout arrow should fire on pump")
        } finally {
            TesseraEngine.shutdown()
        }
    }

    @Test
    fun `module-level state, Num and Args compile and run (the Cubed-port idioms)`() {
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val root = Files.createTempDirectory("tessera-cubed-test")
        val modules = root.resolve("modules")

        // Cubed-port idioms: module-level state mutated from a callback, args via Num/Args
        modules.resolve("state").createDirectories()
        modules.resolve("state/tessera.json").writeText("""{"name":"state","engine":"bytecode"}""")
        modules.resolve("state/index.ts").writeText(
            """
            import { Tessera, Num, Args } from 'ratph6.tessera.api';

            let total: number = 0;
            let on = false;

            Tessera.register("command", (args) => {
              on = !on;
              total = total + Num.parse(Args.get(args, 0), 1);
              Tessera.log("count=" + Args.count(args) + " total=" + total + " on=" + on);
            }).setName("acc");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatchCommand("acc", arrayOf("2.5", "ignored"))
            TesseraEngine.dispatchCommand("acc", arrayOf("4"))
            assertTrue(captured.any { it.contains("count=2 total=2.5 on=true") }, "first call: $captured")
            assertTrue(captured.any { it.contains("count=1 total=6.5 on=false") }, "second call: $captured")
        } finally {
            TesseraEngine.shutdown()
        }
    }
}
