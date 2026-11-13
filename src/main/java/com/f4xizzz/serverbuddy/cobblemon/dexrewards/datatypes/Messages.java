package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes;

import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import java.text.DecimalFormat;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public class Messages {
   public static String prefix = "<dark_gray>[<blue>DexRewards<dark_gray>]";
   public static String rewards_to_claim = "%prefix% You have rewards to claim!";
   public static String reward_claimable = "%prefix% You can claim the %group.name% rewards!";
   public static String rewards_claimed = "%prefix% You've claimed rewards for %group.name%!";
   public static String reset_sender = "%prefix% You've reset %player%'s pokedex reward progress!";
   public static String reset_target = "%prefix% Your pokedex reward progress has been reset!";
   public static String update_command = "%prefix% You've updated the pokedex reward progress of %player%";
   public static String reload = "%prefix% Reloaded!";

   public static String parse(String message) {
      message = message.replaceAll("%prefix%", prefix);
      return message.replaceAll("%pokedex.total%", String.valueOf(DexRewards.DEX_TOTAL));
   }

   public static String parse(String message, RewardGroup group) {
      message = parse(message);
      message = message.replaceAll("%group.name%", group.name);
      return message.replaceAll("%group.percent%", String.valueOf(group.required_percent));
   }

   public static String parse(String message, UUID player_uuid) {
      message = parse(message);
      ServerPlayer player = DexRewards.INSTANCE.server().getPlayerList().getPlayer(player_uuid);
      if (player != null) {
         message = message.replaceAll("%player%", player.getName().getString());
      }

      PlayerData player_data = DexRewards.INSTANCE.config().getPlayerData(player_uuid);
      if (player_data != null) {
         String rank = "None";

         for(RewardGroup group : DexRewards.INSTANCE.config().reward_groups) {
            if (player_data.claimed_rewards.contains(group.name)) {
               rank = group.name;
            }
         }

         message = message.replaceAll("%player.rank%", rank);
         message = message.replaceAll("%player.caught_count%", String.valueOf(player_data.caught_count));
         String percent = (new DecimalFormat("#.##")).format((double)player_data.caught_count / (double)DexRewards.DEX_TOTAL * 100.0);
         message = message.replaceAll("%player.caught_percent%", percent);
      }

      return message;
   }
}
