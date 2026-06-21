package ratph6.tessera

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Tessera : ModInitializer {
    private val logger = LoggerFactory.getLogger("tessera")

	override fun onInitialize() {
		// Earliest mod hook — flip AWT out of headless now, before MC touches it, so the /te console
		// window can open later. (TesseraConsole also clears the cached flag as a fallback.)
		runCatching { System.setProperty("java.awt.headless", "false") }
		logger.info("Tessera initialized.")
	}
}