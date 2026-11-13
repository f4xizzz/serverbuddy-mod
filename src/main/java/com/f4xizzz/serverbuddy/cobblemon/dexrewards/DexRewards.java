package com.f4xizzz.serverbuddy.cobblemon.dexrewards;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries;
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.commands.DexCommands;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.config.Config;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.database.DexRewardsDatabase;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.Messages;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.PlayerData;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.rewards.RewardGroup;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DexRewards {
   private static final String MODID = "serverbuddy-dexrewards";
   private static final Logger LOGGER = LoggerFactory.getLogger("serverbuddy-dexrewards");
   public static DexRewards INSTANCE;
   public static boolean DEBUG = false;
   private FabricServerAudiences audience;
   private MinecraftServer server;
   private Config config;
   public static int DEX_TOTAL;
   public static List<ResourceLocation> VALID_DEX_IDS;

   public void onInitialize() {
      INSTANCE = this;
      new DexCommands();
      ServerLifecycleEvents.SERVER_STARTED.register((ServerLifecycleEvents.ServerStarted)(server) -> {
         this.audience = FabricServerAudiences.of(server);
         this.server = server;
         this.reload();
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("pokedex", "total"), (ctx, arg) -> PlaceholderResult.value(String.valueOf(DEX_TOTAL)));
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("player", "caught_count"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
               return PlaceholderResult.invalid("No Player!");
            } else {
               ServerPlayer player = ctx.player();
               return player != null ? PlaceholderResult.value(String.valueOf(this.config.getPlayerData(player.getUUID()).caught_count)) : PlaceholderResult.invalid("No Player!");
            }
         });
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("player", "rank"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
               return PlaceholderResult.invalid("No Player!");
            } else {
               ServerPlayer player = ctx.player();
               if (player != null) {
                  PlayerData playerData = this.config.getPlayerData(player.getUUID());
                  if (playerData != null) {
                     String rank = "None";

                     for(RewardGroup group : INSTANCE.config().reward_groups) {
                        if (playerData.claimed_rewards.contains(group.name)) {
                           rank = group.name;
                        }
                     }

                     return PlaceholderResult.value(rank);
                  } else {
                     return PlaceholderResult.invalid("No Player Data!");
                  }
               } else {
                  return PlaceholderResult.invalid("No Player!");
               }
            }
         });
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("player", "caught_percent"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
               return PlaceholderResult.invalid("No Player!");
            } else {
               ServerPlayer player = ctx.player();
               if (player != null) {
                  PlayerData playerData = this.config.getPlayerData(player.getUUID());
                  if (playerData != null) {
                     String percent = (new DecimalFormat("#.##")).format((double)playerData.caught_count / (double)DEX_TOTAL * 100.0);
                     return PlaceholderResult.value(percent);
                  } else {
                     return PlaceholderResult.invalid("No Player Data!");
                  }
               } else {
                  return PlaceholderResult.invalid("No Player!");
               }
            }
         });
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("pokedex", "total_reward_groups"), (ctx, arg) -> PlaceholderResult.value(String.valueOf(this.config.reward_groups.size())));
      });
      ServerPlayConnectionEvents.JOIN.register((ServerPlayConnectionEvents.Join)(serverPlayNetworkHandler, packetSender, server) -> {
         ServerPlayer player = serverPlayNetworkHandler.player;
         if (player != null) {
            this.config.loadPlayerData(player.getUUID(), player.getName().getString());
            PlayerData playerData = this.config.getPlayerData(player.getUUID());
            if (playerData != null) {
               playerData.updateCaughtCount();
               playerData.updateClaimableRewards();
               if (!playerData.claimable_rewards.isEmpty()) {
                  player.sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.rewards_to_claim)));
               }

               this.config.updatePlayerData(playerData);
            }
         }

      });
      ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayConnectionEvents.Disconnect)(serverPlayNetworkHandler, server) -> {
         ServerPlayer player = serverPlayNetworkHandler.player;
         if (player != null) {
            this.config.unloadPlayerData(player.getUUID());
         }

      });
      CobblemonEvents.POKEMON_GAINED.subscribe(Priority.LOWEST, (event) -> {
         UUID player_uuid = event.getPlayerId();
         PlayerData playerData = this.config.getPlayerData(player_uuid);
         if (playerData != null) {
            playerData.updateCaughtCount();
            playerData.updateClaimableRewards();
            this.config.updatePlayerData(playerData);
         }

         return Unit.INSTANCE;
      });
      CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.LOWEST, (event) -> {
         UUID player_uuid = event.getPlayerUUID();
         PlayerData playerData = this.config.getPlayerData(player_uuid);
         if (playerData != null) {
            playerData.updateCaughtCount();
            playerData.updateClaimableRewards();
            this.config.updatePlayerData(playerData);
         }

         return Unit.INSTANCE;
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerLifecycleEvents.ServerStopping)(server) -> {
         for(ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData pd = this.config.getPlayerData(p.getUUID());
            if (pd != null) {
               this.config.updatePlayerData(pd);
            }
         }

      });
   }

   public void reload() {
      this.config = new Config();
      DexRewardsDatabase.init(this.config.use_mysql, this.config.mysql_host, this.config.mysql_port, this.config.mysql_database, this.config.mysql_user, this.config.mysql_password);
      int total = 0;
      int invalid_total = 0;
      int unimplemented_total = 0;
      List<ResourceLocation> valid_identifiers = new ArrayList<>();

      for(Map.Entry<ResourceLocation, PokedexEntry> entry : DexEntries.INSTANCE.getEntries().entrySet()) {
         PokemonSpecies var10000 = PokemonSpecies.INSTANCE;
         Species species = PokemonSpecies.getByIdentifier((ResourceLocation)entry.getKey());
         if (species != null) {
            if (!this.config.implemented_only || species.getImplemented()) {
               if (this.config.species_blacklist.contains(species)) {
                  ++invalid_total;
               } else {
                  boolean valid = true;

                  for(String label : species.getLabels()) {
                     if (this.config.label_blacklist.contains(label)) {
                        valid = false;
                     }

                     if (this.config.generation_blacklist.contains(label)) {
                        valid = false;
                     }
                  }

                  if (valid) {
                     ++total;
                     valid_identifiers.add((ResourceLocation)entry.getKey());
                  } else {
                     ++invalid_total;
                  }
               }
            }

            if (!species.getImplemented()) {
               ++unimplemented_total;
            }
         } else {
            if (this.config.allow_invalid_species) {
               ++total;
            }

            ++invalid_total;
         }
      }

      DEX_TOTAL = total;
      this.logInfo("[DexRewards]: Total Valid Pokedex Entries: " + DEX_TOTAL);
      this.logInfo("[DexRewards]: Total Invalid Entries: " + invalid_total);
      this.logInfo("[DexRewards]: Total Unimplemented: " + unimplemented_total);
      this.logInfo("[DexRewards]: Total Pokedex Entries: " + (DEX_TOTAL + invalid_total + unimplemented_total));
      VALID_DEX_IDS = valid_identifiers;

      for(PlayerData data : new ArrayList<>(this.config().player_data.values())) {
         data.updateCaughtCount();
         data.updateClaimableRewards();
         this.config.updatePlayerData(data);
      }

   }

   public FabricServerAudiences audience() {
      return this.audience;
   }

   public MinecraftServer server() {
      return this.server;
   }

   public Config config() {
      return this.config;
   }

   public void logInfo(String message) {
      if (DEBUG) {
         LOGGER.info(message);
      }

   }

   static {
      DEX_TOTAL = DexEntries.INSTANCE.getEntries().size();
      VALID_DEX_IDS = new ArrayList<>();
   }
}
