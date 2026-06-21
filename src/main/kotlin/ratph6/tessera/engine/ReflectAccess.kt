package ratph6.tessera.engine

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection-backed member access for scripts — the part of "access widening" that works on classes
 * that are *already loaded* (where bytecode modifier-flipping is illegal). Mod code runs with full Java
 * access and `net.minecraft` lives in the unnamed module, so `setAccessible(true)` succeeds; GraalJS
 * can't reach `java.lang.reflect` itself, so these helpers are the bridge.
 *
 * Fields/methods are resolved by walking the class hierarchy and cached. Numeric arguments coming from
 * JS (which arrive boxed and loosely typed) are coerced to the target field/parameter type.
 */
object ReflectAccess {

    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    // --- fields -----------------------------------------------------------------------------------

    fun getField(target: Any, name: String): Any? = resolveField(target.javaClass, name).get(target)

    fun setField(target: Any, name: String, value: Any?) {
        val f = resolveField(target.javaClass, name)
        f.set(target, coerce(value, f.type))
    }

    fun getStaticField(className: String, name: String): Any? {
        val cls = loadClass(className)
        return resolveField(cls, name).get(null)
    }

    fun setStaticField(className: String, name: String, value: Any?) {
        val cls = loadClass(className)
        val f = resolveField(cls, name)
        f.set(null, coerce(value, f.type))
    }

    // --- methods ----------------------------------------------------------------------------------

    fun invoke(target: Any, name: String, args: Array<out Any?>): Any? {
        val m = resolveMethod(target.javaClass, name, args.size)
        return m.invoke(target, *coerceArgs(args, m))
    }

    fun invokeStatic(className: String, name: String, args: Array<out Any?>): Any? {
        val cls = loadClass(className)
        val m = resolveMethod(cls, name, args.size)
        return m.invoke(null, *coerceArgs(args, m))
    }

    // --- resolution ------------------------------------------------------------------------------

    private fun loadClass(name: String): Class<*> =
        Class.forName(name, false, TesseraEngine.scriptClassLoader)

    private fun resolveField(start: Class<*>, name: String): Field {
        val key = "${start.name}#$name"
        fieldCache[key]?.let { return it }
        var c: Class<*>? = start
        while (c != null) {
            val f = runCatching { c!!.getDeclaredField(name) }.getOrNull()
            if (f != null) {
                f.isAccessible = true
                fieldCache[key] = f
                return f
            }
            c = c.superclass
        }
        throw NoSuchFieldException("no field '$name' on ${start.name} (or its superclasses)")
    }

    private fun resolveMethod(start: Class<*>, name: String, argCount: Int): Method {
        val key = "${start.name}#$name/$argCount"
        methodCache[key]?.let { return it }
        var c: Class<*>? = start
        while (c != null) {
            val m = c.declaredMethods.firstOrNull { it.name == name && it.parameterCount == argCount }
            if (m != null) {
                m.isAccessible = true
                methodCache[key] = m
                return m
            }
            c = c.superclass
        }
        throw NoSuchMethodException("no method '$name' with $argCount arg(s) on ${start.name} (or its superclasses)")
    }

    // --- coercion --------------------------------------------------------------------------------

    private fun coerceArgs(args: Array<out Any?>, m: Method): Array<Any?> {
        val types = m.parameterTypes
        return Array(args.size) { i -> coerce(args[i], types[i]) }
    }

    /** Convert a (often JS-boxed) value to the declared [type] — chiefly numeric/boolean/char widening. */
    private fun coerce(value: Any?, type: Class<*>): Any? {
        if (value == null || type.isInstance(value)) return value
        if (value is Number) return when (type) {
            Integer.TYPE, Integer::class.java -> value.toInt()
            java.lang.Long.TYPE, java.lang.Long::class.java -> value.toLong()
            java.lang.Double.TYPE, java.lang.Double::class.java -> value.toDouble()
            java.lang.Float.TYPE, java.lang.Float::class.java -> value.toFloat()
            java.lang.Short.TYPE, java.lang.Short::class.java -> value.toShort()
            java.lang.Byte.TYPE, java.lang.Byte::class.java -> value.toByte()
            Character.TYPE, Character::class.java -> value.toInt().toChar()
            else -> value
        }
        if (value is Boolean && (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java)) return value
        return value
    }

    /** True if [name]'s field on [target] is final (informational — final instance fields still write). */
    fun isFinalField(target: Any, name: String): Boolean =
        Modifier.isFinal(resolveField(target.javaClass, name).modifiers)
}
