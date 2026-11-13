package com.f4xizzz.serverbuddy.kits.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.gui.KitsMenuGui
import com.f4xizzz.serverbuddy.kits.utils.SubCommand
import com.f4xizzz.serverbuddy.kits.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

class MenuCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("menu")
                .then(Commands.argument("menu", StringArgumentType.word())
                    .requires(Permissions.require("serverbuddy.kits.command.menu", 2))
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.MENUS.keys.stream(), builder)
                    }
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            execute(ctx, EntityArgument.getPlayer(ctx, "player"))
                        }
                    )
                    .executes { ctx ->
                        execute(ctx, ctx.source.playerOrException)
                    }
                )
                .build()
    }

    companion object {
        fun execute(ctx: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
            val menuId = StringArgumentType.getString(ctx, "menu")

            val menuConfig = ConfigManager.MENUS[menuId]
            if (menuConfig == null) {
                ctx.source.sendMessage(Utils.deserializeText(
                    Utils.parsePlaceholders(
                        player,
                        ConfigManager.CONFIG.messages.kitMenuError
                    )
                ))
                return 1
            }

            KitsMenuGui(player, menuConfig).open()

            return 1
        }
    }
}
