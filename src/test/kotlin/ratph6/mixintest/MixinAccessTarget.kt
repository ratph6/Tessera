package ratph6.mixintest

// access-widener target; lives outside ratph6.tessera.* which the transformer skips
@Suppress("unused")
class MixinAccessTarget {
    private var hidden: Int = 42
    private fun secret(): String = "shh-$hidden"
}
