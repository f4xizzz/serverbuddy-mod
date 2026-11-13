package com.f4xizzz.serverbuddy.modules.sts

import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/** Pricing and selling. Cobblemon only. */
object StsService {
    const val USE = "serverbuddy.sts.use"
    const val ADMIN = "serverbuddy.sts.admin"

    @Volatile var config = StsConfig()
        private set

    fun load(newConfig: StsConfig) {
        config = newConfig
    }

    fun economyReady(): Boolean = Economy.available(config.economy)

    // ---------------------------------------------------------------- price

    /** The base price of [pokemon] (formula or species price), without any player bonus. */
    fun basePrice(pokemon: Pokemon): Double {
        val pricing = config.pricing
        pricing.speciesPrices.entries.firstOrNull { normalize(it.key) == PokemonTools.speciesId(pokemon).lowercase(Locale.ROOT) }?.let { return it.value }
        var price = pricing.base + pokemon.level * pricing.perLevel
        val labels = pokemon.species.labels.map { it.lowercase(Locale.ROOT) }
        val labelMultiplier = pricing.labelMultipliers.entries.filter { it.key.lowercase(Locale.ROOT) in labels || (it.key.equals("legendary", true) && PokemonTools.isLegendaryLike(pokemon)) }.maxOfOrNull { it.value } ?: 1.0
        price *= labelMultiplier
        if (pokemon.shiny) price *= pricing.shinyMultiplier
        price *= 1.0 + PokemonTools.ivPercent(pokemon) / 100.0 * pricing.ivBonus
        return price
    }

    private fun normalize(text: String): String {
        val t = text.trim().lowercase(Locale.ROOT)
        return if (':' in t) t else "cobblemon:$t"
    }

    /** The best permission bonus the player has, as (percent, label). */
    fun bonus(player: ServerPlayer?): Pair<Double, String> {
        if (player == null) return 0.0 to ""
        val best = config.permissionBonuses.entries.filter { it.value.percent > 0 && SbPermissions.check(player, it.key, false) }.maxByOrNull { it.value.percent }
        return if (best == null) 0.0 to "" else best.value.percent to best.value.label
    }

    /** What [pokemon] pays [player]: base price, bonus and the cap. */
    fun price(pokemon: Pokemon, player: ServerPlayer?): Double {
        val (percent, _) = bonus(player)
        val price = basePrice(pokemon) * (1.0 + percent / 100.0)
        return Math.floor(price.coerceAtMost(config.limitPrice) * 100.0) / 100.0
    }

    fun releasePrice(price: Double): Double = Math.floor(price * config.release.pricePercent / 100.0 * 100.0) / 100.0

    fun vars(pokemon: Pokemon, player: ServerPlayer?): Map<String, Any?> {
        val price = price(pokemon, player)
        return PokemonVars.of(pokemon, config.menu.shinyPrefix) + mapOf(
            "price" to Economy.format(price), "release_price" to Economy.format(releasePrice(price)),
            "symbol" to config.currencySymbol, "bonus" to bonus(player).second
        )
    }

    // ---------------------------------------------------------------- cooldown

    fun cooldownSeconds(player: ServerPlayer): Int {
        val config = config
        val shortest = config.permissionCooldowns.entries.filter { SbPermissions.check(player, it.key, false) }.minOfOrNull { it.value }
        return (shortest ?: config.cooldownSeconds).coerceAtLeast(0)
    }

    /** Milliseconds the player still has to wait (0 = can sell). */
    fun waitMs(player: ServerPlayer): Long {
        val seconds = cooldownSeconds(player)
        if (seconds <= 0) return 0
        val last = StsRepository.load(player.uuid).lastSale
        return (last + seconds * 1000L - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun resetCooldown(uuid: java.util.UUID) {
        val data = StsRepository.load(uuid)
        data.lastSale = 0
        StsRepository.save(data)
    }

    // ---------------------------------------------------------------- selling

    /** Why [pokemon] cannot be sold right now (a ready-to-send message), or null when it can. */
    fun check(player: ServerPlayer, pokemon: Pokemon, release: Boolean): String? {
        val messages = config.messages
        if (!economyReady()) return messages.noEconomy
        if (PokemonTools.inBattle(player)) return messages.inBattle
        if (config.blacklist.reason(pokemon) != null) return messages.blacklisted
        if (price(pokemon, player) < config.minPrice) return messages.notWorth
        if (!release) {
            val wait = waitMs(player)
            if (wait > 0) return Texts.fill(messages.cooldown, mapOf("time" to Durations.format(wait)))
        }
        if (config.keepOneInParty && PokemonTools.partySize(player) <= 1 && com.cobblemon.mod.common.Cobblemon.storage.getParty(player).toGappyList().contains(pokemon)) {
            val pcEmpty = com.cobblemon.mod.common.Cobblemon.storage.getPC(player).boxes.all { box -> (0 until 30).all { box.get(it) == null } }
            if (pcEmpty) return messages.lastPokemon
        }
        return null
    }

    /** Sells (or releases) [pokemon]. Returns true when the Pokémon was taken and paid for. */
    fun sell(player: ServerPlayer, pokemon: Pokemon, release: Boolean): Boolean {
        val messages = config.messages
        check(player, pokemon, release)?.let {
            Texts.send(player, it, PokemonVars.of(pokemon, config.menu.shinyPrefix))
            return false
        }
        val vars = vars(pokemon, player)
        val price = price(pokemon, player).let { if (release) releasePrice(it) else it }
        if (!PokemonTools.take(player, pokemon)) {
            Texts.send(player, messages.alreadyGone)
            return false
        }
        if (!Economy.deposit(player, price, config.economy)) {
            PokemonTools.give(player, pokemon)
            Texts.send(player, messages.paymentFailed)
            return false
        }
        val data = StsRepository.load(player.uuid)
        if (!release) data.lastSale = System.currentTimeMillis()
        data.sold++
        data.earned += price
        StsRepository.save(data)
        Texts.send(player, if (release) messages.released else messages.sold, vars)
        return true
    }
}
