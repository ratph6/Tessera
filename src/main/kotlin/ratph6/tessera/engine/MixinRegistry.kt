package ratph6.tessera.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object MixinAt {
    const val HEAD = "HEAD"
    const val RETURN = "RETURN" // alias TAIL
}

// Active TS injections, indexed by internal class name (for MixinTransformer) and by Hook.id (which the
// injected bytecode passes back to MixinHooks). Hooks carry their module so a reload/unload can strip them.
object MixinRegistry {

    data class Hook(
        val id: Int,
        val targetBinary: String, // net.minecraft.client.Minecraft
        val targetInternal: String, // net/minecraft/client/Minecraft
        val method: String,
        val descriptor: String?, // null = match every overload by name
        val at: String,
        val module: TesseraModule?,
        val callback: TesseraCallback,
    )

    private val ids = AtomicInteger(1)
    private val byId = ConcurrentHashMap<Int, Hook>()
    private val byClass = ConcurrentHashMap<String, CopyOnWriteArrayList<Hook>>()

    fun add(
        targetBinary: String,
        method: String,
        descriptor: String?,
        at: String,
        module: TesseraModule?,
        callback: TesseraCallback,
    ): Hook {
        val internal = targetBinary.replace('.', '/')
        val hook = Hook(ids.getAndIncrement(), targetBinary, internal, method, descriptor, at, module, callback)
        byId[hook.id] = hook
        byClass.computeIfAbsent(internal) { CopyOnWriteArrayList() }.add(hook)
        return hook
    }

    fun get(id: Int): Hook? = byId[id]

    fun hooksFor(internalName: String): List<Hook> = byClass[internalName] ?: emptyList()

    fun remove(hook: Hook): String? {
        byId.remove(hook.id)
        byClass[hook.targetInternal]?.let { list ->
            list.remove(hook)
            if (list.isEmpty()) byClass.remove(hook.targetInternal)
        }
        return hook.targetBinary
    }

    // returns binary names of classes that need reverting
    fun removeModule(moduleName: String): Set<String> {
        val affected = HashSet<String>()
        for (hook in byId.values.toList()) {
            if (hook.module?.name == moduleName) {
                remove(hook)
                affected.add(hook.targetBinary)
            }
        }
        return affected
    }

    // returns binary names of classes that need reverting
    fun clear(): Set<String> {
        val affected = byId.values.mapTo(HashSet()) { it.targetBinary }
        byId.clear()
        byClass.clear()
        return affected
    }

    fun isEmpty(): Boolean = byId.isEmpty()
}
