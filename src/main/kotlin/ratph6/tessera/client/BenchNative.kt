package ratph6.tessera.client

import net.minecraft.core.BlockPos
import ratph6.tessera.api.ChatLib

// native kotlin baseline for the examples/bench-* workloads, to compare against the TS->bytecode output. /te bench
object BenchNative {

    fun run() {
        ChatLib.chat("§7[§bTessera§7]§r §fnative JVM baseline (kotlinc) — compare with §a/benchnative §7&§b /benchinterop")
        nativeCompute()
        nativeInterop()
    }

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

    private fun nativeInterop() {
        val n = 30
        val x = -1674
        val y = 4
        val z = 1495
        val start = System.nanoTime()
        var made = 0
        var sink = 0 // touch each BlockPos so it isn't optimised away
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
