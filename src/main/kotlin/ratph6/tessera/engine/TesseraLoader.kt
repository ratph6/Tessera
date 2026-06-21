package ratph6.tessera.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

// Discovers and compiles modules under <gameDir>/tessera/modules/<name>/. Each has a single entry script,
// run on the manifest's engine: "graal" (default) or "bytecode" (swc4j TS->JVM).
object TesseraLoader {
    private val entryCandidates = listOf("index.ts", "index.js", "index.mts", "index.mjs", "index.tsx", "index.cts")

    fun load(modulesDir: Path, classLoader: ClassLoader): List<TesseraModule> {
        if (!Files.exists(modulesDir)) {
            Files.createDirectories(modulesDir)
            return emptyList()
        }
        return Files.list(modulesDir).use { stream ->
            stream.filter { it.isDirectory() && !it.name.startsWith(".") }
                .sorted(compareBy { it.name })
                .toList()
        }.mapNotNull { dir ->
            runCatching { loadModule(dir, classLoader) }
                .onFailure { TesseraEngine.recordError("load:${dir.fileName}", it) }
                .getOrNull()
        }.sortedBy { it.manifest.priority }
    }

    fun loadModule(dir: Path, classLoader: ClassLoader): TesseraModule {
        val manifest = readManifest(dir)
        val entryFile = listOfNotNull(dir.resolve(manifest.entry).takeIf { Files.exists(it) })
            .firstOrNull()
            ?: entryCandidates.map { dir.resolve(it) }.firstOrNull { Files.exists(it) }
            ?: throw IllegalStateException("module '${manifest.name}' has no entry file (looked for ${manifest.entry} / index.*)")

        val source = entryFile.readText()
        return if (manifest.engine == Engines.BYTECODE) {
            val runner = TesseraCompiler.compile(source, "${manifest.name}/${entryFile.name}", classLoader)
            BytecodeModule(manifest, dir, runner)
        } else {
            GraalRuntime.loadModule(manifest, dir, source)
        }
    }

    private fun readManifest(dir: Path): TesseraManifest {
        val manifestFile = dir.resolve("tessera.json")
        val fallbackName = dir.name
        if (!Files.exists(manifestFile)) return TesseraManifest(name = fallbackName)
        return runCatching {
            val json = JsonParser.parseString(manifestFile.readText()).asJsonObject
            TesseraManifest(
                name = json.stringOr("name", fallbackName),
                version = json.stringOr("version", "1.0.0"),
                author = json.stringOr("author", "unknown"),
                description = json.stringOr("description", ""),
                dependencies = json.getAsJsonArray("dependencies")?.map { it.asString } ?: emptyList(),
                priority = json.get("priority")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                entry = json.stringOr("entry", "index.ts"),
                engine = json.stringOr("engine", Engines.DEFAULT).lowercase(),
            )
        }.getOrDefault(TesseraManifest(name = fallbackName))
    }

    private fun JsonObject.stringOr(key: String, default: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString ?: default
}
