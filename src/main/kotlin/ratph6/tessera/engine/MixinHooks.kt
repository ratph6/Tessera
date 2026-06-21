package ratph6.tessera.engine

import ratph6.tessera.api.MixinContext

/**
 * Static entry points that the bytecode injected by [MixinTransformer] calls into. The signatures here
 * are an ABI — `MixinTransformer` emits `INVOKESTATIC` calls against these exact names/descriptors, so
 * changing them means changing the transformer too.
 *
 * Both methods build a [MixinContext], run the registered script callback, and hand the context back so
 * the injected code can read `cancelled` / `returnValue`.
 */
object MixinHooks {

    /** Called at the HEAD of an injected method. [args] are the boxed method arguments. */
    @JvmStatic
    fun head(id: Int, self: Any?, args: Array<Any?>): MixinContext {
        val hook = MixinRegistry.get(id) ?: return MixinContext("", "", self, args)
        val ctx = MixinContext(hook.targetBinary, hook.method, self, args)
        TesseraEngine.invokeMixin(hook, ctx)
        return ctx
    }

    /** Called at each RETURN site of an injected method. [returnValue] is the boxed value about to be returned. */
    @JvmStatic
    fun ret(id: Int, self: Any?, args: Array<Any?>, returnValue: Any?): MixinContext {
        val hook = MixinRegistry.get(id) ?: return MixinContext("", "", self, args)
        val ctx = MixinContext(hook.targetBinary, hook.method, self, args)
        ctx.returnValue = returnValue
        TesseraEngine.invokeMixin(hook, ctx)
        return ctx
    }
}
