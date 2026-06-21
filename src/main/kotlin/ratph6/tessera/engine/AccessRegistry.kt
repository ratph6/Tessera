package ratph6.tessera.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runtime access-widening requests from TypeScript ([ratph6.tessera.api.AccessWidener]). Like Fabric's
 * static access widener, but applied live by [MixinTransformer] when a class is (re)loaded — so scripts
 * can open up private/final Minecraft members on demand.
 *
 * Indexed by internal class name (`net/minecraft/...`) so the transformer can find what to widen.
 * Entries carry their owning module so a `/te reload` or unload drops them.
 *
 * Caveat (enforced by the JVM, not us): modifier changes are only legal while a class is being defined.
 * A widening registered before its class loads takes effect; one registered after the class is already
 * loaded cannot be retro-applied this session (see [MixinManager]).
 */
object AccessRegistry {

    enum class Kind { CLASS, METHOD, FIELD }

    data class Widen(
        val targetBinary: String,
        val targetInternal: String,
        val kind: Kind,
        /** Method or field name; null for [Kind.CLASS]. */
        val member: String?,
        /** Method descriptor to match exactly, or null to match every overload / not applicable. */
        val descriptor: String?,
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
