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

        // fires every frame, so scripts can do frame-rate-independent work
        LevelRenderEvents.END_MAIN.register(LevelRenderEvents.EndMain { _ ->
            TesseraEngine.dispatch(TriggerType.RENDER_WORLD)
        })

        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, client ->
            TesseraEngine.dispatch(TriggerType.WORLD_LOAD)
            client.currentServer?.let { TesseraEngine.dispatch(TriggerType.SERVER_CONNECT, it.ip, 25565) }
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
            // MC 26.1.2 delivers input as KeyEvent / MouseButtonEvent objects
            ScreenKeyboardEvents.afterKeyPress(screen).register(ScreenKeyboardEvents.AfterKeyPress { _, keyEvent ->
                TesseraEngine.dispatch(TriggerType.GUI_KEY, keyEvent)
            })
            ScreenMouseEvents.afterMouseClick(screen).register(ScreenMouseEvents.AfterMouseClick { _, mouseEvent, _ ->
                TesseraEngine.dispatch(TriggerType.GUI_MOUSE_CLICK, mouseEvent)
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
