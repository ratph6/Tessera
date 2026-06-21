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

// Compiles a script straight to JVM bytecode via swc4j. Every `export function` becomes a static method
// on the default `$` class. swc4j doesn't run top-level statements, so they're first moved into a
// generated __tesseraEntry() the engine runs on load — two wrap strategies (textual + AST), see compile().
object TesseraCompiler {

    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")

    private val parser = Swc4j()

    // test-only: skip the textual wrap to exercise the AST path directly
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

    // Tries textual wrap, AST wrap, then as-is. Each attempt force-loads the `$` class so swc4j codegen
    // bugs (unverifiable bytecode) are caught here and the next strategy tried — never escaping as a raw
    // VerifyError. Textual is primary: the AST wrap's swc4j byte spans drift across the shared parser's
    // lifetime and can yield bad bytecode, whereas textual is deterministic.
    fun compile(source: String, fileName: String, parentClassLoader: ClassLoader): ByteCodeRunner {
        val inlined = inlineEvents(source)
        check(inlined, fileName)

        var failure: Throwable? = null
        fun attempt(label: String, src: String?): ByteCodeRunner? {
            if (src == null) return null
            return try {
                val runner = newCompiler(parentClassLoader).compile(src)
                runner.defaultClass // force load so a VerifyError/LinkageError surfaces now
                runner
            } catch (t: Throwable) {
                log.warn("{} compile failed for {} ({})", label, fileName, t.toString())
                failure = t
                null
            }
        }

        // primary: textual wrap (deterministic, handles the common imports + statements + arrows shape)
        if (!disableTextualWrap) attempt("textual-wrap", textualWrapTopLevel(inlined))?.let { return it }

        // fallback: AST wrap relocates top-level function/class declarations the textual wrap can't
        val astWrapped = try {
            wrapTopLevel(inlined, fileName)
        } catch (t: Throwable) {
            log.warn("AST top-level wrap failed for {} ({})", fileName, t.toString()); failure = t; null
        }
        attempt("ast-wrap", astWrapped)?.let { log.info("compiled {} via AST-wrap fallback", fileName); return it }

        // last resort: as-is. Only yields a default class if the script declares `export function`s.
        attempt("as-is", inlined)?.let { return it }

        throw IllegalStateException(
            "could not produce a runnable class for $fileName" +
                (failure?.let { " (cause: ${it::class.simpleName}: ${it.message ?: it})" } ?: ""),
            failure,
        )
    }

    // lines that must stay at module scope (not move into __tesseraEntry)
    private val keepAtTopLevel =
        Regex("""^\s*(import\b|export\b|declare\b|interface\s|type\s|enum\s|abstract\s+class\b|class\s|function\s|async\s+function\b)""")

    // Keeps imports/declarations at module scope, moves everything else into __tesseraEntry(). Returns
    // null when nothing's executable to move. Limitation: a multi-line top-level function/class decl
    // isn't relocated correctly (only its first line stays up top) — those fall through to the AST wrap.
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

    private val eventConstants: Map<String, String> by lazy {
        runCatching {
            Class.forName("ratph6.tessera.api.Event").declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .associate { it.isAccessible = true; it.name to (it.get(null) as String) }
        }.getOrDefault(emptyMap())
    }

    // Replace Event.CHAT etc. with their string literal before compiling: swc4j miscompiles a static-field
    // access passed alongside a lambda arg, but compiles the literal cleanly.
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

    // AST equivalent of textualWrapTopLevel; returns null if parsing fails or nothing to move.
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
