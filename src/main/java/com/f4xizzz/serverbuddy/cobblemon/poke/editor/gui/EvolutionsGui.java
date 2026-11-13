package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class EvolutionsGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      openInternal(player, originalPokemon, editingPokemon, isDevMode, 0);
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      openInternal(player, originalPokemon, editingPokemon, isDevMode, 0);
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, int page) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      List<Species> evolutionChain = new ArrayList<>();
      Species currentSpecies = editingPokemon.getSpecies();
      findEvolutionChain(currentSpecies, evolutionChain);
      int pageSize = 21;
      int maxPages = (int)Math.ceil((double)evolutionChain.size() / (double)pageSize);
      if (maxPages == 0) {
         maxPages = 1;
      }

      int start = page * pageSize;
      int end = Math.min(start + pageSize, evolutionChain.size());
      ItemStack bgBlue = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_primary : null, Items.BLUE_STAINED_GLASS_PANE));
      bgBlue.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlue = GooeyButton.builder().display(bgBlue).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgRed = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_secondary : null, Items.RED_STAINED_GLASS_PANE));
      bgRed.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(bgRed).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_dark : null, Items.BLACK_STAINED_GLASS_PANE));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgWhite = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_white : null, Items.WHITE_STAINED_GLASS_PANE));
      bgWhite.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(bgWhite).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7) {
               if (i != 18 && i != 26 && (i < 45 || i > 53 || i == 48 || i == 49 || i == 50)) {
                  if (i == 27 || i == 35 || i >= 36 && i <= 44) {
                     template.set(i, btnWhite);
                  }
               } else {
                  template.set(i, btnBlack);
               }
            } else {
               template.set(i, btnRed);
            }
         } else {
            template.set(i, btnBlue);
         }
      }

      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

      for(int i = start; i < end; ++i) {
         final int i_ = i;
         Species targetSpecies = (Species)evolutionChain.get(i_);
         int slotIndex = i_ - start;
         Pokemon preview = targetSpecies.create(editingPokemon.getLevel());
         preview.copyFrom(editingPokemon);
         preview.setSpecies(targetSpecies);
         preview.updateForm();
         ItemStack pokeItem = PokemonItem.from(preview);
         pokeItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
         boolean isCurrent = editingPokemon.getSpecies().getName().equalsIgnoreCase(targetSpecies.getName());
         String var10000 = targetSpecies.getName().substring(0, 1).toUpperCase();
         String pokeNameRaw = var10000 + targetSpecies.getName().substring(1);
         String nameFormat;
         if (isCurrent) {
            nameFormat = EditorConfigManager.lang.evolutions_current_name != null ? EditorConfigManager.lang.evolutions_current_name : "<green><pokemon> <gold>(Current)</gold></green>";
         } else {
            nameFormat = EditorConfigManager.lang.evolutions_available_name != null ? EditorConfigManager.lang.evolutions_available_name : "<white><pokemon></white>";
         }

         String displayName = nameFormat.replace("<pokemon>", pokeNameRaw);
         List<Component> lore = new ArrayList<>();
         if (isCurrent) {
            String loreText = EditorConfigManager.lang.evolutions_current_lore != null ? EditorConfigManager.lang.evolutions_current_lore : "<green>Current Evolution</green>";
            lore.add(EditorConfigManager.parse(loreText));
         } else {
            double cost = EditorConfigManager.prices.evolve;
            String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
            String lore1 = EditorConfigManager.lang.evolutions_select_lore1 != null ? EditorConfigManager.lang.evolutions_select_lore1 : "<gray>Click to select.</gray>";
            lore.add(EditorConfigManager.parse(lore1));
            lore.add(Component.empty());
            String lore2 = EditorConfigManager.lang.evolutions_select_lore2 != null ? EditorConfigManager.lang.evolutions_select_lore2 : "<gray>Cost: $<cost></gray>";
            lore.add(EditorConfigManager.parse(lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
            String lore3 = EditorConfigManager.lang.evolutions_select_lore3 != null ? EditorConfigManager.lang.evolutions_select_lore3 : "<gray>Will be charged.</gray>";
            lore.add(EditorConfigManager.parse(lore3));
         }

         template.set(slots[slotIndex], GooeyButton.builder().display(pokeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(displayName)).with(DataComponents.LORE, new ItemLore(lore)).onClick((a) -> {
            if (!isCurrent) {
               editingPokemon.setSpecies(targetSpecies);
               editingPokemon.updateAspects();
               editingPokemon.updateForm();
               editingPokemon.heal();
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
            }
         }).build());
      }

      if (page > 0) {
         ItemStack prevItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.previous_page : null, Items.ARROW));
         String prevName = EditorConfigManager.lang.evolutions_page_prev != null ? EditorConfigManager.lang.evolutions_page_prev : "<yellow>Previous</yellow>";
         template.set(48, GooeyButton.builder().display(prevItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, isDevMode, page - 1);
         }).build());
      } else {
         template.set(48, btnBlack);
      }

      if (page < maxPages - 1) {
         ItemStack nextItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.next_page : null, Items.ARROW));
         String nextName = EditorConfigManager.lang.evolutions_page_next != null ? EditorConfigManager.lang.evolutions_page_next : "<yellow>Next</yellow>";
         template.set(50, GooeyButton.builder().display(nextItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(nextName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, isDevMode, page + 1);
         }).build());
      } else {
         template.set(50, btnBlack);
      }

      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String guiTitleFormat = EditorConfigManager.lang.evolutions_gui_title != null ? EditorConfigManager.lang.evolutions_gui_title : "<dark_gray>Evolution Editor (<current_page>/<total_pages>)</dark_gray>";
      String guiTitle = guiTitleFormat.replace("<current_page>", String.valueOf(page + 1)).replace("<total_pages>", String.valueOf(maxPages));
      GooeyPage gooeyPage = GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build();
      UIManager.openUIForcefully(player, gooeyPage);
   }

   private static void findEvolutionChain(Species currentSpecies, List<Species> chain) {
      if (currentSpecies != null) {
         Species baseSpecies;
         Species pre;
         for(baseSpecies = currentSpecies; baseSpecies.getPreEvolution() != null; baseSpecies = pre) {
            pre = baseSpecies.getPreEvolution().getSpecies();
            if (pre == null || pre.getName().equals(baseSpecies.getName())) {
               break;
            }
         }

         Set<String> visited = new HashSet<>();
         collectEvolutions(baseSpecies, chain, visited);
      }
   }

   private static void collectEvolutions(Species species, List<Species> chain, Set<String> visited) {
      if (species != null && !visited.contains(species.getName())) {
         visited.add(species.getName());
         chain.add(species);

         for(Evolution evolution : species.getStandardForm().getEvolutions()) {
            String nextName = evolution.getResult().getSpecies();
            if (nextName != null) {
               Species nextSpecies = PokemonSpecies.getByName(nextName.toLowerCase());
               if (nextSpecies != null) {
                  collectEvolutions(nextSpecies, chain, visited);
               }
            }
         }

      }
   }

   private static Item getSafeItem(String iconId, Item fallback) {
      if (iconId != null && !iconId.trim().isEmpty()) {
         try {
            Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId));
            return item != null && item != Items.AIR ? item : fallback;
         } catch (Exception var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }
}
