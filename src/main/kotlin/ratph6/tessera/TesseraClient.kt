package ratph6.tessera

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import ratph6.tessera.api.DisplayManager
import ratph6.tessera.client.FabricEventHooks
import ratph6.tessera.client.TesseraScaffold
import ratph6.tessera.engine.TesseraEngine

// client entry point. engine itself boots lazily on the first tick / command registration (guaranteed render thread).
object TesseraClient : ClientModInitializer {
    private val log = LoggerFactory.getLogger("Tessera")

    override fun onInitializeClient() {
        // AWT latches headless on first use, so set it before anything touches AWT
        runCatching { System.setProperty("java.awt.headless", "false") }

        val tesseraDir = FabricLoader.getInstance().gameDir.resolve("tessera")
        val modulesDir = tesseraDir.resolve("modules")
        TesseraScaffold.install(tesseraDir)
        log.info("client init — modules dir: {}", modulesDir)

        // engine output -> in-game chat, marshalled onto the render thread
        TesseraEngine.chatSink = { message ->
            val mc = Minecraft.getInstance()
            val show = Runnable { runCatching { mc.gui.chat.addClientSystemMessage(Component.literal(message)) } }
            if (mc.isSameThread) show.run() else mc.execute(show)
        }

        TesseraEngine.resetHooks.add(Runnable { DisplayManager.clear() })

        // re-register script commands after (re)load so late-loaded ones aren't "unknown command"
        TesseraEngine.modulesChangedSink = { ratph6.tessera.client.TesseraCommand.refreshScriptCommands() }

        FabricEventHooks.register(modulesDir, TesseraClient::class.java.classLoader)
    }
}
