package ratph6.tessera.api

import ratph6.tessera.triggers.MatchMode
import ratph6.tessera.triggers.TriggerMeta
import ratph6.tessera.triggers.TriggerRegistry

/**
 * Fluent, chainable handle returned by [Tessera.register]. Every setter returns `this` so scripts can
 * write `Tessera.register("chat", "onChat").setContains().setCriteria("hi").setPriority(10)`.
 */
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

    /**
     * Only fire when the event's primary value is an instance of [className] (matched by simple or
     * fully-qualified name, including superclasses) — e.g. on `Event.PACKET_RECEIVED`,
     * `.setFilteredClass("ClientboundSetHealthPacket")` fires only for that packet. ChatTriggers name.
     */
    fun setFilteredClass(className: String): TriggerHandle { meta.filterClass = className; return this }
    fun setEventClass(className: String): TriggerHandle { meta.eventClass = className; return this }
    fun unregister(): TriggerHandle { TriggerRegistry.unregister(meta.id); return this }

    /** The numeric id of the underlying trigger (mostly for debugging). */
    fun id(): Int = meta.id
}
