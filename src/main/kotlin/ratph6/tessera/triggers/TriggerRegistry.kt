package ratph6.tessera.triggers

import ratph6.tessera.engine.TesseraCallback
import ratph6.tessera.engine.TesseraModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// registry of live triggers indexed by type. registration and lookup happen on different threads, hence concurrent collections.
object TriggerRegistry {
    private val ids = AtomicInteger(1)
    private val byId = ConcurrentHashMap<Int, TriggerMeta>()
    private val byType = ConcurrentHashMap<String, CopyOnWriteArrayList<TriggerMeta>>()

    fun register(type: String, module: TesseraModule?, callback: TesseraCallback): TriggerMeta {
        val meta = TriggerMeta(ids.getAndIncrement(), type, module, callback)
        byId[meta.id] = meta
        index(meta)
        return meta
    }

    private fun index(meta: TriggerMeta) {
        val list = byType.getOrPut(meta.type) { CopyOnWriteArrayList() }
        if (meta !in list) {
            list.add(meta)
            list.sortBy { it.priority }
        }
    }

    fun get(id: Int): TriggerMeta? = byId[id]

    // enabled triggers of a type, lowest priority value first
    fun byType(type: String): List<TriggerMeta> = byType[type]?.filter { it.enabled } ?: emptyList()

    // no-allocation check for any enabled trigger of a type
    fun hasType(type: String): Boolean = byType[type]?.any { it.enabled } == true

    fun count(): Int = byId.size
    fun countForModule(module: String): Int = byId.values.count { it.module?.name == module }

    fun reindex(meta: TriggerMeta) {
        byType[meta.type]?.sortBy { it.priority }
    }

    fun unregister(id: Int) {
        val meta = byId.remove(id) ?: return
        meta.enabled = false
        byType[meta.type]?.remove(meta)
    }

    fun removeModule(module: String) {
        val removed = byId.values.filter { it.module?.name == module }
        for (m in removed) {
            m.enabled = false
            byType[m.type]?.remove(m)
            byId.remove(m.id)
        }
    }

    fun clear() {
        byId.clear()
        byType.clear()
    }

    fun commandByName(name: String): TriggerMeta? =
        byType(TriggerType.COMMAND).firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun allCommands(): List<TriggerMeta> = byType(TriggerType.COMMAND)
}
