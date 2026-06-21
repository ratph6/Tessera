package ratph6.tessera.api

import net.minecraft.client.gui.GuiGraphicsExtractor

// 2D HUD drawing. Only valid inside a renderOverlay/HUD trigger. Colours are packed ARGB (use color()).
object Renderer {
    // set by the HUD render hook for each frame's overlay dispatch
    @JvmField var graphics: GuiGraphicsExtractor? = null

    private val font get() = Mc.client.font

    @JvmStatic fun drawRect(color: Int, x: Int, y: Int, width: Int, height: Int) {
        graphics?.fill(x, y, x + width, y + height, color)
    }

    @JvmStatic fun drawString(text: String, x: Int, y: Int, color: Int) {
        graphics?.text(font, text, x, y, color)
    }

    @JvmStatic fun drawStringWithShadow(text: String, x: Int, y: Int, color: Int) {
        graphics?.text(font, text, x, y, color, true)
    }

    // horizontal or vertical only — the HUD draw target can't do diagonals
    @JvmStatic fun drawLine(color: Int, x1: Int, y1: Int, x2: Int, y2: Int, width: Int) {
        val g = graphics ?: return
        if (y1 == y2) g.fill(minOf(x1, x2), y1, maxOf(x1, x2), y1 + width.coerceAtLeast(1), color)
        else if (x1 == x2) g.fill(x1, minOf(y1, y2), x1 + width.coerceAtLeast(1), maxOf(y1, y2), color)
    }

    @JvmStatic fun color(r: Int, g: Int, b: Int): Int = color(r, g, b, 255)

    @JvmStatic fun color(r: Int, g: Int, b: Int, a: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @JvmStatic fun getStringWidth(text: String): Int = font.width(text)
    @JvmStatic fun getFontHeight(): Int = font.lineHeight
    @JvmStatic fun getScreenWidth(): Int = runCatching { Mc.client.window.guiScaledWidth }.getOrDefault(0)
    @JvmStatic fun getScreenHeight(): Int = runCatching { Mc.client.window.guiScaledHeight }.getOrDefault(0)
}
