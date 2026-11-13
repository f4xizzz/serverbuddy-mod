package com.f4xizzz.serverbuddy.cobblemon.features.tokens

import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** O que a ficha faz. */
enum class TokenType {
    /** Clique direito num Pokémon seu: ele vira shiny. */
    SHINY,

    /** Clique direito num Pokémon seu: muda o sexo (ver `gender` em [TokenDef]). */
    GENDER,

    /** Uma bola do Cobblemon: se ela capturar um Pokémon, ele vira shiny. */
    SHINY_BALL
}

class TokenBlacklist(
    @Doc("Species that do not accept the token. Example: [\"mewtwo\", \"mew\"].")
    val species: List<String> = emptyList(),
    @Doc("Cobblemon labels that do not accept the token. Example: [\"legendary\", \"mythical\", \"ultra_beast\", \"paradox\"].")
    val labels: List<String> = emptyList()
)

/**
 * Uma ficha. O `item` é editável por completo (item, nome, descrição, custom_model_data, stackable, hide_tooltip,
 * hide_additional_tooltip...); para SHINY_BALL use o id de uma bola do Cobblemon (ex.: `cobblemon:poke_ball`).
 */
class TokenDef(
    @Doc("What the token does: SHINY (the Pokémon becomes shiny), GENDER (changes the gender) or SHINY_BALL (a ball that makes the Pokémon shiny if it is caught).")
    val type: TokenType = TokenType.SHINY,
    @Doc("The token item, fully editable. For SHINY_BALL use the id of a Cobblemon ball (cobblemon:poke_ball, cobblemon:master_ball...).")
    val item: ItemSpec = ItemSpec(),
    @Doc("GENDER only: MALE, FEMALE or SWITCH (flips to the other gender).")
    val gender: String = "SWITCH",
    @Doc("true = spends one token per use (the Shiny Ball always follows the normal ball rule).")
    val consume: Boolean = true,
    @Doc("Pokémon that do not accept this token.")
    val blacklist: TokenBlacklist = TokenBlacklist()
) {
    fun isBlacklisted(pokemon: Pokemon): Boolean {
        val species = pokemon.species
        if (blacklist.species.any { it.equals(species.name, ignoreCase = true) }) return true
        return blacklist.labels.any { label -> species.labels.any { it.equals(label, ignoreCase = true) } }
    }
}

class TokenMessages(
    val given: String = "<green>You gave <yellow>%amount%x</yellow> %token_name% <green>to <yellow>%player%</yellow>.",
    val received: String = "<green>You received <yellow>%amount%x</yellow> %token_name%<green>.",
    @SerializedName("unknown_token")
    val unknownToken: String = "<red>The token '%token%' does not exist. Tokens: <yellow>%list%",
    @SerializedName("not_owner")
    val notOwner: String = "<red>You can only use the token on one of your own Pokémon.",
    @SerializedName("in_battle")
    val inBattle: String = "<red>You can't use the token during a battle.",
    val blacklisted: String = "<red>%pokemon% can't receive this token.",
    @SerializedName("shiny_applied")
    val shinyApplied: String = "<green>%pokemon% is now <gold>shiny</gold>!",
    @SerializedName("already_shiny")
    val alreadyShiny: String = "<red>%pokemon% is already shiny.",
    @SerializedName("gender_applied")
    val genderApplied: String = "<green>%pokemon%'s gender is now %gender%<green>.",
    @SerializedName("gender_same")
    val genderSame: String = "<red>%pokemon% is already %gender%<red>.",
    @SerializedName("gender_impossible")
    val genderImpossible: String = "<red>%pokemon% can't change gender.",
    @SerializedName("shiny_ball_captured")
    val shinyBallCaptured: String = "<gold>A Shiny Ball transformou %pokemon% em shiny!",
    @SerializedName("shiny_ball_already")
    val shinyBallAlready: String = "<yellow>%pokemon% was already shiny: the Shiny Ball changed nothing.",
    @SerializedName("shiny_ball_blacklisted")
    val shinyBallBlacklisted: String = "<yellow>%pokemon% does not accept the Shiny Ball: it was caught, but it did not become shiny."
)

