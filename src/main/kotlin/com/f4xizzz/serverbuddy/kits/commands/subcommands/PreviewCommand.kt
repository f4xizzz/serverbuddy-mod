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
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

class PreviewCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("preview")
                .then(Commands.argument("kit", StringArgumentType.word())
                    .requires(Permissions.require("serverbuddy.kits.command.preview", 2))
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.KITS.keys.stream(), builder)
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
            val kitId = StringArgumentType.getString(ctx, "kit")

            val kit = ConfigManager.KITS[kitId]
            if (kit == null) {
                ctx.source.sendMessage(Utils.deserializeText(
                    Utils.parsePlaceholders(
                        player,
                        ConfigManager.CONFIG.messages.kitNotFound,
                        kitId,
                        null,
                        null
                    )
                ))
                return 1
            }

            val preview = kit.createPreview(player) ?: run {
                ctx.source.sendMessage(Utils.deserializeText(
                    Utils.parsePlaceholders(
                        player,
                        ConfigManager.CONFIG.messages.kitNoPreview,
                        kitId,
                        kit,
                        null
                    )
                ))
                return 1
            }

            preview.open()

            return 1
        }
    }
}
