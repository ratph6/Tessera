package ratph6.tessera.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Where in a target method an injection runs. */
object MixinAt {
    const val HEAD = "HEAD"
    /** Every `return` site (alias `TAIL`). */
    const val RETURN = "RETURN"
}

/**
 * The set of TypeScript-defined injections currently active. Indexed two ways: by the internal class
 * name (`net/minecraft/...`) so [MixinTransformer] can find what to inject when a class is (re)loaded,
 * and by a small integer [Hook.id] that the injected bytecode passes back to [MixinHooks] to identify
 * which callback to run.
 *
 * Hooks carry their owning [module] so a `/te reload` or module unload can strip them again.
 */
object MixinRegistry {

    data class Hook(
        val id: Int,
        /** Binary name, e.g. `net.minecraft.client.Minecraft`. */
        val targetBinary: String,
        /** Internal name, e.g. `net/minecraft/client/Minecraft`. */
        val targetInternal: String,
        val method: String,
        /** Method descriptor to match exactly, or null to match every overload by name. */
        val descriptor: String?,
        /** One of [MixinAt]. */
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

    /** Hooks whose target is the class with this internal name (`net/minecraft/...`). */
    fun hooksFor(internalName: String): List<Hook> = byClass[internalName] ?: emptyList()

    fun remove(hook: Hook): String? {
        byId.remove(hook.id)
        byClass[hook.targetInternal]?.let { list ->
            list.remove(hook)
            if (list.isEmpty()) byClass.remove(hook.targetInternal)
        }
        return hook.targetBinary
    }

    /** Drop every hook owned by [moduleName]; returns the binary names of classes that need reverting. */
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

    /** Drop every hook; returns the binary names of all classes that need reverting. */
    fun clear(): Set<String> {
        val affected = byId.values.mapTo(HashSet()) { it.targetBinary }
        byId.clear()
        byClass.clear()
        return affected
    }

    fun isEmpty(): Boolean = byId.isEmpty()
}
