package com.f4xizzz.serverbuddy.modules.astral

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

/** Why a trade did or did not happen. */
enum class TradeResult { OK, CLOSED, GONE, SOLD_OUT, LIMIT_REACHED, IN_BATTLE, NOT_ENOUGH, NO_ROOM }

/** What a player can do with an offer right now. */
enum class Availability { OPEN, SOLD_OUT, LIMIT_REACHED }

/** The trader's rules: rotation of the pools, offers, tokens and the trade itself. Touches Cobblemon: load only when it is installed. */
object AstralService {
    private val log = LoggerFactory.getLogger("ServerBuddy/Astral")

    const val USE = "serverbuddy.astral.use"
    const val ADMIN = "serverbuddy.astral.admin"
    const val MAX_SIZE = 7
    private val STATS = listOf("hp", "atk", "def", "spa", "spd", "spe")

    @Volatile
    var config: AstralConfig = AstralConfig()
        private set

    fun load(config: AstralConfig) {
        this.config = config
    }

    fun pool(name: String): PoolSettings? = config.pools.entries().firstOrNull { it.first == name }?.second

    fun poolNames(): List<String> = config.pools.entries().map { it.first }

    /** The token's name, in the singular when [amount] is 1 ("shiny tokens" -> "shiny token"). */
    fun currencyName(currency: String, amount: Number = 2): String {
        val name = config.currencyNames[currency] ?: currency
        return if (amount.toLong() == 1L && name.endsWith("s")) name.dropLast(1) else name
    }

    fun balance(player: UUID, currency: String): Long = AstralRepository.balance(player, currency)

    // ---------------------------------------------------------------- rotation

    private fun resetMs(): Long = (config.resetHours * 3_600_000.0).toLong()

    /** Milliseconds until [pool] picks new offers (-1 when it never rotates on its own or has not started). */
    fun msUntilReset(pool: String): Long {
        val reset = resetMs()
        val at = AstralRepository.rotatedAt(pool) ?: return -1
        return if (reset <= 0) -1 else (at + reset - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /** Rotates every pool that never started or whose time is up. Returns the pools that changed. */
    fun rotateIfNeeded(): List<String> {
        val changed = ArrayList<String>()
        val reset = resetMs()
        val now = System.currentTimeMillis()
        for ((name, settings) in config.pools.entries()) {
            if (!settings.enabled) continue
            val at = AstralRepository.rotatedAt(name)
            if (at == null || (reset > 0 && now - at >= reset)) {
                rotate(name)
                changed.add(name)
            }
        }
        return changed
    }

    /** Picks new Pokémon for [pool] right now. False if the pool does not exist or has nothing to offer. */
    fun rotate(pool: String): Boolean {
        val settings = pool(pool) ?: return false
        val candidates = candidates(settings)
        if (candidates.isEmpty()) log.warn("The '{}' pool has no species that match its filters: it will be empty.", pool)
        val picked = candidates.shuffled().take(settings.size.coerceIn(1, MAX_SIZE))
        val offers = picked.mapIndexed { index, species ->
            Offer(pool, index, species.resourceIdentifier.toString(), settings.level.coerceIn(1, 100), settings.shiny, settings.perfectIvs.coerceIn(0, 6))
        }
        AstralRepository.replaceOffers(pool, offers, System.currentTimeMillis())
        return true
    }

    private fun candidates(settings: PoolSettings): List<Species> {
        val only = settings.species.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val never = settings.exclude.map { it.trim().lowercase() }.toSet()
        return PokemonSpecies.species.filter { species ->
            val id = species.resourceIdentifier.toString().lowercase()
            val name = species.name.lowercase()
            when {
                id in never || name in never -> false
                settings.excludeLabels.any { label -> species.labels.any { it.equals(label, true) } } -> false
                only.isNotEmpty() -> id in only || name in only
                settings.labels.isNotEmpty() -> settings.labels.any { label -> species.labels.any { it.equals(label, true) } }
                else -> true
            }
        }
    }

    // ---------------------------------------------------------------- offers

    fun offers(pool: String): List<Offer> = AstralRepository.offers(pool).filter { it.slot in 0 until MAX_SIZE }

    /** A fresh Pokémon for [offer] (used both for the menu icon and for the trade). Null if the species does not exist anymore. */
    fun pokemonFor(offer: Offer): Pokemon? = try {
        val perfect = STATS.shuffled().take(offer.perfectIvs.coerceIn(0, 6)).joinToString("") { " ${it}_iv=31" }
        val pokemon = PokemonProperties.parse("${offer.speciesId} level=${offer.level}$perfect").create()
        if (offer.shiny) pokemon.shiny = true
        pokemon
    } catch (e: Exception) {
        log.warn("Could not create the offer {} of the '{}' pool: {}", offer.speciesId, offer.pool, e.toString())
        null
    }

    /** How many copies are left for everybody (null = unlimited). */
    fun stockLeft(offer: Offer): Int? {
        val settings = pool(offer.pool) ?: return null
        if (settings.stock <= 0) return null
        val at = AstralRepository.rotatedAt(offer.pool) ?: return null
        return (settings.stock - AstralRepository.boughtTotal(offer.pool, offer.slot, at)).coerceAtLeast(0)
    }

    /** How many more times [player] can trade for the offer (null = no limit). */
    fun playerLeft(player: UUID, offer: Offer): Int? {
        val settings = pool(offer.pool) ?: return null
        if (settings.perPlayerLimit <= 0) return null
        val at = AstralRepository.rotatedAt(offer.pool) ?: return null
        return (settings.perPlayerLimit - AstralRepository.boughtBy(player, offer.pool, offer.slot, at)).coerceAtLeast(0)
    }

    fun availability(player: UUID, offer: Offer): Availability = when {
        stockLeft(offer) == 0 -> Availability.SOLD_OUT
        playerLeft(player, offer) == 0 -> Availability.LIMIT_REACHED
        else -> Availability.OPEN
    }

    // ---------------------------------------------------------------- the trade

    fun trade(player: ServerPlayer, pool: String, slot: Int): TradeResult {
        if (!config.enabled) return TradeResult.CLOSED
        val settings = pool(pool)?.takeIf { it.enabled } ?: return TradeResult.GONE
        val offer = offers(pool).firstOrNull { it.slot == slot } ?: return TradeResult.GONE
        when (availability(player.uuid, offer)) {
            Availability.SOLD_OUT -> return TradeResult.SOLD_OUT
            Availability.LIMIT_REACHED -> return TradeResult.LIMIT_REACHED
            Availability.OPEN -> {}
        }
        if (PokemonTools.inBattle(player)) return TradeResult.IN_BATTLE
        val price = settings.price.toLong().coerceAtLeast(0)
        if (!AstralRepository.trySpend(player.uuid, settings.currency, price)) return TradeResult.NOT_ENOUGH
        val pokemon = pokemonFor(offer)
        if (pokemon == null || !PokemonTools.give(player, pokemon)) {
            AstralRepository.add(player.uuid, settings.currency, price)
            return if (pokemon == null) TradeResult.GONE else TradeResult.NO_ROOM
        }
        AstralRepository.rotatedAt(pool)?.let { AstralRepository.recordPurchase(player.uuid, pool, slot, it) }
        return TradeResult.OK
    }

    /** The online player, or the uuid the server remembers for [name]. */
    fun findUuid(server: MinecraftServer, name: String): UUID? =
        server.playerList.getPlayerByName(name)?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id
}
