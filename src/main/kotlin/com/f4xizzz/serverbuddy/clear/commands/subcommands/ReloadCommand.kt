package com.f4xizzz.serverbuddy.clear.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.clear.ClearModule
import com.f4xizzz.serverbuddy.clear.utils.SubCommand
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("reload")
            .requires(Permissions.require("serverbuddy.clear.command.reload", 4))
            .executes(Companion::reload)
            .build()
    }

    companion object {
        fun reload(ctx: CommandContext<CommandSourceStack>): Int {
            ClearModule.INSTANCE.reload()
            ctx.source.sendMessage(Component.text("Reloaded ServerBuddy Clear").color(NamedTextColor.GREEN))
            return 1
        }
    }
}