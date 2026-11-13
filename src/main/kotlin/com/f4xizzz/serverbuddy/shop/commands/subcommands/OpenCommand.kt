package com.f4xizzz.serverbuddy.shop.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.shop.ShopAPI
import com.f4xizzz.serverbuddy.shop.config.Lang
import com.f4xizzz.serverbuddy.shop.utils.SubCommand
import com.f4xizzz.serverbuddy.shop.utils.asAdventure
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class OpenCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("open")
            .requires(Permissions.require("serverbuddy.shop.command.open", 2))
            .then(Commands.argument("shop", StringArgumentType.string())
                .suggests { _, builder ->
                    SharedSuggestionProvider.suggest(ShopAPI.getShopIDs().stream(), builder)
                }
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(Permissions.require("serverbuddy.shop.command.open.others", 2))
                    .executes { ctx ->
                        openOther(
                            ctx,
                            StringArgumentType.getString(ctx, "shop"),
                            EntityArgument.getPlayer(ctx, "player")
                        )
                    }
                )
                .executes { ctx ->
                    openSelf(
                        ctx,
                        StringArgumentType.getString(ctx, "shop")
                    )
                }
            )
            .build()
    }

    companion object {
        fun openSelf(
            ctx: CommandContext<CommandSourceStack>,
            shop: String,
        ): Int {
            if (!ctx.source.isPlayer) {
                ctx.source.sendSystemMessage(Component.literal("You must be a player to run this command!").withStyle { it.withColor(ChatFormatting.RED) })
                return 1
            }

            return openOther(ctx, shop, ctx.source.playerOrException)
        }

        fun openOther(
            ctx: CommandContext<CommandSourceStack>,
            shopId: String,
            player: ServerPlayer,
        ): Int {
            val shop = ShopAPI.getShop(shopId)
            if (shop == null) {
                Lang.ERROR_SHOP_NOT_FOUND.forEach {
                    ctx.source.sendMessage(it.asAdventure(mapOf("%shop_id%" to shopId)))
                }
                return 1
            }

            shop.open(player)

            ctx.source.sendSystemMessage(Component.literal("Opened shop '$shopId' for ${player.name.string}!").withStyle { it.withColor(ChatFormatting.GREEN) })

            return 1
        }
    }
}
