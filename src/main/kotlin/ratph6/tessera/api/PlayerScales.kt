package ratph6.tessera.api

import com.google.gson.JsonParser
import ratph6.tessera.engine.TesseraEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

// Remote per-player scale table fetched from a URL: { "PlayerName": { "x": 2, "y": 2, "z": 2 } }.
// Lookups are case-insensitive.
object PlayerScales {

    private class Scale(val x: Double, val y: Double, val z: Double)

    private val table = ConcurrentHashMap<String, Scale>()

    // fetch off-thread (HTTP can't run on the render thread); replaces the table on success
    @JvmStatic
    fun fetch(url: String) {
        Thread({
            runCatching {
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
                val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
                val root = JsonParser.parseString(body).asJsonObject
                val fresh = HashMap<String, Scale>()
                for ((name, value) in root.entrySet()) {
                    val o = value.asJsonObject
                    fresh[name.lowercase()] = Scale(o.get("x").asDouble, o.get("y").asDouble, o.get("z").asDouble)
                }
                table.clear()
                table.putAll(fresh)
                TesseraEngine.enqueue { TesseraEngine.chat("§aloaded ${fresh.size} player size(s)") }
            }.onFailure {
                TesseraEngine.recordError("PlayerScales.fetch", it.message ?: it.toString())
                TesseraEngine.enqueue { TesseraEngine.chat("§cfailed to fetch player sizes: ${it.message ?: it.toString()}") }
            }
        }, "tessera-playerscales").apply { isDaemon = true }.start()
    }

    @JvmStatic fun has(name: String): Boolean = table.containsKey(name.lowercase())
    @JvmStatic fun getX(name: String): Double = table[name.lowercase()]?.x ?: 1.0
    @JvmStatic fun getY(name: String): Double = table[name.lowercase()]?.y ?: 1.0
    @JvmStatic fun getZ(name: String): Double = table[name.lowercase()]?.z ?: 1.0
    @JvmStatic fun count(): Int = table.size
}
