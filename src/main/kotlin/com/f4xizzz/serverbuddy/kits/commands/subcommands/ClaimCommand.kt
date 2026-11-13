package com.f4xizzz.serverbuddy.kits.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.utils.SubCommand
import com.f4xizzz.serverbuddy.kits.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

class ClaimCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("claim")
            .then(Commands.argument("kit", StringArgumentType.word())
                .requires(Permissions.require("serverbuddy.kits.command.claim", 2))
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ConfigManager.KITS.filter { entry ->
                        return@filter !(ctx.source.isPlayer && !entry.value.hasPermission(ctx.source.player!!))
                    }.keys, builder)
                }
                .requires { obj: CommandSourceStack -> obj.isPlayer }
                .executes(Companion::claim)
            )
            .build()
    }

    companion object {
        fun claim(ctx: CommandContext<CommandSourceStack>): Int {
            val player = ctx.source.player
            if (player == null) {
                ctx.source.sendMessage(Utils.deserializeText("<red>You must be a player to use this command!"))
                return 1
            }

            val kitId = StringArgumentType.getString(ctx, "kit")

            val kit = ConfigManager.KITS[kitId]
            if (kit == null) {
                player.sendMessage(Utils.deserializeText(
                    ConfigManager.CONFIG.messages.kitNotFound.replace("%kit_name%", kitId)
                ))
                return 1
            }

            if (!kit.hasPermission(player)) {
                player.sendMessage(Utils.deserializeText(
                    ConfigManager.CONFIG.messages.kitNoPermission.replace("%kit_name%", kitId)
                ))
                return 1
            }

            kit.claim(kitId, player)

            return 1
        }
    }
}
