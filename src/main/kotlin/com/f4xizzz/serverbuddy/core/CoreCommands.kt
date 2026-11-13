package com.f4xizzz.serverbuddy.core

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.SharedSuggestionProvider

/**
 * Comandos de administração geral do ServerBuddy (dentro de `/serverbuddy`):
 * `modules` (estado de cada módulo) e `config check|update` (arquivos de config).
 */
object CoreCommands {
    /**
     * `/sb modules`: the state of every module, plus `enable|disable <module>` (writes modules.conf), `commands [module]` (every
     * command by module) and `command <name> off|on` (switch ONE command off while its module keeps working).
     */
    @JvmStatic
    fun modules(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("modules")
            .requires(SbPermissions.require("serverbuddy.admin.modules", 2))
            .executes { listModules(it) }
            .then(Commands.literal("enable").then(moduleArgument().executes { setModule(it, true) }))
            .then(Commands.literal("disable").then(moduleArgument().executes { setModule(it, false) }))
            .then(
                Commands.literal("commands")
                    .executes { listCommands(it, null) }
                    .then(
                        Commands.argument("module", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(SbCommands.catalog().map { it.module }.distinct(), builder) }
                            .executes { listCommands(it, StringArgumentType.getString(it, "module")) }
                    )
            )
            .then(
                Commands.literal("command").then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(SbCommands.catalog().map { it.defaultName }, builder) }
                        .then(Commands.literal("off").executes { setCommand(it, StringArgumentType.getString(it, "name"), true) })
                        .then(Commands.literal("on").executes { setCommand(it, StringArgumentType.getString(it, "name"), false) })
                )
            )

    private fun moduleArgument() =
        Commands.argument("module", StringArgumentType.word())
            .suggests { _, builder ->
                SharedSuggestionProvider.suggest(Modules.all().map { it.id } + COBBLEMON_FEATURES.map { it.first }.filter { ModuleSettings.isToggle(it) }, builder)
            }

    private fun setModule(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "module").lowercase()
        val inThisJar = Modules.all().any { it.id == id } ||
            (ModuleSettings.isToggle(id) && COBBLEMON_FEATURES.any { it.first == id })
        if (!inThisJar) {
            Texts.fail(source, "<red>There is no module called <white>${Texts.escape(id)}</white> in this jar. See <white>/sb modules</white>.")
            return 0
        }
        val word = if (enabled) "on" else "off"
        if (ModuleSettings.enabled(id) == enabled) {
            Texts.reply(source, "<yellow><white>${Texts.escape(id)}</white> is already $word in modules.conf.")
            return 1
        }
        if (!ModuleSettings.setModuleEnabled(id, enabled)) {
            Texts.fail(source, "<red>Could not edit modules.conf. Edit the file by hand and restart the server.")
            return 0
        }
        Texts.reply(source, "<green><white>${Texts.escape(id)}</white> is now $word in modules.conf. <gray>Restart the server for it to take effect.", broadcast = true)
        return 1
    }

    private fun listCommands(ctx: CommandContext<CommandSourceStack>, module: String?): Int {
        val source = ctx.source
        val groups = SbCommands.catalog().filter { module == null || it.module.equals(module, true) }.groupBy { it.module }
        if (groups.isEmpty()) {
            Texts.reply(source, if (module == null) "<gray>No command has been registered yet." else "<yellow>No commands for <white>${Texts.escape(module)}</white>.")
            return 0
        }
        Texts.reply(source, "<gold><bold>ServerBuddy</bold> · commands <gray>(<white>/sb modules command <name> off|on</white> switches one)")
        var total = 0
        for ((owner, list) in groups) {
            val line = list.joinToString(" ") { entry ->
                total++
                val aliases = if (entry.aliases.isEmpty()) "" else " <dark_gray>(" + entry.aliases.joinToString(", ") { "/" + Texts.escape(it) } + ")</dark_gray>"
                when {
                    ModuleSettings.commandDisabled(entry.defaultName) -> "<red><st>/${Texts.escape(entry.name)}</st></red>"
                    entry.disabled -> "<yellow>/${Texts.escape(entry.name)}</yellow><gray> (restart)</gray>"
                    else -> "<green>/${Texts.escape(entry.name)}</green>"
                } + aliases
            }
            Texts.reply(source, "<white>${Texts.escape(owner)}</white> <dark_gray>·</dark_gray> $line")
        }
        return total
    }

    private fun setCommand(ctx: CommandContext<CommandSourceStack>, name: String, off: Boolean): Int {
        val source = ctx.source
        val entry = SbCommands.find(name)
        if (entry == null) {
            Texts.fail(source, "<red>There is no ServerBuddy command called <white>/${Texts.escape(name)}</white>. See <white>/sb modules commands</white>.")
            return 0
        }
        if (ModuleSettings.commandDisabled(entry.defaultName) == off) {
            Texts.reply(source, "<yellow><white>/${Texts.escape(entry.name)}</white> is already ${if (off) "off" else "on"} in modules.conf.")
            return 1
        }
        if (!ModuleSettings.setCommandDisabled(entry.defaultName, off)) {
            Texts.fail(source, "<red>Could not edit modules.conf. Edit the file by hand and restart the server.")
            return 0
        }
        if (off) {
            SbCommands.removeLive(source.server, entry)
            Texts.reply(source, "<green><white>/${Texts.escape(entry.name)}</white> is switched off now. <gray>The module keeps working; <white>/sb modules command ${Texts.escape(entry.defaultName)} on</white> and a restart bring it back.", broadcast = true)
        } else {
            Texts.reply(source, "<green><white>/${Texts.escape(entry.name)}</white> is on again in modules.conf. <gray>Restart the server to bring it back.", broadcast = true)
        }
        return 1
    }

    @JvmStatic
    fun config(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("config")
            .requires(SbPermissions.require("serverbuddy.admin.config", 2))
            .then(Commands.literal("check").executes { check(it) })
            .then(
                Commands.literal("update")
                    .executes { update(it, null) }
                    .then(
                        Commands.argument("arquivo", StringArgumentType.greedyString())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(ConfigFiles.entries().map { it.label }, builder) }
                            .executes { update(it, StringArgumentType.getString(it, "arquivo")) }
                    )
            )

    private fun listModules(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        Texts.reply(source, "<gold><bold>ServerBuddy</bold> · modules <gray>(toggle in config/ServerBuddy/modules.conf)")
        for (module in Modules.all()) {
            val line = when {
                Modules.isRunning(module.id) -> module.idleReason()
                    ?.let { "<yellow>✖ <white>${module.id}</white> <gray>parado: ${Texts.escape(it)}" }
                    ?: "<green>✔ <white>${module.id}</white>"
                !ModuleSettings.enabled(module.id) -> "<red>✖ <white>${module.id}</white> <gray>disabled"
                else -> "<yellow>✖ <white>${module.id}</white> <gray>did not start (see the console)"
            }
            val about = module.description.takeIf { it.isNotBlank() }?.let { " <dark_gray>— <gray>${Texts.escape(it)}" } ?: ""
            Texts.reply(source, line + about)
        }
        // os recursos do Cobblemon também têm liga/desliga no modules.conf, mas não são módulos com pasta própria
        val cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon")
        // these features live in the Plus and Pro jars: the Basic jar does not have them
        for ((id, about) in COBBLEMON_FEATURES) {
            val line = when {
                !ModuleSettings.enabled(id) -> "<red>✖ <white>$id</white> <gray>disabled"
                !cobblemon -> "<yellow>✖ <white>$id</white> <gray>stopped: needs Cobblemon (not installed)"
                else -> "<green>✔ <white>$id</white>"
            }
            Texts.reply(source, "$line <dark_gray>— <gray>${Texts.escape(about)}")
        }
        return Modules.all().size
    }

    private val COBBLEMON_FEATURES = listOf(
        "poketokens" to "Pokémon tokens (/poketoken): Shiny, Gender and the Shiny Ball.",
        "pokeview" to "View a player's party and PC (/pokesee and /compsee).",
        "pokeodds" to "View and change the Shiny rate (/pokeodds)."
    )

    private fun check(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        var pending = 0
        for (entry in ConfigFiles.entries()) {
            val missing = ConfigFiles.missing(entry)
            when {
                missing == null -> Texts.reply(source, "<gray>? <white>${Texts.escape(entry.label)}</white> <gray>(file not found or unreadable)")
                missing.isEmpty() -> Unit
                else -> {
                    pending++
                    Texts.reply(source, "<yellow>! <white>${Texts.escape(entry.label)}</white> <gray>faltam: <white>${Texts.escape(missing.joinToString(", "))}")
                }
            }
        }
        if (pending == 0) {
            Texts.reply(source, "<green>All config files are up to date.")
        } else {
            Texts.reply(source, "<gray>Use <white>/serverbuddy config update</white> to complete them (keeps a .bak of each file).")
        }
        return pending
    }

    private fun update(ctx: CommandContext<CommandSourceStack>, only: String?): Int {
        val source = ctx.source
        var changed = 0
        for (entry in ConfigFiles.entries()) {
            if (only != null && !entry.label.equals(only.trim(), ignoreCase = true)) continue
            val added = ConfigFiles.update(entry) ?: continue
            if (added.isNotEmpty()) {
                changed++
                Texts.reply(source, "<green>✔ <white>${Texts.escape(entry.label)}</white> <gray>added: <white>${Texts.escape(added.joinToString(", "))}")
            }
        }
        if (changed == 0) Texts.reply(source, "<green>Nothing to complete.")
        else Texts.reply(source, "<gray>Run <white>/serverbuddy reload</white> to apply. Original files were saved as <white>*.bak</white>.", broadcast = true)
        return changed
    }
}
