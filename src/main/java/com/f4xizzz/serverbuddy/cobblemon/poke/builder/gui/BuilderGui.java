package com.f4xizzz.serverbuddy.cobblemon.poke.builder.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.utils.SmogonAPIUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.PokemonTooltipUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public class BuilderGui {
   public static void open(ServerPlayer player, String pokemonName, List<SmogonAPIUtils.SmogonBuild> builds) {
      openInternal(player, pokemonName, builds, 0);
   }

   private static void openInternal(ServerPlayer player, String pokemonName, List<SmogonAPIUtils.SmogonBuild> builds, int selectedBuildIndex) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      ItemStack bgBlueItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.background_primary)));
      bgBlueItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton bgBlue = GooeyButton.builder().display(bgBlueItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(" ")).build();
      ItemStack bgRedItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.background_secondary)));
      bgRedItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton bgRed = GooeyButton.builder().display(bgRedItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(" ")).build();
      ItemStack bgBlackItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.background_dark)));
      bgBlackItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton bgBlack = GooeyButton.builder().display(bgBlackItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(" ")).build();
      ItemStack bgGrayItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.background_light)));
      bgGrayItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton bgGray = GooeyButton.builder().display(bgGrayItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(" ")).build();
      ItemStack statusRedItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.status_incorrect)));
      statusRedItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton statusRed = GooeyButton.builder().display(statusRedItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(BuilderConfigManager.lang.builder_status_incorrect)).build();
      ItemStack statusYellowItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.status_partial)));
      statusYellowItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton statusYellow = GooeyButton.builder().display(statusYellowItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(BuilderConfigManager.lang.builder_status_partial)).build();
      ItemStack statusGreenItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.status_correct)));
      statusGreenItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton statusGreen = GooeyButton.builder().display(statusGreenItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(BuilderConfigManager.lang.builder_status_correct)).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 12 && i != 14 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7 && i != 10 && i != 16) {
               if ((i < 18 || i > 26) && (i < 45 || i > 53 || i == 49)) {
                  if (i == 27 || i == 31 || i == 35 || i == 36 || i == 40 || i == 44) {
                     template.set(i, bgGray);
                  }
               } else {
                  template.set(i, bgBlack);
               }
            } else {
               template.set(i, bgRed);
            }
         } else {
            template.set(i, bgBlue);
         }
      }

      int[] buildSlots = builds.size() == 1 ? new int[]{13} : new int[]{11, 13, 15};
      ItemStack modelItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));

      try {
         Pokemon mockPokemon = PokemonProperties.Companion.parse(pokemonName).create();
         if (mockPokemon != null) {
            modelItem = PokemonItem.from(mockPokemon);
         }
      } catch (Exception var30) {
         System.out.println("[PokeBuilder] Error generating the 3D model for the Pokémon mock: " + pokemonName);
      }

      modelItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);

      for(int i = 0; i < builds.size() && i < buildSlots.length; ++i) {
         final int i_ = i;
         int slot = buildSlots[i_];
         SmogonAPIUtils.SmogonBuild build = (SmogonAPIUtils.SmogonBuild)builds.get(i_);
         boolean isSelected = i_ == selectedBuildIndex;
         ItemStack displayItem = modelItem.copy();
         if (isSelected) {
            displayItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
         }

         template.set(slot, GooeyButton.builder().display(displayItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(isSelected ? "<green>\u25b6 " + build.name + " \u25c0</green>" : "<yellow>" + build.name + "</yellow>")).with(DataComponents.LORE, getBuildLore(build, isSelected)).onClick((a) -> {
            if (!isSelected) {
               SoundUtils.playAnySound(player, BuilderConfigManager.sounds.click);
               openInternal(player, pokemonName, builds, i_);
            }

         }).build());
      }

      SmogonAPIUtils.SmogonBuild activeBuild = builds.isEmpty() ? null : (SmogonAPIUtils.SmogonBuild)builds.get(selectedBuildIndex);
      int[] partySlots = new int[]{28, 29, 30, 32, 33, 34};
      int[] statusSlots = new int[]{37, 38, 39, 41, 42, 43};
      PlayerPartyStore partyStore = Cobblemon.INSTANCE.getStorage().getParty(player);

      for(int i = 0; i < 6; ++i) {
         Pokemon partyPokemon = partyStore.get(new PartyPosition(i));
         int pSlot = partySlots[i];
         int sSlot = statusSlots[i];
         if (partyPokemon != null) {
            ItemStack partyItem = PokemonItem.from(partyPokemon);
            partyItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            template.set(pSlot, GooeyButton.builder().display(partyItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse("<aqua>" + partyPokemon.getDisplayName(false).getString() + "</aqua>")).with(DataComponents.LORE, getPokemonPartyTooltip(partyPokemon, pokemonName, activeBuild)).build());
            if (activeBuild != null) {
               if (normalizeString(partyPokemon.getSpecies().getName()).equals(normalizeString(pokemonName))) {
                  if (isBuildPerfect(partyPokemon, activeBuild)) {
                     template.set(sSlot, statusGreen);
                  } else {
                     template.set(sSlot, statusYellow);
                  }
               } else {
                  template.set(sSlot, statusRed);
               }
            } else {
               template.set(sSlot, statusRed);
            }
         } else {
            ItemStack emptyBall = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.empty_slot)));
            emptyBall.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            template.set(pSlot, GooeyButton.builder().display(emptyBall).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(BuilderConfigManager.lang.builder_empty_slot)).build());
            template.set(sSlot, statusRed);
         }
      }

      ItemStack closeItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(BuilderConfigManager.guiconfig.icons.close_button)));
      closeItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, BuilderConfigManager.parse(BuilderConfigManager.lang.builder_button_close)).onClick((a) -> {
         SoundUtils.playAnySound(player, BuilderConfigManager.sounds.save);
         UIManager.closeUI(player);
      }).build());
      String var10000 = BuilderConfigManager.lang.builder_gui_title;
      String var10002 = pokemonName.substring(0, 1).toUpperCase();
      String guiTitle = var10000.replace("<pokemon_name>", var10002 + pokemonName.substring(1).toLowerCase());
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(BuilderConfigManager.parse(guiTitle)).build());
   }

   private static boolean isBuildPerfect(Pokemon pokemon, SmogonAPIUtils.SmogonBuild build) {
      String pokeNature = normalizeString(pokemon.getNature().getName().getPath());
      String buildNature = normalizeString(build.nature);
      if (!buildNature.equals("serious") && !pokeNature.equals(buildNature)) {
         return false;
      } else {
         String pokeAbility = normalizeString(pokemon.getAbility().getTemplate().getName());
         String buildAbility = normalizeString(build.ability);
         return buildAbility.equals("qualquer") || pokeAbility.equals(buildAbility);
      }
   }

   private static String normalizeString(String input) {
      return input == null ? "" : input.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
   }

   private static ItemLore getBuildLore(SmogonAPIUtils.SmogonBuild build, boolean isSelected) {
      List<Component> lore = new ArrayList<>();
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_format.replace("<battle_type>", build.battleType)));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_generation.replace("<generation>", String.valueOf(build.generation))));
      lore.add(BuilderConfigManager.parse(" "));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_item.replace("<item>", build.item)));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_ability.replace("<ability>", build.ability)));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_nature.replace("<nature>", build.nature)));
      lore.add(BuilderConfigManager.parse(" "));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_evs_title));
      if (build.evs.isEmpty()) {
         lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_evs_none));
      } else {
         for(Map.Entry<String, Integer> ev : build.evs.entrySet()) {
            lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_evs_line.replace("<stat>", ((String)ev.getKey()).toUpperCase()).replace("<value>", String.valueOf(ev.getValue()))));
         }
      }

      lore.add(BuilderConfigManager.parse(" "));
      lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_moves_title));

      for(String move : build.moves) {
         lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_moves_line.replace("<move>", move)));
      }

      lore.add(BuilderConfigManager.parse(" "));
      if (isSelected) {
         lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_selected));
      } else {
         lore.add(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_lore_unselected));
      }

      return new ItemLore(lore);
   }

   private static ItemLore getPokemonPartyTooltip(Pokemon p, String searchedPokemonName, SmogonAPIUtils.SmogonBuild build) {
      boolean isSameSpecies = normalizeString(p.getSpecies().getName()).equals(normalizeString(searchedPokemonName));
      boolean compareMode = isSameSpecies && build != null;
      Map<String, String> overrides = new HashMap<>();
      if (compareMode) {
         String natureName = p.getNature().getName().getPath();
         boolean natureMatch = normalizeString(build.nature).equals("serious") || normalizeString(natureName).equals(normalizeString(build.nature));
         overrides.put("nature", colorize(PokemonTooltipUtils.capitalize(natureName), natureMatch));
         String abilityName = p.getAbility().getTemplate().getName();
         boolean abilityMatch = normalizeString(build.ability).equals("qualquer") || normalizeString(abilityName).equals(normalizeString(build.ability));
         overrides.put("ability", colorize(PokemonTooltipUtils.capitalize(abilityName), abilityMatch));
         ItemStack heldItem = p.heldItem();
         String itemName = heldItem.isEmpty() ? BuilderConfigManager.lang.word_none : heldItem.getHoverName().getString();
         boolean itemMatch = normalizeString(build.item).equals("nenhum") || normalizeString(itemName).equals(normalizeString(build.item));
         overrides.put("item", colorize(itemName, itemMatch));
         List<Move> moves = p.getMoveSet().getMovesWithNulls();

         for(int i = 0; i < 4; ++i) {
            final int i_ = i;
            Move move = i_ < moves.size() ? (Move)moves.get(i_) : null;
            if (move != null) {
               String moveName = move.getDisplayName().getString();
               boolean isRecommended = build.moves.stream().anyMatch((m) -> normalizeString(m).equals(normalizeString(moveName)));
               overrides.put("move" + (i_ + 1), colorize(moveName, isRecommended));
            }
         }
      }

      return PokemonTooltipUtils.build(BuilderConfigManager.lang.pokemon_tooltip, p, BuilderConfigManager.tooltipStrings(), overrides);
   }

   private static String colorize(String text, boolean match) {
      return match ? "<green>" + text + "</green>" : "<red>" + text + "</red>";
   }
}
