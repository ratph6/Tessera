package ratph6.tessera.api

// Numeric parsing — swc4j compiles to bytecode, not JS, so parseFloat etc aren't guaranteed.
object Num {
    @JvmStatic fun parse(s: String, fallback: Double): Double = s.trim().toDoubleOrNull() ?: fallback

    @JvmStatic fun parse(s: String): Double = parse(s, 0.0)
}
