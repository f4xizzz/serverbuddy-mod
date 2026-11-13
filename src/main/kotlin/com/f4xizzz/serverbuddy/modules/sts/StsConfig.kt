package com.f4xizzz.serverbuddy.modules.sts

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.MenuButton
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.modules.pokeservices.ConfirmSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PickerSettings
import com.f4xizzz.serverbuddy.modules.pokeservices.PokemonFilter
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/STS/config.json` (only created when Cobblemon is installed). */
@Doc(
    "STS · Sell To Server. Players sell a Pokémon from their party or PC to the server for money (/sts).\n" +
        "\n" +
        "PRICE of a Pokémon:\n" +
        "      (pricing.base + level × pricing.per_level)\n" +
        "      × the best multiplier among its labels (pricing.label_multipliers: legendary, mythical, ultra_beast...)\n" +
        "      × pricing.shiny_multiplier if shiny\n" +
        "      × (1 + IV% × pricing.iv_bonus)           IV% is 0-1: a perfect Pokémon with iv_bonus 1 is worth double\n" +
        "      × the player's best permission bonus (permission_bonuses)\n" +
        "  A species listed in pricing.species_prices uses that fixed price instead of the formula. The result is capped by limit_price.\n" +
        "\n" +
        "The money comes from the economy named in \"economy\" (\"\" = the first installed; \"impactor\" or \"impactor:dollars\"...).\n" +
        "Without an economy mod the module stays idle. Staff: /sts admin reset <player> clears someone's cooldown (serverbuddy.sts.admin, level 2).\n" +
        "Players use /sts (permission serverbuddy.sts.use, on by default)."
)
class StsConfig(
    @Doc("false = the module does nothing.")
    val enabled: Boolean = true,
    @Doc("Which economy pays: \"\" (the first installed), \"impactor\", \"impactor:dollars\"...")
    val economy: String = "",
    @SerializedName("currency_symbol")
    @Doc("Shown before prices in the menus and messages (%price% already has no symbol).")
    val currencySymbol: String = "$",
    @SerializedName("cooldown_seconds")
    @Doc("Seconds a player waits between two sales (0 = no wait).")
    val cooldownSeconds: Int = 30,
    @SerializedName("permission_cooldowns")
    @Doc("A permission → the cooldown in seconds for players who have it (the shortest one wins).")
    val permissionCooldowns: Map<String, Int> = mapOf("serverbuddy.sts.cooldown.vip" to 15),
    @SerializedName("limit_price")
    @Doc("The most a single Pokémon can ever pay.")
    val limitPrice: Double = 100000.0,
    @SerializedName("min_price")
    @Doc("Pokémon worth less than this are refused (\"not worth anything\").")
    val minPrice: Double = 1.0,
    @SerializedName("keep_one_in_party")
    @Doc("true = the last Pokémon of the party cannot be sold while the PC is empty, so nobody is left without one.")
    val keepOneInParty: Boolean = true,
    val pricing: StsPricing = StsPricing(),
    @SerializedName("permission_bonuses")
    @Doc("A permission → a percentage added to every sale (the best one the player has counts).")
    val permissionBonuses: Map<String, StsBonus> = mapOf(
        "serverbuddy.sts.bonus.vip" to StsBonus(100.0, "<gradient:gold:yellow><bold>VIP</bold></gradient>"),
        "serverbuddy.sts.bonus.booster" to StsBonus(50.0, "<light_purple><bold>Booster</bold></light_purple>")
    ),
    @Doc("Pokémon that cannot be sold.")
    val blacklist: PokemonFilter = PokemonFilter(labels = listOf("legendary", "mythical")),
    val release: StsRelease = StsRelease(),
    val menu: PickerSettings = PickerSettings(
        title = "<dark_green><bold>STS <gray>» sell a Pokémon",
        lore = listOf("<gray>Level <white>%level%", "<gray>IVs <white>%ivs%", "", "<gray>Worth: <green>%symbol%%price%", "<yellow>Click to sell")
    ),
    val confirm: StsConfirmMenu = StsConfirmMenu(),
    val messages: StsMessages = StsMessages()
)

class StsPricing(
    @Doc("Starting price of any Pokémon.")
    val base: Double = 100.0,
    @SerializedName("per_level")
    @Doc("Added for every level.")
    val perLevel: Double = 10.0,
    @SerializedName("label_multipliers")
    @Doc("A species label → price multiplier (the highest one that applies is used).")
    val labelMultipliers: Map<String, Double> = mapOf("baby" to 1.5, "ultra_beast" to 4.0, "paradox" to 4.0, "legendary" to 6.0, "mythical" to 8.0),
    @SerializedName("shiny_multiplier")
    val shinyMultiplier: Double = 3.0,
    @SerializedName("iv_bonus")
    @Doc("Extra value for good IVs: price × (1 + IV% × iv_bonus). 0 = IVs do not matter.")
    val ivBonus: Double = 1.0,
    @SerializedName("species_prices")
    @Doc("Fixed prices that replace the formula for a species (before bonuses and the limit).")
    val speciesPrices: Map<String, Double> = mapOf("cobblemon:magikarp" to 25.0)
)

class StsBonus(
    @Doc("Percent added to the sale price.")
    val percent: Double = 0.0,
    @Doc("How the bonus is called in messages (MiniMessage).")
    val label: String = ""
)

class StsRelease(
    @Doc("true = players can also release a Pokémon for a part of its price, with no cooldown.")
    val enabled: Boolean = false,
    @SerializedName("price_percent")
    @Doc("Percent of the price paid when releasing (selling pays 100%).")
    val pricePercent: Double = 25.0
)

class StsConfirmMenu(
    @Doc("Title. Placeholders: the Pokémon ones (%name% %level%...) plus %price%, %symbol%.")
    val title: String = "<dark_green><bold>Sell %name%?",
    val sell: MenuButton = MenuButton(
        slots = listOf(11), hideWhenUnavailable = false,
        icon = ItemSpec("minecraft:lime_stained_glass_pane", name = "<green><bold>Sell", lore = listOf("<gray>You get <green>%symbol%%price%"))
    ),
    val release: MenuButton = MenuButton(
        slots = listOf(22), hideWhenUnavailable = true,
        icon = ItemSpec("minecraft:orange_stained_glass_pane", name = "<gold><bold>Release", lore = listOf("<gray>Set it free for <green>%symbol%%release_price%", "<gray>No cooldown."))
    ),
    val cancel: MenuButton = MenuButton(slots = listOf(15), hideWhenUnavailable = false, icon = ItemSpec("minecraft:red_stained_glass_pane", name = "<red><bold>Cancel")),
    @SerializedName("icon_slot")
    val iconSlot: Int = 13,
    val filler: ItemSpec = ItemSpec("minecraft:gray_stained_glass_pane", name = " ", hideTooltip = true)
) {
    fun asConfirm() = ConfirmSettings(sell, cancel, iconSlot, filler)
}

class StsMessages(
    @Doc("Placeholders: the Pokémon ones plus %price% %symbol% %bonus% (the permission bonus label, empty if none).")
    val sold: String = "<green>You sold <white>%name%</white> for <gold>%symbol%%price%</gold>. %bonus%",
    val released: String = "<green>You released <white>%name%</white> and got <gold>%symbol%%release_price%</gold>.",
    @SerializedName("cooldown")
    @Doc("Placeholders: %time%.")
    val cooldown: String = "<red>You can sell again in <white>%time%</white>.",
    @SerializedName("not_worth")
    val notWorth: String = "<red>That Pokémon is not worth anything to the server.",
    val blacklisted: String = "<red>The server does not buy <white>%name%</white>.",
    @SerializedName("in_battle")
    val inBattle: String = "<red>You cannot sell while in a battle.",
    @SerializedName("last_pokemon")
    val lastPokemon: String = "<red>You cannot sell your last Pokémon.",
    @SerializedName("no_economy")
    val noEconomy: String = "<red>The server has no economy to pay with.",
    @SerializedName("payment_failed")
    val paymentFailed: String = "<red>The payment failed, so your Pokémon was kept.",
    @SerializedName("already_gone")
    val alreadyGone: String = "<red>That Pokémon is no longer in your party or PC.",
    @SerializedName("only_players")
    val onlyPlayers: String = "<red>Only players can use the STS.",
    @SerializedName("price_line")
    @Doc("/sts price. Placeholders: Pokémon ones plus %price% %symbol%.")
    val priceLine: String = "<gray><white>%name%</white> (level %level%) is worth <gold>%symbol%%price%</gold> to the server.",
    @SerializedName("no_pokemon")
    val noPokemon: String = "<red>There is no Pokémon in that party slot.",
    @SerializedName("admin_reset")
    val adminReset: String = "<green>The STS cooldown of <white>%player%</white> was cleared.",
    @SerializedName("unknown_player")
    val unknownPlayer: String = "<red>Unknown player: <white>%player%"
)
