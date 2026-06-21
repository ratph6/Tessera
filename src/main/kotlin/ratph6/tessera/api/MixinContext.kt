package ratph6.tessera.api

// Handed to a Mixin callback: read receiver/args, cancel, or override the return value. args are
// boxed and read-only (mutating them doesn't reach the original method). The field surface
// (cancelled/returnValue/hasReturnOverride) is read directly by injected bytecode — it's an ABI,
// keep in sync with MixinTransformer.
class MixinContext(
    @JvmField val target: String,
    @JvmField val method: String,
    // receiver, or null for a static method
    @JvmField val self: Any?,
    @JvmField val args: Array<Any?>,
) {
    @JvmField var cancelled: Boolean = false
    @JvmField var returnValue: Any? = null
    @JvmField var hasReturnOverride: Boolean = false

    // skip the rest of the original method
    fun cancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled

    fun getArg(i: Int): Any? = args.getOrNull(i)

    fun argCount(): Int = args.size

    // override the return value; at a HEAD injection this also cancels the body. value must be
    // assignable to the declared return type (unbox/checkcast happens in injected bytecode).
    fun setReturnValue(value: Any?) {
        returnValue = value
        hasReturnOverride = true
        cancelled = true
    }
}
