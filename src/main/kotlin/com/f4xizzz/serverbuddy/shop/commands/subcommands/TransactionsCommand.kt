package com.f4xizzz.serverbuddy.shop.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.gui.TransactionsGUI
import com.f4xizzz.serverbuddy.shop.logging.LoggerManager
import com.f4xizzz.serverbuddy.shop.utils.SubCommand
import com.f4xizzz.serverbuddy.shop.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.*

class TransactionsCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("transactions")
            .requires(Permissions.require("serverbuddy.shop.command.transactions", 2))
            .then(Commands.argument("player", StringArgumentType.string())
                .suggests { _, builder ->
                    SharedSuggestionProvider.suggest(ShopModule.INSTANCE.server.playerList.players.map { it.name.string }, builder)
                }
                .executes(::execute)
            )
            .build()
    }

    companion object {
        fun execute(
            ctx: CommandContext<CommandSourceStack>
        ): Int {
            val viewer = ctx.source.player ?: run {
                ctx.source.sendSystemMessage(Component.literal("You must be a player to run this command!").withStyle { it.withColor(ChatFormatting.RED) })
                return 0
            }

            val target = StringArgumentType.getString(ctx, "player")

            var uuid: UUID? = try {
                UUID.fromString(target)
            } catch (_: IllegalArgumentException) {
                null
            }

            if (uuid == null) {
                val player = ShopModule.INSTANCE.server.playerList.players.find { it.name.string.equals(target, ignoreCase = true) }
                if (player == null) {
                    ctx.source.sendSystemMessage(Component.literal("Player '$target' not found online! Offline lookups require using a UUID").withStyle { it.withColor(ChatFormatting.RED) })
                    return 0
                }
                uuid = player.uuid
            }

            if (!LoggerManager.canListLogs()) {
                ctx.source.sendSystemMessage(Component.literal("The current logger does not support listing transactions!").withStyle { it.withColor(ChatFormatting.RED) })
                return 0
            }

            ctx.source.sendSystemMessage(Component.literal("Fetching transactions for ${target}...").withStyle(ChatFormatting.GREEN))

            LoggerManager.getUserTransactions(uuid).thenAccept { transactions ->
                if (transactions.isEmpty()) {
                    ctx.source.sendSystemMessage(Component.literal("No transactions found for player ${target}!").withStyle(ChatFormatting.YELLOW))
                    return@thenAccept
                }

                TransactionsGUI(viewer, uuid, transactions.reversed()).open()
            }.exceptionally {
                Utils.printError("An error occurred while fetching transactions for player ${target}!")
                ctx.source.sendSystemMessage(Component.literal("An error occurred while fetching transactions for player ${target}!").withStyle(ChatFormatting.RED))
                return@exceptionally null
            }

            return 1
        }
    }
}
