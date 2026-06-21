package ratph6.tessera.engine

import org.graalvm.polyglot.Value
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier

/**
 * A script callback, normalized so the engine can invoke it the same way regardless of which engine
 * produced it:
 *  - [HandleCallback] wraps a JVM callable — a bytecode-path arrow/named function swc4j compiled to a
 *    `java.util.function.*` lambda, or a GraalJS guest function that GraalJS coerced into such a SAM
 *    when it was passed to a `Consumer`/`Runnable`-typed API method (e.g. [ratph6.tessera.api.Tessera.register]).
 *  - [GraalCallback] wraps a raw GraalJS guest function (e.g. a module's exported function looked up
 *    by name from its namespace) and calls it directly.
 */
sealed interface TesseraCallback {
    /** Invoke with positional [args]; surplus/missing args are reconciled per-implementation. */
    fun invoke(args: List<Any?>)
}

/** A JVM-side callable invoked through a [MethodHandle], padded/truncated to [paramCount]. */
class HandleCallback(private val handle: MethodHandle, val paramCount: Int) : TesseraCallback {
    override fun invoke(args: List<Any?>) {
        handle.invokeWithArguments(Callbacks.adapt(args, paramCount))
    }
}

/**
 * A raw GraalJS guest function. JavaScript tolerates arity mismatches (extra args ignored, missing
 * ones become `undefined`), so every provided arg is passed through as-is. Must be called on the JS
 * thread (the GraalJS [org.graalvm.polyglot.Context] is single-threaded) — which is where dispatch
 * already runs.
 */
class GraalCallback(private val fn: Value) : TesseraCallback {
    override fun invoke(args: List<Any?>) {
        fn.executeVoid(*args.toTypedArray())
    }
}

object Callbacks {
    /**
     * Normalize a script callback into a [TesseraCallback]. A GraalJS [Value] that is executable becomes a
     * [GraalCallback]; anything else is treated as a single-abstract-method object (a lambda or a
     * GraalJS function already coerced to a functional interface) and becomes a [HandleCallback].
     */
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

    /** Adapt a list of positional args to exactly [paramCount] (truncate extras, pad with nulls). */
    fun adapt(args: List<Any?>, paramCount: Int): List<Any?> = when {
        paramCount <= args.size -> args.subList(0, paramCount)
        else -> args + List(paramCount - args.size) { null }
    }
}
