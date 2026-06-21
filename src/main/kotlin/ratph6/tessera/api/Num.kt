package ratph6.tessera.api

/**
 * Small numeric helpers for scripts. swc4j compiles to JVM bytecode, not a JS engine, so JS globals
 * like `parseFloat` aren't guaranteed — use these to parse command arguments (which arrive as
 * strings) into numbers.
 */
object Num {
    /** Parse [s] as a number, returning [fallback] if it isn't one. */
    @JvmStatic fun parse(s: String, fallback: Double): Double = s.trim().toDoubleOrNull() ?: fallback

    /** Parse [s] as a number, returning 0 if it isn't one. */
    @JvmStatic fun parse(s: String): Double = parse(s, 0.0)
}
