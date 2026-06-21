package ratph6.tessera.engine

import com.caoccao.javet.swc4j.Swc4j
import com.caoccao.javet.swc4j.compiler.ByteCodeCompiler
import com.caoccao.javet.swc4j.compiler.ByteCodeCompilerOptions
import com.caoccao.javet.swc4j.compiler.ByteCodeRunner
import com.caoccao.javet.swc4j.compiler.JdkVersion
import com.caoccao.javet.swc4j.enums.Swc4jMediaType
import com.caoccao.javet.swc4j.enums.Swc4jParseMode
import com.caoccao.javet.swc4j.options.Swc4jParseOptions
import java.lang.reflect.Modifier
import java.net.URI

/**
 * Compiles a Tessera script (TypeScript / modern JS) straight to JVM bytecode using swc4j's
 * [ByteCodeCompiler]. Scripts run as native JVM classes — every `export function` becomes a static
 * method on the default `$` class, and scripts reach the Tessera API by importing our Kotlin objects by
 * package, e.g. `import { Tessera, Event } from 'ratph6.tessera.api'`.
 *
 * Because the compiler does not execute top-level statements, the source is first rewritten so any
 * top-level code (e.g. `Tessera.register(...)`) is moved into a generated `__tesseraEntry()` function that
 * the engine runs on load — so scripts need neither a `main()` nor `export`. Two wrap strategies
 * exist (see [compile]): a textual one (default; deterministic) and an AST one (fallback).
 */
object TesseraCompiler {

    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")

    private val parser = Swc4j()

    /** Test-only: skip the textual wrap to exercise the AST wrap path directly. */
    internal var disableTextualWrap = false

    private val typeAliasMap: Map<String, String> = mapOf(
        "string" to "java.lang.String", "String" to "java.lang.String",
        "number" to "double", "int" to "int", "long" to "long", "short" to "short",
        "byte" to "byte", "float" to "float", "double" to "double",
        "boolean" to "boolean", "void" to "void",
        "any" to "java.lang.Object", "unknown" to "java.lang.Object", "Object" to "java.lang.Object",
    )

    private val bannedImports = listOf("Runtime", "ProcessBuilder", "ProcessHandle")

    class CompileViolation(message: String) : RuntimeException(message)

    fun check(source: String, fileName: String) {
        for (line in source.lineSequence()) {
            if (!line.contains("import")) continue
            for (banned in bannedImports) {
                if (Regex("""\b${Regex.escape(banned)}\b""").containsMatchIn(line)) {
                    throw CompileViolation("$fileName: importing '$banned' is not allowed in Tessera scripts")
                }
            }
        }
    }

    private fun newCompiler(parentClassLoader: ClassLoader): ByteCodeCompiler =
        ByteCodeCompiler.of(
            ByteCodeCompilerOptions.builder()
                .jdkVersion(JdkVersion.JDK_17)
                .parentClassLoader(parentClassLoader)
                .typeAliasMap(typeAliasMap)
                .build(),
        )

    /**
     * Compile [source] to runnable JVM classes (inlining Event.*, auto-wrapping top-level code).
     *
     * Tries, in order: the textual top-level wrap, the AST-based wrap, then the source as-is. Each
     * attempt both compiles *and* force-loads the `$` class, so a swc4j codegen bug that emits
     * unverifiable bytecode is caught here and the next strategy is tried — a bad attempt can never
     * escape as a raw VerifyError. The textual wrap is primary because the AST wrap slices the source
     * using swc4j byte-position spans, which drift across the shared parser's lifetime (a later module
     * sees offset spans) and can yield bad bytecode; the textual wrap is deterministic. If no strategy
     * yields a loadable class, throws with the real cause.
     */
    fun compile(source: String, fileName: String, parentClassLoader: ClassLoader): ByteCodeRunner {
        val inlined = inlineEvents(source)
        check(inlined, fileName)

        var failure: Throwable? = null
        fun attempt(label: String, src: String?): ByteCodeRunner? {
            if (src == null) return null
            return try {
                val runner = newCompiler(parentClassLoader).compile(src)
                runner.defaultClass // force class load so a VerifyError/LinkageError surfaces now
                runner
            } catch (t: Throwable) {
                log.warn("{} compile failed for {} ({})", label, fileName, t.toString())
                failure = t
                null
            }
        }

        // Primary: textual wrap (no swc4j AST/spans → deterministic). Handles the common Tessera script
        // shape: imports + top-level statements + arrow callbacks.
        if (!disableTextualWrap) attempt("textual-wrap", textualWrapTopLevel(inlined))?.let { return it }

        // Fallback: AST wrap. Correctly relocates top-level function/class declarations (which the
        // textual wrap can't), for scripts that use them.
        val astWrapped = try {
            wrapTopLevel(inlined, fileName)
        } catch (t: Throwable) {
            log.warn("AST top-level wrap failed for {} ({})", fileName, t.toString()); failure = t; null
        }
        attempt("ast-wrap", astWrapped)?.let { log.info("compiled {} via AST-wrap fallback", fileName); return it }

        // Last resort: compile as-is. Only yields a default class if the script declares
        // `export function`s (a top-level-only script needs one of the wraps above).
        attempt("as-is", inlined)?.let { return it }

        throw IllegalStateException(
            "could not produce a runnable class for $fileName" +
                (failure?.let { " (cause: ${it::class.simpleName}: ${it.message ?: it})" } ?: ""),
            failure,
        )
    }

