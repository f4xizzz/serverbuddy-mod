package com.f4xizzz.serverbuddy.modules.gts

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.Webhooks
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonTools
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.UUID

/** Listing, buying, cancelling, expiring and paying. Cobblemon only (the market sells Pokémon and items). */
object GtsService {
    const val USE = "serverbuddy.gts.use"
    const val ADMIN = "serverbuddy.gts.admin"

    @Volatile var config = GtsConfig()
        private set

    fun load(newConfig: GtsConfig) {
        config = newConfig
    }

    fun economyReady(): Boolean = Economy.available(config.economy)

    private var ticks = 0L

    // ---------------------------------------------------------------- items as text

    fun itemToText(stack: ItemStack): String? = ItemStack.CODEC.encodeStart(ServerHolder.nbtOps(), stack).result().map { it.toString() }.orElse(null)

    fun itemFromText(text: String): ItemStack? = try {
        ItemStack.CODEC.parse(ServerHolder.nbtOps(), TagParser.parseTag(text)).result().orElse(null)
    } catch (e: Exception) {
        null
    }

    private fun itemBlocked(stack: ItemStack): Boolean {
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        return config.itemBlacklist.any { raw ->
            val rule = raw.trim()
            when {
                rule.startsWith("#") -> ResourceLocation.tryParse(rule.substring(1))?.let { stack.`is`(TagKey.create<Item>(Registries.ITEM, it)) } ?: false
                rule.endsWith(":*") -> id.namespace == rule.removeSuffix(":*")
                else -> id.toString() == rule
            }
        }
    }

    // ---------------------------------------------------------------- rules

    fun maxListings(player: ServerPlayer): Int =
        maxOf(config.maxListings, config.listingTiers.entries.filter { SbPermissions.check(player, it.key, false) }.maxOfOrNull { it.value } ?: 0)

    fun minimumFor(pokemon: Pokemon): Double {
        val m = config.pokemonMinimums
        var min = m.base
        if (pokemon.shiny) min += m.shiny
        if (PokemonTools.isLegendaryLike(pokemon)) min += m.legendary
        if (hasHiddenAbility(pokemon)) min += m.hiddenAbility
        val perfect = Stats.PERMANENT.count { pokemon.ivs.getOrDefault(it) == 31 }
        min += m.perfectIvs.entries.filter { (it.key.toIntOrNull() ?: 99) <= perfect }.maxOfOrNull { it.value } ?: 0.0
        m.species.entries.firstOrNull { normalize(it.key) == PokemonTools.speciesId(pokemon).lowercase(Locale.ROOT) }?.let { min = maxOf(min, it.value) }
        return min
    }

    private fun normalize(text: String): String {
        val t = text.trim().lowercase(Locale.ROOT)
        return if (':' in t) t else "cobblemon:$t"
    }

    private fun hasHiddenAbility(pokemon: Pokemon): Boolean = try {
        pokemon.form.abilities.any { it.template == pokemon.ability.template && it.priority == Priority.LOW }
    } catch (e: Throwable) {
        false
    }

    private fun price(value: Double) = Economy.format(value)

