package ratph6.tessera.client

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import ratph6.tessera.api.BlockWrapper
import ratph6.tessera.api.DisplayManager
import ratph6.tessera.api.Renderer
import ratph6.tessera.api.Renderer3D
import ratph6.tessera.engine.TesseraEngine
import ratph6.tessera.triggers.TriggerType
import java.nio.file.Path

// wires Tessera to Fabric API events. engine boots lazily on whichever fires first (always render thread).
object FabricEventHooks {

    fun register(modulesDir: Path, classLoader: ClassLoader) {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            ensureBootstrapped(modulesDir, classLoader)
            TesseraEngine.pump()
            TesseraEngine.dispatch(TriggerType.TICK, TesseraEngine.tickCount)
            TesseraEngine.dispatch(TriggerType.GAME_TICK, TesseraEngine.tickCount)
        })

        // runs last so Tessera draws on top
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("tessera", "overlay"),
            HudElement { graphics, _ ->
                Renderer.graphics = graphics
                try {
                    TesseraEngine.dispatch(TriggerType.RENDER_OVERLAY)
                    DisplayManager.renderAll()
                } finally {
                    Renderer.graphics = null
                }
            },
        )

        // COLLECT_SUBMITS is the frame stage where custom geometry can still be added to the submit
        // collector and be flushed (submits added in END_MAIN are already drawn). Bind Renderer3D so
        // RENDER_WORLD scripts can draw boxes/lines/text in world space; try/finally pops the pose.
        LevelRenderEvents.COLLECT_SUBMITS.register(LevelRenderEvents.CollectSubmits { ctx ->
            Renderer3D.begin(ctx)
            try {
                TesseraEngine.dispatch(TriggerType.RENDER_WORLD)
            } finally {
                Renderer3D.end()
            }
        })

        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, client ->
            TesseraEngine.dispatch(TriggerType.WORLD_LOAD)
            client.currentServer?.let {
                // ServerData.ip is the raw address string and may carry its own :port
                val raw = it.ip
                val colon = raw.lastIndexOf(':')
                val port = if (colon > 0 && raw.indexOf(':') == colon) raw.substring(colon + 1).toIntOrNull() else null
                val host = if (port != null) raw.substring(0, colon) else raw
                TesseraEngine.dispatch(TriggerType.SERVER_CONNECT, host, port ?: 25565)
            }
        })
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            TesseraEngine.dispatch(TriggerType.WORLD_UNLOAD)
            TesseraEngine.dispatch(TriggerType.SERVER_DISCONNECT, "disconnected")
        })

        ClientLifecycleEvents.CLIENT_STARTED.register(ClientLifecycleEvents.ClientStarted {
            ensureBootstrapped(modulesDir, classLoader)
            TesseraEngine.dispatch(TriggerType.GAME_LOAD)
        })

        // cancel to veto the swing
        AttackBlockCallback.EVENT.register(AttackBlockCallback { _, world, _, pos, _ ->
            val cancelled = TesseraEngine.dispatch(TriggerType.BLOCK_BREAK, BlockWrapper(world.getBlockState(pos), pos))
            if (cancelled) InteractionResult.FAIL else InteractionResult.PASS
        })

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            val name = screen.javaClass.simpleName
            val container = screen is AbstractContainerScreen<*>
            TesseraEngine.dispatch(TriggerType.GUI_OPEN, name)
            if (container) TesseraEngine.dispatch(TriggerType.INVENTORY_OPEN, name)

            ScreenEvents.remove(screen).register(ScreenEvents.Remove {
                TesseraEngine.dispatch(TriggerType.GUI_CLOSE, name)
                if (container) TesseraEngine.dispatch(TriggerType.INVENTORY_CLOSE, name)
            })
            ScreenEvents.afterBackground(screen).register(ScreenEvents.AfterBackground { _, _, _, _, _ ->
                TesseraEngine.dispatch(TriggerType.GUI_DRAW_BACKGROUND, name)
            })
            // MC 26.2 delivers input as KeyEvent / MouseButtonEvent objects. The allow* phase makes
            // these genuinely cancellable: returning false swallows the input.
            ScreenKeyboardEvents.allowKeyPress(screen).register(ScreenKeyboardEvents.AllowKeyPress { _, keyEvent ->
                !TesseraEngine.dispatch(TriggerType.GUI_KEY, keyEvent)
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, mouseEvent ->
                !TesseraEngine.dispatch(TriggerType.GUI_MOUSE_CLICK, mouseEvent)
            })
        })

        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            ensureBootstrapped(modulesDir, classLoader)
            TesseraCommand.register(dispatcher)
        })
    }

    private fun ensureBootstrapped(modulesDir: Path, classLoader: ClassLoader) {
        if (TesseraEngine.booted) return
        runCatching { TesseraEngine.bootstrap(modulesDir, classLoader) }
            .onFailure { TesseraEngine.recordError("bootstrap", it.message ?: it.toString()) }
    }
}
