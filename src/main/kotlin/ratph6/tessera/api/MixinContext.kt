package ratph6.tessera.api

/**
 * The object handed to a [Mixin] injection callback. It is the live link between the running Minecraft
 * method and the script: read the receiver and arguments, cancel the call, or substitute its return
 * value.
 *
 * ```ts
 * import { Mixin } from 'ratph6.tessera.api';
 *
 * // Stop the player from ever taking fall damage.
 * Mixin.inject("net.minecraft.world.entity.LivingEntity", "causeFallDamage", "HEAD", (ctx) => {
 *   ctx.cancel();                 // skip the original method body
 * });
 *
 * // Force a method that returns a boolean to always return true.
 * Mixin.inject("net.minecraft.client.player.LocalPlayer", "isSprinting", "RETURN", (ctx) => {
 *   ctx.setReturnValue(true);
 * });
 * ```
 *
 * The [self] receiver and the boxed [args] mirror the target method's signature (primitive arguments
 * arrive boxed, e.g. an `int` becomes a `java.lang.Integer`). [args] is read-only — mutating elements
 * does not change the values the original method sees.
 *
 * The field-access surface ([cancelled], [returnValue], [hasReturnOverride]) is read directly by the
 * bytecode Tessera injects into the target method, so the names and descriptors here are an ABI — keep
 * them in sync with `MixinTransformer`.
 */
class MixinContext(
    /** Binary name of the injected class, e.g. `net.minecraft.client.Minecraft`. */
    @JvmField val target: String,
    /** Name of the injected method. */
    @JvmField val method: String,
    /** The receiver (`this`) of the call, or `null` for a static method. */
    @JvmField val self: Any?,
    /** The target method's arguments, boxed. Read-only. */
    @JvmField val args: Array<Any?>,
) {
    /** Set by [cancel]/[setReturnValue]; read by the injected bytecode to short-circuit the method. */
    @JvmField var cancelled: Boolean = false

    /** At a `RETURN` injection this starts as the method's actual return value; [setReturnValue] replaces it. */
    @JvmField var returnValue: Any? = null

    /** True once [setReturnValue] has been called — tells the injected bytecode to use [returnValue]. */
    @JvmField var hasReturnOverride: Boolean = false

    /** Skip the rest of the original method (a `HEAD` injection returns immediately). */
    fun cancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled

    /** The i-th argument (boxed), or `null` if out of range. */
    fun getArg(i: Int): Any? = args.getOrNull(i)

    fun argCount(): Int = args.size

    /**
     * Substitute the method's return value. At a `HEAD` injection this also cancels the original body;
     * at a `RETURN` injection it replaces the value about to be returned. The value must be assignable
     * to the method's declared return type (an unbox/checkcast happens in the injected bytecode).
     */
    fun setReturnValue(value: Any?) {
        returnValue = value
        hasReturnOverride = true
        cancelled = true
    }
}
