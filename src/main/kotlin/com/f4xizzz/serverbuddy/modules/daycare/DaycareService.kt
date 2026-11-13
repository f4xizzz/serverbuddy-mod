package com.f4xizzz.serverbuddy.modules.daycare

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import net.minecraft.server.level.ServerPlayer

/** Slots, prices, level gain, depositing and withdrawing. Cobblemon only. */
object DaycareService {
    const val USE = "serverbuddy.daycare.use"
    const val ADMIN = "serverbuddy.daycare.admin"

    @Volatile var config = DaycareConfig()
        private set

    fun load(newConfig: DaycareConfig) {
        config = newConfig
    }

    /** (slots, deposit price) for this player: the tier with the most slots, or the base values. */
    fun allowance(player: ServerPlayer): Pair<Int, Double> {
        val tier = config.permissionTiers.values.let { tiers -> config.permissionTiers.entries.filter { SbPermissions.check(player, it.key, false) }.maxByOrNull { it.value.capacity } }
        return if (tier != null && tier.value.capacity > config.baseCapacity) tier.value.capacity to tier.value.price else config.baseCapacity to config.basePrice
    }

    private fun msPerLevel(): Long = (config.minutesToLevel100 * 60_000L / 100L).coerceAtLeast(1L)

    fun levelsGained(deposit: Deposit, now: Long = System.currentTimeMillis()): Int {
        val room = (config.maxLevel - deposit.depositLevel).coerceAtLeast(0)
        return ((now - deposit.depositTime).coerceAtLeast(0) / msPerLevel()).toInt().coerceAtMost(room)
    }

    fun currentLevel(deposit: Deposit): Int = (deposit.depositLevel + levelsGained(deposit)).coerceAtMost(config.maxLevel)

    /** Milliseconds until the Pokémon reaches the maximum level (0 when it is already there). */
    fun timeLeftMs(deposit: Deposit, now: Long = System.currentTimeMillis()): Long {
        val left = (config.maxLevel - currentLevel(deposit)).coerceAtLeast(0)
        if (left == 0) return 0
        val spent = (now - deposit.depositTime).coerceAtLeast(0) % msPerLevel()
        return left * msPerLevel() - spent
    }

    fun vars(deposit: Deposit, player: ServerPlayer, pokemon: Pokemon?): Map<String, Any?> {
        val base = pokemon?.let { PokemonVars.of(it, config.picker.shinyPrefix) } ?: emptyMap()
        return base + mapOf(
            "old_level" to deposit.depositLevel, "current_level" to currentLevel(deposit), "gained" to levelsGained(deposit),
            "time" to if (timeLeftMs(deposit) == 0L) "-" else Durations.format(timeLeftMs(deposit)), "slot" to deposit.slot + 1,
            "price" to Economy.format(allowance(player).second), "symbol" to config.currencySymbol
        )
    }

    fun economyReady(): Boolean = Economy.available(config.economy)

    /** Why [pokemon] cannot be left now (a message), or null. */
    fun checkDeposit(player: ServerPlayer, pokemon: Pokemon): String? {
        val messages = config.messages
        if (PokemonTools.inBattle(player)) return messages.inBattle
        if (config.blacklist.reason(pokemon) != null) return messages.blacklisted
        val used = DaycareRepository.deposits(player.uuid).size
        if (used >= allowance(player).first) return messages.full
        if (config.keepOneInParty && PokemonTools.partySize(player) <= 1 && Cobblemon.storage.getParty(player).toGappyList().contains(pokemon)) {
            val pcEmpty = Cobblemon.storage.getPC(player).boxes.all { box -> (0 until 30).all { box.get(it) == null } }
            if (pcEmpty) return messages.lastPokemon
        }
        return null
    }

    /** Leaves [pokemon] in the first free slot (charging the price). */
    fun deposit(player: ServerPlayer, pokemon: Pokemon): Boolean {
        val messages = config.messages
        checkDeposit(player, pokemon)?.let {
            Texts.send(player, it, PokemonVars.of(pokemon, config.picker.shinyPrefix))
            return false
        }
        val (capacity, price) = allowance(player)
        if (price > 0) {
            if (!economyReady()) {
                Texts.send(player, messages.noEconomy)
                return false
            }
            if (Economy.balance(player, config.economy)?.let { it < price } != false) {
                Texts.send(player, messages.noMoney, mapOf("price" to Economy.format(price), "symbol" to config.currencySymbol))
                return false
            }
        }
        val taken = DaycareRepository.deposits(player.uuid).map { it.slot }.toSet()
        val slot = (0 until capacity).firstOrNull { it !in taken } ?: run {
            Texts.send(player, messages.full)
            return false
        }
        val deposit = Deposit(slot, System.currentTimeMillis(), pokemon.level, PokemonTools.toJson(pokemon, player))
        if (!PokemonTools.take(player, pokemon)) {
            Texts.send(player, messages.alreadyGone)
            return false
        }
        if (!DaycareRepository.add(player.uuid, deposit)) {
            PokemonTools.give(player, pokemon)
            return false
        }
        if (price > 0 && !Economy.withdraw(player, price, config.economy)) {
            // the balance changed between the check and the charge: undo
            DaycareRepository.remove(player.uuid, slot)
            PokemonTools.give(player, pokemon)
            Texts.send(player, messages.noMoney, mapOf("price" to Economy.format(price), "symbol" to config.currencySymbol))
            return false
        }
        Texts.send(player, messages.deposited, PokemonVars.of(pokemon, config.picker.shinyPrefix) + mapOf("price" to Economy.format(price), "symbol" to config.currencySymbol))
        return true
    }

    /** Takes the Pokémon in [slot] back, with the levels it gained. */
    fun withdraw(player: ServerPlayer, slot: Int): Boolean {
        val messages = config.messages
        val deposit = DaycareRepository.deposits(player.uuid).firstOrNull { it.slot == slot } ?: return false
        if (PokemonTools.inBattle(player)) {
            Texts.send(player, messages.inBattle)
            return false
        }
        val pokemon = PokemonTools.fromJson(deposit.pokemonJson, player)
        if (pokemon == null) {
            Texts.send(player, messages.alreadyGone)
            return false
        }
        val level = currentLevel(deposit)
        val vars = vars(deposit, player, pokemon)
        if (!DaycareRepository.remove(player.uuid, slot)) return false
        if (level != pokemon.level) pokemon.level = level
        if (!PokemonTools.give(player, pokemon)) {
            // no room: put it back where it was (with its original time) instead of losing it
            DaycareRepository.add(player.uuid, deposit)
            Texts.send(player, messages.noRoom)
            return false
        }
        Texts.send(player, messages.withdrawn, vars + PokemonVars.of(pokemon, config.picker.shinyPrefix))
        return true
    }
}
