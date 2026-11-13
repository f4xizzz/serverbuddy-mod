package com.f4xizzz.serverbuddy.shout

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Cooldowns
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

@Doc("Chat shouts: /itemshout, /pokeshout and /pokeshoutall. Show the held item or party Pokémon in chat, with hover details. MiniMessage formatting. Players with serverbuddy.shout.bypass skip cooldowns.")
class ShoutConfig(
    @SerializedName("item_shout")
    @Doc("/itemshout: show the held item.")
    val itemShout: ItemShoutConfig = ItemShoutConfig(),
    @SerializedName("poke_shout")
    @Doc("/pokeshout and /pokeshoutall: show party Pokémon (requires Cobblemon).")
    val pokeShout: PokeShoutConfig = PokeShoutConfig()
)

class ItemShoutConfig(
    @Doc("Enable or disable the command.")
    val enabled: Boolean = true,
    @SerializedName("cooldown_seconds")
    @Doc("Cooldown in seconds between uses (0 = no cooldown).")
    val cooldownSeconds: Int = 30,
    @Doc("Chat message. Values: %player%, %item% (item name with hover tooltip), %count% (quantity when greater than 1).")
    val format: String = "<dark_gray>[<aqua>Item</aqua>] <white>%player% <gray>shows: %item%%count%",
    @SerializedName("count_format")
    @Doc("Text inserted into %count% when holding more than one item. Value: %amount%.")
    val countFormat: String = " <dark_gray>x%amount%",
    @SerializedName("empty_hand")
    @Doc("Notice when the hand is empty.")
    val emptyHand: String = "<red>You must hold an item in your hand.",
    @Doc("Cooldown notice. Value: %seconds%.")
    val cooldown: String = "<red>Wait <yellow>%seconds%s</yellow> before using /itemshout again."
)

class PokeShoutConfig(
    @Doc("Enable or disable Pokémon commands.")
    val enabled: Boolean = true,
    @SerializedName("cooldown_seconds")
    @Doc("Cooldown in seconds (shared by /pokeshout and /pokeshoutall).")
    val cooldownSeconds: Int = 60,
    @Doc("/pokeshout message. Values: %player%, %pokemon% (label text with hover details).")
    val format: String = "<dark_gray>[<gold>Pokémon</gold>] <white>%player% <gray>shows: %pokemon%",
    @SerializedName("format_all")
    @Doc("/pokeshoutall message. Values: %player%, %pokemons% (list separated by separator).")
    val formatAll: String = "<dark_gray>[<gold>Party</gold>] <white>%player% <gray>shows their party: %pokemons%",
    @Doc("Pokémon label in chat. Values: %name% %species% %level% %shiny% %gender% %nature% %ability% %held_item% %ball% %ivs% %evs% %move1% to %move4% %health% %friendship% %trainer%.")
    val label: String = "%shiny%<yellow>%name% <gray>Lv.%level%",
    @Doc("Separator between Pokémon in /pokeshoutall.")
    val separator: String = "<gray>, ",
    @Doc("Hover lines for a Pokémon (same values as label).")
    val hover: List<String> = listOf(
        "%shiny%<yellow><bold>%name%</bold> <gray>Lv.%level% %gender%",
        "<gray>Nature: <white>%nature%",
        "<gray>Ability: <white>%ability%",
        "<gray>Item: <white>%held_item%",
        "<gray>IVs: <white>%ivs%",
        "<gray>EVs: <white>%evs%",
        "<gray>Moves: <white>%move1%<gray>, <white>%move2%<gray>, <white>%move3%<gray>, <white>%move4%"
    ),
    @SerializedName("shiny_prefix")
    @Doc("Text inserted into %shiny% when the Pokémon is shiny.")
    val shinyPrefix: String = "<gold>★ </gold>",
    @SerializedName("no_pokemon")
    @Doc("Notice for an empty slot. Value: %slot%.")
    val noPokemon: String = "<red>No Pokémon in slot %slot%.",
    @SerializedName("empty_party")
    @Doc("Notice for an empty party.")
    val emptyParty: String = "<red>Your party is empty.",
    @Doc("Cooldown notice. Value: %seconds%.")
    val cooldown: String = "<red>Wait <yellow>%seconds%s</yellow> before showing your Pokémon again."
)

/** Módulo Shout: `/itemshout` (e, com o Cobblemon, `/pokeshout` e `/pokeshoutall`). */
class ShoutModule : SbModule {
    override val id = "shout"
    override val folder = "Shout"
    override val legacyFolders = emptyList<String>()

    companion object {
        @Volatile
        var config: ShoutConfig = ShoutConfig()
            private set

        fun load() {
            config = ConfigFiles.load("Shout", "config.json", ShoutConfig::class.java) { ShoutConfig() }
        }

        /** 0 = liberado (e marca o uso); senão, os segundos que faltam. Quem tem `serverbuddy.shout.bypass` nunca espera. */
        fun cooldown(player: net.minecraft.server.level.ServerPlayer, key: String, seconds: Int): Long {
            if (SbPermissions.check(player, "serverbuddy.shout.bypass", 4)) return 0
            return Cooldowns.use(key, player.uuid, seconds)
        }
    }

    override val description = "Show the held item in chat (/itemshout) and, with Cobblemon, party Pokémon (/pokeshout)."

    override fun init() {
        load()
        SbCommands.onRegister { dispatcher, _ -> registerItemShout(dispatcher) }
    }

    override fun reload() {
        load()
    }

    private fun registerItemShout(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "itemshout") { name ->
            Commands.literal(name)
                .requires(SbPermissions.require("serverbuddy.itemshout.use", 2))
                .executes { itemShout(it) }
        }
    }

    private fun itemShout(ctx: CommandContext<CommandSourceStack>): Int {
        val cfg = config.itemShout
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>This command can only be used by a player.")
            return 0
        }
        if (!cfg.enabled) return 0
        val stack = player.mainHandItem
        if (stack.isEmpty) {
            Texts.send(player, cfg.emptyHand)
            return 0
        }
        val wait = cooldown(player, "itemshout", cfg.cooldownSeconds)
        if (wait > 0) {
            Texts.send(player, cfg.cooldown, mapOf("seconds" to wait))
            return 0
        }
        val count = if (stack.count > 1) Texts.fill(cfg.countFormat, mapOf("amount" to stack.count)) else ""
        val message = Texts.render(
            cfg.format,
            mapOf("player" to player.gameProfile.name, "count" to count),
            mapOf("item" to stack.displayName)
        )
        ctx.source.server.playerList.broadcastSystemMessage(message, false)
        return 1
    }
}
