package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.f4xizzz.serverbuddy.util.SoundPlayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class CatchComboManager {
   private static final Map<UUID, CatchComboState> STATES = new HashMap<>();

   private CatchComboManager() {
   }

   public static void loadAll() {
      STATES.clear();
      STATES.putAll(CatchComboRepository.loadAll());
   }

   public static CatchComboState getState(UUID playerId) {
      return (CatchComboState)STATES.computeIfAbsent(playerId, (id) -> new CatchComboState());
   }

   public static void resetState(UUID playerId) {
      STATES.remove(playerId);
      CatchComboRepository.deleteAsync(playerId);
   }

   public static void setState(UUID playerId, Species species, int count) {
      CatchComboState state = getState(playerId);
      state.speciesId = species.getResourceIdentifier().toString();
      state.speciesDisplayName = species.getName();
      state.comboCount = Math.max(0, count);
      CatchComboRepository.saveAsync(playerId, state);
   }

   public static CatchComboConfig.ComboTier getActiveTier(int comboCount) {
      CatchComboConfig.ComboTier active = null;

      for(CatchComboConfig.ComboTier tier : CatchComboConfigManager.getConfig().tiers) {
         if (comboCount >= tier.combo && (active == null || tier.combo > active.combo)) {
            active = tier;
         }
      }

      return active;
   }

   public static int registerCapture(ServerPlayer player, Species species) {
      CatchComboConfig config = CatchComboConfigManager.getConfig();
      CatchComboState state = getState(player.getUUID());
      String newSpeciesId = species.getResourceIdentifier().toString();
      boolean hadPreviousCombo = state.speciesId != null;
      boolean broke = hadPreviousCombo && !state.speciesId.equals(newSpeciesId);
      String brokenSpeciesName = state.speciesDisplayName;
      int brokenCount = state.comboCount;
      if (hadPreviousCombo && !broke) {
         ++state.comboCount;
      } else {
         state.speciesId = newSpeciesId;
         state.speciesDisplayName = species.getName();
         state.comboCount = 1;
      }

      CatchComboSoundsConfig sounds = CatchComboSoundsConfigManager.getConfig();
      if (broke && brokenCount >= config.actionBarStartAt) {
         SoundPlayer.play(player, sounds.broken);
         player.displayClientMessage(Component.literal("\u2716 Catch combo quebrado! ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal(brokenSpeciesName).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})).append(Component.literal(" (" + brokenCount + "x)").withStyle(ChatFormatting.GRAY)), false);
      } else if (!broke && state.comboCount >= config.actionBarStartAt) {
         SoundPlayer.play(player, sounds.increment);
      }

      CatchComboRepository.saveAsync(player.getUUID(), state);
      CatchComboLeaderboardRepository.recordAttemptAsync(player.getUUID(), player.getName().getString(), state.speciesId, state.speciesDisplayName, state.comboCount);
      if (config.enabled && state.comboCount > config.actionBarStartAt) {
         sendActionBar(player, state);
      }

      return state.comboCount;
   }

   public static void sendActionBar(ServerPlayer player, CatchComboState state) {
      CatchComboConfig config = CatchComboConfigManager.getConfig();
      String msg = config.actionBarMessage.replace("%species%", state.speciesDisplayName == null ? "?" : state.speciesDisplayName).replace("%combo%", String.valueOf(state.comboCount));
      player.displayClientMessage(Component.literal(msg.replace("&", "\u00a7")), true);
   }

   public static void applySpawnBuffs(ServerPlayer player, Pokemon wildPokemon) {
      CatchComboConfig config = CatchComboConfigManager.getConfig();
      if (config.enabled) {
         CatchComboState state = (CatchComboState)STATES.get(player.getUUID());
         if (state != null && state.speciesId != null) {
            ResourceLocation wildSpeciesId = wildPokemon.getSpecies().getResourceIdentifier();
            if (wildSpeciesId.toString().equals(state.speciesId)) {
               CatchComboConfig.ComboTier tier = getActiveTier(state.comboCount);
               if (tier != null) {
                  List<Stat> stats = new ArrayList<>(Arrays.asList(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));
                  Collections.shuffle(stats);
                  int guaranteed = Math.min(Math.max(tier.guaranteedPerfectIvs, 0), stats.size());

                  for(int i = 0; i < guaranteed; ++i) {
                     wildPokemon.getIvs().set((Stat)stats.get(i), 31);
                  }

                  if (tier.chancePerfectIv > 0.0) {
                     for(int i = guaranteed; i < stats.size(); ++i) {
                        if (player.getRandom().nextDouble() <= tier.chancePerfectIv) {
                           wildPokemon.getIvs().set((Stat)stats.get(i), 31);
                        }
                     }
                  }

                  if (tier.shinyChance > 0.0 && !wildPokemon.getShiny() && player.getRandom().nextDouble() <= tier.shinyChance) {
                     wildPokemon.setShiny(true);
                  }

                  if (tier.hiddenAbilityChance > 0.0 && player.getRandom().nextDouble() <= tier.hiddenAbilityChance) {
                     for(PotentialAbility potential : wildPokemon.getForm().getAbilities()) {
                        if (potential instanceof HiddenAbility) {
                           wildPokemon.updateAbility(potential.getTemplate().create(false, Priority.LOWEST));
                           break;
                        }
                     }
                  }

               }
            }
         }
      }
   }
}
