package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards;

import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class CommandReward extends Reward {
   List<String> commands;

   public CommandReward(String name, String type, List<String> commands) {
      super(name, type);
      this.commands = commands;
   }

   public void distribute_reward(ServerPlayer player) {
      for(String command : this.commands) {
         command = command.replaceAll("%player%", player.getName().getString());
         Commands cmdManager = ((MinecraftServer)Objects.requireNonNull(player.getServer())).getCommands();
         CommandSourceStack source = player.getServer().createCommandSourceStack();
         cmdManager.performPrefixedCommand(source, command);
      }

   }
}
