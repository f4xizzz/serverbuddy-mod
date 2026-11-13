package com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes;

import com.cobblemon.mod.common.Cobblemon;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class PlayerData {
   public UUID uuid;
   public String username;
   public int caught_count;
   public List<String> claimed_rewards;
   public List<String> claimable_rewards;

   public PlayerData(UUID uuid, String username, int caught_count, List<String> claimed_rewards, List<String> claimable_rewards) {
      this.uuid = uuid;
      this.username = username;
      this.caught_count = caught_count;
      this.claimed_rewards = claimed_rewards;
      this.claimable_rewards = claimable_rewards;
   }

   public int updateCaughtCount() {
      this.caught_count = 0;
      List<String> validIdsAsString = new ArrayList<>();

      for(ResourceLocation id : DexRewards.VALID_DEX_IDS) {
         validIdsAsString.add(id.toString());
      }

      Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(this.uuid).getSpeciesRecords().values().forEach((speciesRecord) -> {
         String recordId = speciesRecord.getId().toString();
         String status = speciesRecord.getKnowledge().name();
         if (validIdsAsString.contains(recordId) && status.equals("CAUGHT")) {
            ++this.caught_count;
         }

      });
      return this.caught_count;
   }

   public void updateClaimableRewards() {
      int old_size = this.claimable_rewards.size();
      ServerPlayer player = DexRewards.INSTANCE.server().getPlayerList().getPlayer(this.uuid);
      List<String> new_claimable_rewards = new ArrayList<>();

      for(RewardGroup group : DexRewards.INSTANCE.config().reward_groups) {
         if (!this.claimed_rewards.contains(group.name)) {
            double percent_caught = (double)this.caught_count / (double)DexRewards.DEX_TOTAL * 100.0;
            if (percent_caught >= group.required_percent) {
               new_claimable_rewards.add(group.name);
               if (!this.claimable_rewards.contains(group.name) && player != null) {
                  player.sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.reward_claimable, group)));
               }
            }
         }
      }

      this.claimable_rewards = new_claimable_rewards;
      if (old_size < this.claimable_rewards.size() && player != null) {
         player.sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.rewards_to_claim)));
      }

   }
}
