package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class CatchAreaTicker {
   private CatchAreaTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(CatchAreaTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 20 == 0) {
         CatchAreaSpawner.tick(server);
         CatchAreaContainment.tick(server);
         CatchAreaAccessGuard.tick(server);
         CatchAreaTimers.tick(server);
         RandomPoolManager.refreshIfDue();
      }
   }
}
