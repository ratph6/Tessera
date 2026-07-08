package ratph6.tessera.api

import java.util.concurrent.CopyOnWriteArrayList

// Tracks every Display so the HUD hook can render them each frame.
object DisplayManager {
    val displays = CopyOnWriteArrayList<Display>()
    fun clear() = displays.clear()
    // one bad display must not take down the HUD render pass
    fun renderAll() {
        for (d in displays) runCatching { d.render() }
            .onFailure { ratph6.tessera.engine.TesseraEngine.recordError("display", it) }
    }
}

// A persistent multi-line HUD overlay.
class Display {
    // scripts may mutate from off-thread (mixin hooks); render iterates on the render thread
    private val lines = CopyOnWriteArrayList<String>()
    private var x = 2
    private var y = 2
    private var textColor = 0xFFFFFFFF.toInt()
    private var backgroundColor = 0
    private var shadow = true
    private var align = "left"
    private var visible = true

    init { DisplayManager.displays.add(this) }

    fun setLine(index: Int, text: String): Display { while (lines.size <= index) lines.add(""); lines[index] = text; return this }
    fun addLine(text: String): Display { lines.add(text); return this }
    fun clearLines(): Display { lines.clear(); return this }
    fun setX(value: Int): Display { x = value; return this }
    fun setY(value: Int): Display { y = value; return this }
    fun setTextColor(color: Int): Display { textColor = color; return this }
    fun setBackgroundColor(color: Int): Display { backgroundColor = color; return this }
    fun setAlign(value: String): Display { align = value; return this }
    fun setShadow(value: Boolean): Display { shadow = value; return this }
    fun setVisible(value: Boolean): Display { visible = value; return this }

    fun remove() { DisplayManager.displays.remove(this) }

    internal fun render() {
        if (!visible) return
        val snapshot = lines.toList() // consistent view: width, count and draw all agree
        if (snapshot.isEmpty()) return
        val lineHeight = Renderer.getFontHeight() + 1
        if (backgroundColor != 0) {
            val width = snapshot.maxOf { Renderer.getStringWidth(it) }
            // the rect must track the text's alignment, not assume left-aligned
            val left = when (align) {
                "right" -> x - width
                "center" -> x - width / 2
                else -> x
            }
            Renderer.drawRect(backgroundColor, left - 1, y - 1, width + 2, snapshot.size * lineHeight + 1)
        }
        snapshot.forEachIndexed { i, text ->
            val lineX = when (align) {
                "right" -> x - Renderer.getStringWidth(text)
                "center" -> x - Renderer.getStringWidth(text) / 2
                else -> x
            }
            val ly = y + i * lineHeight
            if (shadow) Renderer.drawStringWithShadow(text, lineX, ly, textColor)
            else Renderer.drawString(text, lineX, ly, textColor)
        }
    }
}
