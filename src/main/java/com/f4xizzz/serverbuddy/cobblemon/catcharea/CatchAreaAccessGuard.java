package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import java.util.UUID;
import kotlin.Unit;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class CatchAreaAccessGuard {
   private CatchAreaAccessGuard() {
   }

   public static void registerEvents() {
      CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.NORMAL, (event) -> {
         handleCaptureAttempt(event);
         return Unit.INSTANCE;
      });
   }

   private static void handleCaptureAttempt(PokeBallCaptureCalculatedEvent event) {
      UUID entityId = event.getPokemonEntity().getUUID();
      String areaName = CatchAreaEntityTracker.getAreaForEntity(entityId);
      if (areaName != null) {
         LivingEntity thrower = event.getThrower();
         if (thrower instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)thrower;
            UUID ownerId = CatchAreaOccupancyManager.getRenterOf(areaName);
            if (ownerId == null || !ownerId.equals(player.getUUID())) {
               if (!hasBypass(player)) {
                  CaptureContext blocked = new CaptureContext(event.getCaptureResult().getNumberOfShakes(), false, false);
                  event.setCaptureResult(blocked);
                  player.displayClientMessage(Component.literal("You can only capture Pokémon from your own catch area!").withStyle(ChatFormatting.RED), true);
               }
            }
         }
      }
   }

   public static void tick(MinecraftServer server) {
      for(CatchAreaSession session : CatchAreaOccupancyManager.getAllSessions()) {
         CatchAreaRegion region = CatchAreaRegionManager.get(session.areaName);
         if (region != null) {
            ServerLevel world = region.resolveWorld(server);
            if (world != null) {
               for(ServerPlayer player : world.players()) {
                  if (!player.getUUID().equals(session.playerId) && !hasBypass(player)) {
                     BlockPos pos = player.blockPosition();
                     if (region.contains(pos)) {
                        ejectPlayer(player, world, region);
                     }
                  }
               }
            }
         }
      }

   }

   private static void ejectPlayer(ServerPlayer player, ServerLevel world, CatchAreaRegion region) {
      double ejectX = (double)(region.getMinX() - 3);
      double ejectY = (double)(region.getMinY() + 1);
      double ejectZ = (double)(region.getMinZ() + region.getMaxZ()) / 2.0;
      player.teleportTo(world, ejectX, ejectY, ejectZ, player.getYRot(), player.getXRot());
      player.displayClientMessage(Component.literal("This catch area is being used by another player!").withStyle(ChatFormatting.RED), false);
   }

   private static boolean hasBypass(ServerPlayer player) {
      return player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.catcharea.bypass", false);
   }
}
