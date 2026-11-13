package com.f4xizzz.serverbuddy.announcements.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.announcements.AnnouncementsModule
import com.f4xizzz.serverbuddy.announcements.utils.SubCommand
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("reload")
            .requires(Permissions.require("serverbuddy.announcements.command.reload", 4))
            .executes(Companion::reload)
            .build()
    }

    companion object {
        fun reload(ctx: CommandContext<CommandSourceStack>): Int {
            AnnouncementsModule.INSTANCE.reload()
            ctx.source.sendMessage(Utils.deserializeText("<green>Reloaded ServerBuddy Announcements"))
            return 1
        }
    }
}
