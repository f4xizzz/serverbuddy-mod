package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ItemReward extends Reward {
   public ItemStack reward_item;

   public ItemReward(String name, String type, ItemStack reward_item) {
      super(name, type);
      this.reward_item = reward_item;
   }

   public void distribute_reward(ServerPlayer player) {
      if (!player.getInventory().add(this.reward_item.copy())) {
         player.drop(this.reward_item.copy(), false);
      }

   }
}
