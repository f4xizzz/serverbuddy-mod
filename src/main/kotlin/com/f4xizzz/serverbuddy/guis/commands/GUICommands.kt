package com.f4xizzz.serverbuddy.guis.commands

import com.mojang.brigadier.CommandDispatcher
import com.f4xizzz.serverbuddy.guis.api.GUIsAPI
import com.f4xizzz.serverbuddy.guis.config.ConfigManager
import com.f4xizzz.serverbuddy.guis.utils.Utils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class GUICommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        for (guiEntry in ConfigManager.GUIS) {
            for (command in guiEntry.value.aliasCommands) {
                dispatcher.register(Commands.literal(command)
                    .requires { obj: CommandSourceStack -> obj.isPlayer }
                    .requires { guiEntry.value.hasAliasPermission(it) }
                    .executes { ctx ->
                        val player = ctx.source.player
                        if (player == null) {
                            ctx.source.sendMessage(
                                Component.text("Must be a player to run this command!")
                                    .color(NamedTextColor.RED)
                            )
                            return@executes 1
                        }

                        if (!guiEntry.value.hasAliasPermission(player.createCommandSourceStack())) {
                            ctx.source.sendMessage(
                                Component.text("You don't have permission to run this command!")
                                    .color(NamedTextColor.RED)
                            )
                            return@executes 1
                        }

                        val gui = GUIsAPI.getGUIConfig(guiEntry.key)

                        if (gui == null) {
                            Utils.printError("There was an error while running the command '$command' for player '${player.name.string}'! " +
                                    "The GUI '${guiEntry.key}' returned null. Was it deleted, renamed, or changed?")
                            ctx.source.sendMessage(
                                Component.text("Error while attempting to open this GUI! Check the console for more information.")
                                    .color(NamedTextColor.RED)
                            )
                            return@executes 1
                        }

                        gui.openGUI(player, guiEntry.key)
                        return@executes 1
                    }
                )
            }
        }
    }
}
