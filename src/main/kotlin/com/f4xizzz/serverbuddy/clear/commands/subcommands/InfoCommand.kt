package com.f4xizzz.serverbuddy.clear.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.clear.ClearModule
import com.f4xizzz.serverbuddy.clear.config.ConfigManager
import com.f4xizzz.serverbuddy.clear.utils.SubCommand
import com.f4xizzz.serverbuddy.clear.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

class InfoCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("info")
            .requires(Permissions.require("serverbuddy.clear.command.info", 4))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ConfigManager.CONFIG.clears.keys, builder)
                }
                .executes { ctx ->
                    execute(ctx, StringArgumentType.getString(ctx, "id"))
                }
            )
            .executes { ctx ->
                execute(ctx, null)
            }
            .build()
    }

    companion object {
        private fun execute(
            ctx: CommandContext<CommandSourceStack>,
            id: String?
        ): Int {
            var clearEntries = ConfigManager.CONFIG.clears.filter { it.value.enabled }
            if (id != null) {
                val clearConfig = ConfigManager.CONFIG.clears[id]
                if (clearConfig == null) {
                    ctx.source.sendFailure(
                        Component.literal("Clear with id $id not found.")
                            .withStyle { it.withColor(ChatFormatting.RED) })
                    return 0
                }

                if (!clearConfig.enabled) {
                    ctx.source.sendFailure(
                        Component.literal("The Clear entry $id is disabled.")
                            .withStyle { it.withColor(ChatFormatting.RED) })
                    return 0
                }

                val clearTask = ClearModule.INSTANCE.clearManager.getClearTask(id)
                if (clearTask == null) {
                    ctx.source.sendFailure(
                        Component.literal("Clear Task for $id not found. Is it disabled?")
                            .withStyle { it.withColor(ChatFormatting.RED) })
                    return 0
                }

                clearEntries = mapOf(id to clearConfig)
            }

            if (clearEntries.isEmpty()) {
                ctx.source.sendFailure(
                    Component.literal("No enabled clear entries found.")
                        .withStyle { it.withColor(ChatFormatting.RED) })
                return 0
            }

            for ((clearId, clearConfig) in clearEntries) {
                val clearTask = ClearModule.INSTANCE.clearManager.getClearTask(clearId) ?: continue
                for (line in clearConfig.messages.info) {
                    ctx.source.sendSystemMessage(
                        Utils.deserializeText(
                        line.replace("%time_remaining%", Utils.getFormattedTime(clearTask.getTimer().toLong()))
                    ))
                }
            }

            return 1
        }
    }
}