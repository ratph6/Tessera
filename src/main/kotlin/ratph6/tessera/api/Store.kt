package ratph6.tessera.api

import java.util.concurrent.ConcurrentHashMap

// Global key→value store for script state. Use instead of many captured `let`s: swc4j boxes each
// reassigned captured var and miscomputes the stack when one callback captures many (VerifyError);
// going through Store keeps callbacks capture-free.
object Store {
    private val nums = ConcurrentHashMap<String, Double>()
    private val bools = ConcurrentHashMap<String, Boolean>()
    private val strs = ConcurrentHashMap<String, String>()

    @JvmStatic fun getNum(key: String, fallback: Double): Double = nums[key] ?: fallback
    @JvmStatic fun setNum(key: String, value: Double) { nums[key] = value }

    @JvmStatic fun getBool(key: String, fallback: Boolean): Boolean = bools[key] ?: fallback
    @JvmStatic fun setBool(key: String, value: Boolean) { bools[key] = value }

    // flip a boolean (default false), return the new value
    @JvmStatic fun toggle(key: String): Boolean {
        val next = !(bools[key] ?: false)
        bools[key] = next
        return next
    }

    @JvmStatic fun getStr(key: String, fallback: String): String = strs[key] ?: fallback
    @JvmStatic fun setStr(key: String, value: String) { strs[key] = value }

    @JvmStatic fun clear() { nums.clear(); bools.clear(); strs.clear() }
}
