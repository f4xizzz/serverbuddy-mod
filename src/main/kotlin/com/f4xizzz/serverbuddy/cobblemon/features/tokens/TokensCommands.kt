package com.f4xizzz.serverbuddy.cobblemon.features.tokens

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument

/**
 * `/poketoken give <jogador> <ficha> [quantidade]` e `/poketoken list` (também em `/serverbuddy poketoken ...`).
 * Permissão: `serverbuddy.poketoken.give` (padrão: nível 2).
 */
object TokensCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /poketoken (o nome pode mudar em modules.conf) e /serverbuddy poketoken (nome fixo)
        dispatcher.register(node(ModuleSettings.commandName("poketoken")))
        ModuleCommands.mount(dispatcher, node("poketoken").build(), "poketoken")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.poketoken.give", 2))
            .then(
                Commands.literal("give").then(
                    Commands.argument("player", EntityArgument.player()).then(
                        Commands.argument("token", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(TokensConfigManager.config.tokens.keys, builder) }
                            .executes { give(it, 1) }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes { give(it, IntegerArgumentType.getInteger(it, "amount")) }
                            )
                    )
                )
            )
            .then(Commands.literal("list").executes { list(it) })
            .then(
                Commands.literal("reload").executes {
                    TokensConfigManager.load()
                    Texts.reply(it.source, "<green>Tokens reloaded (${TokensConfigManager.config.tokens.size}).")
                    1
                }
            )

    private fun give(ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val config = TokensConfigManager.config
        val target = EntityArgument.getPlayer(ctx, "player")
        val id = StringArgumentType.getString(ctx, "token")
        val def = config.tokens[id]
        if (def == null) {
            Texts.fail(ctx.source, config.messages.unknownToken, mapOf("token" to id, "list" to config.tokens.keys.joinToString(", ")))
            return 0
        }
        val stack = TokenItems.create(id, def, amount)
        if (stack.isEmpty) {
            Texts.fail(ctx.source, "<red>The token item '$id' is invalid in the config (see the console).")
            return 0
        }
        target.inventory.add(stack)
        if (!stack.isEmpty) target.drop(stack, false)

        val vars = mapOf("amount" to amount, "player" to target.gameProfile.name, "token_name" to (def.item.name ?: id))
        Texts.reply(ctx.source, config.messages.given, vars, broadcast = true)
        Texts.send(target, config.messages.received, vars)
        return amount
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val tokens = TokensConfigManager.config.tokens
        Texts.reply(ctx.source, "<gold>Fichas (${tokens.size}):")
        for ((id, def) in tokens) {
            Texts.reply(ctx.source, "<gray> - <yellow>$id</yellow> <dark_gray>(${def.type}, item ${def.item.item})")
        }
        return tokens.size
    }
}
