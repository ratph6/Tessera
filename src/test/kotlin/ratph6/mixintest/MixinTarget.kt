package ratph6.mixintest

// mixin target; lives outside ratph6.tessera.* on purpose — the transformer skips its own classes
@Suppress("unused")
class MixinTarget {
    var counter: Int = 0

    fun greet(n: Int): String = "hi$n"
    fun sideEffect() { counter++ }
    fun flag(): Boolean = false
}
