package ratph6.tessera.engine

import ratph6.tessera.api.MixinContext

// ABI for the bytecode MixinTransformer injects: it emits INVOKESTATIC against these exact
// names/descriptors, so changing a signature means changing the transformer too.
object MixinHooks {

    // HEAD of an injected method; args are boxed
    @JvmStatic
    fun head(id: Int, self: Any?, args: Array<Any?>): MixinContext {
        val hook = MixinRegistry.get(id) ?: return MixinContext("", "", self, args)
        val ctx = MixinContext(hook.targetBinary, hook.method, self, args)
        TesseraEngine.invokeMixin(hook, ctx)
        return ctx
    }

    // each RETURN site; returnValue is the boxed value about to be returned
    @JvmStatic
    fun ret(id: Int, self: Any?, args: Array<Any?>, returnValue: Any?): MixinContext {
        val hook = MixinRegistry.get(id) ?: return MixinContext("", "", self, args)
        val ctx = MixinContext(hook.targetBinary, hook.method, self, args)
        ctx.returnValue = returnValue
        TesseraEngine.invokeMixin(hook, ctx)
        return ctx
    }
}
