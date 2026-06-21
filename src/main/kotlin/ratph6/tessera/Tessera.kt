package ratph6.tessera

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Tessera : ModInitializer {
    private val logger = LoggerFactory.getLogger("tessera")

	override fun onInitialize() {
		// flip AWT out of headless before MC touches it, so /te console can open later
		runCatching { System.setProperty("java.awt.headless", "false") }
		logger.info("Tessera initialized.")
	}
}