package com.f4xizzz.serverbuddy.guis.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.guis.commands.subcommands.DebugCommand
import com.f4xizzz.serverbuddy.guis.commands.subcommands.OpenCommand
import com.f4xizzz.serverbuddy.guis.commands.subcommands.PrintNBTCommand
import com.f4xizzz.serverbuddy.guis.commands.subcommands.ReloadCommand
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class BaseCommands {
    private val aliases = listOf("guis", "skiesguis", "guis", "gui", "sg")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val rootCommands: List<LiteralCommandNode<CommandSourceStack>> = aliases.map {
            Commands.literal(it).build()
        }

        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            OpenCommand().build(),
            ReloadCommand().build(),
            DebugCommand().build(),
            PrintNBTCommand().build(),
        )

        rootCommands.forEach { root ->
            subCommands.forEach { sub -> root.addChild(sub) }
            ModuleCommands.mount(dispatcher, root, "guis")
        }
    }
}
