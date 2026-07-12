package ratph6.tessera.api

import ratph6.tessera.engine.MixinManager
import ratph6.tessera.engine.MixinRegistry
import java.util.function.Consumer

// Runtime method injection (Fabric-mixin equivalent, no restart). Targets are Mojang-mapped binary
// names. "HEAD" (default) may cancel/override; "RETURN" (alias "TAIL") runs at every return; ctors,
// abstract and native methods can't be injected. Needs runtime instrumentation
// (-Djdk.attach.allowAttachSelf=true on a full JDK); otherwise the first inject reports an error.
//
// THREADING: TesseraEngine.invokeMixin marshals any call that arrives off the JS/render thread (netty,
// render workers) onto the JS thread — observe-only there, since the target method has already returned,
// so cancel/override can't apply. Calls that arrive ON the JS thread (e.g. LocalPlayer.sendPosition,
// most gameplay) run inline, so cancel/override DO apply.
object Mixin {

    @JvmStatic
    fun inject(target: String, method: String, callback: Consumer<Any?>): MixinHandle =
        inject(target, method, "HEAD", callback)

    @JvmStatic
    fun inject(target: String, method: String, at: String, callback: Consumer<Any?>): MixinHandle =
        MixinHandle(MixinManager.register(target, method, null, at, callback))

    // disambiguate an overload by JVM descriptor; the plain inject hooks every method with that name
    @JvmStatic
    fun injectExact(target: String, method: String, descriptor: String, at: String, callback: Consumer<Any?>): MixinHandle =
        MixinHandle(MixinManager.register(target, method, descriptor, at, callback))
}

// remove() detaches the injection (target method reverts to original).
class MixinHandle internal constructor(private val hook: MixinRegistry.Hook) {
    fun remove() = MixinManager.remove(hook)
}
