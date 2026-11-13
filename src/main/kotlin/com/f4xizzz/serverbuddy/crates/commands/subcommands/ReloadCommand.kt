package com.f4xizzz.serverbuddy.crates.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.utils.SubCommand
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("reload")
            .requires(Permissions.require("serverbuddy.crates.command.reload", 2))
            .executes(Companion::reload)
            .build()
    }

    companion object {
        fun reload(ctx: CommandContext<CommandSourceStack>): Int {
            CratesModule.INSTANCE.reload()
            ctx.source.sendMessage(Component.text("Reloaded ${CratesModule.MOD_NAME}!").color(NamedTextColor.GREEN))
            return 1
        }
    }
}
