package com.f4xizzz.serverbuddy.shop.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.ShopAPI
import com.f4xizzz.serverbuddy.shop.commands.subcommands.DebugCommand
import com.f4xizzz.serverbuddy.shop.commands.subcommands.OpenCommand
import com.f4xizzz.serverbuddy.shop.commands.subcommands.ReloadCommand
import com.f4xizzz.serverbuddy.shop.commands.subcommands.TransactionsCommand
import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.config.Lang
import com.f4xizzz.serverbuddy.shop.utils.asAdventure
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class BaseCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            ReloadCommand().build(),
            DebugCommand().build(),
            OpenCommand().build(),
            TransactionsCommand().build(),
        )

        // /serverbuddy shop ... (e /sb shop ...)
        val moduleRoot = buildRoot("shop")
        subCommands.forEach { sub -> moduleRoot.addChild(sub) }
        ModuleCommands.mountModule(dispatcher, moduleRoot)

        // comandos raiz definidos na server configuration (ex.: /shop): sempre registrados
        ConfigManager.CONFIG.commands.forEach { name ->
            val root = buildRoot(name)
            subCommands.forEach { sub -> root.addChild(sub) }
            ModuleCommands.mountRoot(dispatcher, root)
        }
    }

    private fun buildRoot(name: String): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal(name)
            .requires(Permissions.require("serverbuddy.shop.command.base", 2))
            .executes(::execute)
            .build()
    }

    companion object {
        fun execute(
            ctx: CommandContext<CommandSourceStack>
        ): Int {
            if (!ctx.source.isPlayer) {
                ctx.source.sendSystemMessage(Component.literal("You must be a player to run this command!").withStyle { it.withColor(ChatFormatting.RED) })
                return 0
            }

            if (ConfigManager.CONFIG.baseShop.isEmpty()) {
                Lang.ERROR_NO_BASE_SHOP.forEach {
                    ctx.source.sendMessage(it.asAdventure())
                }
                return 0
            }

            val shop = ShopAPI.getShop(ConfigManager.CONFIG.baseShop)
            if (shop == null) {
                Lang.ERROR_SHOP_NOT_FOUND.forEach {
                    ctx.source.sendMessage(it.asAdventure(mapOf("%shop_id%" to ConfigManager.CONFIG.baseShop)))
                }
                return 0
            }

            shop.open(ctx.source.playerOrException)

            return 1
        }
    }
}
