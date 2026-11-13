package com.f4xizzz.serverbuddy.announcements.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.announcements.commands.subcommands.*
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class BaseCommands {
    private val aliases = listOf("announcements", "skiesannouncements", "announcements")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val rootCommands: List<LiteralCommandNode<CommandSourceStack>> = aliases.map {
            Commands.literal(it).build()
        }

        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            ReloadCommand().build(),
            DebugCommand().build(),
            ListCommand().build(),
            AnnounceCommand().build(),
            SendCommand().build(),
        )

        rootCommands.forEach { root ->
            subCommands.forEach { sub -> root.addChild(sub) }
            ModuleCommands.mount(dispatcher, root, "announcements")
        }
    }
}
