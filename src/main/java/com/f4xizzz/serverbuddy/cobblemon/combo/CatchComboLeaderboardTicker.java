package com.f4xizzz.serverbuddy.cobblemon.combo;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class CatchComboLeaderboardTicker {
   private CatchComboLeaderboardTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(CatchComboLeaderboardTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 20 == 0) {
         CatchComboLeaderboardManager.tick(server);
      }
   }
}
