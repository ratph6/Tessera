package ratph6.tessera.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import ratph6.tessera.api.ChatLib
import ratph6.tessera.engine.TesseraEngine
import ratph6.tessera.triggers.TriggerRegistry

/** Registers `/te ...` and every script-defined `command` trigger with brigadier. */
object TesseraCommand {

    /** Script command names already added to the live dispatcher (brigadier has no removal, so we de-dup). */
    private val registered = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The live client dispatcher from the registration callback — reused to add commands after reloads. */
    @Volatile private var dispatcher: CommandDispatcher<FabricClientCommandSource>? = null

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        this.dispatcher = dispatcher
        val tessera = ClientCommands.literal("te")
            .then(ClientCommands.literal("reload").executes { TesseraEngine.reload(); 1 })
            .then(ClientCommands.literal("list").executes { listModules(); 1 })
            .then(ClientCommands.literal("errors").executes { showErrors(); 1 })
            .then(ClientCommands.literal("console").executes { TesseraConsole.open(); ChatLib.chat("§7[§bTessera§7]§r opened console window"); 1 })
            .then(ClientCommands.literal("bench").executes { BenchNative.run(); 1 })
            .then(
                ClientCommands.literal("load").then(
                    ClientCommands.argument("module", StringArgumentType.word()).executes { ctx ->
                        TesseraEngine.loadModuleByName(StringArgumentType.getString(ctx, "module")); 1
                    },
                ),
            )
            .then(
                ClientCommands.literal("unload").then(
                    ClientCommands.argument("module", StringArgumentType.word()).executes { ctx ->
                        TesseraEngine.unloadModule(StringArgumentType.getString(ctx, "module")); 1
                    },
                ),
            )
            .then(
                ClientCommands.literal("create").then(
                    ClientCommands.argument("module", StringArgumentType.word()).executes { ctx ->
                        createModule(StringArgumentType.getString(ctx, "module")); 1
                    },
                ),
            )
            .then(
                ClientCommands.literal("code").then(
                    ClientCommands.argument("module", StringArgumentType.word()).executes { ctx ->
                        openModule(StringArgumentType.getString(ctx, "module")); 1
                    },
                ),
            )
            .then(
                ClientCommands.literal("eval").then(
                    ClientCommands.argument("code", StringArgumentType.greedyString()).executes { ctx ->
                        TesseraEngine.evaluate(StringArgumentType.getString(ctx, "code")); 1
                    },
                ),
            )
            .executes { listModules(); 1 }
        dispatcher.register(tessera)
        registerScriptCommands(dispatcher)
    }

    /**
     * Add any not-yet-registered script `command` triggers to [dispatcher]. The `executes` body looks
     * the trigger up by name at *invoke* time, so a node registered once always dispatches to the
     * current trigger — survives reloads. Call [refreshScriptCommands] after modules (re)load.
     */
    private fun registerScriptCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        for (cmd in TriggerRegistry.allCommands()) {
            val name = cmd.name ?: continue
            if (!registered.add(name)) continue // already a node in the live dispatcher
            dispatcher.register(
                ClientCommands.literal(name)
                    .executes { TesseraEngine.dispatchCommand(name, emptyArray()); 1 }
                    .then(
                        ClientCommands.argument("args", StringArgumentType.greedyString()).executes { ctx ->
                            TesseraEngine.dispatchCommand(name, splitArgs(ctx)); 1
                        },
                    ),
            )
        }
    }

    /**
     * Re-register script commands into the live client dispatcher (called after `/te reload` or a
     * module load). Without this, commands from modules loaded after startup parse as "unknown".
     */
    fun refreshScriptCommands() {
        dispatcher?.let { registerScriptCommands(it) }
    }

    private fun splitArgs(ctx: CommandContext<FabricClientCommandSource>): Array<String> =
        StringArgumentType.getString(ctx, "args").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toTypedArray()

    private fun createModule(name: String) {
        val dir = TesseraEngine.modulesDir()
        if (dir == null) { ChatLib.chat("§7[§bTessera§7]§r §cengine not ready"); return }
        TesseraScaffold.createModule(dir, name).fold(
            { ChatLib.chat("§7[§bTessera§7]§r §acreated module §f$name§7 — §b/te load $name§7 or §b/te code $name") },
            { ChatLib.chat("§7[§bTessera§7]§r §ccreate failed: §f${it.message}") },
        )
    }

    private fun openModule(name: String) {
        val dir = TesseraEngine.modulesDir()?.resolve(name)
        if (dir == null || !java.nio.file.Files.isDirectory(dir)) {
            ChatLib.chat("§7[§bTessera§7]§r §cno such module: §f$name"); return
        }
        TesseraScaffold.openInEditor(dir).fold(
            { ChatLib.chat("§7[§bTessera§7]§r §aopening §f$name§7 in VSCodium") },
            { ChatLib.chat("§7[§bTessera§7]§r §ccouldn't launch VSCodium (is §fcodium§c on PATH?): §f${it.message}") },
        )
    }

    private fun listModules() {
        val modules = TesseraEngine.loadedModuleList()
        ChatLib.chat("§7[§bTessera§7]§r §f${modules.size} module(s) loaded:")
        if (modules.isEmpty()) ChatLib.chat("  §8(none — put modules in .minecraft/tessera/modules/)")
        for (m in modules) {
            val count = TriggerRegistry.countForModule(m.name)
            ChatLib.chat("  §a${m.name} §7v${m.manifest.version} §8— $count trigger(s)")
        }
    }

    private fun showErrors() {
        val errors = TesseraEngine.recentErrors(10)
        ChatLib.chat("§7[§bTessera§7]§r §c${errors.size} recent error(s)§7 (full stacks in §b/te console§7):")
        if (errors.isEmpty()) ChatLib.chat("  §a(no errors)")
        for (e in errors) ChatLib.chat("  §c${e.where}§7: §f${e.detail.lineSequence().firstOrNull() ?: e.detail}")
    }
}
