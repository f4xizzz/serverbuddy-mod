package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemFactory;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemTagTicker;
import java.util.List;
import java.util.UUID;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class BossInteractionHandler {
   private BossInteractionHandler() {
   }

   public static void register() {
      UseEntityCallback.EVENT.register(BossInteractionHandler::onUseEntity);
      CobblemonEvents.BATTLE_STARTED_PRE.subscribe(BossInteractionHandler::onBattleStartedPre);
   }

   private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
      if (hitResult != null) {
         return InteractionResult.PASS;
      } else if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else if (world.isClientSide) {
         return InteractionResult.PASS;
      } else if (player instanceof ServerPlayer) {
         ServerPlayer serverPlayer = (ServerPlayer)player;
         if (entity instanceof PokemonEntity) {
            PokemonEntity pokemonEntity = (PokemonEntity)entity;
            BossDefinition boss = BossEntityTracker.getBoss(entity.getUUID());
            if (boss == null) {
               return InteractionResult.PASS;
            } else if (BossBattleManager.findActiveBattle(serverPlayer.getUUID(), boss.id) != null) {
               return InteractionResult.PASS;
            } else {
               BossDebug.log("[Boss] {} clicou no boss '{}' (entityUuid={}).", serverPlayer.getName().getString(), boss.id, entity.getUUID());
               if (!validate(boss, serverPlayer, pokemonEntity)) {
                  return InteractionResult.FAIL;
               } else {
                  PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(serverPlayer);
                  float fleeDistance = -1.0F;
                  BossDebug.log("[Boss] '{}': starting BattleBuilder.pve() — species={}, level={}, hp={}/{}, pos=({}, {}, {}) world={}, fleeDistance={}.", boss.id, boss.speciesId, boss.level, pokemonEntity.getPokemon().getCurrentHealth(), pokemonEntity.getPokemon().getMaxHealth(), entity.getX(), entity.getY(), entity.getZ(), boss.world, fleeDistance);
                  BattleStartResult startResult = BattleBuilder.INSTANCE.pve(serverPlayer, pokemonEntity, UUID.randomUUID(), BattleFormat.Companion.getGEN_9_SINGLES(), false, false, fleeDistance, party);
                  startResult.ifSuccessful((battle) -> {
                     BossDebug.log("[Boss] '{}': battle created successfully, battleId={}.", boss.id, battle.getBattleId());
                     return Unit.INSTANCE;
                  });
                  startResult.ifErrored((errored) -> {
                     ServerBuddy.LOGGER.warn("[Boss] '{}': BattleBuilder.pve() failed to create battle! generalErrors={}", boss.id, errored.getGeneralErrors());
                     return Unit.INSTANCE;
                  });
                  return InteractionResult.SUCCESS;
               }
            }
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   private static void onBattleStartedPre(BattleStartedEvent.Pre event) {
      PokemonBattle battle = event.getBattle();

      for(BattleActor actor : battle.getActors()) {
         if (actor instanceof PokemonBattleActor pokemonActor) {
            PokemonEntity wildEntity = pokemonActor.getEntity();
            BossDefinition boss = BossEntityTracker.getBoss(wildEntity.getUUID());
            if (boss != null) {
               List<ServerPlayer> players = battle.getPlayers();
               if (players.isEmpty()) {
                  return;
               }

               ServerPlayer player = (ServerPlayer)players.get(0);
               if (!validate(boss, player, wildEntity)) {
                  BossDebug.log("[Boss] '{}': BATTLE_STARTED_PRE cancelled — player '{}' did not pass the restrictions outside the normal click (interaction wheel?).", boss.id, player.getName().getString());
                  event.cancel();
               }

               return;
            }
         }
      }

   }

   private static boolean validate(BossDefinition boss, ServerPlayer player, PokemonEntity pokemonEntity) {
      long cooldownRemaining = BossCooldownManager.remainingSeconds(player.getUUID(), boss);
      if (cooldownRemaining > 0L) {
         BossDebug.log("[Boss] '{}': player '{}' em cooldown, faltam {}s.", boss.id, player.getName().getString(), cooldownRemaining);
         player.displayClientMessage(Component.literal("You can only challenge this boss again in " + BossCooldownManager.formatDuration(cooldownRemaining) + ".").withStyle(ChatFormatting.RED), false);
         return false;
      } else if (!BossVisibility.canInteract(boss, player)) {
         BossDebug.log("[Boss] '{}': interaction blocked by BossVisibility (visibilityMode={}, visibilityTag={}).", boss.id, boss.visibilityMode, boss.visibilityTag);
         denyWithDialogue(player, boss, boss.blockedDialogue, pokemonEntity, "You cannot interact with this boss.");
         return false;
      } else if (boss.blockedIfPlayerHasTag != null && !boss.blockedIfPlayerHasTag.isBlank() && player.getTags().contains(boss.blockedIfPlayerHasTag)) {
         BossDebug.log("[Boss] '{}': player tem a tag de bloqueio '{}', luta negada.", boss.id, boss.blockedIfPlayerHasTag);
         denyWithDialogue(player, boss, boss.blockedDialogue, pokemonEntity, "You can no longer fight this boss.");
         return false;
      } else {
         if (boss.requiredHeldItem != null && !boss.requiredHeldItem.isBlank()) {
            if (!BossItemManager.exists(boss.requiredHeldItem)) {
               ServerBuddy.LOGGER.warn("Boss '{}' requires the item '{}', which no longer exists in Bosses/Items/. Ignoring the requirement.", boss.id, boss.requiredHeldItem);
            } else if (!hasRequiredItem(player, boss)) {
               BossDebug.log("[Boss] '{}': player does not have the required item '{}' (mode={}).", boss.id, boss.requiredHeldItem, boss.requiredItemMode);
               denyWithDialogue(player, boss, boss.missingItemDialogue, pokemonEntity, "You need the right item to challenge this boss.");
               return false;
            }
         }

         return true;
      }
   }

   private static boolean hasRequiredItem(ServerPlayer player, BossDefinition boss) {
      return boss.requiredItemMode == BossDefinition.ItemRequirementMode.INVENTORY_ANY ? BossItemTagTicker.isInInventory(player, boss.requiredHeldItem) : BossItemFactory.isBossItem(player.getMainHandItem(), boss.requiredHeldItem);
   }

   private static void denyWithDialogue(ServerPlayer player, BossDefinition boss, BossDialogueDefinition dialogue, PokemonEntity pokemonEntity, String fallbackMessage) {
      if (dialogue != null && !dialogue.lines.isEmpty()) {
         BossDialogueOpener.open(player, boss, dialogue, pokemonEntity.getPokemon().getAspects());
      } else {
         player.displayClientMessage(Component.literal(fallbackMessage).withStyle(ChatFormatting.RED), true);
      }

   }
}
