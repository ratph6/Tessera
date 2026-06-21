package ratph6.tessera.engine

import com.caoccao.javet.swc4j.compiler.ByteCodeRunner
import org.graalvm.polyglot.Value
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** The execution engine a module runs on (see [TesseraManifest.engine]). */
object Engines {
    const val GRAAL = "graal"
    const val BYTECODE = "bytecode"
    const val DEFAULT = GRAAL
}

/** Parsed `tessera.json` manifest. */
data class TesseraManifest(
    val name: String,
    val version: String = "1.0.0",
    val author: String = "unknown",
    val description: String = "",
    val dependencies: List<String> = emptyList(),
    val priority: Int = 0,
    val entry: String = "index.ts",
    /** `"graal"` (real ECMAScript via GraalJS, default) or `"bytecode"` (swc4j TS→JVM, native speed). */
    val engine: String = Engines.DEFAULT,
)

/**
 * A compiled, loaded module. Engine-agnostic surface: its manifest/dir, the names of its exported
 * functions, and a uniform [TesseraCallback] for any one of them. Two implementations:
 *  - [BytecodeModule] — swc4j compiled the TS to JVM classes; functions are static methods.
 *  - [GraalModule] — GraalJS evaluated the (transpiled) JS module; functions live on its namespace.
 */
sealed interface TesseraModule {
    val manifest: TesseraManifest
    val directory: Path
    val name: String get() = manifest.name

    /** Names of the module's exported/callable functions. */
    val exportedFunctions: List<String>

    /** A callable handle to an exported function, or null if it doesn't exist. */
    fun callbackFor(functionName: String): TesseraCallback?

    /** Whether the module exports a callable function with this name. */
    fun hasFunction(functionName: String): Boolean = callbackFor(functionName) != null
}

/**
 * A module compiled to JVM bytecode by swc4j. Exported functions live on [defaultClass] as static
 * methods; Tessera looks them up (and caches a [MethodHandle]) when a trigger needs to call one.
 */
class BytecodeModule(
    override val manifest: TesseraManifest,
    override val directory: Path,
    val runner: ByteCodeRunner,
) : TesseraModule {

    /** The swc4j default class `$` containing the script's top-level (`export function`) methods. */
    val defaultClass: Class<*> = runner.defaultClass

    private val handleCache = ConcurrentHashMap<String, MethodHandle>()

    override val exportedFunctions: List<String> by lazy {
        defaultClass.declaredMethods
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .distinct()
    }

    /** Resolve (and cache) a callable handle for an exported function, or null if it doesn't exist. */
    fun handleFor(functionName: String): MethodHandle? {
        handleCache[functionName]?.let { return it }
        val method = defaultClass.declaredMethods.firstOrNull { it.name == functionName } ?: return null
        method.isAccessible = true
        val handle = MethodHandles.lookup().unreflect(method)
        handleCache[functionName] = handle
        return handle
    }

    fun parameterCount(functionName: String): Int =
        defaultClass.declaredMethods.firstOrNull { it.name == functionName }?.parameterCount ?: 0

    override fun callbackFor(functionName: String): TesseraCallback? =
        handleFor(functionName)?.let { HandleCallback(it, parameterCount(functionName)) }
}

/**
 * A module evaluated as an ES module by GraalJS. Its top-level code already ran on load (so
 * top-level `Tessera.register(...)` calls are live); [namespace] is the module's export namespace, from
 * which named exported functions (for the `main`/`init` entry and trigger-name conventions) are read.
 */
class GraalModule(
    override val manifest: TesseraManifest,
    override val directory: Path,
) : TesseraModule {

    // Set once, right after the module's JS is evaluated (the namespace is the eval result). Deferred
    // because top-level code — which needs `this` as the current module — runs during that same eval.
    @Volatile private var namespace: Value? = null
    internal fun attach(ns: Value) { namespace = ns }

    override val exportedFunctions: List<String> by lazy {
        val ns = namespace ?: return@lazy emptyList()
        runCatching {
            ns.memberKeys.filter { ns.getMember(it)?.canExecute() == true }
        }.getOrDefault(emptyList())
    }

    override fun callbackFor(functionName: String): TesseraCallback? {
        val ns = namespace ?: return null
        val member = runCatching { ns.getMember(functionName) }.getOrNull() ?: return null
        return if (member.canExecute()) GraalCallback(member) else null
    }
}
