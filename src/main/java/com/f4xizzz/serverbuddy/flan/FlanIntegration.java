package com.f4xizzz.serverbuddy.flan;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.logs.ServerConfig;
import com.f4xizzz.serverbuddy.logs.ServerConfigManager;
import io.github.flemmli97.flan.api.ClaimHandler;
import io.github.flemmli97.flan.api.data.IPlayerData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class FlanIntegration {
   private static final Map<UUID, Integer> BROKEN_SINCE_REWARD = new HashMap<>();

   private FlanIntegration() {
   }

   public static void register() {
      if (FabricLoader.getInstance().isModLoaded("flan")) {
         PlayerBlockBreakEvents.AFTER.register((PlayerBlockBreakEvents.After)(world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
               onBlockBroken(serverPlayer);
            }
         });
         ServerBuddy.LOGGER.info("[Flan] Mining claim-blocks integration enabled.");
      }
   }

   private static void onBlockBroken(ServerPlayer player) {
      ServerConfig config = ServerConfigManager.getConfig();
      if (config.flanBlockRewardEnabled && config.flanBlocksBrokenPerReward > 0 && config.flanClaimBlocksReward > 0) {
         int brokenSinceReward = (Integer)BROKEN_SINCE_REWARD.merge(player.getUUID(), 1, Integer::sum);
         if (brokenSinceReward >= config.flanBlocksBrokenPerReward) {
            BROKEN_SINCE_REWARD.put(player.getUUID(), brokenSinceReward - config.flanBlocksBrokenPerReward);
            IPlayerData data = ClaimHandler.getPlayerData(player);
            data.setAdditionalClaims(data.getAdditionalClaims() + config.flanClaimBlocksReward);
            player.displayClientMessage(Component.literal("\u26cf You earned ").withStyle(ChatFormatting.GREEN).append(Component.literal(config.flanClaimBlocksReward + " claim blocks").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})).append(Component.literal(" for mining!").withStyle(ChatFormatting.GREEN)), true);
         }
      }
   }
}
