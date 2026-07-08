package ratph6.tessera.engine

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

// Reflection-backed member access — the part of "access widening" that works on already-loaded classes
// (where bytecode modifier-flipping is illegal). setAccessible succeeds since net.minecraft is in the
// unnamed module; GraalJS can't reach java.lang.reflect itself, so these helpers bridge it.
object ReflectAccess {

    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

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

    fun invoke(target: Any, name: String, args: Array<out Any?>): Any? {
        val m = resolveMethod(target.javaClass, name, args)
        return m.invoke(target, *coerceArgs(args, m))
    }

    fun invokeStatic(className: String, name: String, args: Array<out Any?>): Any? {
        val cls = loadClass(className)
        val m = resolveMethod(cls, name, args)
        return m.invoke(null, *coerceArgs(args, m))
    }

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

    // Overload resolution: exact type match first, then coercion-compatible, then bare arity as a
    // last resort. declaredMethods order is unspecified by the JVM — picking "first with N args"
    // nondeterministically bound a different overload between runs.
    private fun resolveMethod(start: Class<*>, name: String, args: Array<out Any?>): Method {
        val sig = args.joinToString(",") { it?.javaClass?.name ?: "null" }
        val key = "${start.name}#$name/${args.size}/$sig"
        methodCache[key]?.let { return it }
        var arity: Method? = null
        var compatible: Method? = null
        var c: Class<*>? = start
        while (c != null) {
            val candidates = c.declaredMethods.filter { it.name == name && it.parameterCount == args.size }
            candidates.firstOrNull { typesExact(it.parameterTypes, args) }?.let { m ->
                m.isAccessible = true
                methodCache[key] = m
                return m
            }
            if (compatible == null) compatible = candidates.firstOrNull { typesCompatible(it.parameterTypes, args) }
            if (arity == null) arity = candidates.firstOrNull()
            c = c.superclass
        }
        val m = compatible ?: arity
            ?: throw NoSuchMethodException("no method '$name' with ${args.size} arg(s) on ${start.name} (or its superclasses)")
        m.isAccessible = true
        methodCache[key] = m
        return m
    }

    private fun typesExact(types: Array<Class<*>>, args: Array<out Any?>): Boolean =
        types.indices.all { i -> args[i]?.let { a -> !types[i].isPrimitive && types[i] == a.javaClass } ?: !types[i].isPrimitive }

    private fun typesCompatible(types: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (i in types.indices) {
            val t = types[i]
            val a = args[i]
            if (a == null) {
                if (t.isPrimitive) return false else continue
            }
            when {
                t.isInstance(a) -> {}
                a is Number && (t.isPrimitive && t != java.lang.Boolean.TYPE || Number::class.java.isAssignableFrom(t) || t == Character.TYPE) -> {}
                a is Boolean && (t == java.lang.Boolean.TYPE || t == java.lang.Boolean::class.java) -> {}
                else -> return false
            }
        }
        return true
    }

    private fun coerceArgs(args: Array<out Any?>, m: Method): Array<Any?> {
        val types = m.parameterTypes
        return Array(args.size) { i -> coerce(args[i], types[i]) }
    }

    // convert a (often JS-boxed) value to the declared type — chiefly numeric/boolean/char widening
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

    // informational only — final instance fields still write via reflection
    fun isFinalField(target: Any, name: String): Boolean =
        Modifier.isFinal(resolveField(target.javaClass, name).modifiers)
}
