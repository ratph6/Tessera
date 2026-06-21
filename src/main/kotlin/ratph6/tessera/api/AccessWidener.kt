package ratph6.tessera.api

import ratph6.tessera.engine.AccessRegistry
import ratph6.tessera.engine.MixinManager
import ratph6.tessera.engine.ReflectAccess

// Runtime access widener. Bytecode widening only works on not-yet-loaded classes (the JVM only
// permits access changes while a class is being defined) — register early. For already-loaded
// classes use the reflection accessors below. Widenings revert on reload/unload.
object AccessWidener {

    // bytecode widening — not-yet-loaded classes only
    @JvmStatic
    fun widenField(target: String, field: String) =
        MixinManager.widen(target, AccessRegistry.Kind.FIELD, field, null)

    @JvmStatic
    fun widenMethod(target: String, method: String) =
        MixinManager.widen(target, AccessRegistry.Kind.METHOD, method, null)

    @JvmStatic
    fun widenMethod(target: String, method: String, descriptor: String) =
        MixinManager.widen(target, AccessRegistry.Kind.METHOD, method, descriptor)

    @JvmStatic
    fun makeExtendable(target: String) =
        MixinManager.widen(target, AccessRegistry.Kind.CLASS, null, null)

    // reflection accessors — work on already-loaded classes
    @JvmStatic
    fun getField(target: Any, field: String): Any? = ReflectAccess.getField(target, field)

    @JvmStatic
    fun setField(target: Any, field: String, value: Any?) = ReflectAccess.setField(target, field, value)

    @JvmStatic
    fun getStaticField(className: String, field: String): Any? = ReflectAccess.getStaticField(className, field)

    @JvmStatic
    fun setStaticField(className: String, field: String, value: Any?) =
        ReflectAccess.setStaticField(className, field, value)

    @JvmStatic
    fun invoke(target: Any, method: String, vararg args: Any?): Any? = ReflectAccess.invoke(target, method, args)

    @JvmStatic
    fun invokeStatic(className: String, method: String, vararg args: Any?): Any? =
        ReflectAccess.invokeStatic(className, method, args)
}
