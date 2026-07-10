package ratph6.tessera.engine

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

// Bundles a multi-file graal module into ONE script where each file keeps its OWN scope — the same
// name can be declared in several files without colliding, and files share only what they explicitly
// export/import. Each file becomes a lazily-evaluated factory in a small CommonJS-style registry:
//
//   __reg["utils/Math.ts"] = { loaded:false, exports:{}, factory:function(module, exports, __req){
//       const Vec3 = Java.type("net.minecraft.world.phys.Vec3");   // external (Java) imports
//       const { helper } = __req("utils/Other.ts");                // local imports
//       ...file body (its top-level const/let/function are private to this factory)...
//       exports.dist = dist;                                       // exports
//   }};
//
// __req(id) runs a factory once and caches its exports (so circular imports resolve like Node's).
// Java-package imports are turned into Java.type bindings; no-import globals (Minecraft, Vec3, …) are
// still bound once by GraalRuntime's globals prelude, which every factory closes over.
object GraalModuleBundler {
    private val sourceExtensions = listOf("ts", "js", "mts", "mjs", "tsx", "jsx", "cts", "cjs")
    private val importStatement = Regex(
        """(?m)^[ \t]*import(?:\s+(type))?(?:\s+([\s\S]*?)\s+from)?\s*['"]([^'"]+)['"]\s*;?[ \t]*(?=\r?\n|$)""",
    )
    // export forms
    private val exportDecl = Regex("""(?m)^([ \t]*)export\s+(default\s+)?((?:async\s+)?(?:const|let|var|function|class))\s+([A-Za-z_$][A-Za-z0-9_$]*)""")
    private val exportDefaultExpr = Regex("""(?m)^([ \t]*)export\s+default\s+(?!(?:async\s+)?(?:function|class)\b)""")
    private val exportNamed = Regex("""(?m)^[ \t]*export\s*\{([^}]*)\}\s*(?:from\s*['"][^'"]+['"]\s*)?;?[ \t]*$""")
    private val identifier = Regex("""^[A-Za-z_$][A-Za-z0-9_$]*$""")
    private val asSplit = Regex("""\s+as\s+""")

    data class Bundle(val source: String, val entryId: String)

    fun bundle(dir: Path, entryFile: Path): Bundle {
        val root = dir.toAbsolutePath().normalize()
        val entry = entryFile.toAbsolutePath().normalize()
        val factories = LinkedHashMap<String, String>()
        val sideEffects = LinkedHashSet<String>()

        fun idOf(f: Path): String = root.relativize(f).toString().replace('\\', '/')

        fun process(file: Path) {
            val f = file.toAbsolutePath().normalize()
            if (!f.startsWith(root)) throw IllegalArgumentException("file '$f' escapes module dir '$root'")
            val id = idOf(f)
            if (factories.containsKey(id)) return
            factories[id] = "" // reserve (guards against import cycles)
            factories[id] = buildFactory(root, f, id, ::process)
        }

        // 1) auto-include + run every direct source file in the module root (drop-a-file-and-it-runs)
        directSourceFiles(root).forEach { sib ->
            process(sib)
            sideEffects.add(idOf(sib.toAbsolutePath().normalize()))
        }
        // 2) make sure the entry is processed (it usually is, as a root sibling)
        process(entry)

        val sb = StringBuilder()
        sb.append("const __reg = Object.create(null);\n")
        sb.append("function __req(id){var m=__reg[id];if(!m){throw new Error('Tessera: module not found: '+id);}")
        sb.append("if(m.loaded){return m.exports;}m.loaded=true;m.factory(m,m.exports,__req);return m.exports;}\n")
        for (js in factories.values) sb.append(js).append('\n')
        // run the root siblings for their side effects (idempotent; entry is run by the caller)
        for (sideId in sideEffects) if (sideId != idOf(entry)) sb.append("__req(").append(q(sideId)).append(");\n")

        return Bundle(sb.toString(), idOf(entry))
    }

