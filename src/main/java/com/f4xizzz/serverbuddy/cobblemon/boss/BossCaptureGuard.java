package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class BossCaptureGuard {
   private BossCaptureGuard() {
   }

   public static void register() {
      CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.NORMAL, (event) -> {
         handleCaptureAttempt(event);
         return Unit.INSTANCE;
      });
      CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.NORMAL, (event) -> {
         handleThrownHit(event);
         return Unit.INSTANCE;
      });
   }

   private static void handleThrownHit(ThrownPokeballHitEvent event) {
      BossDefinition boss = BossEntityTracker.getBoss(event.getPokemon().getUUID());
      if (boss != null) {
         Entity owner = event.getPokeBall().getOwner();
         ServerPlayer var10000;
         if (owner instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)owner;
            var10000 = serverPlayer;
         } else {
            var10000 = null;
         }

         ServerPlayer player = var10000;
         BossBattleManager.ActiveBossBattle active = player == null ? null : BossBattleManager.findActiveBattle(player.getUUID(), boss.id);
         if (active == null) {
            event.cancel();
            if (player != null) {
               player.displayClientMessage(Component.literal("You need to be in a battle with this boss to throw a Poké Ball at it!").withStyle(ChatFormatting.RED), true);
            }

         } else {
            BattlePokemon bossMon = active.bossBattlePokemon();
            int maxHealth = bossMon.getMaxHealth();
            float hpPercent = maxHealth <= 0 ? 0.0F : (float)bossMon.getHealth() / (float)maxHealth;
            if (hpPercent > boss.minCaptureHealthPercent) {
               event.cancel();
               int thresholdPercent = Math.round(boss.minCaptureHealthPercent * 100.0F);
               player.displayClientMessage(Component.literal("This boss can only be captured with its health below " + thresholdPercent + "%!").withStyle(ChatFormatting.RED), true);
            }

         }
      }
   }

   private static void handleCaptureAttempt(PokeBallCaptureCalculatedEvent event) {
      BossDefinition boss = BossEntityTracker.getBoss(event.getPokemonEntity().getUUID());
      if (boss != null) {
         LivingEntity thrower = event.getThrower();
         if (thrower instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)thrower;
            BossBattleManager.ActiveBossBattle active = BossBattleManager.findActiveBattle(player.getUUID(), boss.id);
            if (active == null) {
               deny(event, player, "You need to be in a battle with this boss to capture it!");
            } else {
               BattlePokemon bossMon = active.bossBattlePokemon();
               int maxHealth = bossMon.getMaxHealth();
               float hpPercent = maxHealth <= 0 ? 0.0F : (float)bossMon.getHealth() / (float)maxHealth;
               if (hpPercent > boss.minCaptureHealthPercent) {
                  int thresholdPercent = Math.round(boss.minCaptureHealthPercent * 100.0F);
                  deny(event, player, "This boss can only be captured with its health below " + thresholdPercent + "%!");
               }

            }
         } else {
            deny(event, (ServerPlayer)null, "This boss can only be captured in battle!");
         }
      }
   }

   private static void deny(PokeBallCaptureCalculatedEvent event, ServerPlayer player, String message) {
      CaptureContext blocked = new CaptureContext(event.getCaptureResult().getNumberOfShakes(), false, false);
      event.setCaptureResult(blocked);
      if (player != null) {
         player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
      }

   }
}
