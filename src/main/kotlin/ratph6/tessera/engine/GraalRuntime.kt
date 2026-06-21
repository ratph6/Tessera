package ratph6.tessera.engine

import com.caoccao.javet.swc4j.Swc4j
import com.caoccao.javet.swc4j.enums.Swc4jMediaType
import com.caoccao.javet.swc4j.enums.Swc4jModuleKind
import com.caoccao.javet.swc4j.enums.Swc4jParseMode
import com.caoccao.javet.swc4j.enums.Swc4jSourceMapOption
import com.caoccao.javet.swc4j.options.Swc4jTranspileOptions
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.net.URI
import java.nio.file.Path

// The GraalJS "real JavaScript" path: swc4j transpiles TS→JS, then GraalJS evaluates it as an ES
// module with full ECMAScript. The Context is single-threaded — only ever touched from the JS thread,
// so no locking needed. allowHostClassLookup is restricted, so guest code can't reach Runtime/etc.
object GraalRuntime {

    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")
    private val swc4j = Swc4j()

    @Volatile private var engine: Engine? = null
    @Volatile private var ctx: Context? = null

    private fun engine(): Engine = engine ?: synchronized(this) {
        engine ?: Engine.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build().also { engine = it }
    }

    // Tessera API names -> FQCN, reached via Java.type (binding the bare Class would expose java.lang.Class).
    private val tesseraApi: Map<String, String> = listOf(
        "Tessera", "ChatLib", "Player", "World", "Renderer", "Tessellator", "PlayerScales",
        "Num", "Args", "Store", "Server", "TabList", "Scoreboard", "KeyBind",
        "Event", "Display", "CancellableEvent", "Mixin", "MixinContext", "AccessWidener",
    ).associateWith { "ratph6.tessera.api.$it" }

    // no-import name -> FQCN for unique-named Minecraft classes
    private val mcGlobals: Map<String, String> by lazy { loadMcGlobals() }

    // every no-import name: Minecraft + Tessera API (Tessera wins ties)
    private val globals: Map<String, String> by lazy { mcGlobals + tesseraApi }

    private fun loadMcGlobals(): Map<String, String> = runCatching {
        GraalRuntime::class.java.getResourceAsStream("/tessera/minecraft-globals.json")?.use { ins ->
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            com.google.gson.Gson().fromJson<Map<String, String>>(java.io.InputStreamReader(ins, Charsets.UTF_8), type)
        }
    }.getOrNull() ?: emptyMap()

    // HostAccess.ALL plus number->float coercions: GraalJS hands JS numbers as double/int/long, so
    // without these any Java method taking a float (Minecraft has hundreds) throws "lossy coercion".
    internal val hostAccess: HostAccess by lazy {
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(Double::class.javaObjectType, Float::class.javaObjectType, { true }, { it.toFloat() })
            .targetTypeMapping(Integer::class.javaObjectType, Float::class.javaObjectType, { true }, { it.toFloat() })
            .targetTypeMapping(java.lang.Long::class.javaObjectType, Float::class.javaObjectType, { true }, { it.toFloat() })
            .build()
    }

    private fun context(): Context {
        ctx?.let { return it }
        val c = Context.newBuilder("js")
            .engine(engine())
            .allowHostAccess(hostAccess)
            // Java.type allowed only for Tessera API + Minecraft; Runtime & friends stay refused.
            .allowHostClassLookup { it.startsWith("ratph6.tessera.api.") || it.startsWith("net.minecraft.") }
            // resolve Java.type against the loader that sees those classes (Knot in-game)
            .hostClassLoader(GraalRuntime::class.java.classLoader)
            .allowExperimentalOptions(true)
            .option("js.ecmascript-version", "2024")
            .build()
        bootstrapJs(c)
        ctx = c
        return c
    }

