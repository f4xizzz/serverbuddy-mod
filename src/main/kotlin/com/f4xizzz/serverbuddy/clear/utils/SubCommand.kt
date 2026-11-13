package com.f4xizzz.serverbuddy.clear.utils

import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.commands.CommandSourceStack

interface SubCommand {
    fun build(): LiteralCommandNode<CommandSourceStack>
}