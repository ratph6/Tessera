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

/**
 * Client entry point. Installs the first-run scaffolding, points the engine's output at in-game
 * chat, clears HUD displays on reload, and wires up the Fabric API events. The engine itself is
 * bootstrapped lazily on the first client tick / command registration (guaranteed render thread).
 */
object TesseraClient : ClientModInitializer {
    private val log = LoggerFactory.getLogger("Tessera")

    override fun onInitializeClient() {
        // Allow the Swing console window (/te console). AWT latches headless-ness on first use, so
        // set it before anything touches AWT.
        runCatching { System.setProperty("java.awt.headless", "false") }

        val tesseraDir = FabricLoader.getInstance().gameDir.resolve("tessera")
        val modulesDir = tesseraDir.resolve("modules")
        TesseraScaffold.install(tesseraDir)
        log.info("client init — modules dir: {}", modulesDir)

        // console.log / Tessera.log / errors -> in-game chat, marshalled onto the render thread.
        TesseraEngine.chatSink = { message ->
            val mc = Minecraft.getInstance()
            val show = Runnable { runCatching { mc.gui.chat.addClientSystemMessage(Component.literal(message)) } }
            if (mc.isSameThread) show.run() else mc.execute(show)
        }

        TesseraEngine.resetHooks.add(Runnable { DisplayManager.clear() })

        // After modules (re)load, register their `command` triggers with brigadier so commands from a
        // /te reload (loaded after startup) are recognized instead of "unknown command".
        TesseraEngine.modulesChangedSink = { ratph6.tessera.client.TesseraCommand.refreshScriptCommands() }

        FabricEventHooks.register(modulesDir, TesseraClient::class.java.classLoader)
    }
}
