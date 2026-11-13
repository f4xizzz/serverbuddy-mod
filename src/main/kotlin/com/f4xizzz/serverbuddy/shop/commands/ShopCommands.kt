package com.f4xizzz.serverbuddy.shop.commands

import com.mojang.brigadier.CommandDispatcher
import com.f4xizzz.serverbuddy.shop.ShopAPI
import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.config.Lang
import com.f4xizzz.serverbuddy.shop.utils.asAdventure
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ShopCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        for ((id, shopConfig) in ConfigManager.SHOPS) {
            for (alias in shopConfig.aliasCommands) {
                dispatcher.register(Commands.literal(alias.command)
                    .requires { obj: CommandSourceStack -> obj.isPlayer }
                    .requires { alias.hasPermission(it) }
                    .executes { ctx ->
                        val player = ctx.source.player
                        if (player == null) {
                            ctx.source.sendMessage(
                                Component.text("Must be a player to run this command!")
                                    .color(NamedTextColor.RED)
                            )
                            return@executes 1
                        }

                        if (!alias.hasPermission(player.createCommandSourceStack())) {
                            ctx.source.sendMessage(
                                Component.text("You don't have permission to run this command!")
                                    .color(NamedTextColor.RED)
                            )
                            return@executes 1
                        }

                        val shop = ShopAPI.getShop(id)
                        if (shop == null) {
                            Lang.ERROR_SHOP_NOT_FOUND.forEach {
                                ctx.source.sendMessage(it.asAdventure(mapOf("%shop_id%" to id)))
                            }
                            return@executes 1
                        }

                        shop.open(player)
                        return@executes 1
                    }
                )
            }
        }
    }
}
