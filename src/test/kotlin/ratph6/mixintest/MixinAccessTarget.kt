package ratph6.mixintest

/**
 * Target for [ratph6.tessera.engine.AccessWidenerTest]: a final class with a private field and a
 * private method, so the test can verify the transformer makes them public / non-final. Lives outside
 * `ratph6.tessera.*` (which the transformer skips).
 */
@Suppress("unused")
class MixinAccessTarget {
    private var hidden: Int = 42
    private fun secret(): String = "shh-$hidden"
}
