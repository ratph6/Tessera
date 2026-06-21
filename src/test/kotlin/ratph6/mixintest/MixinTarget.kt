package ratph6.mixintest

/**
 * Plain target the mixin transformer rewrites in [ratph6.tessera.engine.MixinTransformerTest]. Lives
 * outside the `ratph6.tessera.*` package on purpose: the transformer skips Tessera's own classes (so
 * scripts can't inject the runtime), which would otherwise refuse this target.
 */
@Suppress("unused")
class MixinTarget {
    var counter: Int = 0

    fun greet(n: Int): String = "hi$n"
    fun sideEffect() { counter++ }
    fun flag(): Boolean = false
}
