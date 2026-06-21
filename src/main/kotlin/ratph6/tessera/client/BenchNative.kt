package ratph6.tessera.client

import net.minecraft.core.BlockPos
import ratph6.tessera.api.ChatLib

/**
 * Hand-written JVM baseline for the two benchmarks in `examples/bench-*`.
 *
 * Run with `/te bench`. The workloads are identical to the TypeScript versions — the only
 * difference is that this code was written directly in Kotlin and compiled by kotlinc, whereas
 * Tessera compiles the TS to JVM bytecode via swc4j. Comparing the two numbers in the same client
 * shows how close Tessera's output runs to code a human wrote against the JVM.
 */
object BenchNative {

    fun run() {
        ChatLib.chat("§7[§bTessera§7]§r §fnative JVM baseline (kotlinc) — compare with §a/benchnative §7&§b /benchinterop")
        nativeCompute()
        nativeInterop()
    }

    /** Mirror of examples/bench-native: isPrime over 1,000,000 numbers. */
    private fun nativeCompute() {
        val n = 1_000_000
        val start = System.nanoTime()
        var count = 0
        for (i in 0 until n) {
            var prime = true
            if (i < 2) {
                prime = false
            } else if (i % 2 == 0) {
                prime = i == 2
            } else {
                var j = 3
                while (j * j <= i) {
                    if (i % j == 0) { prime = false; break }
                    j += 2
                }
            }
            if (prime) count++
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        ChatLib.chat("§a[native-compute] isPrime x$n  ->  $ms ms  ($count primes)")
    }

    /** Mirror of examples/bench-interop: 27,000 × `new BlockPos`. */
    private fun nativeInterop() {
        val n = 30
        val x = -1674
        val y = 4
        val z = 1495
        val start = System.nanoTime()
        var made = 0
        var sink = 0 // touch each BlockPos so the allocation isn't optimised away
        for (x1 in x until x + n) {
            for (z1 in z until z + n) {
                for (y1 in y until y + n) {
                    val p = BlockPos(x1, y1, z1)
                    sink += p.x
                    made++
                }
            }
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        ChatLib.chat("§b[native-interop] new BlockPos x$made  ->  $ms ms  (sink=$sink)")
    }
}
