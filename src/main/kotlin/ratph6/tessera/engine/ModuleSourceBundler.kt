package ratph6.tessera.engine

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

data class BundledSource(
    val source: String,
    val fileName: String,
)

// Tessera executes a module as one script. This bundles local module files into that one script so
// users can split helpers into sibling files and use `import "./helper.js"` for side-effect loading.
object ModuleSourceBundler {
    private val sourceExtensions = listOf("ts", "js", "mts", "mjs", "tsx", "jsx", "cts", "cjs")
    private val entryFileNames = setOf("index.ts", "index.js", "index.mts", "index.mjs", "index.tsx", "index.cts")
    private val importStatement = Regex(
        """(?m)^[ \t]*import(?:\s+(type))?(?:\s+([\s\S]*?)\s+from)?\s*['"]([^'"]+)['"]\s*;?[ \t]*(?=\r?\n|$)""",
    )
    private val identifier = Regex("""^[A-Za-z_$][A-Za-z0-9_$]*$""")
    private val asSplit = Regex("""\s+as\s+""")

    fun bundle(manifest: TesseraManifest, dir: Path, entryFile: Path): BundledSource {
        val root = dir.toAbsolutePath().normalize()
        val entry = entryFile.toAbsolutePath().normalize()
        val state = State(root)

        directSourceFiles(root)
            .filter {
                val normalized = it.toAbsolutePath().normalize()
                normalized != entry && it.fileName.toString() !in entryFileNames
            }
            .forEach { state.appendFile(it.toAbsolutePath().normalize()) }
        state.appendFile(entry)

        return BundledSource(
            source = state.build(),
            fileName = "${manifest.name}/${bundleFileName(entry, state.seenExtensions)}",
        )
    }

