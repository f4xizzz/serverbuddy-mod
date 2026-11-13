package com.f4xizzz.serverbuddy.cobblemon.features.shout

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.shout.PokeShoutConfig
import com.f4xizzz.serverbuddy.shout.ShoutModule
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * `/pokeshout <slot>` e `/pokeshoutall`: mostra um Pokémon (ou o time inteiro) no chat, com os detalhes no hover.
 * Permissões: `serverbuddy.pokeshout.use` e `serverbuddy.pokeshout.all` (padrão: nível 2).
 */
object PokeShoutCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "pokeshout") { name ->
            Commands.literal(name)
                .requires(SbPermissions.require("serverbuddy.pokeshout.use", 2))
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes { shout(it, IntegerArgumentType.getInteger(it, "slot")) })
        }
        SbCommands.register(dispatcher, "pokeshoutall") { name ->
            Commands.literal(name)
                .requires(SbPermissions.require("serverbuddy.pokeshout.all", 2))
                .executes { shoutAll(it) }
        }
    }

    private fun shout(ctx: CommandContext<CommandSourceStack>, slot: Int): Int {
        val cfg = ShoutModule.config.pokeShout
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>This command can only be used by a player.")
            return 0
        }
        if (!cfg.enabled) return 0
        val pokemon = Cobblemon.storage.getParty(player).get(slot - 1)
        if (pokemon == null) {
            Texts.send(player, cfg.noPokemon, mapOf("slot" to slot))
            return 0
        }
        val wait = ShoutModule.cooldown(player, "pokeshout", cfg.cooldownSeconds)
        if (wait > 0) {
            Texts.send(player, cfg.cooldown, mapOf("seconds" to wait))
            return 0
        }
        val message = Texts.render(cfg.format, mapOf("player" to player.gameProfile.name), mapOf("pokemon" to pokemonText(pokemon, cfg)))
        ctx.source.server.playerList.broadcastSystemMessage(message, false)
        return 1
    }

    private fun shoutAll(ctx: CommandContext<CommandSourceStack>): Int {
        val cfg = ShoutModule.config.pokeShout
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>This command can only be used by a player.")
            return 0
        }
        if (!cfg.enabled) return 0
        val team = Cobblemon.storage.getParty(player).toGappyList().filterNotNull()
        if (team.isEmpty()) {
            Texts.send(player, cfg.emptyParty)
            return 0
        }
        val wait = ShoutModule.cooldown(player, "pokeshout", cfg.cooldownSeconds)
        if (wait > 0) {
            Texts.send(player, cfg.cooldown, mapOf("seconds" to wait))
            return 0
        }
        val joined = Component.empty()
        team.forEachIndexed { index, pokemon ->
            if (index > 0) joined.append(Texts.parse(cfg.separator))
            joined.append(pokemonText(pokemon, cfg))
        }
        val message = Texts.render(cfg.formatAll, mapOf("player" to player.gameProfile.name), mapOf("pokemons" to joined))
        ctx.source.server.playerList.broadcastSystemMessage(message, false)
        return 1
    }

    /** O texto do Pokémon no chat, com as linhas do hover da config. */
    private fun pokemonText(pokemon: Pokemon, cfg: PokeShoutConfig): Component {
        val vars = PokemonVars.of(pokemon, cfg.shinyPrefix)
        val label = Texts.render(cfg.label, vars)
        val hover = Texts.joinLines(cfg.hover.map { Texts.render(it, vars) })
        return label.copy().withStyle { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)) }
    }
}
