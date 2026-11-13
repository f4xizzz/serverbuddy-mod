package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards;

import net.minecraft.server.level.ServerPlayer;

public class Reward {
   public String name;
   public String type;

   public Reward(String name, String type) {
      this.name = name;
      this.type = type;
   }

   public void distribute_reward(ServerPlayer player) {
   }
}