    private fun base(vars: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf("symbol" to config.currencySymbol) + vars

    /** Shared checks before a listing is created. Returns a ready message, or null. */
    private fun checkListing(player: ServerPlayer, price: Double, minimum: Double, what: String): String? {
        val messages = config.messages
        if (!economyReady()) return messages.noEconomy
        if (PokemonTools.inBattle(player)) return messages.inBattle
        val timeout = GtsRepository.timeoutUntil(player.uuid)
        if (timeout > System.currentTimeMillis()) return Texts.fill(messages.timedOut, mapOf("time" to Durations.format(timeout - System.currentTimeMillis())))
        if (price < config.minPrice || price > config.maxPrice) return Texts.fill(messages.priceRange, base(mapOf("min" to price(config.minPrice), "max" to price(config.maxPrice))))
        if (price < minimum) return Texts.fill(messages.priceTooLow, base(mapOf("min" to price(minimum), "what" to what)))
        val max = maxListings(player)
        if (GtsRepository.count(ListingStatus.ACTIVE, null, player.uuid) >= max) return Texts.fill(messages.tooMany, mapOf("max" to max))
        return null
    }

    private fun chargeFee(player: ServerPlayer): Boolean {
        val fee = config.listingFee
        if (fee <= 0) return true
        if (Economy.withdraw(player, fee, config.economy)) return true
        Texts.send(player, config.messages.feeFailed, base(mapOf("fee" to price(fee))))
        return false
    }

    // ---------------------------------------------------------------- listing

    fun listPokemon(player: ServerPlayer, slot: Int, price: Double): Boolean {
        val messages = config.messages
        if (!config.allowPokemon) {
            Texts.send(player, messages.disabledKind)
            return false
        }
        val pokemon = Cobblemon.storage.getParty(player).get(slot - 1)
        if (pokemon == null) {
            Texts.send(player, messages.noPokemon)
            return false
        }
        val name = plainName(pokemon)
        if (config.pokemonBlacklist.reason(pokemon) != null) {
            Texts.send(player, messages.blacklisted, mapOf("what" to name))
            return false
        }
        if (config.keepOneInParty && PokemonTools.partySize(player) <= 1) {
            val pcEmpty = Cobblemon.storage.getPC(player).boxes.all { box -> (0 until 30).all { box.get(it) == null } }
            if (pcEmpty) {
                Texts.send(player, messages.lastPokemon)
                return false
            }
        }
        checkListing(player, price, minimumFor(pokemon), name)?.let {
            Texts.send(player, it)
            return false
        }
        if (!chargeFee(player)) return false
        val json = PokemonTools.toJson(pokemon, player)
        if (!PokemonTools.take(player, pokemon)) return false
        return create(player, ListingKind.POKEMON, price, json, name + " (Lv. ${pokemon.level})", pokemon, null)
    }

    fun listItem(player: ServerPlayer, price: Double, amount: Int?): Boolean {
        val messages = config.messages
        if (!config.allowItems) {
            Texts.send(player, messages.disabledKind)
            return false
        }
        val held = player.mainHandItem
        if (held.isEmpty) {
            Texts.send(player, messages.noItem)
            return false
        }
        val count = (amount ?: held.count).coerceIn(1, held.count)
        val name = held.hoverName.string
        if (itemBlocked(held)) {
            Texts.send(player, messages.blacklisted, mapOf("what" to name))
            return false
        }
        val perItem = config.itemMinimums.entries.firstOrNull { it.key.equals(BuiltInRegistries.ITEM.getKey(held.item).toString(), true) }?.value ?: 0.0
        checkListing(player, price, perItem * count, "$count× $name")?.let {
            Texts.send(player, it)
            return false
        }
        val sample = held.copyWithCount(count)
        val text = itemToText(sample) ?: return false
        if (!chargeFee(player)) return false
        held.shrink(count)
        return create(player, ListingKind.ITEM, price, text, "$count× $name", null, sample)
    }

    private fun create(player: ServerPlayer, kind: ListingKind, price: Double, payload: String, label: String, pokemon: Pokemon?, item: ItemStack?): Boolean {
        val now = System.currentTimeMillis()
        val listing = Listing(UUID.randomUUID().toString(), player.uuid, player.gameProfile.name, kind, price, now, now + config.listingHours * 3_600_000L, ListingStatus.ACTIVE, payload, label)
        if (!GtsRepository.add(listing)) {
            // could not save: give the thing back so nothing is lost
            if (pokemon != null) PokemonTools.give(player, pokemon) else if (item != null) RewardService.giveItem(player, item, "gts")
            return false
        }
        Texts.send(player, config.messages.listed, base(mapOf("what" to label, "price" to price(price), "id" to listing.id.take(8))))
        return true
    }

    private fun plainName(pokemon: Pokemon): String = pokemon.nickname?.string?.takeIf { it.isNotBlank() } ?: pokemon.species.name

    // ---------------------------------------------------------------- buying

    fun buy(player: ServerPlayer, id: String): Boolean {
        val messages = config.messages
        val listing = GtsRepository.get(id)
        if (listing == null || listing.status != ListingStatus.ACTIVE || listing.expires < System.currentTimeMillis()) {
            Texts.send(player, messages.gone)
            return false
        }
        if (listing.sellerUuid == player.uuid) {
            Texts.send(player, messages.ownListing)
            return false
        }
        if (!economyReady()) {
            Texts.send(player, messages.noEconomy)
            return false
        }
        if (PokemonTools.inBattle(player)) {
            Texts.send(player, messages.inBattle)
            return false
        }
        if (Economy.balance(player, config.economy)?.let { it < listing.price } != false) {
            Texts.send(player, messages.notEnoughMoney, base(mapOf("price" to price(listing.price))))
            return false
        }
        // decode first: a broken listing must not take the buyer's money
        val pokemon = if (listing.kind == ListingKind.POKEMON) PokemonTools.fromJson(listing.payload, player) else null
        val item = if (listing.kind == ListingKind.ITEM) itemFromText(listing.payload) else null
        if (pokemon == null && item == null) {
            Texts.send(player, messages.gone)
            return false
        }
        if (pokemon != null && Cobblemon.storage.getParty(player).occupied() >= 6 && Cobblemon.storage.getPC(player).boxes.all { box -> (0 until 30).all { box.get(it) != null } }) {
            Texts.send(player, messages.noRoom)
            return false
        }
        if (!GtsRepository.markSold(id, player.uuid, player.gameProfile.name)) {
            Texts.send(player, messages.gone)
            return false
        }
        if (!Economy.withdraw(player, listing.price, config.economy)) {
            GtsRepository.move(id, ListingStatus.SOLD, ListingStatus.ACTIVE)
            Texts.send(player, messages.notEnoughMoney, base(mapOf("price" to price(listing.price))))
            return false
        }
        if (pokemon != null) PokemonTools.give(player, pokemon) else if (item != null) RewardService.giveItem(player, item, "gts")

        val tax = Math.floor(listing.price * config.saleTaxPercent / 100.0 * 100.0) / 100.0
        val received = listing.price - tax
        val vars = base(mapOf("what" to listing.label, "price" to price(listing.price), "seller" to Texts.escape(listing.sellerName), "buyer" to Texts.escape(player.gameProfile.name), "tax" to price(tax), "received" to price(received)))
        Texts.send(player, messages.bought, vars)
        paySeller(player.server, listing, received, vars)
        val hook = config.webhook
        if (listing.price >= hook.minPrice) {
            Webhooks.post(hook.settings.resolve(), Texts.fill(hook.message, mapOf("seller" to listing.sellerName, "buyer" to player.gameProfile.name, "what" to listing.label, "price" to config.currencySymbol + price(listing.price))))
        }
        return true
    }

    private fun paySeller(server: MinecraftServer, listing: Listing, amount: Double, vars: Map<String, Any?>) {
        val seller = server.playerList.getPlayer(listing.sellerUuid)
        if (seller != null && Economy.deposit(seller, amount, config.economy)) {
            Texts.send(seller, config.messages.sold, vars)
        } else {
            GtsRepository.addPayout(listing.sellerUuid, amount)
        }
    }

    // ---------------------------------------------------------------- cancelling and returning

    fun cancel(player: ServerPlayer, id: String): Boolean {
        val listing = GtsRepository.get(id)
        if (listing == null || listing.sellerUuid != player.uuid || !GtsRepository.move(id, ListingStatus.ACTIVE, ListingStatus.CANCELLED)) {
            Texts.send(player, config.messages.gone)
            return false
        }
        Texts.send(player, config.messages.cancelled, mapOf("what" to listing.label))
        return true
    }

    /** Gives an expired or cancelled listing back to its owner. */
    fun takeBack(player: ServerPlayer, id: String): Boolean {
        val listing = GtsRepository.get(id)
        if (listing == null || listing.sellerUuid != player.uuid || listing.status !in setOf(ListingStatus.EXPIRED, ListingStatus.CANCELLED)) {
            Texts.send(player, config.messages.gone)
            return false
        }
        val pokemon = if (listing.kind == ListingKind.POKEMON) PokemonTools.fromJson(listing.payload, player) else null
        val item = if (listing.kind == ListingKind.ITEM) itemFromText(listing.payload) else null
        if (pokemon == null && item == null) return false
        if (!GtsRepository.move(id, listing.status, ListingStatus.RETURNED)) return false
        if (pokemon != null) {
            if (!PokemonTools.give(player, pokemon)) {
                GtsRepository.move(id, ListingStatus.RETURNED, listing.status)
                Texts.send(player, config.messages.noRoom)
                return false
            }
        } else if (item != null) {
            RewardService.giveItem(player, item, "gts")
        }
        Texts.send(player, config.messages.returned, mapOf("what" to listing.label))
        return true
    }

    // ---------------------------------------------------------------- time, joining, moderation

    fun tick(server: MinecraftServer) {
        if (++ticks % 1200L != 0L) return
        GtsRepository.expireDue(System.currentTimeMillis())
    }

    /** Pays sales that happened while [player] was offline and reminds about returned listings. */
    fun onJoin(player: ServerPlayer) {
        val amounts = GtsRepository.takePayouts(player.uuid)
        if (amounts.isNotEmpty()) {
            val total = amounts.sum()
            if (Economy.deposit(player, total, config.economy)) {
                Texts.send(player, config.messages.soldOffline, base(mapOf("count" to amounts.size, "amount" to price(total))))
            } else {
                for (amount in amounts) GtsRepository.addPayout(player.uuid, amount)
            }
        }
        val returned = GtsRepository.returnableCount(player.uuid)
        if (returned > 0) Texts.send(player, config.messages.expiredNotice, mapOf("count" to returned))
    }

    /** Staff: pulls an active listing off the market (it goes to its owner's Returned tab). */
    fun adminRemove(idOrPrefix: String): Boolean {
        val listing = GtsRepository.page(ListingStatus.ACTIVE, null, null, 0, 1000).firstOrNull { it.id == idOrPrefix || it.id.startsWith(idOrPrefix) } ?: return false
        return GtsRepository.move(listing.id, ListingStatus.ACTIVE, ListingStatus.CANCELLED)
    }

    // ---------------------------------------------------------------- display

    fun lines(listing: Listing, viewer: ServerPlayer, kind: String): List<String> {
        val template = when (kind) {
            "own" -> config.menu.ownLore
            "returned" -> config.menu.returnedLore
            else -> config.menu.listingLore
        }
        val vars = base(mapOf("seller" to Texts.escape(listing.sellerName), "price" to price(listing.price), "time" to Durations.format((listing.expires - System.currentTimeMillis()).coerceAtLeast(0)), "id" to listing.id.take(8)))
        return template.map { Texts.fill(it, vars) }
    }

    /** The item that represents a listing in the menu. */
    fun icon(listing: Listing, viewer: ServerPlayer, kind: String): ItemStack? {
        val menu = config.menu
        val extra = lines(listing, viewer, kind)
        val stack: ItemStack
        if (listing.kind == ListingKind.POKEMON) {
            val pokemon = PokemonTools.fromJson(listing.payload, viewer) ?: return null
            stack = com.cobblemon.mod.common.item.PokemonItem.from(pokemon)
            val vars = PokemonVars.of(pokemon, menu.shinyPrefix)
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Texts.render(menu.pokemonName, vars))
            stack.set(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore((menu.pokemonLore + extra).flatMap { Texts.fill(it, vars).split("\n") }.map { Texts.parse(it) }))
        } else {
            stack = itemFromText(listing.payload) ?: return null
            val existing = stack.get(net.minecraft.core.component.DataComponents.LORE)?.lines().orEmpty()
            stack.set(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore(existing + extra.map { Texts.parse(it) }))
        }
        return stack
    }
}
