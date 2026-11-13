package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CatchAreaTimers {
   private CatchAreaTimers() {
   }

   public static void tick(MinecraftServer server) {
      CatchAreaConfig config = CatchAreaConfigManager.getConfig();
      long now = System.currentTimeMillis();

      for(CatchAreaSession session : CatchAreaOccupancyManager.getAllSessions()) {
         if (now >= session.expiresAtMillis) {
            endSession(server, session, Component.literal("\u23f0 Your catch area expired! ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}).append(Component.literal("Espero que tenha se divertido.").withStyle(ChatFormatting.GRAY)));
         } else {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
               if (session.offlineSinceMillis == null) {
                  session.offlineSinceMillis = now;
                  CatchAreaSessionRepository.saveAsync(session);
               } else if (now - session.offlineSinceMillis >= (long)config.offlineTimeoutMinutes * 60000L) {
                  endSession(server, session, Component.literal("\u2716 Catch area cancelled! ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal("You were offline for too long. The amount paid was not refunded.").withStyle(ChatFormatting.GRAY)));
               }
            } else {
               if (session.offlineSinceMillis != null) {
                  session.offlineSinceMillis = null;
                  CatchAreaSessionRepository.saveAsync(session);
               }

               CatchAreaRegion region = CatchAreaRegionManager.get(session.areaName);
               if (region != null) {
                  ServerLevel world = region.resolveWorld(server);
                  if (world != null) {
                     boolean inside = player.serverLevel() == world && region.contains(player.blockPosition());
                     if (!inside) {
                        if (session.awayFromAreaSinceMillis == null) {
                           session.awayFromAreaSinceMillis = now;
                           CatchAreaSessionRepository.saveAsync(session);
                        }

                        long remainingSeconds = Math.max(0L, (session.expiresAtMillis - now) / 1000L);
                        player.displayClientMessage(Component.literal("\u26a0 Catch Area: ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal(formatDuration(remainingSeconds) + " restantes").withStyle(ChatFormatting.YELLOW)), true);
                        if (now - session.awayFromAreaSinceMillis >= (long)config.awayFromAreaTimeoutMinutes * 60000L) {
                           endSession(server, session, Component.literal("\u2716 Catch area cancelled! ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal("You stayed outside of it for too long. The amount paid was not refunded.").withStyle(ChatFormatting.GRAY)));
                           continue;
                        }
                     } else if (session.awayFromAreaSinceMillis != null) {
                        session.awayFromAreaSinceMillis = null;
                        CatchAreaSessionRepository.saveAsync(session);
                     }

                     CatchAreaDisplays.update(world, region, session);
                  }
               }
            }
         }
      }

   }

   public static void endSession(MinecraftServer server, CatchAreaSession session, Component message) {
      CatchAreaRegion region = CatchAreaRegionManager.get(session.areaName);
      if (region != null) {
         ServerLevel world = region.resolveWorld(server);
         if (world != null) {
            CatchAreaDisplays.remove(world, region);

            for(UUID entityId : new HashSet<>(CatchAreaEntityTracker.getEntities(session.areaName))) {
               Entity entity = world.getEntity(entityId);
               if (entity != null) {
                  entity.discard();
               }
            }
         }
      }

      CatchAreaOccupancyManager.release(session.playerId);
      if (message != null) {
         ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
         if (player != null) {
            player.displayClientMessage(message, false);
         }
      }

   }

   private static String formatDuration(long totalSeconds) {
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      return hours + "h" + minutes + "m" + seconds + "s";
   }
}