    /** Lines that must stay at module scope (not be moved into `__tesseraEntry`). */
    private val keepAtTopLevel =
        Regex("""^\s*(import\b|export\b|declare\b|interface\s|type\s|enum\s|abstract\s+class\b|class\s|function\s|async\s+function\b)""")

    /**
     * Keeps import/declaration lines at module scope and moves everything else (statements,
     * `let`/`const`, registrations) into a generated `__tesseraEntry()` so arrow callbacks still share the
     * same scope. Purely textual — no swc4j AST — so it's deterministic. Returns null if there's
     * nothing executable to move (e.g. a convention module of only `export function`s).
     *
     * Limitation: a top-level `function`/`class` *declaration* spanning multiple lines isn't relocated
     * correctly (only its first line is kept up top); such scripts fall through to the AST wrap.
     */
    private fun textualWrapTopLevel(source: String): String? {
        val header = StringBuilder()
        val body = StringBuilder()
        var movedExecutable = false
        for (line in source.lineSequence()) {
            if (keepAtTopLevel.containsMatchIn(line)) {
                header.append(line).append('\n')
            } else {
                body.append(line).append('\n')
                val t = line.trim()
                if (t.isNotEmpty() && !t.startsWith("//")) movedExecutable = true
            }
        }
        if (!movedExecutable) return null
        return buildString {
            append(header)
            append("export function __tesseraEntry(): void {\n")
            append(body)
            append("}\n")
        }
    }

    /** Names + string values of every constant on ratph6.tessera.api.Event. */
    private val eventConstants: Map<String, String> by lazy {
        runCatching {
            Class.forName("ratph6.tessera.api.Event").declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .associate { it.isAccessible = true; it.name to (it.get(null) as String) }
        }.getOrDefault(emptyMap())
    }

    /**
     * Replace `Event.CHAT` etc. with their string literal ("chat") before compiling. swc4j
     * miscompiles a static-field access passed alongside a lambda argument, but compiles a string
     * literal cleanly — so scripts keep the nice `Event.CHAT` syntax while the compiler sees "chat".
     */
    private fun inlineEvents(source: String): String {
        if (!source.contains("Event.") || eventConstants.isEmpty()) return source
        return Regex("""\bEvent\.([A-Za-z_][A-Za-z0-9_]*)\b""").replace(source) { m ->
            eventConstants[m.groupValues[1]]?.let { "\"$it\"" } ?: m.value
        }
    }

    private fun mediaTypeFor(fileName: String): Swc4jMediaType = when (fileName.substringAfterLast('.', "").lowercase()) {
        "tsx" -> Swc4jMediaType.Tsx
        "jsx" -> Swc4jMediaType.Jsx
        "js", "mjs", "cjs" -> Swc4jMediaType.JavaScript
        else -> Swc4jMediaType.TypeScript
    }

    /**
     * Returns a rewritten source where top-level statements are moved into `export function
     * __tesseraEntry()`, leaving imports + declarations (functions, classes, types) in place. Returns
     * null if parsing fails or there are no top-level statements to move (so the caller compiles raw).
     */
    private fun wrapTopLevel(source: String, fileName: String): String? {
        val options = Swc4jParseOptions()
            .setSpecifier(URI.create("file:///$fileName").toURL())
            .setMediaType(mediaTypeFor(fileName))
            .setParseMode(Swc4jParseMode.Module)
            .setCaptureAst(true)
        val program = parser.parse(source, options).program ?: return null
        val body = program.javaClass.getMethod("getBody").invoke(program) as? List<*> ?: return null

        data class Seg(val keep: Boolean, val start: Int, val end: Int)
        val segs = body.filterNotNull().map { node ->
            val span = node.javaClass.getMethod("getSpan").invoke(node)
            val start = (span.javaClass.getMethod("getStart").invoke(span) as Number).toInt()
            val end = (span.javaClass.getMethod("getEnd").invoke(span) as Number).toInt()
            Seg(isDeclaration(node.javaClass.simpleName), start, end)
        }.sortedBy { it.start }

        if (segs.none { !it.keep }) return null // nothing to move; compile as-is

        return buildString {
            for (s in segs) if (s.keep) append(slice(source, s.start, s.end)).append('\n')
            append("export function __tesseraEntry(): void {\n")
            for (s in segs) if (!s.keep) append(slice(source, s.start, s.end)).append('\n')
            append("}\n")
        }
    }

    private fun isDeclaration(simpleName: String): Boolean =
        simpleName.contains("ImportDecl") || simpleName.contains("Export") ||
            simpleName.contains("FnDecl") || simpleName.contains("ClassDecl") ||
            simpleName.contains("TsInterface") || simpleName.contains("TsTypeAlias") ||
            simpleName.contains("TsEnum") || simpleName.contains("TsModule")

    private fun slice(source: String, start: Int, end: Int): String {
        val s = start.coerceIn(0, source.length)
        val e = end.coerceIn(s, source.length)
        return source.substring(s, e)
    }
}
