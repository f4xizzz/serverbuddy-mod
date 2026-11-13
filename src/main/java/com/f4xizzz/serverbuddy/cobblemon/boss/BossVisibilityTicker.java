package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.f4xizzz.serverbuddy.mixin.EntityTrackerInvoker;
import com.f4xizzz.serverbuddy.mixin.ServerChunkLoadingManagerAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class BossVisibilityTicker {
   private static final int TICK_INTERVAL = 5;
   private static final Map<String, Boolean> LAST_VISIBLE = new HashMap<>();

   private BossVisibilityTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(BossVisibilityTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 5 == 0) {
         for(BossDefinition boss : BossManager.getAll().values()) {
            if (boss.visibilityBehavior == BossDefinition.VisibilityBehavior.HIDE_ENTITY && boss.visibilityMode != BossDefinition.VisibilityMode.NONE) {
               Entity entity = resolveEntity(server, boss);
               if (entity != null) {
                  Object tracker = findTracker(entity);
                  if (tracker instanceof EntityTrackerInvoker) {
                     EntityTrackerInvoker invoker = (EntityTrackerInvoker)tracker;
                     ServerLevel world = (ServerLevel)entity.level();

                     for(ServerPlayer player : world.players()) {
                        boolean shouldSee = BossVisibility.isAllowed(boss, player);
                        String var10000 = boss.id;
                        String key = var10000 + "|" + String.valueOf(player.getUUID());
                        Boolean wasVisible = (Boolean)LAST_VISIBLE.put(key, shouldSee);
                        if (wasVisible == null || wasVisible != shouldSee) {
                           if (shouldSee) {
                              invoker.serverbuddy$updateTrackedStatus(player);
                           } else {
                              invoker.serverbuddy$stopTracking(player);
                           }
                        }
                     }
                  }
               }
            }
         }

      }
   }

   private static Object findTracker(Entity entity) {
      ServerLevel world = (ServerLevel)entity.level();
      ChunkMap manager = world.getChunkSource().chunkMap;
      return ((ServerChunkLoadingManagerAccessor)manager).serverbuddy$getEntityTrackers().get(entity.getId());
   }

   private static Entity resolveEntity(MinecraftServer server, BossDefinition boss) {
      if (boss.world != null && boss.entityUuid != null) {
         ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
         if (world == null) {
            return null;
         } else {
            try {
               return world.getEntity(UUID.fromString(boss.entityUuid));
            } catch (IllegalArgumentException var4) {
               return null;
            }
         }
      } else {
         return null;
      }
   }
}
