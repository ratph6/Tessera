package ratph6.tessera.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// Runtime access-widening requests, applied live by MixinTransformer on (re)load. Indexed by internal
// class name. JVM only allows modifier changes while a class is being defined — widen before first load.
object AccessRegistry {

    enum class Kind { CLASS, METHOD, FIELD }

    data class Widen(
        val targetBinary: String,
        val targetInternal: String,
        val kind: Kind,
        val member: String?, // null for CLASS
        val descriptor: String?, // null = match every overload / n/a
        val module: TesseraModule?,
    )

    private val byClass = ConcurrentHashMap<String, CopyOnWriteArrayList<Widen>>()
    private val all = CopyOnWriteArrayList<Widen>()

    fun add(targetBinary: String, kind: Kind, member: String?, descriptor: String?, module: TesseraModule?): Widen {
        val internal = targetBinary.replace('.', '/')
        val w = Widen(targetBinary, internal, kind, member, descriptor, module)
        byClass.computeIfAbsent(internal) { CopyOnWriteArrayList() }.add(w)
        all.add(w)
        return w
    }

    fun forClass(internalName: String): List<Widen> = byClass[internalName] ?: emptyList()

    fun removeModule(moduleName: String): Set<String> {
        val affected = HashSet<String>()
        for (w in all.toList()) {
            if (w.module?.name == moduleName) {
                all.remove(w)
                byClass[w.targetInternal]?.let { list ->
                    list.remove(w)
                    if (list.isEmpty()) byClass.remove(w.targetInternal)
                }
                affected.add(w.targetBinary)
            }
        }
        return affected
    }

    fun clear(): Set<String> {
        val affected = all.mapTo(HashSet()) { it.targetBinary }
        all.clear()
        byClass.clear()
        return affected
    }

    fun isEmpty(): Boolean = all.isEmpty()
}
