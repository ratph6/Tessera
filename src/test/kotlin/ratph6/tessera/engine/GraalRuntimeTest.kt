package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.tessera.api.Tessera
import ratph6.tessera.triggers.TriggerRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Headless end-to-end test of the GraalJS engine (the default). Proves the thing the bytecode path
 * can't do: real ECMAScript — module-level `let` closed over by callbacks, real array methods
 * (`.map`/`.filter`/`.reduce`), and `Array.from` over the command's args — with no `Store`/`Num`/
 * `Args` workarounds. Top-level `Tessera.register(...)` runs on load (GraalJS executes top-level code),
 * and callbacks dispatch through the same engine path as the bytecode modules. No Minecraft.
 */
class GraalRuntimeTest {

    @Test
    fun `graal module runs real JS - arrays, let closures, and dispatches`() {
        TriggerRegistry.clear() // singleton shared across tests
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-graal").resolve("modules")

        // No tessera.json -> default engine is "graal". Plain TypeScript, real JS semantics.
        modules.resolve("arr").createDirectories()
        modules.resolve("arr/index.ts").writeText(
            """
            import { Tessera, Event } from 'ratph6.tessera.api';

            let hits = 0;                 // module-level let, closed over by the chat callback
            const recent: string[] = [];

            Tessera.register(Event.CHAT, (message: string) => {
              hits++;
              recent.push(message);
              if (recent.length > 3) recent.shift();
            });

            Tessera.register(Event.COMMAND, (args) => {
              const nums = Array.from(args).map(Number).filter((n) => !Number.isNaN(n));
              const total = nums.reduce((a, b) => a + b, 0);
              Tessera.log("sum=" + total + " n=" + nums.length + " hits=" + hits + " recent=" + recent.join(","));
            }).setName("sum");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            // Top-level register calls ran during module evaluation.
            assertEquals(1, TriggerRegistry.byType("chat").size, "chat trigger registered")
            assertEquals(1, TriggerRegistry.byType("command").size, "command registered")

            // Closure over `let hits` + array push persist across dispatches.
            TesseraEngine.fireChat("chat", "alpha", "alpha")
            TesseraEngine.fireChat("chat", "beta", "beta")

            // Real array methods over the (host) args array.
            captured.clear()
            TesseraEngine.dispatchCommand("sum", arrayOf("3", "x", "8", "1"))
            assertTrue(
                captured.any { it.contains("sum=12 n=3 hits=2 recent=alpha,beta") },
                "real arrays + closure over let: $captured",
            )
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear() // don't leak triggers into other tests (shared singleton)
        }
    }

    @Test
    fun `setFilteredClass gates dispatch by the event value's class`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-filter").resolve("modules")
        modules.resolve("f").createDirectories()
        modules.resolve("f/index.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            Tessera.register("mytest", () => Tessera.log("fired")).setFilteredClass("Integer");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatch("mytest", 5)        // Integer -> matches -> fires
            assertTrue(captured.any { it.contains("fired") }, "matching class should fire: $captured")
            captured.clear()
            TesseraEngine.dispatch("mytest", "hello")  // String -> no match -> filtered out
            assertTrue(captured.none { it.contains("fired") }, "non-matching class must be filtered: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }

    @Test
    fun `async await resumes after a timer fires`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-async").resolve("modules")
        modules.resolve("a").createDirectories()
        modules.resolve("a/index.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            async function go() {
              Tessera.log("before");
              await sleep(0);     // schedules a Tessera timer; suspends here
              Tessera.log("after");
            }
            go();
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            assertTrue(captured.any { it.contains("before") }, "sync part of async fn ran: $captured")
            assertTrue(captured.none { it.contains("after") }, "must NOT resume before the timer: $captured")
            TesseraEngine.pump() // fires the sleep(0) timer -> resolves the promise -> microtasks drain -> "after"
            assertTrue(captured.any { it.contains("after") }, "async fn resumed after pump: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }

    @Test
    fun `no-import Minecraft client class resolves and static call works`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-mc").resolve("modules")
        modules.resolve("mc").createDirectories()
        // No import for Minecraft; getInstance() returns null headlessly (no client) but must RESOLVE.
        modules.resolve("mc/index.ts").writeText(
            """
            Tessera.register(Event.COMMAND, () => {
              const mc = Minecraft.getInstance();
              Tessera.log("mc=" + (mc === null ? "null" : "present"));
            }).setName("mc");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatchCommand("mc", emptyArray())
            // It resolved + the static call ran (null is the correct headless result) — no ReferenceError.
            assertTrue(captured.any { it.contains("mc=null") || it.contains("mc=present") },
                "Minecraft global should resolve and getInstance() be callable: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }

    @Test
    fun `minecraft and Tessera names work with no import`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-noimport").resolve("modules")

        // No import lines at all: Tessera/Event resolve as Tessera globals, BlockPos as a Minecraft global,
        // each bound via Java.type because the script references them.
        modules.resolve("noimp").createDirectories()
        modules.resolve("noimp/index.ts").writeText(
            """
            Tessera.register(Event.COMMAND, () => {
              const p = new BlockPos(10, 64, -5);
              Tessera.log("pos=" + p.getX() + "," + p.getY() + "," + p.getZ());
            }).setName("pos");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatchCommand("pos", emptyArray())
            assertTrue(
                captured.any { it.contains("pos=10,64,-5") },
                "no-import `new BlockPos(...)` should construct a real Minecraft BlockPos: $captured",
            )
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }
}
