package com.f4xizzz.serverbuddy.guis.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.guis.GuisModule
import com.f4xizzz.serverbuddy.guis.utils.SubCommand
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class DebugCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("debug")
            .requires(Permissions.require("serverbuddy.guis.command.debug", 2))
            .executes(Companion::debug)
            .build()
    }

    companion object {
        fun debug(ctx: CommandContext<CommandSourceStack>): Int {
            val newMode = !GuisModule.INSTANCE.configManager.config.debug
            GuisModule.INSTANCE.configManager.config.debug = newMode
            GuisModule.INSTANCE.configManager.saveFile("config.json", GuisModule.INSTANCE.configManager.config)

            if (newMode) {
                ctx.source.sendMessage(Component.text("Debug mode has been enabled!").color(NamedTextColor.GREEN))
            } else {
                ctx.source.sendMessage(Component.text("Debug mode has been disabled!").color(NamedTextColor.RED))
            }
            return 1
        }
    }
}
