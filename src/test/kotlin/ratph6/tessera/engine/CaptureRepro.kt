package ratph6.tessera.engine
import org.junit.jupiter.api.Test
class CaptureRepro {
  private fun show(tag: String, src: String) {
    try { val r = TesseraCompiler.compile(src, "$tag/index.ts", TesseraCompiler::class.java.classLoader)
      System.err.println("[$tag] OK ${r.defaultClass.declaredMethods.joinToString { it.name }}")
    } catch (t: Throwable) { var c: Throwable = t; while (c.cause != null && c.cause !== c) c = c.cause!!
      System.err.println("[$tag] FAIL ${c::class.simpleName}: ${(c.message ?: "").take(40)}") }
  }
  @Test fun t() {
    // Many reassigned+captured module lets in one lambda -> swc4j max_stack bug.
    val lets = (1..10).joinToString("\n") { "let v$it: number = 0;" }
    val reads = (1..10).joinToString(" + ") { "v$it" }
    val reassign = (1..10).joinToString("\n") { "v$it = v$it + 1;" }
    show("captures10", "import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';\n$lets\n" +
      "Tessera.register(Event.TICK, () => { ChatLib.chat(\"\" + ($reads)); });\n" +
      "Tessera.register(Event.COMMAND, (args) => { $reassign }).setName(\"r\");")
    // Same logic but state in a static API (no captures).
    show("nocapture", "import { Tessera, Event, ChatLib, Store } from 'ratph6.tessera.api';\n" +
      "Tessera.register(Event.TICK, () => { ChatLib.chat(\"\" + Store.getNum(\"v1\", 0)); });\n" +
      "Tessera.register(Event.COMMAND, (args) => { Store.setNum(\"v1\", Store.getNum(\"v1\", 0) + 1); }).setName(\"r2\");")
  }
}
