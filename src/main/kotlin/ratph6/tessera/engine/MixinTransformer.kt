package ratph6.tessera.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

// The retransform-capable transformer behind TS mixins: rewrites matching methods to call MixinHooks at
// HEAD and/or RETURN. The JVM always hands the original bytes, so removing a hook + retransforming
// cleanly reverts — no accumulation. Injection is kept conservative so frame computation stays trivial.
object MixinTransformer : ClassFileTransformer {

    private const val CTX = "ratph6/tessera/api/MixinContext"
    private const val HOOKS = "ratph6/tessera/engine/MixinHooks"
    private const val HEAD_DESC = "(ILjava/lang/Object;[Ljava/lang/Object;)L$CTX;"
    private const val RET_DESC = "(ILjava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;)L$CTX;"

    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray,
    ): ByteArray? {
        if (className == null) return null
        // Skip Tessera's own classes BEFORE touching MixinRegistry — otherwise transform() for a
        // still-being-defined MixinRegistry/MixinHooks triggers a re-entrant load.
        if (className.startsWith("ratph6/tessera/")) return null
        val hooks = MixinRegistry.hooksFor(className)
        val widenings = AccessRegistry.forClass(className)
        if (hooks.isEmpty() && widenings.isEmpty()) return null

        return try {
            val cn = ClassNode()
            ClassReader(classfileBuffer).accept(cn, ClassReader.EXPAND_FRAMES)

            // Access widening (flag flips) only at initial load — the JVM rejects modifier changes on a
            // redefine/retransform. classBeingRedefined == null ⇒ first load.
            val accessChanged = if (classBeingRedefined == null) applyAccess(cn, widenings) else false
            var injected = false
            for (mn in cn.methods.toList()) {
                if (mn.name == "<init>" || mn.name == "<clinit>") continue
                if (mn.access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) != 0) continue
                val matching = hooks.filter { it.method == mn.name && (it.descriptor == null || it.descriptor == mn.desc) }
                for (hook in matching) {
                    when (hook.at) {
                        MixinAt.HEAD -> { injectHead(mn, hook.id); injected = true }
                        MixinAt.RETURN -> { injectReturn(mn, hook.id); injected = true }
                    }
                }
            }
            if (!accessChanged && !injected) return null

            // Recompute frames only when code changed (injection); access-only widening keeps the original
            // frames — safer than COMPUTE_FRAMES, which needs common-superclass lookups on Minecraft types.
            val cw = LoaderAwareWriter(loader, if (injected) ClassWriter.COMPUTE_FRAMES else 0)
            cn.accept(cw)
            cw.toByteArray()
        } catch (t: Throwable) {
            TesseraEngine.recordError("mixin:transform:$className", t)
            null
        }
    }

    private fun applyAccess(cn: ClassNode, widenings: List<AccessRegistry.Widen>): Boolean {
        if (widenings.isEmpty()) return false
        var changed = false
        for (w in widenings) when (w.kind) {
            AccessRegistry.Kind.CLASS -> {
                val na = publicNonFinal(cn.access)
                if (na != cn.access) { cn.access = na; changed = true }
            }
            AccessRegistry.Kind.METHOD -> for (m in cn.methods) {
                if (m.name == w.member && (w.descriptor == null || w.descriptor == m.desc)) {
                    val na = publicNonFinal(m.access)
                    if (na != m.access) { m.access = na; changed = true }
                }
            }
            AccessRegistry.Kind.FIELD -> for (f in cn.fields) {
                if (f.name == w.member) {
                    val na = publicNonFinal(f.access)
                    if (na != f.access) { f.access = na; changed = true }
                }
            }
        }
        return changed
    }

    private fun publicNonFinal(access: Int): Int =
        (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED or Opcodes.ACC_FINAL).inv()) or Opcodes.ACC_PUBLIC

    // HEAD: ctx = MixinHooks.head(id, self, args); if (ctx.cancelled) return [override|default];
    private fun injectHead(mn: MethodNode, id: Int) {
        val isStatic = mn.access and Opcodes.ACC_STATIC != 0
        val ctxLocal = mn.maxLocals
        mn.maxLocals += 1

        val il = InsnList()
        pushInt(il, id)
        il.add(selfNode(isStatic))
        il.add(buildArgsArray(mn, isStatic))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "head", HEAD_DESC, false))
        il.add(VarInsnNode(Opcodes.ASTORE, ctxLocal))

        val skip = LabelNode()
        il.add(VarInsnNode(Opcodes.ALOAD, ctxLocal))
        il.add(FieldInsnNode(Opcodes.GETFIELD, CTX, "cancelled", "Z"))
        il.add(JumpInsnNode(Opcodes.IFEQ, skip))
        il.add(returnFromCtx(mn, ctxLocal))
        il.add(skip)

        mn.instructions.insert(il)
    }

    // emit `hasReturnOverride ? (R) returnValue : defaultZero`
    private fun returnFromCtx(mn: MethodNode, ctxLocal: Int): InsnList {
        val ret = Type.getReturnType(mn.desc)
        val il = InsnList()
        if (ret.sort == Type.VOID) {
            il.add(InsnNode(Opcodes.RETURN))
            return il
        }
        val useOriginal = LabelNode()
        il.add(VarInsnNode(Opcodes.ALOAD, ctxLocal))
        il.add(FieldInsnNode(Opcodes.GETFIELD, CTX, "hasReturnOverride", "Z"))
        il.add(JumpInsnNode(Opcodes.IFEQ, useOriginal))
        il.add(VarInsnNode(Opcodes.ALOAD, ctxLocal))
        il.add(FieldInsnNode(Opcodes.GETFIELD, CTX, "returnValue", "Ljava/lang/Object;"))
        unbox(il, ret)
        il.add(InsnNode(ret.getOpcode(Opcodes.IRETURN)))
        // no override: zero/null of the right type (a plain cancel() with no value)
        il.add(useOriginal)
        pushZero(il, ret)
        il.add(InsnNode(ret.getOpcode(Opcodes.IRETURN)))
        return il
    }

    // RETURN: at each return site, ctx = MixinHooks.ret(id, self, args, boxedReturn);
    //         push ctx.hasReturnOverride ? (R) ctx.returnValue : original; then the original return.
    private fun injectReturn(mn: MethodNode, id: Int) {
        val isStatic = mn.access and Opcodes.ACC_STATIC != 0
        val ret = Type.getReturnType(mn.desc)
        // snapshot: we mutate the list while iterating
        val returns = mn.instructions.toArray().filter { isReturnInsn(it.opcode) }
        for (insn in returns) {
            val pre = if (ret.sort == Type.VOID) {
                voidReturnInsns(mn, id, isStatic)
            } else {
                valueReturnInsns(mn, id, isStatic, ret)
            }
            mn.instructions.insertBefore(insn, pre)
        }
    }

    private fun voidReturnInsns(mn: MethodNode, id: Int, isStatic: Boolean): InsnList {
        val il = InsnList()
        pushInt(il, id)
        il.add(selfNode(isStatic))
        il.add(buildArgsArray(mn, isStatic))
        il.add(InsnNode(Opcodes.ACONST_NULL))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "ret", RET_DESC, false))
        il.add(InsnNode(Opcodes.POP))
        return il
    }

    private fun valueReturnInsns(mn: MethodNode, id: Int, isStatic: Boolean, ret: Type): InsnList {
        val retLocal = mn.maxLocals; mn.maxLocals += 1
        val ctxLocal = mn.maxLocals; mn.maxLocals += 1
        val il = InsnList()
        // box original return value -> retLocal (stack on entry: [retval])
        box(il, ret)
        il.add(VarInsnNode(Opcodes.ASTORE, retLocal))
        pushInt(il, id)
        il.add(selfNode(isStatic))
        il.add(buildArgsArray(mn, isStatic))
        il.add(VarInsnNode(Opcodes.ALOAD, retLocal))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "ret", RET_DESC, false))
        il.add(VarInsnNode(Opcodes.ASTORE, ctxLocal))
        // leave (R) (override ? ctx.returnValue : original) on the stack for the trailing xRETURN
        val useOriginal = LabelNode()
        val done = LabelNode()
        il.add(VarInsnNode(Opcodes.ALOAD, ctxLocal))
        il.add(FieldInsnNode(Opcodes.GETFIELD, CTX, "hasReturnOverride", "Z"))
        il.add(JumpInsnNode(Opcodes.IFEQ, useOriginal))
        il.add(VarInsnNode(Opcodes.ALOAD, ctxLocal))
        il.add(FieldInsnNode(Opcodes.GETFIELD, CTX, "returnValue", "Ljava/lang/Object;"))
        unbox(il, ret)
        il.add(JumpInsnNode(Opcodes.GOTO, done))
        il.add(useOriginal)
        il.add(VarInsnNode(Opcodes.ALOAD, retLocal))
        unbox(il, ret)
        il.add(done)
        return il
    }

    private fun isReturnInsn(op: Int): Boolean =
        op == Opcodes.RETURN || op == Opcodes.IRETURN || op == Opcodes.LRETURN ||
            op == Opcodes.FRETURN || op == Opcodes.DRETURN || op == Opcodes.ARETURN

    private fun selfNode(isStatic: Boolean): AbstractInsnNode =
        if (isStatic) InsnNode(Opcodes.ACONST_NULL) else VarInsnNode(Opcodes.ALOAD, 0)

    // Object[] of the boxed method args, left on the stack
    private fun buildArgsArray(mn: MethodNode, isStatic: Boolean): InsnList {
        val il = InsnList()
        val args = Type.getArgumentTypes(mn.desc)
        pushInt(il, args.size)
        il.add(TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"))
        var local = if (isStatic) 0 else 1
        for (i in args.indices) {
            val t = args[i]
            il.add(InsnNode(Opcodes.DUP))
            pushInt(il, i)
            il.add(VarInsnNode(t.getOpcode(Opcodes.ILOAD), local))
            box(il, t)
            il.add(InsnNode(Opcodes.AASTORE))
            local += t.size
        }
        return il
    }

    // box the primitive on the stack (no-op for reference types)
    private fun box(il: InsnList, t: Type) {
        val (owner, prim) = when (t.sort) {
            Type.BOOLEAN -> "java/lang/Boolean" to "Z"
            Type.CHAR -> "java/lang/Character" to "C"
            Type.BYTE -> "java/lang/Byte" to "B"
            Type.SHORT -> "java/lang/Short" to "S"
            Type.INT -> "java/lang/Integer" to "I"
            Type.FLOAT -> "java/lang/Float" to "F"
            Type.LONG -> "java/lang/Long" to "J"
            Type.DOUBLE -> "java/lang/Double" to "D"
            else -> return
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "valueOf", "($prim)L$owner;", false))
    }

    // cast/unbox the reference on the stack to t
    private fun unbox(il: InsnList, t: Type) {
        val (owner, method, prim) = when (t.sort) {
            Type.BOOLEAN -> Triple("java/lang/Boolean", "booleanValue", "Z")
            Type.CHAR -> Triple("java/lang/Character", "charValue", "C")
            Type.BYTE -> Triple("java/lang/Byte", "byteValue", "B")
            Type.SHORT -> Triple("java/lang/Short", "shortValue", "S")
            Type.INT -> Triple("java/lang/Integer", "intValue", "I")
            Type.FLOAT -> Triple("java/lang/Float", "floatValue", "F")
            Type.LONG -> Triple("java/lang/Long", "longValue", "J")
            Type.DOUBLE -> Triple("java/lang/Double", "doubleValue", "D")
            else -> {
                val internal = if (t.sort == Type.ARRAY) t.descriptor else t.internalName
                if (internal != "java/lang/Object") il.add(TypeInsnNode(Opcodes.CHECKCAST, internal))
                return
            }
        }
        il.add(TypeInsnNode(Opcodes.CHECKCAST, owner))
        il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, method, "()$prim", false))
    }

    // zero value of t (default for cancel() with no return override)
    private fun pushZero(il: InsnList, t: Type) {
        when (t.sort) {
            Type.LONG -> il.add(InsnNode(Opcodes.LCONST_0))
            Type.FLOAT -> il.add(InsnNode(Opcodes.FCONST_0))
            Type.DOUBLE -> il.add(InsnNode(Opcodes.DCONST_0))
            Type.OBJECT, Type.ARRAY -> il.add(InsnNode(Opcodes.ACONST_NULL))
            else -> il.add(InsnNode(Opcodes.ICONST_0)) // boolean/char/byte/short/int
        }
    }

    private fun pushInt(il: InsnList, value: Int) {
        when {
            value in -1..5 -> il.add(InsnNode(Opcodes.ICONST_0 + value))
            value in Byte.MIN_VALUE..Byte.MAX_VALUE -> il.add(IntInsnNode(Opcodes.BIPUSH, value))
            value in Short.MIN_VALUE..Short.MAX_VALUE -> il.add(IntInsnNode(Opcodes.SIPUSH, value))
            else -> il.add(LdcInsnNode(value))
        }
    }

    // Resolves common-superclass queries against the target loader (Knot for Minecraft), falling back to
    // Object — never loading through the wrong loader, never failing the transform over a frame merge.
    private class LoaderAwareWriter(private val loader: ClassLoader?, flags: Int) : ClassWriter(flags) {
        private val cl = loader ?: ClassLoader.getSystemClassLoader()

        override fun getClassLoader(): ClassLoader = cl

        override fun getCommonSuperClass(type1: String, type2: String): String = try {
            val c1 = Class.forName(type1.replace('/', '.'), false, cl)
            val c2 = Class.forName(type2.replace('/', '.'), false, cl)
            when {
                c1.isAssignableFrom(c2) -> type1
                c2.isAssignableFrom(c1) -> type2
                c1.isInterface || c2.isInterface -> "java/lang/Object"
                else -> {
                    var c = c1
                    while (!c.isAssignableFrom(c2)) c = c.superclass ?: return "java/lang/Object"
                    c.name.replace('.', '/')
                }
            }
        } catch (_: Throwable) {
            "java/lang/Object"
        }
    }
}
