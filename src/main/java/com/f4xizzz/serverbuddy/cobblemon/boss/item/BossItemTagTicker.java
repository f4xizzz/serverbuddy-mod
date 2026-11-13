package com.f4xizzz.serverbuddy.cobblemon.boss.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class BossItemTagTicker {
   private BossItemTagTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(BossItemTagTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      if (server.getTickCount() % 10 == 0) {
         for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            for(BossItemDefinition item : BossItemManager.getAll().values()) {
               if (item.grantsTag != null && !item.grantsTag.isBlank()) {
                  boolean hasItem = item.tagMode == BossItemDefinition.TagMode.HELD_ONLY ? BossItemFactory.isBossItem(player.getMainHandItem(), item.name) : isInInventory(player, item.name);
                  if (hasItem) {
                     player.getTags().add(item.grantsTag);
                  } else {
                     player.getTags().remove(item.grantsTag);
                  }
               }
            }
         }

      }
   }

   public static boolean isInInventory(ServerPlayer player, String name) {
      for(ItemStack stack : player.getInventory().items) {
         if (BossItemFactory.isBossItem(stack, name)) {
            return true;
         }
      }

      for(ItemStack stack : player.getInventory().armor) {
         if (BossItemFactory.isBossItem(stack, name)) {
            return true;
         }
      }

      return BossItemFactory.isBossItem(player.getOffhandItem(), name);
   }
}
