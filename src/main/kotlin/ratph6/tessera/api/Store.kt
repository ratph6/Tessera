package ratph6.tessera.api

import java.util.concurrent.ConcurrentHashMap

/**
 * A simple global key→value store for script state (numbers, booleans, strings).
 *
 * Use it instead of many module-level `let`s when callbacks need to share mutable state: the swc4j
 * bytecode compiler boxes each reassigned captured variable and miscomputes the stack when a single
 * callback captures many of them (→ `VerifyError`). Reading/writing through [Store] keeps callbacks
 * capture-free, sidestepping that limit. Keys are arbitrary strings.
 *
 * ```ts
 * Store.setNum("size", 2);
 * Tessera.register(Event.RENDER_ENTITY, (e: EntityWrapper) => Tessellator.scale(Store.getNum("size", 1), 1, 1));
 * ```
 */
object Store {
    private val nums = ConcurrentHashMap<String, Double>()
    private val bools = ConcurrentHashMap<String, Boolean>()
    private val strs = ConcurrentHashMap<String, String>()

    @JvmStatic fun getNum(key: String, fallback: Double): Double = nums[key] ?: fallback
    @JvmStatic fun setNum(key: String, value: Double) { nums[key] = value }

    @JvmStatic fun getBool(key: String, fallback: Boolean): Boolean = bools[key] ?: fallback
    @JvmStatic fun setBool(key: String, value: Boolean) { bools[key] = value }

    /** Flip a boolean (defaulting to false) and return the new value. */
    @JvmStatic fun toggle(key: String): Boolean {
        val next = !(bools[key] ?: false)
        bools[key] = next
        return next
    }

    @JvmStatic fun getStr(key: String, fallback: String): String = strs[key] ?: fallback
    @JvmStatic fun setStr(key: String, value: String) { strs[key] = value }

    /** Wipe everything (e.g. on a fresh load). */
    @JvmStatic fun clear() { nums.clear(); bools.clear(); strs.clear() }
}
