package ratph6.tessera.engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ratph6.tessera.api.Tessera
import ratph6.tessera.triggers.TriggerRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class TextualWrapTest {
  @Test fun `textual wrap runs top-level state + callbacks`() {
    // textual wrap is the primary path: top-level-only script, module state mutates across callbacks
    TriggerRegistry.clear() // shared singleton
    val captured = mutableListOf<String>()
    TesseraEngine.chatSink = { captured.add(it) }
    val modules = Files.createTempDirectory("tessera-tw").resolve("modules")
    modules.resolve("cub").createDirectories()
    modules.resolve("cub/tessera.json").writeText("""{"name":"cub","engine":"bytecode"}""")
    modules.resolve("cub/index.ts").writeText(
      """
      import { Tessera, Num, Args } from 'ratph6.tessera.api';
      // header comment with § characters: §a§b
      let total: number = 0;
      let on = false;
      Tessera.register("command", (args) => {
        on = !on;
        total = total + Num.parse(Args.get(args, 0), 1);
        Tessera.log("§atotal=" + total + " on=" + on + " n=" + Args.count(args));
      }).setName("twacc");
      """.trimIndent(),
    )
    TesseraEngine.bootstrap(modules, Tessera::class.java.classLoader)
    try {
      assertEquals(1, TriggerRegistry.byType("command").size, "command registered")
      captured.clear()
      TesseraEngine.dispatchCommand("twacc",arrayOf("2.5", "z"))
      TesseraEngine.dispatchCommand("twacc",arrayOf("4"))
      assertTrue(captured.any { it.contains("total=2.5 on=true n=2") }, "1st: $captured")
      assertTrue(captured.any { it.contains("total=6.5 on=false n=1") }, "2nd: $captured")
    } finally { TesseraEngine.shutdown() }
  }

  @Test fun `the real cubed example compiles to a runnable class`() {
    // regression: the shipped Cubed module must compile and expose a runnable __tesseraEntry
    val src = Files.readString(java.nio.file.Path.of("examples/cubed/index.ts"))
    val runner = TesseraCompiler.compile(src, "cubed/index.ts", Tessera::class.java.classLoader)
    assertTrue(
      runner.defaultClass.declaredMethods.any { it.name == "__tesseraEntry" },
      "cubed should compile to a \$ class with __tesseraEntry",
    )
  }
}
