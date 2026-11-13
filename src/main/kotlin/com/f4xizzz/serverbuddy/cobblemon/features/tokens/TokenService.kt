package com.f4xizzz.serverbuddy.cobblemon.features.tokens

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.f4xizzz.serverbuddy.ServerBuddy
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Texts
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Efeito das fichas num Pokémon (SHINY e GENDER); a Shiny Ball é tratada em [TokenListeners]. */
object TokenService {
    /** Usa a ficha [stack] no Pokémon [entity] do jogador. Mostra o resultado no chat e gasta a ficha se deu certo. */
    fun useOn(player: ServerPlayer, stack: ItemStack, id: String, def: TokenDef, entity: PokemonEntity) {
        val messages = TokensConfigManager.config.messages
        val pokemon = entity.pokemon
        val vars = HashMap<String, Any?>()
        vars["pokemon"] = PokemonVars.name(pokemon)
        vars["token"] = id

        if (pokemon.getOwnerPlayer()?.uuid != player.uuid) {
            Texts.send(player, messages.notOwner, vars)
            return
        }
        if (entity.isBattling) {
            Texts.send(player, messages.inBattle, vars)
            return
        }
        if (def.isBlacklisted(pokemon)) {
            Texts.send(player, messages.blacklisted, vars)
            return
        }

        val applied = when (def.type) {
            TokenType.SHINY -> applyShiny(player, pokemon, vars, messages)
            TokenType.GENDER -> applyGender(player, pokemon, def, vars, messages)
            TokenType.SHINY_BALL -> false
        }
        if (!applied) return

        if (def.consume && !player.isCreative) stack.shrink(1)
        ServerBuddy.LOGGER.info("[PokeTokens] {} used the token '{}' on {}.", player.gameProfile.name, id, pokemon.species.name)
    }

    private fun applyShiny(player: ServerPlayer, pokemon: Pokemon, vars: Map<String, Any?>, messages: TokenMessages): Boolean {
        if (pokemon.shiny) {
            Texts.send(player, messages.alreadyShiny, vars)
            return false
        }
        pokemon.shiny = true
        pokemon.updateAspects()
        Texts.send(player, messages.shinyApplied, vars)
        return true
    }

    private fun applyGender(player: ServerPlayer, pokemon: Pokemon, def: TokenDef, vars: MutableMap<String, Any?>, messages: TokenMessages): Boolean {
        val possible = pokemon.form.possibleGenders
        if (possible.size < 2) {
            Texts.send(player, messages.genderImpossible, vars)
            return false
        }
        val target = when (def.gender.uppercase()) {
            "MALE" -> Gender.MALE
            "FEMALE" -> Gender.FEMALE
            else -> if (pokemon.gender == Gender.MALE) Gender.FEMALE else Gender.MALE
        }
        vars["gender"] = genderName(target)
        if (target !in possible) {
            Texts.send(player, messages.genderImpossible, vars)
            return false
        }
        if (pokemon.gender == target) {
            Texts.send(player, messages.genderSame, vars)
            return false
        }
        pokemon.gender = target
        pokemon.updateAspects()
        Texts.send(player, messages.genderApplied, vars)
        return true
    }

    private fun genderName(gender: Gender): String = when (gender) {
        Gender.MALE -> "<blue>male</blue>"
        Gender.FEMALE -> "<light_purple>female</light_purple>"
        else -> "<gray>genderless</gray>"
    }
}
