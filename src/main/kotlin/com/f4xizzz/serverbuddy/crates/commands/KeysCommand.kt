package com.f4xizzz.serverbuddy.crates.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.ConfigManager
import com.f4xizzz.serverbuddy.crates.gui.KeysInventory
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class KeysCommand {
    private val aliases = ConfigManager.CONFIG.keys.aliases

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val rootCommands: List<LiteralCommandNode<CommandSourceStack>> = aliases.map {
            Commands.literal(it)
                .requires(Permissions.require("serverbuddy.crates.command.keys", 2))
                .executes { ctx -> execute(ctx, ctx.source.playerOrException) }
                .build()
        }

        rootCommands.forEach { root ->
            ModuleCommands.mountRoot(dispatcher, root)
        }
    }

    companion object {
        fun execute(ctx: CommandContext<CommandSourceStack>, target: ServerPlayer): Int {
            if (ctx.source.isPlayer && ConfigManager.CONFIG.keys.useMenu) {
                KeysInventory(ctx.source.playerOrException, target).open()
            } else {
                val storage = CratesModule.INSTANCE.storage

                storage.getUserAsync(target.uuid).thenAccept { playerData ->
                    target.server.execute {
                        if (playerData.keys.isEmpty()) {
                            ctx.source.sendSystemMessage(
                                Component.literal("${target.name.string} has no keys.").withStyle(ChatFormatting.RED)
                            )
                            return@execute
                        }

                        ctx.source.sendSystemMessage(
                            Component.literal("${target.name.string}'s keys:").withStyle(ChatFormatting.GOLD)
                        )

                        for ((keyId, amount) in playerData.keys) {
                            val key = ConfigManager.KEYS[keyId]
                            if (key != null) {
                                ctx.source.sendSystemMessage(
                                    Component.literal(" - ${key.name} ($keyId): $amount")
                                        .withStyle(ChatFormatting.GREEN)
                                )
                            } else {
                                ctx.source.sendSystemMessage(
                                    Component.literal(" - Unknown Key ($keyId): $amount").withStyle(ChatFormatting.RED)
                                )
                            }
                        }
                    }
                }.exceptionally { e ->
                    target.server.execute {
                        ctx.source.sendSystemMessage(
                            Component.literal("Error retrieving player data: ${e.message}")
                                .withStyle(ChatFormatting.RED)
                        )
                    }
                    null
                }
            }

            return 1
        }
    }
}
