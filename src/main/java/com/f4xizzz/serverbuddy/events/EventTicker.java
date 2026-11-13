package com.f4xizzz.serverbuddy.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class EventTicker {
   private EventTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(EventTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 20 == 0) {
         EventManager.checkEvents(server);
      }
   }
}