    private fun buildFactory(root: Path, file: Path, id: String, process: (Path) -> Unit): String {
        var source = file.readText()
        val externalConsts = StringBuilder()
        val localRequires = StringBuilder()

        // --- imports ---
        for (m in importStatement.findAll(source)) {
            val typeOnly = m.groupValues[1].isNotEmpty()
            val clause = m.groupValues[2].trim().takeIf { it.isNotEmpty() }
            val spec = m.groupValues[3]
            if (typeOnly) continue // pure type import — nothing at runtime
            if (isLocal(spec)) {
                val target = resolveLocal(root, file, spec)
                process(target)
                val depId = root.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/')
                if (clause != null) localRequires.append(bindLocal(clause, depId)).append('\n')
                else localRequires.append("__req(").append(q(depId)).append(");\n") // side-effect import
            } else if (clause != null) {
                externalConsts.append(bindExternal(clause, spec)).append('\n')
            }
        }
        source = importStatement.replace(source, "")

        // --- exports --- (exported name -> local expression to assign)
        val exports = LinkedHashMap<String, String>()
        // `export [default] const/let/var/function/class NAME` -> strip the export keywords, record NAME
        source = exportDecl.replace(source) { mm ->
            val isDefault = mm.groupValues[2].isNotBlank()
            val keyword = mm.groupValues[3]
            val name = mm.groupValues[4]
            exports[if (isDefault) "default" else name] = name
            mm.groupValues[1] + keyword + " " + name
        }
        // `export default <expr>` -> `exports.default = <expr>` (inline assignment; no separate record)
        source = exportDefaultExpr.replace(source) { mm -> mm.groupValues[1] + "exports.default = " }
        // `export { a, b as c }` -> record (exported name = local, value = imported), strip statement
        source = exportNamed.replace(source) { mm ->
            for (b in parseBindings(mm.groupValues[1])) exports[b.local] = b.imported
            ""
        }

        // live-binding getters, defined BEFORE the body so a circular require mid-factory sees the
        // exported names (like ESM live bindings). Functions are hoisted; const/let read once declared.
        val getters = StringBuilder()
        for ((name, expr) in exports) {
            getters.append("Object.defineProperty(exports, ").append(q(name))
                .append(", { enumerable:true, configurable:true, get:function(){ return ").append(expr).append("; } });\n")
        }

        return buildString {
            append("__reg[").append(q(id)).append("] = { loaded:false, exports:{}, factory:function(module, exports, __req){\n")
            append(externalConsts)
            append(localRequires)
            append(getters)
            append(source.trim()).append('\n')
            append("}};")
        }
    }

    // brace external import -> Java.type consts; default external import -> the spec is the FQCN
    private fun bindExternal(clause: String, spec: String): String {
        val c = clause.trim()
        if (c.startsWith("{") && c.endsWith("}")) {
            return parseBindings(c.removePrefix("{").removeSuffix("}"))
                .joinToString("\n") { "const ${it.local} = Java.type('$spec.${it.imported}');" }
        }
        if (identifier.matches(c)) return "const $c = Java.type('$spec');"
        return "" // namespace/other external forms unsupported
    }

    // local import -> destructure from the dependency's exports
    private fun bindLocal(clause: String, depId: String): String {
        val c = clause.trim()
        if (c.startsWith("{") && c.endsWith("}")) {
            val names = parseBindings(c.removePrefix("{").removeSuffix("}"))
                .joinToString(", ") { if (it.local == it.imported) it.local else "${it.imported}: ${it.local}" }
            return "const { $names } = __req(${q(depId)});"
        }
        if (identifier.matches(c)) return "const $c = (__req(${q(depId)}).default ?? __req(${q(depId)}));"
        return "__req(${q(depId)});"
    }

    private fun parseBindings(inner: String): List<Binding> =
        inner.split(',').mapNotNull { raw ->
            val s = raw.trim()
            if (s.isEmpty() || s.startsWith("type ")) return@mapNotNull null
            val parts = s.split(asSplit, limit = 2)
            val imported = parts[0].trim()
            val local = parts.getOrNull(1)?.trim() ?: imported
            if (!identifier.matches(imported) || !identifier.matches(local)) return@mapNotNull null
            Binding(imported, local)
        }

    private data class Binding(val imported: String, val local: String)

    private fun isLocal(spec: String) = spec.startsWith("./") || spec.startsWith("../")

    private fun directSourceFiles(dir: Path): List<Path> {
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { s ->
            s.filter { Files.isRegularFile(it) && isSourceFile(it) }
                .sorted(compareBy { it.fileName.toString() })
                .toList()
        }
    }

    private fun isSourceFile(path: Path): Boolean {
        val name = path.fileName.toString()
        if (name.endsWith(".d.ts") || name.endsWith(".d.mts") || name.endsWith(".d.cts")) return false
        return name.substringAfterLast('.', "") in sourceExtensions
    }

    private fun resolveLocal(root: Path, from: Path, spec: String): Path {
        val raw = from.parent.resolve(spec).normalize()
        val candidates = LinkedHashSet<Path>()
        candidates.add(raw)
        val fileName = raw.fileName?.toString() ?: ""
        val stem = fileName.substringBeforeLast('.', fileName)
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "js" -> listOf("ts", "tsx", "mts", "cts", "jsx").forEach { candidates.add(raw.resolveSibling("$stem.$it")) }
            "mjs" -> listOf("mts", "ts", "tsx").forEach { candidates.add(raw.resolveSibling("$stem.$it")) }
            "cjs" -> listOf("cts", "ts", "tsx").forEach { candidates.add(raw.resolveSibling("$stem.$it")) }
            "jsx" -> listOf("tsx", "ts").forEach { candidates.add(raw.resolveSibling("$stem.$it")) }
            "" -> {
                for (ext in sourceExtensions) candidates.add(raw.resolveSibling("$fileName.$ext"))
                for (ext in sourceExtensions) candidates.add(raw.resolve("index.$ext"))
            }
        }
        for (c in candidates.map { it.toAbsolutePath().normalize() }) {
            if (!c.startsWith(root)) throw IllegalArgumentException("local import '$spec' escapes module dir")
            if (Files.isRegularFile(c) && isSourceFile(c)) return c
        }
        throw IllegalArgumentException("cannot resolve local import '$spec' from ${root.relativize(from)}")
    }

    private fun q(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
