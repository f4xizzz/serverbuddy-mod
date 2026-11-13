package com.f4xizzz.serverbuddy.cobblemon.features.tokens

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.f4xizzz.serverbuddy.ServerBuddy
import com.f4xizzz.serverbuddy.cobblemon.features.PokemonVars
import com.f4xizzz.serverbuddy.core.Texts
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Eventos das fichas:
 * - fichas SHINY/GENDER: clique direito num Pokémon do próprio jogador;
 * - Shiny Ball: a bola lançada recebe uma marca e, se capturar, o Pokémon vira shiny. Se a bola errar e voltar como
 *   item, ela volta como Shiny Ball (o Cobblemon devolveria uma bola comum).
 * Enquanto a ficha está na mão ela não faz mais nada (não é comida, não coloca bloco).
 */
object TokenListeners {
    private const val BALL_TAG = "sb_shiny_ball:"

    private class Pending(val tick: Int, val tokenId: String)
    private class Lost(val level: ServerLevel, val pos: Vec3, val tokenId: String, val ballItem: Item)

    private val pending = HashMap<UUID, Pending>()
    private val lost = ArrayList<Lost>()

    fun register() {
        UseItemCallback.EVENT.register { player, world, hand -> onUseItem(player, world, hand) }
        UseBlockCallback.EVENT.register { player, _, hand, _ -> onUseBlock(player, hand) }
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ -> onUseEntity(player, world, hand, entity) }
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> onEntityLoad(entity) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, world -> onEntityUnload(entity, world) }
        ServerTickEvents.END_SERVER_TICK.register { server -> onTick(server) }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event -> onCaptured(event) }
    }

    // ---------------------------------------------------------------- uso do item

    private fun onUseItem(player: Player, world: Level, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        val id = TokenItems.identify(stack) ?: return InteractionResultHolder.pass(stack)
        val def = TokensConfigManager.config.tokens[id] ?: return InteractionResultHolder.pass(stack)
        if (def.type == TokenType.SHINY_BALL) {
            if (!world.isClientSide && player is ServerPlayer) {
                pending[player.uuid] = Pending(player.server?.tickCount ?: 0, id)
            }
            return InteractionResultHolder.pass(stack)
        }
        return InteractionResultHolder.fail(stack)
    }

    private fun onUseBlock(player: Player, hand: InteractionHand): InteractionResult {
        val id = TokenItems.identify(player.getItemInHand(hand)) ?: return InteractionResult.PASS
        val def = TokensConfigManager.config.tokens[id] ?: return InteractionResult.PASS
        return if (def.type == TokenType.SHINY_BALL) InteractionResult.PASS else InteractionResult.FAIL
    }

    private fun onUseEntity(player: Player, world: Level, hand: InteractionHand, entity: Entity): InteractionResult {
        val stack = player.getItemInHand(hand)
        val id = TokenItems.identify(stack) ?: return InteractionResult.PASS
        val def = TokensConfigManager.config.tokens[id] ?: return InteractionResult.PASS
        if (def.type == TokenType.SHINY_BALL || entity !is PokemonEntity) return InteractionResult.PASS
        if (world.isClientSide || player !is ServerPlayer) return InteractionResult.SUCCESS
        TokenService.useOn(player, stack, id, def, entity)
        return InteractionResult.SUCCESS
    }

    // ---------------------------------------------------------------- Shiny Ball

    /** A bola que acabou de ser lançada por quem tinha uma Shiny Ball na mão ganha a marca. */
    private fun onEntityLoad(entity: Entity) {
        if (entity !is EmptyPokeBallEntity) return
        val owner = entity.owner as? ServerPlayer ?: return
        val mark = pending.remove(owner.uuid) ?: return
        val now = owner.server?.tickCount ?: return
        if (now - mark.tick > 2) return
        entity.addTag(BALL_TAG + mark.tokenId)
    }

    /** Bola marcada que sumiu sem capturar nem escapar: o Cobblemon vai soltar uma bola comum; trocamos no fim do tick. */
    private fun onEntityUnload(entity: Entity, world: ServerLevel) {
        if (entity !is EmptyPokeBallEntity) return
        val tag = entity.tags.firstOrNull { it.startsWith(BALL_TAG) } ?: return
        if (entity.captureState != EmptyPokeBallEntity.CaptureState.NOT) return
        lost.add(Lost(world, entity.position(), tag.removePrefix(BALL_TAG), entity.pokeBall.item()))
    }

    private fun onTick(server: MinecraftServer) {
        if (lost.isEmpty()) return
        val batch = ArrayList(lost)
        lost.clear()
        for (entry in batch) {
            val def = TokensConfigManager.config.tokens[entry.tokenId]?.takeIf { it.type == TokenType.SHINY_BALL } ?: continue
            val area = AABB(entry.pos, entry.pos).inflate(1.5)
            val drop = entry.level.getEntitiesOfClass(ItemEntity::class.java, area) { e ->
                e.age <= 5 && e.item.item == entry.ballItem && TokenItems.identify(e.item) == null
            }.firstOrNull() ?: continue
            val replacement = TokenItems.create(entry.tokenId, def, drop.item.count)
            if (!replacement.isEmpty) drop.item = replacement
        }
    }

    private fun onCaptured(event: PokemonCapturedEvent) {
        val tag = event.pokeBallEntity.tags.firstOrNull { it.startsWith(BALL_TAG) } ?: return
        val id = tag.removePrefix(BALL_TAG)
        val def = TokensConfigManager.config.tokens[id]?.takeIf { it.type == TokenType.SHINY_BALL } ?: return
        val pokemon = event.pokemon
        val player = event.player
        val messages = TokensConfigManager.config.messages
        val vars = mapOf("pokemon" to PokemonVars.name(pokemon), "token" to id)

        if (def.isBlacklisted(pokemon)) {
            Texts.send(player, messages.shinyBallBlacklisted, vars)
            return
        }
        if (pokemon.shiny) {
            Texts.send(player, messages.shinyBallAlready, vars)
            return
        }
        pokemon.shiny = true
        pokemon.updateAspects()
        Texts.send(player, messages.shinyBallCaptured, vars)
        ServerBuddy.LOGGER.info("[PokeTokens] {} capturou um {} shiny com a '{}'.", player.gameProfile.name, pokemon.species.name, id)
    }
}
