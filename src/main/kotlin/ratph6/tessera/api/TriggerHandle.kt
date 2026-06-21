package ratph6.tessera.api

import ratph6.tessera.triggers.MatchMode
import ratph6.tessera.triggers.TriggerMeta
import ratph6.tessera.triggers.TriggerRegistry

// Chainable handle from Tessera.register; every setter returns this.
class TriggerHandle(private val meta: TriggerMeta) {
    fun setPriority(priority: Int): TriggerHandle { meta.priority = priority; TriggerRegistry.reindex(meta); return this }
    fun setCriteria(pattern: String): TriggerHandle { meta.criteria = pattern; return this }
    fun setContains(): TriggerHandle { meta.matchMode = MatchMode.CONTAINS; return this }
    fun setStart(): TriggerHandle { meta.matchMode = MatchMode.START; return this }
    fun setEnd(): TriggerHandle { meta.matchMode = MatchMode.END; return this }
    fun setExact(): TriggerHandle { meta.matchMode = MatchMode.EXACT; return this }
    fun setCancelable(value: Boolean): TriggerHandle { meta.cancelable = value; return this }
    fun setDelay(ms: Int): TriggerHandle { meta.delayMs = ms; return this }
    fun setFps(fps: Int): TriggerHandle { if (fps > 0) meta.delayMs = (1000.0 / fps).toInt().coerceAtLeast(1); return this }
    fun setName(name: String): TriggerHandle { meta.name = name; return this }
    fun setSound(sound: String): TriggerHandle { meta.sound = sound; return this }
    fun filterClass(className: String): TriggerHandle { meta.filterClass = className; return this }

    // only fire when the primary value is an instance of className (simple or FQ name, incl. superclasses)
    fun setFilteredClass(className: String): TriggerHandle { meta.filterClass = className; return this }
    fun setEventClass(className: String): TriggerHandle { meta.eventClass = className; return this }
    fun unregister(): TriggerHandle { TriggerRegistry.unregister(meta.id); return this }

    fun id(): Int = meta.id
}
