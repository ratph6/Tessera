package ratph6.tessera.engine

import org.graalvm.polyglot.Value
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier

// A script callback, normalized so the engine invokes it the same way regardless of engine:
// HandleCallback for a JVM SAM (bytecode path, or a GraalJS fn coerced to a functional interface),
// GraalCallback for a raw GraalJS guest function.
sealed interface TesseraCallback {
    fun invoke(args: List<Any?>)
}

class HandleCallback(private val handle: MethodHandle, val paramCount: Int) : TesseraCallback {
    override fun invoke(args: List<Any?>) {
        handle.invokeWithArguments(Callbacks.adapt(args, paramCount))
    }
}

// JS tolerates arity mismatches, so args pass through as-is. Must run on the JS thread (Context is
// single-threaded) — which is where dispatch already runs.
class GraalCallback(private val fn: Value) : TesseraCallback {
    override fun invoke(args: List<Any?>) {
        fn.executeVoid(*args.toTypedArray())
    }
}

object Callbacks {
    // Executable Value -> GraalCallback; anything else is treated as a SAM object -> HandleCallback.
    fun resolve(callback: Any): TesseraCallback {
        if (callback is Value && callback.canExecute()) return GraalCallback(callback)
        val cls = callback.javaClass
        // A lambda implements exactly one functional interface (plus maybe Serializable).
        val sam = cls.interfaces
            .flatMap { it.methods.asList() }
            .firstOrNull { Modifier.isAbstract(it.modifiers) }
            ?: cls.methods.firstOrNull { Modifier.isAbstract(it.modifiers) }
            ?: throw IllegalArgumentException("callback is not a function (${cls.name})")
        sam.isAccessible = true
        return HandleCallback(MethodHandles.publicLookup().unreflect(sam).bindTo(callback), sam.parameterCount)
    }

    // truncate extras, pad missing with nulls
    fun adapt(args: List<Any?>, paramCount: Int): List<Any?> = when {
        paramCount <= args.size -> args.subList(0, paramCount)
        else -> args + List(paramCount - args.size) { null }
    }
}
