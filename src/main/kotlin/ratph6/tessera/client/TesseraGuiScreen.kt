package ratph6.tessera.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import ratph6.tessera.api.Renderer
import ratph6.tessera.engine.TesseraEngine
import ratph6.tessera.triggers.TriggerType

// A blank, input-blocking screen that hands rendering to a script via the GUI_SCREEN_RENDER trigger.
// Being a real Screen is what makes a script GUI behave like a proper menu: the game stops taking
// movement/inventory input and frees the cursor. Rendering goes through GuiGraphicsExtractor — the same
// target the HUD Renderer uses — so scripts draw with the ordinary Renderer API. The dispatched args
// are (mouseX, mouseY), already in gui-scaled coordinates, so no manual mouse conversion is needed.
class TesseraGuiScreen : Screen(Component.literal("Tessera")) {

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick) // dim vanilla background
        Renderer.graphics = graphics
        try {
            TesseraEngine.dispatch(TriggerType.GUI_SCREEN_RENDER, mouseX, mouseY)
        } finally {
            Renderer.graphics = null
        }
    }
}
