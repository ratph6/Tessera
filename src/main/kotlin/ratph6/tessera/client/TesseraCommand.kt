package ratph6.tessera.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import ratph6.tessera.api.ChatLib
import ratph6.tessera.engine.TesseraEngine
import ratph6.tessera.triggers.TriggerRegistry

// registers /te and every script-defined command with brigadier
object TesseraCommand {

    // brigadier has no removal, so de-dup names we've already added
    private val registered = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // kept so we can add commands after reloads
    @Volatile private var dispatcher: CommandDispatcher<FabricClientCommandSource>? = null

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        this.dispatcher = dispatcher
        // Fabric hands us a BRAND-NEW dispatcher each time it (re)builds the client command tree (world
        // join, resource reload, etc.). The old dispatcher's nodes are gone, so the `registered` guard —
        // which exists to avoid double-adding to the SAME dispatcher — must be reset here, or a script
        // command that was ever added stays skipped forever and never lands in the live tree.
        registered.clear()
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

    // executes body looks the trigger up by name at invoke time, so a node registered once survives reloads
    private fun registerScriptCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>): Boolean {
        var added = false
        for (cmd in TriggerRegistry.allCommands()) {
            val name = cmd.name ?: continue
            if (!registered.add(name)) continue // already a node in the live dispatcher
            added = true
            dispatcher.register(
                ClientCommands.literal(name)
                    .executes { runScriptCommand(name, emptyArray()); 1 }
                    .then(
                        ClientCommands.argument("args", StringArgumentType.greedyString()).executes { ctx ->
                            runScriptCommand(name, splitArgs(ctx)); 1
                        },
                    ),
            )
        }
        return added
    }

    // surface "nothing happened" — the node outlives its trigger when the module is unloaded
    private fun runScriptCommand(name: String, args: Array<String>) {
        if (!TesseraEngine.dispatchCommand(name, args)) {
            ChatLib.chat("§7[§bTessera§7]§r §cno module currently registers §f/$name§c (was it unloaded?)")
        }
    }

    // call after reload/module load, else commands from late-loaded modules parse as "unknown"
    fun refreshScriptCommands() {
        dispatcher?.let {
            // completions are cached client-side; without a refresh new commands run but don't tab-complete
            if (registerScriptCommands(it)) runCatching { ClientCommands.refreshCommandCompletions() }
        }
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
