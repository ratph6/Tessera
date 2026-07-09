package ratph6.tessera.api

import ratph6.tessera.client.TesseraGuiScreen

// Opens a blank, input-blocking screen for script-drawn GUIs (e.g. a ClickGUI). While open the game
// takes no movement/inventory input and the cursor is free — draw inside a GUI_SCREEN_RENDER trigger
// with the Renderer API. That trigger's callback receives (mouseX, mouseY), already gui-scaled. ESC
// closes it (or call Gui.close()). All calls marshal onto the render thread.
object Gui {
    @JvmStatic
    fun open() {
        val mc = Mc.client
        mc.execute { if (mc.gui.screen() !is TesseraGuiScreen) mc.setScreenAndShow(TesseraGuiScreen()) }
    }

    @JvmStatic
    fun close() {
        // onClose() returns to the game (setScreenAndShow's param is non-null, so it can't close)
        val mc = Mc.client
        mc.execute { (mc.gui.screen() as? TesseraGuiScreen)?.onClose() }
    }

    @JvmStatic
    fun toggle() {
        if (isOpen()) close() else open()
    }

    @JvmStatic
    fun isOpen(): Boolean = runCatching { Mc.client.gui.screen() is TesseraGuiScreen }.getOrDefault(false)
}
