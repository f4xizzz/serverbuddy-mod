package com.f4xizzz.serverbuddy.clear.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.clear.ClearModule
import com.f4xizzz.serverbuddy.clear.commands.subcommands.DebugCommand
import com.f4xizzz.serverbuddy.clear.commands.subcommands.ForceCommand
import com.f4xizzz.serverbuddy.clear.commands.subcommands.InfoCommand
import com.f4xizzz.serverbuddy.clear.commands.subcommands.ReloadCommand
import com.f4xizzz.serverbuddy.clear.config.ConfigManager
import com.f4xizzz.serverbuddy.clear.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class BaseCommand {
    private val aliases = listOf("clear", "skiesclear")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val rootCommands: List<LiteralCommandNode<CommandSourceStack>> = aliases.map {
            Commands.literal(it)
                .requires(Permissions.require("serverbuddy.clear.command.base", 4))
                .executes { ctx ->
                    execute(ctx, null)
                }
                .build()
        }

        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            DebugCommand().build(),
            ReloadCommand().build(),
            ForceCommand().build(),
            InfoCommand().build()
        )

        rootCommands.forEach { root ->
            subCommands.forEach { sub -> root.addChild(sub) }
            ModuleCommands.mount(dispatcher, root, "clear")
        }
    }

    companion object {
        private fun execute(
            ctx: CommandContext<CommandSourceStack>,
            id: String?
        ): Int {
            var clearEntries = ConfigManager.CONFIG.clears.filter { it.value.enabled }
            if (clearEntries.isEmpty()) {
                ctx.source.sendFailure(
                    Component.literal("No enabled clear entries found.")
                    .withStyle { it.withColor(ChatFormatting.RED) })
                return 0
            }

            for ((clearId, clearConfig) in clearEntries) {
                val clearTask = ClearModule.INSTANCE.clearManager.getClearTask(clearId) ?: continue
                for (line in clearConfig.messages.info) {
                    ctx.source.sendSystemMessage(Utils.deserializeText(
                        line.replace("%time_remaining%", Utils.getFormattedTime(clearTask.getTimer().toLong()))
                    ))
                }
            }

            return 1
        }
    }
}