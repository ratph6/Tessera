package ratph6.tessera.engine

import com.caoccao.javet.swc4j.compiler.ByteCodeRunner
import org.graalvm.polyglot.Value
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object Engines {
    const val GRAAL = "graal"
    const val BYTECODE = "bytecode"
    const val DEFAULT = GRAAL
}

// Parsed tessera.json manifest.
data class TesseraManifest(
    val name: String,
    val version: String = "1.0.0",
    val author: String = "unknown",
    val description: String = "",
    val dependencies: List<String> = emptyList(),
    val priority: Int = 0,
    val entry: String = "index.ts",
    val engine: String = Engines.DEFAULT, // "graal" or "bytecode"
)

// Engine-agnostic surface for a loaded module. Two impls: BytecodeModule (swc4j JVM classes, static
// methods) and GraalModule (GraalJS namespace).
sealed interface TesseraModule {
    val manifest: TesseraManifest
    val directory: Path
    val name: String get() = manifest.name

    val exportedFunctions: List<String>

    fun callbackFor(functionName: String): TesseraCallback?

    fun hasFunction(functionName: String): Boolean = callbackFor(functionName) != null
}

class BytecodeModule(
    override val manifest: TesseraManifest,
    override val directory: Path,
    val runner: ByteCodeRunner,
) : TesseraModule {

    // swc4j default class `$` holding the script's `export function` methods
    val defaultClass: Class<*> = runner.defaultClass

    private val handleCache = ConcurrentHashMap<String, MethodHandle>()

    override val exportedFunctions: List<String> by lazy {
        defaultClass.declaredMethods
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .distinct()
    }

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

// A module evaluated as an ES module by GraalJS. Top-level code already ran on load; namespace holds
// its exported functions.
class GraalModule(
    override val manifest: TesseraManifest,
    override val directory: Path,
) : TesseraModule {

    // set once after the JS is evaluated (the namespace is the eval result); deferred because top-level
    // code — which needs the current module — runs during that same eval
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
