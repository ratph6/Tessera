package ratph6.tessera.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.tessera.api.Tessera
import ratph6.tessera.triggers.TriggerRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// headless e2e for the GraalJS engine: real ECMAScript the bytecode path can't do
class GraalRuntimeTest {

    @Test
    fun `graal module runs real JS - arrays, let closures, and dispatches`() {
        TriggerRegistry.clear() // shared singleton
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-graal").resolve("modules")

        // no tessera.json -> default engine is "graal"
        modules.resolve("arr").createDirectories()
        modules.resolve("arr/index.ts").writeText(
            """
            import { Tessera, Event } from 'ratph6.tessera.api';

            let hits = 0;                 // closed over by the chat callback
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
            assertEquals(1, TriggerRegistry.byType("chat").size, "chat trigger registered")
            assertEquals(1, TriggerRegistry.byType("command").size, "command registered")

            // closure over `let hits` + array push persist across dispatches
            TesseraEngine.fireChat("chat", "alpha", "alpha")
            TesseraEngine.fireChat("chat", "beta", "beta")

            captured.clear()
            TesseraEngine.dispatchCommand("sum", arrayOf("3", "x", "8", "1"))
            assertTrue(
                captured.any { it.contains("sum=12 n=3 hits=2 recent=alpha,beta") },
                "real arrays + closure over let: $captured",
            )
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear() // shared singleton
        }
    }

    @Test
    fun `a handle can be unregistered and registered again`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-toggle").resolve("modules")
        modules.resolve("t").createDirectories()
        // keep the handle, then drive it off/on from commands — the exact toggle pattern scripts use
        modules.resolve("t/index.ts").writeText(
            """
            import { Tessera, Event } from 'ratph6.tessera.api';
            const h = Tessera.register("toggle", () => Tessera.log("fired"));
            Tessera.register(Event.COMMAND, () => h.unregister()).setName("off");
            Tessera.register(Event.COMMAND, () => h.register()).setName("on");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatch("toggle", 1)                       // initially active -> fires
            assertTrue(captured.any { it.contains("fired") }, "fresh trigger should fire: $captured")

            captured.clear()
            TesseraEngine.dispatchCommand("off", emptyArray())        // h.unregister()
            assertEquals(0, TriggerRegistry.byType("toggle").size, "unregister removes it")
            TesseraEngine.dispatch("toggle", 2)                       // must not fire
            assertTrue(captured.none { it.contains("fired") }, "unregistered trigger must not fire: $captured")

            captured.clear()
            TesseraEngine.dispatchCommand("on", emptyArray())         // h.register() again
            assertEquals(1, TriggerRegistry.byType("toggle").size, "register re-activates it")
            TesseraEngine.dispatch("toggle", 3)                       // fires again
            assertTrue(captured.any { it.contains("fired") }, "re-registered trigger should fire again: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }

    @Test
    fun `Renderer3D resolves as a global and draw calls are safe outside RENDER_WORLD`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-r3d").resolve("modules")
        modules.resolve("r").createDirectories()
        // no import: Renderer3D must resolve as a global; every draw is a no-op with no bound render
        // context and must NOT throw (proves the graceful-degradation contract).
        modules.resolve("r/index.ts").writeText(
            """
            Tessera.register(Event.COMMAND, () => {
              Renderer3D.setDepth(true);
              const c = Renderer3D.color(255, 0, 0);
              Renderer3D.drawBox(0, 0, 0, 1, 1, 1, c, 2);
              Renderer3D.drawLine(0, 0, 0, 1, 1, 1, c, 1);
              Renderer3D.drawFilledBox(0, 0, 0, 1, 1, 1, c);
              Renderer3D.drawText3D("hi", 0, 0, 0, c);
              Tessera.log("r3d ok c=" + c);
            }).setName("r3d");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            captured.clear()
            TesseraEngine.dispatchCommand("r3d", emptyArray())
            // color(255,0,0) packs to 0xFFFF0000 = -65536; the log proves every call ran without error
            assertTrue(captured.any { it.contains("r3d ok c=-65536") }, "Renderer3D global + no-op draws: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
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
            TesseraEngine.dispatch("mytest", 5)        // Integer -> fires
            assertTrue(captured.any { it.contains("fired") }, "matching class should fire: $captured")
            captured.clear()
            TesseraEngine.dispatch("mytest", "hello")  // String -> filtered out
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
            TesseraEngine.pump() // fires the timer -> resolves promise -> drains microtasks
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
        // no import; getInstance() is null headlessly but the global must still resolve
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
            // resolved + static call ran (null is correct headlessly) — no ReferenceError
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

        // no imports: Tessera/Event/BlockPos resolve as globals, bound via Java.type on reference
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

    @Test
    fun `each file has its own scope - same top-level names in different files do not collide`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-scope").resolve("modules")
        modules.resolve("m").createDirectories()
        // a.ts and b.ts BOTH declare `const NAME` and `function helper` at top level.
        modules.resolve("m/a.ts").writeText(
            """
            export const NAME = "A";
            export function helper(): string { return "helperA"; }
            """.trimIndent(),
        )
        modules.resolve("m/b.ts").writeText(
            """
            const NAME = "B-private";                 // same name as a.ts — must stay private
            function helper(): string { return "helperB"; }
            export function bValue(): string { return NAME + ":" + helper(); }
            """.trimIndent(),
        )
        // a root sibling that is never imported must still run once on load (side effect)
        modules.resolve("m/side.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            Tessera.log("side-ran");
            """.trimIndent(),
        )
        modules.resolve("m/index.ts").writeText(
            """
            import { Tessera, Event } from 'ratph6.tessera.api';
            import { NAME, helper } from "./a";
            import { bValue } from "./b";
            Tessera.register(Event.COMMAND, () => {
              Tessera.log(NAME + "|" + helper() + "|" + bValue());
            }).setName("m");
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            assertTrue(captured.any { it.contains("side-ran") }, "un-imported root sibling should run on load: $captured")
            captured.clear()
            TesseraEngine.dispatchCommand("m", emptyArray())
            // a's NAME/helper are "A"/"helperA"; b's private NAME/helper stay "B-private"/"helperB"
            assertTrue(
                captured.any { it.contains("A|helperA|B-private:helperB") },
                "same names in a.ts and b.ts must not collide, sharing only via export/import: $captured",
            )
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }

    @Test
    fun `circular imports resolve - other reads a value exported by index`() {
        TriggerRegistry.clear()
        GraalRuntime.reset()
        val captured = mutableListOf<String>()
        TesseraEngine.chatSink = { captured.add(it) }

        val modules = Files.createTempDirectory("tessera-circ").resolve("modules")
        modules.resolve("c").createDirectories()
        // index exports `shared` at the top, then side-effect-imports other at the bottom;
        // other imports `shared` back from index (a cycle) and reads it on load.
        modules.resolve("c/index.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            export const shared = "from-index";
            import "./other";
            """.trimIndent(),
        )
        modules.resolve("c/other.ts").writeText(
            """
            import { Tessera } from 'ratph6.tessera.api';
            import { shared } from "./index";
            Tessera.log("other sees: " + shared);
            """.trimIndent(),
        )

        TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
        try {
            assertTrue(captured.any { it.contains("other sees: from-index") }, "circular import should resolve: $captured")
        } finally {
            TesseraEngine.shutdown()
            GraalRuntime.reset()
            TriggerRegistry.clear()
        }
    }
}
