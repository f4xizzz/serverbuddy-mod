package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class CatchAreaContainment {
   private CatchAreaContainment() {
   }

   public static void tick(MinecraftServer server) {
      for(CatchAreaSession session : CatchAreaOccupancyManager.getAllSessions()) {
         CatchAreaRegion region = CatchAreaRegionManager.get(session.areaName);
         if (region != null) {
            ServerLevel world = region.resolveWorld(server);
            if (world != null) {
               for(UUID entityId : new HashSet<>(CatchAreaEntityTracker.getEntities(session.areaName))) {
                  Entity entity = world.getEntity(entityId);
                  if (entity != null && entity.isAlive()) {
                     BlockPos pos = entity.blockPosition();
                     if (!region.contains(pos)) {
                        double clampedX = clamp(entity.getX(), (double)region.getMinX(), (double)region.getMaxX() + 1.0);
                        double clampedY = clamp(entity.getY(), (double)region.getMinY(), (double)region.getMaxY() + 1.0);
                        double clampedZ = clamp(entity.getZ(), (double)region.getMinZ(), (double)region.getMaxZ() + 1.0);
                        entity.teleportTo(clampedX, clampedY, clampedZ);
                     }
                  } else {
                     CatchAreaEntityTracker.untrack(session.areaName, entityId);
                  }
               }
            }
         }
      }

   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
