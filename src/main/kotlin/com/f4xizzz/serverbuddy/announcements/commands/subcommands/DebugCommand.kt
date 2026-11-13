package com.f4xizzz.serverbuddy.announcements.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.announcements.AnnouncementsModule
import com.f4xizzz.serverbuddy.announcements.utils.SubCommand
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class DebugCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("debug")
            .requires(Permissions.require("serverbuddy.announcements.command.debug", 4))
            .executes(Companion::debug)
            .build()
    }

    companion object {
        fun debug(ctx: CommandContext<CommandSourceStack>): Int {
            val newMode = !AnnouncementsModule.INSTANCE.configManager.config.debug
            AnnouncementsModule.INSTANCE.configManager.config.debug = newMode
            AnnouncementsModule.INSTANCE.configManager.saveFile("config.json", AnnouncementsModule.INSTANCE.configManager.config)

            if (newMode) {
                ctx.source.sendMessage(Utils.deserializeText("<green>Debug mode has been enabled!"))
            } else {
                ctx.source.sendMessage(Utils.deserializeText("<red>Debug mode has been disabled!"))
            }
            return 1
        }
    }
}
