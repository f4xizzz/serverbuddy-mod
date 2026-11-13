package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class BossRespawnScheduler {
   private static final Map<String, Long> PENDING = new HashMap<>();

   private BossRespawnScheduler() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(BossRespawnScheduler::onTick);
   }

   public static void schedule(BossDefinition boss, MinecraftServer server) {
      if (boss.respawnDelaySeconds <= 0) {
         respawn(boss, server);
      } else {
         PENDING.put(boss.id, (long)server.getTickCount() + (long)boss.respawnDelaySeconds * 20L);
      }
   }

   public static boolean forceRespawn(BossDefinition boss, MinecraftServer server) {
      PENDING.remove(boss.id);
      return respawn(boss, server);
   }

   private static void onTick(MinecraftServer server) {
      if (!PENDING.isEmpty()) {
         long now = (long)server.getTickCount();
         PENDING.entrySet().removeIf((entry) -> {
            BossDefinition boss = BossManager.get((String)entry.getKey());
            if (boss == null) {
               return true;
            } else {
               long remainingTicks = (Long)entry.getValue() - now;
               if (remainingTicks > 0L) {
                  updateDisplay(boss, server, remainingTicks);
                  return false;
               } else {
                  respawn(boss, server);
                  return true;
               }
            }
         });
      }
   }

   private static void updateDisplay(BossDefinition boss, MinecraftServer server, long remainingTicks) {
      if (boss.respawnDelaySeconds > 1 && boss.world != null) {
         ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
         if (world != null) {
            long secondsRemaining = (remainingTicks + 19L) / 20L;
            BossRespawnDisplay.update(world, boss, secondsRemaining);
         }
      }
   }

   private static boolean respawn(BossDefinition boss, MinecraftServer server) {
      if (boss.world == null) {
         return false;
      } else {
         ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
         if (world == null) {
            ServerBuddy.LOGGER.warn("[Boss] Could not respawn '{}': world '{}' does not exist.", boss.id, boss.world);
            return false;
         } else {
            BossRespawnDisplay.remove(world, boss);
            if (boss.entityUuid != null) {
               try {
                  UUID oldUuid = UUID.fromString(boss.entityUuid);
                  BossEntityTracker.untrack(oldUuid);
                  Entity oldEntity = world.getEntity(oldUuid);
                  if (oldEntity instanceof PokemonEntity && oldEntity.isAlive()) {
                     oldEntity.discard();
                  }
               } catch (IllegalArgumentException var6) {
               }
            }

            try {
               BossSpawner.spawn(world, boss);
               BossManager.save(boss);
               ServerBuddy.LOGGER.info("[Boss] '{}' respawnado em ({}, {}, {}) no mundo {}.", new Object[]{boss.id, boss.x, boss.y, boss.z, boss.world});
               return true;
            } catch (Exception e) {
               ServerBuddy.LOGGER.error("Could not respawn boss '" + boss.id + "'!", e);
               return false;
            }
         }
      }
   }
}
