package ratph6.tessera.api

import java.util.concurrent.CopyOnWriteArrayList

// Tracks every Display so the HUD hook can render them each frame.
object DisplayManager {
    val displays = CopyOnWriteArrayList<Display>()
    fun clear() = displays.clear()
    fun renderAll() { for (d in displays) d.render() }
}

// A persistent multi-line HUD overlay.
class Display {
    private val lines = ArrayList<String>()
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
        if (!visible || lines.isEmpty()) return
        val lineHeight = Renderer.getFontHeight() + 1
        if (backgroundColor != 0) {
            val width = lines.maxOf { Renderer.getStringWidth(it) }
            Renderer.drawRect(backgroundColor, x - 1, y - 1, width + 2, lines.size * lineHeight + 1)
        }
        lines.forEachIndexed { i, text ->
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
