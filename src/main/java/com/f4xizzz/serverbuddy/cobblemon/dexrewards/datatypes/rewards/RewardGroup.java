package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class RewardGroup {
   public String name;
   public ItemStack icon;
   public double required_percent;
   List<Reward> rewards;

   public RewardGroup(String name, ItemStack icon, double required_percent, List<Reward> rewards) {
      this.name = name;
      this.icon = icon;
      this.required_percent = required_percent;
      this.rewards = rewards;
   }

   public void distribute_rewards(ServerPlayer player) {
      for(Reward reward : this.rewards) {
         reward.distribute_reward(player);
      }

   }
}