/** `config/ServerBuddy/PokeTokens/config.json`: as fichas e as mensagens (MiniMessage). */
@Doc(
    "Pokémon tokens · /poketoken give <player> <token> [amount]\n" +
        "Each token is an item that changes a Pokémon (right-click your Pokémon) or a special ball.\n" +
        "You can create as many tokens as you like in \"tokens\" (the key is the name used in the command).\n" +
        "Reload with /serverbuddy reload or /poketoken reload."
)
class TokensConfig(
    @Doc("Messages (MiniMessage). Values: %pokemon%, %token%, %token_name%, %amount%, %player%, %gender%, %list%.")
    val messages: TokenMessages = TokenMessages(),
    @Doc("The tokens. Tip: copy a whole token, change the key and the item to create another.")
    val tokens: Map<String, TokenDef> = defaultTokens()
) {
    companion object {
        fun defaultTokens(): Map<String, TokenDef> = linkedMapOf(
            "shiny" to TokenDef(
                type = TokenType.SHINY,
                item = ItemSpec(
                    item = "minecraft:nether_star",
                    name = "<gradient:#f6d365:#fda085><bold>Shiny Token</bold></gradient>",
                    lore = listOf(
                        "<gray>Right-click one of",
                        "<gray>your Pokémon to make it <gold>shiny</gold>.",
                        "",
                        "<dark_gray>Single use"
                    ),
                    hideAdditionalTooltip = true,
                    glint = true
                )
            ),
            "shiny_ball" to TokenDef(
                type = TokenType.SHINY_BALL,
                item = ItemSpec(
                    item = "cobblemon:poke_ball",
                    name = "<gradient:#f6d365:#fda085><bold>Shiny Ball</bold></gradient>",
                    lore = listOf(
                        "<gray>If this ball catches a Pokémon,",
                        "<gray>it becomes <gold>shiny</gold>.",
                        "",
                        "<dark_gray>Single use"
                    ),
                    hideAdditionalTooltip = true,
                    glint = true
                )
            ),
            "gender" to TokenDef(
                type = TokenType.GENDER,
                gender = "SWITCH",
                item = ItemSpec(
                    item = "minecraft:amethyst_shard",
                    name = "<gradient:#a18cd1:#fbc2eb><bold>Gender Token</bold></gradient>",
                    lore = listOf(
                        "<gray>Right-click one of",
                        "<gray>your Pokémon to switch its gender",
                        "<gray>(male <-> female).",
                        "",
                        "<dark_gray>Single use"
                    ),
                    hideAdditionalTooltip = true,
                    glint = true
                )
            ),
            "gender_male" to TokenDef(
                type = TokenType.GENDER,
                gender = "MALE",
                item = ItemSpec(
                    item = "minecraft:lapis_lazuli",
                    name = "<blue><bold>Gender Token: Male</bold>",
                    lore = listOf(
                        "<gray>Right-click one of",
                        "<gray>your Pokémon to make it <blue>male</blue>.",
                        "",
                        "<dark_gray>Single use"
                    ),
                    hideAdditionalTooltip = true
                )
            ),
            "gender_female" to TokenDef(
                type = TokenType.GENDER,
                gender = "FEMALE",
                item = ItemSpec(
                    item = "minecraft:pink_dye",
                    name = "<light_purple><bold>Gender Token: Female</bold>",
                    lore = listOf(
                        "<gray>Right-click one of",
                        "<gray>your Pokémon to make it <light_purple>female</light_purple>.",
                        "",
                        "<dark_gray>Single use"
                    ),
                    hideAdditionalTooltip = true
                )
            )
        )
    }
}

object TokensConfigManager {
    @Volatile
    var config: TokensConfig = TokensConfig()
        private set

    fun load() {
        config = ConfigFiles.load("PokeTokens", "config.json", TokensConfig::class.java) { TokensConfig() }
    }
}
