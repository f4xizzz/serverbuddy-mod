package com.f4xizzz.serverbuddy.kits.commands.subcommands

import com.mojang.brigadier.arguments.BoolArgumentType
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
import net.minecraft.commands.arguments.EntityArgument

class GiveCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("give")
                .then(Commands.argument("kit", StringArgumentType.word())
                    .requires(Permissions.require("serverbuddy.kits.command.give", 2))
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.KITS.keys.stream(), builder)
                    }
                    .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.argument("bypass", BoolArgumentType.bool())
                            .executes(Companion::give)
                        )
                        .executes(Companion::give)
                    )
                )
                .build()
    }

    companion object {
        fun give(ctx: CommandContext<CommandSourceStack>): Int {
            val players = EntityArgument.getPlayers(ctx, "player")
            if (players.isNullOrEmpty()) {
                ctx.source.sendMessage(Utils.deserializeText("<red>You must provide a target player!"))
                return 1
            }

            var bypass = false
            try { bypass = BoolArgumentType.getBool(ctx, "bypass") } catch (_: Exception) { }

            val kitId = StringArgumentType.getString(ctx, "kit")

            val kit = ConfigManager.KITS[kitId]
            if (kit == null) {
                ctx.source.sendMessage(Utils.deserializeText(
                    ConfigManager.CONFIG.messages.kitNotFound.replace("%kit_name%", kitId)
                ))
                return 1
            }

            for (player in players) {
                kit.claim(kitId, player, bypass, bypass)
            }

            return 1
        }
    }
}
