package ratph6.tessera.api

import ratph6.tessera.engine.MixinManager
import ratph6.tessera.engine.MixinRegistry
import java.util.function.Consumer

/**
 * Inject TypeScript callbacks straight into Minecraft methods at runtime — the scripting equivalent of
 * a Fabric mixin, but defined live from a Tessera module (no restart, no `.mixins.json`).
 *
 * ```ts
 * import { Mixin, ChatLib } from 'ratph6.tessera.api';
 *
 * // Run code at the start of every client tick.
 * Mixin.inject("net.minecraft.client.Minecraft", "tick", (ctx) => {
 *   // ... runs on the render thread, before the original body
 * });
 *
 * // Cancel a method entirely.
 * Mixin.inject("net.minecraft.client.player.LocalPlayer", "aiStep", "HEAD", (ctx) => {
 *   ctx.cancel();
 * });
 *
 * // Override what a method returns.
 * Mixin.inject("net.minecraft.client.player.LocalPlayer", "isSprinting", "RETURN", (ctx) => {
 *   ctx.setReturnValue(true);
 * });
 * ```
 *
 * **Targets** are Mojang-mapped binary class names (`net.minecraft...`) and method names — the same
 * names the rest of the Tessera API uses, which resolve in the dev client. The callback receives a
 * [MixinContext].
 *
 * **Injection points:** `"HEAD"` (default) runs before the body and may [MixinContext.cancel] or
 * [MixinContext.setReturnValue]; `"RETURN"` (alias `"TAIL"`) runs at every return and may override the
 * value. Constructors (`<init>`), `abstract` and `native` methods cannot be injected.
 *
 * **Threading:** the callback runs on whatever thread the target method runs on. Most client methods
 * run on the render thread (Tessera's JS thread), so GraalJS callbacks are fine there; targeting an
 * off-thread method (e.g. netty) with a GraalJS module will error.
 *
 * **Requirements:** runtime instrumentation must be available — launch with
 * `-Djdk.attach.allowAttachSelf=true` on a full JDK. If it isn't, the first `inject` call reports a
 * clear error and the rest of Tessera keeps working.
 */
object Mixin {

    /** Inject at the HEAD of [method] on [target]. */
    @JvmStatic
    fun inject(target: String, method: String, callback: Consumer<Any?>): MixinHandle =
        inject(target, method, "HEAD", callback)

    /** Inject at [at] (`"HEAD"` or `"RETURN"`) of [method] on [target]. */
    @JvmStatic
    fun inject(target: String, method: String, at: String, callback: Consumer<Any?>): MixinHandle =
        MixinHandle(MixinManager.register(target, method, null, at, callback))

    /**
     * Inject into the single overload of [method] whose JVM [descriptor] matches (e.g.
     * `"(Lnet/minecraft/world/entity/Entity;)Z"`). Use this to disambiguate overloaded methods; omit it
     * (the other overloads) to hook every method with that name.
     */
    @JvmStatic
    fun injectExact(target: String, method: String, descriptor: String, at: String, callback: Consumer<Any?>): MixinHandle =
        MixinHandle(MixinManager.register(target, method, descriptor, at, callback))
}

/** Handle to a live injection; call [remove] to detach it (the target method reverts to original). */
class MixinHandle internal constructor(private val hook: MixinRegistry.Hook) {
    fun remove() = MixinManager.remove(hook)
}
