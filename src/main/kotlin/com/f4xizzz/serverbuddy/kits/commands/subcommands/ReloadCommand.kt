package com.f4xizzz.serverbuddy.kits.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.utils.SubCommand
import com.f4xizzz.serverbuddy.kits.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("reload")
            .requires(Permissions.require("serverbuddy.kits.command.reload", 2))
            .executes(Companion::reload)
            .build()
    }

    companion object {
        fun reload(ctx: CommandContext<CommandSourceStack>): Int {
            KitsModule.INSTANCE.reload()
            ctx.source.sendMessage(Utils.deserializeText("<green>Reloaded ServerBuddy Kits"))
            return 1
        }
    }
}
