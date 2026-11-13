package com.f4xizzz.serverbuddy.modules.craftblock

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/**
 * Blocks crafting by removing recipes (see [CraftBlockConfig]) and offers `/craftblock list|check` to audit what is blocked.
 */
class CraftBlockModule : SbModule {
    override val id = "craftblock"
    override val folder = "CraftBlock"
    override val legacyFolders = emptyList<String>()
    override val description = "Blocks crafting of items, namespaces or datapack tags, with an audit command (/craftblock check)."

    override fun init() {
        CraftBlockService.load(loadConfig())
        // the recipes are complete when the server is up, and again after every /reload (which sends the unfiltered ones)
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            CraftBlockService.capture(server)
            CraftBlockService.apply(server, false)
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) {
                CraftBlockService.capture(server)
                CraftBlockService.apply(server, true)
            }
        }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    override fun reload() {
        CraftBlockService.load(loadConfig())
        ServerHolder.server?.let { CraftBlockService.apply(it, true) }
    }

    private fun loadConfig(): CraftBlockConfig = ConfigFiles.load(folder, "config.json", CraftBlockConfig::class.java) { CraftBlockConfig() }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "craftblock") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("craftblock").build(), "craftblock")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(SbPermissions.require("serverbuddy.craftblock.admin", 2))
            .executes { list(it) }
            .then(Commands.literal("list").executes { list(it) })
            .then(Commands.literal("check").executes { check(it) })

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val messages = CraftBlockService.config.messages
        val report = CraftBlockService.report
        if (report.removed.isEmpty()) {
            Texts.reply(ctx.source, messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, messages.listHeader, mapOf("recipes" to report.total))
        for ((rule, ids) in report.removed) {
            val examples = ids.take(4).joinToString(", ") { Texts.escape(it.toString()) } + if (ids.size > 4) ", ..." else ""
            Texts.reply(ctx.source, messages.listLine, mapOf("rule" to Texts.escape(rule), "count" to ids.size, "examples" to examples))
        }
        return report.total
    }

    private fun check(ctx: CommandContext<CommandSourceStack>): Int {
        val messages = CraftBlockService.config.messages
        val report = CraftBlockService.report
        var problems = 0
        for (rule in report.unmatched) {
            Texts.reply(ctx.source, messages.checkUnmatched, mapOf("rule" to Texts.escape(rule)))
            problems++
        }
        for ((item, type, recipe) in report.leaks.take(20)) {
            Texts.reply(ctx.source, messages.checkLeak, mapOf("item" to item, "type" to type, "recipe" to recipe.toString()))
            problems++
        }
        if (report.leaks.size > 20) Texts.reply(ctx.source, "<gray>...and ${report.leaks.size - 20} more.")
        if (problems == 0) Texts.reply(ctx.source, messages.checkOk)
        return problems
    }
}