    // Install timer/async globals backed by Tessera tick-thread timers. This is what makes async/await
    // progress: await sleep(ms) schedules a timer that resolves the promise, draining the microtask queue.
    private fun bootstrapJs(c: Context) {
        runCatching {
            c.eval(
                "js",
                """
                const __Tessera = Java.type('ratph6.tessera.api.Tessera');
                globalThis.setTimeout = (fn, ms) => __Tessera.setTimeout(fn, (ms | 0));
                globalThis.setInterval = (fn, ms) => __Tessera.setInterval(fn, (ms | 0));
                globalThis.clearTimeout = (id) => __Tessera.clearTimer(id | 0);
                globalThis.clearInterval = (id) => __Tessera.clearTimer(id | 0);
                globalThis.sleep = (ms) => new Promise((resolve) => __Tessera.setTimeout(resolve, (ms | 0)));
                """.trimIndent(),
            )
        }.onFailure { log.warn("graal async bootstrap failed ({})", it.toString()) }
    }

    private fun mediaTypeFor(fileName: String): Swc4jMediaType =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "tsx" -> Swc4jMediaType.Tsx
            "jsx" -> Swc4jMediaType.Jsx
            "js", "mjs", "cjs" -> Swc4jMediaType.JavaScript
            else -> Swc4jMediaType.TypeScript
        }

    fun transpileTs(source: String, fileName: String): String {
        val options = Swc4jTranspileOptions()
            .setSpecifier(URI.create("file:///$fileName").toURL())
            .setMediaType(mediaTypeFor(fileName))
            .setParseMode(Swc4jParseMode.Module)
            .setModuleKind(Swc4jModuleKind.Esm)
            .setSourceMap(Swc4jSourceMapOption.None)
        return swc4j.transpile(source, options).code
    }

    // Modules are single-file: their `import { X } from 'spec'` lines have no JS module to resolve, so
    // each is rewritten to `const X = Java.type('spec.X')`.
    private val braceImport = Regex("""import\s*\{([^}]*)\}\s*from\s*['"]([^'"]+)['"]\s*;?""")
    private val identifier = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")
    private val asSplit = Regex("""\s+as\s+""")

    // returns the new source and the names it bound
    private fun rewriteImports(js: String): Pair<String, Set<String>> {
        val bound = HashSet<String>()
        val out = braceImport.replace(js) { m ->
            val spec = m.groupValues[2]
            m.groupValues[1].split(',').mapNotNull { raw ->
                val parts = raw.trim().split(asSplit)
                val orig = parts[0].trim()
                if (orig.isEmpty()) return@mapNotNull null
                val local = (parts.getOrNull(1) ?: orig).trim()
                bound.add(local)
                "const $local = Java.type('$spec.$orig');"
            }.joinToString(" ")
        }
        return out to bound
    }

    // bind every no-import global the script references (and didn't import) to its host type
    private fun globalsPrelude(js: String, alreadyBound: Set<String>): String {
        val used = identifier.findAll(js).mapTo(HashSet()) { it.value }
        val sb = StringBuilder()
        for ((name, fqcn) in globals) {
            if (name !in alreadyBound && name in used) {
                sb.append("const ").append(name).append(" = Java.type('").append(fqcn).append("');\n")
            }
        }
        return sb.toString()
    }

    private fun prepare(source: String, fileName: String): String {
        val (rewritten, bound) = rewriteImports(transpileTs(source, fileName))
        return globalsPrelude(rewritten, bound) + rewritten
    }

    // Transpile + evaluate as an ES module. Top-level code runs immediately (so top-level
    // Tessera.register calls attach to `module`); the returned namespace holds exported functions.
    fun loadModule(manifest: TesseraManifest, dir: Path, source: String): GraalModule {
        val js = prepare(source, "${manifest.name}/${manifest.entry}")
        val module = GraalModule(manifest, dir)
        val c = context()
        TesseraEngine.withCurrentModule(module) {
            val src = Source.newBuilder("js", js, "${manifest.name}.mjs")
                .mimeType("application/javascript+module")
                .build()
            module.attach(c.eval(src))
        }
        return module
    }

    // one-off snippet for `/te eval`
    fun evalSnippet(code: String): Value {
        val js = prepare(code, "tessera-eval.ts")
        return TesseraEngine.withCurrentModule(null) { context().eval("js", js) }
    }

    // dispose the context so a reload starts from clean JS global state
    fun reset() {
        synchronized(this) {
            runCatching { ctx?.close(true) }.onFailure { log.warn("graal context close failed ({})", it.toString()) }
            ctx = null
        }
    }
}
