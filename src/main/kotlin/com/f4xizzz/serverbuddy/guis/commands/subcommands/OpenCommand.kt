package com.f4xizzz.serverbuddy.guis.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.guis.api.GUIsAPI
import com.f4xizzz.serverbuddy.guis.config.ConfigManager
import com.f4xizzz.serverbuddy.guis.utils.SubCommand
import com.f4xizzz.serverbuddy.guis.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument

class OpenCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("open")
            .requires(Permissions.require("serverbuddy.guis.command.open", 2))
            .then(Commands.argument("gui_id", StringArgumentType.string())
                .suggests { _, builder ->
                    SharedSuggestionProvider.suggest(ConfigManager.GUIS.keys.stream(), builder)
                }
                .then(
                    Commands.argument("player", EntityArgument.player())
                    .requires(Permissions.require("serverbuddy.guis.command.open.others", 2))
                    .executes(Companion::openGUIPlayer)
                )
                .executes(Companion::openGUISelf)
            )
            .build()
    }

    companion object {
        private fun openGUISelf(ctx: CommandContext<CommandSourceStack>): Int {
            try {
                val player = ctx.source.player
                if (player != null) {
                    val guiID = StringArgumentType.getString(ctx, "gui_id")

                    val gui = GUIsAPI.getGUIConfig(guiID)
                    if (gui == null) {
                        ctx.source.sendMessage(
                            Utils.deserializeText("<red>Could not find a GUI with the ID $guiID!")
                        )
                        return 1
                    }

                    gui.openGUI(player, guiID)
                } else {
                    ctx.source.sendMessage(Utils.deserializeText("<red>Please provide a player argument!"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return 1
        }

        private fun openGUIPlayer(ctx: CommandContext<CommandSourceStack>): Int {
            val player = EntityArgument.getPlayer(ctx, "player")
            val guiID = StringArgumentType.getString(ctx, "gui_id")

            val gui = GUIsAPI.getGUIConfig(guiID)
            if (gui == null) {
                ctx.source.sendMessage(Utils.deserializeText("<red>Could not find a GUI with the ID $guiID!"))
                return 1
            }

            gui.openGUI(player, guiID)
            return 1
        }
    }
}