    private fun directSourceFiles(dir: Path): List<Path> {
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && isSourceFile(it) }
                .sorted(compareBy { it.fileName.toString() })
                .toList()
        }
    }

    private fun isSourceFile(path: Path): Boolean {
        val name = path.fileName.toString()
        if (name.endsWith(".d.ts") || name.endsWith(".d.mts") || name.endsWith(".d.cts")) return false
        val ext = name.substringAfterLast('.', "")
        return ext in sourceExtensions
    }

    private fun bundleFileName(entry: Path, seenExtensions: Set<String>): String {
        val base = entry.fileName.toString().substringBeforeLast('.', "index")
        return when {
            seenExtensions.any { it == "tsx" || it == "jsx" } -> "$base.bundle.tsx"
            seenExtensions.any { it == "ts" || it == "mts" || it == "cts" } -> "$base.bundle.ts"
            else -> "$base.bundle.js"
        }
    }

    private fun isLocalSpecifier(spec: String): Boolean =
        spec.startsWith("./") || spec.startsWith("../")

    private fun displayPath(root: Path, path: Path): String =
        root.relativize(path).toString().replace('\\', '/')

    private class State(private val root: Path) {
        private val visited = LinkedHashSet<Path>()
        private val chunks = ArrayList<String>()
        private val braceImports = LinkedHashMap<String, LinkedHashMap<String, ImportBinding>>()
        private val defaultImports = LinkedHashMap<String, String>()
        private val exactImports = LinkedHashSet<String>()
        val seenExtensions = LinkedHashSet<String>()

        fun appendFile(file: Path) {
            val normalized = file.toAbsolutePath().normalize()
            if (!normalized.startsWith(root)) {
                throw IllegalArgumentException("local source '${normalized}' escapes module directory '$root'")
            }
            if (!visited.add(normalized)) return

            seenExtensions.add(normalized.fileName.toString().substringAfterLast('.', "").lowercase())
            val source = normalized.readText()
            val localAliasBlocks = ArrayList<String>()

            for (match in importStatement.findAll(source)) {
                val typeOnly = match.groupValues[1].isNotEmpty()
                val clause = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
                val spec = match.groupValues[3]
                if (isLocalSpecifier(spec)) {
                    appendFile(resolveLocal(normalized, spec))
                    localAliasBlock(typeOnly, clause, spec, normalized)?.let(localAliasBlocks::add)
                } else {
                    recordExternalImport(typeOnly, clause, spec, match.value.trim())
                }
            }

            val stripped = importStatement.replace(source, "").trim()

            if (stripped.isNotEmpty() || localAliasBlocks.isNotEmpty()) {
                chunks.add(buildString {
                    append("// <").append(displayPath(root, normalized)).append(">\n")
                    for (alias in localAliasBlocks) append(alias).append('\n')
                    if (stripped.isNotEmpty()) append(stripped).append('\n')
                    append("// </").append(displayPath(root, normalized)).append(">\n")
                })
            }
        }

        private fun resolveLocal(from: Path, spec: String): Path {
            val raw = from.parent.resolve(spec).normalize()
            val candidates = LinkedHashSet<Path>()
            candidates.add(raw)

            val fileName = raw.fileName?.toString() ?: ""
            fun sibling(ext: String) {
                val stem = fileName.substringBeforeLast('.', fileName)
                candidates.add(raw.resolveSibling("$stem.$ext"))
            }

            when (fileName.substringAfterLast('.', "").lowercase()) {
                "js" -> listOf("ts", "tsx", "mts", "cts", "jsx").forEach(::sibling)
                "mjs" -> listOf("mts", "ts", "tsx").forEach(::sibling)
                "cjs" -> listOf("cts", "ts", "tsx").forEach(::sibling)
                "jsx" -> listOf("tsx", "ts").forEach(::sibling)
                "" -> {
                    for (ext in sourceExtensions) candidates.add(raw.resolveSibling("$fileName.$ext"))
                    for (ext in sourceExtensions) candidates.add(raw.resolve("index.$ext"))
                }
            }

            for (candidate in candidates.map { it.toAbsolutePath().normalize() }) {
                if (!candidate.startsWith(root)) {
                    throw IllegalArgumentException(
                        "local import '$spec' from ${displayPath(root, from)} escapes module directory",
                    )
                }
                if (Files.isRegularFile(candidate) && isSourceFile(candidate)) return candidate
            }
            throw IllegalArgumentException("cannot resolve local import '$spec' from ${displayPath(root, from)}")
        }

        private fun recordExternalImport(typeOnly: Boolean, clause: String?, spec: String, statement: String) {
            if (typeOnly || clause == null) {
                exactImports.add(statement)
                return
            }

            val c = clause.trim()
            if (c.startsWith("{") && c.endsWith("}")) {
                val map = braceImports.getOrPut(spec) { LinkedHashMap() }
                for (binding in parseNamedBindings(c)) map.putIfAbsent(binding.key, binding)
            } else if (identifier.matches(c)) {
                defaultImports.putIfAbsent(c, spec)
            } else {
                exactImports.add(statement)
            }
        }

        private fun localAliasBlock(typeOnly: Boolean, clause: String?, spec: String, from: Path): String? {
            if (typeOnly || clause == null) return null
            val c = clause.trim()
            if (!c.startsWith("{") || !c.endsWith("}")) {
                throw IllegalArgumentException(
                    "unsupported local import '$spec' from ${displayPath(root, from)}; use `import \"$spec\"` or named imports",
                )
            }
            val aliases = parseNamedBindings(c)
                .filter { it.local != it.imported }
                .joinToString("\n") { "const ${it.local} = ${it.imported};" }
            return aliases.ifEmpty { null }
        }

        private fun parseNamedBindings(clause: String): List<ImportBinding> =
            clause.removePrefix("{").removeSuffix("}")
                .split(',')
                .mapNotNull { raw ->
                    val cleaned = raw.trim()
                    if (cleaned.startsWith("type ")) return@mapNotNull null
                    if (cleaned.isEmpty()) return@mapNotNull null
                    val parts = cleaned.split(asSplit, limit = 2)
                    val imported = parts[0].trim()
                    val local = parts.getOrNull(1)?.trim() ?: imported
                    if (!identifier.matches(imported) || !identifier.matches(local)) return@mapNotNull null
                    ImportBinding(imported, local)
                }

        fun build(): String = buildString {
            for ((spec, bindings) in braceImports) {
                if (bindings.isEmpty()) continue
                append("import { ")
                append(bindings.values.joinToString(", ") { it.render() })
                append(" } from '").append(spec).append("';\n")
            }
            for ((local, spec) in defaultImports) {
                append("import ").append(local).append(" from '").append(spec).append("';\n")
            }
            for (statement in exactImports) append(statement).append('\n')
            if (isNotEmpty()) append('\n')
            chunks.forEach { append(it).append('\n') }
        }
    }

    private data class ImportBinding(val imported: String, val local: String) {
        val key: String = "$imported:$local"
        fun render(): String = if (imported == local) imported else "$imported as $local"
    }
}
