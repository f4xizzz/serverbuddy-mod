package com.f4xizzz.serverbuddy.modules.wondertrade

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.Webhooks
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.random.Random

/** The trade itself: cooldown, picking what the player gets, swapping and the announcements. Cobblemon only. */
object WonderTradeService {
    const val USE = "serverbuddy.wondertrade.use"
    const val ADMIN = "serverbuddy.wondertrade.admin"

    @Volatile var config = WonderTradeConfig()
        private set

    fun load(newConfig: WonderTradeConfig) {
        config = newConfig
    }

    fun cooldownMinutes(player: ServerPlayer): Int {
        val shortest = config.permissionCooldowns.entries.filter { SbPermissions.check(player, it.key, false) }.minOfOrNull { it.value }
        return (shortest ?: config.cooldownMinutes).coerceAtLeast(0)
    }

    fun waitMs(player: ServerPlayer): Long {
        val minutes = cooldownMinutes(player)
        if (minutes <= 0) return 0
        return (WonderTradeRepository.lastTrade(player.uuid) + minutes * 60_000L - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun resetCooldown(uuid: UUID) = WonderTradeRepository.setLastTrade(uuid, 0)

    /** Why [pokemon] cannot be offered now (a ready message), or null. */
    fun check(player: ServerPlayer, pokemon: Pokemon): String? {
        val messages = config.messages
        if (PokemonTools.inBattle(player)) return messages.inBattle
        val wait = waitMs(player)
        if (wait > 0) return Texts.fill(messages.cooldown, mapOf("time" to Durations.format(wait)))
        if (config.blacklist.reason(pokemon) != null) return messages.blacklisted
        if (config.keepOneInParty && PokemonTools.partySize(player) <= 1 && Cobblemon.storage.getParty(player).toGappyList().contains(pokemon)) {
            val pcEmpty = Cobblemon.storage.getPC(player).boxes.all { box -> (0 until 30).all { box.get(it) == null } }
            if (pcEmpty) return messages.lastPokemon
        }
        return null
    }

    private fun plainName(pokemon: Pokemon): String = pokemon.nickname?.string?.takeIf { it.isNotBlank() } ?: pokemon.species.name

    // ---------------------------------------------------------------- what the player gets

    private fun fromPool(player: ServerPlayer, offered: Pokemon): Pair<PoolEntry, Pokemon>? {
        if (config.randomOnly) return null
        val offeredLegendary = PokemonTools.isLegendaryLike(offered)
        val suitable = WonderTradeRepository.candidates(player.uuid).filter { entry ->
            (!config.levelMatching || kotlin.math.abs(entry.level - offered.level) <= config.levelMatchRange) &&
                (!config.legendaryMatching || entry.legendary == offeredLegendary) &&
                (!config.shinyMatching || entry.shiny == offered.shiny)
        }.shuffled()
        for (entry in suitable) {
            val pokemon = PokemonTools.fromJson(entry.json, player) ?: continue
            return entry to pokemon
        }
        return null
    }

    private fun isLegendaryLike(species: Species): Boolean = species.labels.any { it in setOf("legendary", "mythical", "ultra_beast", "paradox") }

    private fun generate(offered: Pokemon): Pokemon {
        val settings = config.generated
        val offeredLegendary = PokemonTools.isLegendaryLike(offered)
        val wantLegendary = offeredLegendary || (settings.allowLegendary && Random.nextInt(50) == 0)
        var species: Species = PokemonSpecies.random()
        for (attempt in 0 until 60) {
            val candidate = PokemonSpecies.random()
            if (isLegendaryLike(candidate) == wantLegendary) {
                species = candidate
                break
            }
        }
        val range = settings.levelRange.coerceAtLeast(0)
        val level = (offered.level + Random.nextInt(-range, range + 1)).coerceIn(1, 100)
        val pokemon = PokemonProperties.parse("${species.resourceIdentifier} level=$level").create()
        val shiny = (config.shinyMatching && offered.shiny) || (settings.shinyOdds > 0 && Random.nextInt(settings.shinyOdds) == 0)
        if (shiny) pokemon.shiny = true
        return pokemon
    }

    // ---------------------------------------------------------------- the trade

    /** Swaps [offered] for a pool or generated Pokémon. True when the trade happened. */
    fun trade(player: ServerPlayer, offered: Pokemon): Boolean {
        val messages = config.messages
        check(player, offered)?.let {
            Texts.send(player, it, PokemonVars.of(offered, config.menu.shinyPrefix))
            return false
        }
        val offeredJson = PokemonTools.toJson(offered, player)
        val offeredName = PokemonVars.name(offered)
        val offeredPlain = plainName(offered)
        val pooled = fromPool(player, offered)
        // reserve the pooled Pokémon first: if somebody else just took it, fall back to a generated one
        val received: Pair<PoolEntry?, Pokemon> = if (pooled != null && WonderTradeRepository.take(pooled.first.id)) pooled.first to pooled.second else null to generate(offered)
        if (!PokemonTools.take(player, offered)) {
            // put a reserved pool Pokémon back, nothing else was changed
            received.first?.let { WonderTradeRepository.add(it) }
            Texts.send(player, messages.alreadyGone)
            return false
        }
        WonderTradeRepository.add(PoolEntry(UUID.randomUUID().toString(), player.uuid, player.gameProfile.name, offered.level, offered.shiny, PokemonTools.isLegendaryLike(offered), PokemonTools.speciesId(offered), offeredJson))
        WonderTradeRepository.trim(config.poolMax)
        val got = received.second
        PokemonTools.give(player, got)
        WonderTradeRepository.setLastTrade(player.uuid, System.currentTimeMillis())
        val gotPlain = plainName(got)
        WonderTradeRepository.addHistory(player.uuid, TradeRecord(System.currentTimeMillis(), offeredPlain, gotPlain, got.level), config.historySize)

        val from = received.first?.ownerName?.let { Texts.escape(it) } ?: messages.fromWild
        val gotName = PokemonVars.name(got)
        Texts.send(player, messages.traded, mapOf("gave" to offeredName, "got" to gotName, "level" to got.level, "from" to from))
        announce(player, offeredName, gotName, offeredPlain, gotPlain, got)
        return true
    }

    private fun announce(player: ServerPlayer, gaveName: String, gotName: String, gavePlain: String, gotPlain: String, got: Pokemon) {
        val broadcast = config.broadcast
        val rare = (got.shiny && broadcast.shinies) || (PokemonTools.isLegendaryLike(got) && broadcast.legendaries)
        val vars = mapOf("player" to Texts.escape(player.gameProfile.name), "gave" to gaveName, "got" to gotName)
        val server = player.server
        if (rare) server.playerList.broadcastSystemMessage(Texts.render(config.messages.broadcastRare, vars), false)
        else if (broadcast.trades) server.playerList.broadcastSystemMessage(Texts.render(config.messages.broadcast, vars), false)
        if (got.shiny || PokemonTools.isLegendaryLike(got)) {
            Webhooks.post(config.webhook.resolve(), Texts.fill(config.messages.webhookRare, mapOf("player" to player.gameProfile.name, "got" to gotPlain, "gave" to gavePlain)))
        }
    }
}
