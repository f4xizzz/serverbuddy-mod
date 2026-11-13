package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatureAssignments;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AspectsGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      open(player, originalPokemon, editingPokemon, isDevMode, 0);
   }

   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, int page) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      Set<String> availableAspects = new HashSet<>();

      for(Object formObj : editingPokemon.getSpecies().getForms()) {
         if (formObj instanceof FormData form) {
            String formName = form.getName().toLowerCase().trim();
            if (!formName.equals("normal")) {
               availableAspects.add(formName);
            }

            for(Object aspectObj : form.getAspects()) {
               availableAspects.add(aspectObj.toString().toLowerCase().trim());
            }

            for(Object labelObj : form.getLabels()) {
               availableAspects.add(labelObj.toString().toLowerCase().trim());
            }
         }
      }

      for(Object featureObj : editingPokemon.getSpecies().getFeatures()) {
         availableAspects.add(featureObj.toString().toLowerCase().trim());
      }

      for(Object labelObj : editingPokemon.getSpecies().getLabels()) {
         availableAspects.add(labelObj.toString().toLowerCase().trim());
      }

      for(String assignment : SpeciesFeatureAssignments.getFeatures(editingPokemon.getSpecies())) {
         availableAspects.add(assignment.toLowerCase().trim());
      }

      for(String forced : editingPokemon.getForcedAspects()) {
         availableAspects.add(forced.toLowerCase().trim());
      }

      for(String aspect : editingPokemon.getAspects()) {
         availableAspects.add(aspect.toLowerCase().trim());
      }

      availableAspects.removeIf((a) -> a.startsWith("gen") || a.equals("legendary") || a.equals("mythical") || a.equals("ultrabeast") || a.equals("male") || a.equals("female") || a.equals("genderless") || a.equals("shiny"));
      List<String> aspectList = new ArrayList<>(availableAspects);
      aspectList.sort(String::compareTo);
      int maxPerPage = 14;
      int totalPages = Math.max(1, (int)Math.ceil((double)aspectList.size() / (double)maxPerPage));
      int currentPage = Math.max(0, Math.min(page, totalPages - 1));
      ItemStack blackGlass = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_dark : null, Items.BLACK_STAINED_GLASS_PANE));
      blackGlass.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(blackGlass).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack redGlass = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_secondary : null, Items.RED_STAINED_GLASS_PANE));
      redGlass.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(redGlass).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack whiteGlass = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_white : null, Items.WHITE_STAINED_GLASS_PANE));
      whiteGlass.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(whiteGlass).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      int[] blackSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52, 53};
      int[] redSlots = new int[]{9, 17, 18, 26};
      int[] whiteSlots = new int[]{27, 35, 36, 44};

      for(int slot : blackSlots) {
         template.set(slot, btnBlack);
      }

      for(int slot : redSlots) {
         template.set(slot, btnRed);
      }

      for(int slot : whiteSlots) {
         template.set(slot, btnWhite);
      }

      String backText = EditorConfigManager.lang.aspects_button_return != null ? EditorConfigManager.lang.aspects_button_return : "<red>Return</red>";
      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      if (currentPage > 0) {
         ItemStack prevItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.previous_page : null, Items.ARROW));
         String prevName = (EditorConfigManager.lang.aspects_page_prev != null ? EditorConfigManager.lang.aspects_page_prev : "<yellow>Previous (<current_page>/<total_pages>)</yellow>").replace("<current_page>", String.valueOf(currentPage)).replace("<total_pages>", String.valueOf(totalPages));
         template.set(48, GooeyButton.builder().display(prevItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            open(player, originalPokemon, editingPokemon, isDevMode, currentPage - 1);
         }).build());
      }

      if (currentPage < totalPages - 1) {
         ItemStack nextItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.next_page : null, Items.ARROW));
         String nextName = (EditorConfigManager.lang.aspects_page_next != null ? EditorConfigManager.lang.aspects_page_next : "<yellow>Next (<next_page>/<total_pages>)</yellow>").replace("<next_page>", String.valueOf(currentPage + 2)).replace("<total_pages>", String.valueOf(totalPages));
         template.set(50, GooeyButton.builder().display(nextItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(nextName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            open(player, originalPokemon, editingPokemon, isDevMode, currentPage + 1);
         }).build());
      }

      Set<String> currentPokemonAspects = new HashSet<>();

      for(String asp : editingPokemon.getAspects()) {
         currentPokemonAspects.add(asp.toLowerCase().trim());
      }

      int[] modelSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34};
      int[] toggleSlots = new int[]{19, 20, 21, 22, 23, 24, 25, 37, 38, 39, 40, 41, 42, 43};

      for(int i = 0; i < 14; ++i) {
         final int i_ = i;
         int modelSlot = modelSlots[i_];
         int toggleSlot = toggleSlots[i_];
         int aspectIndex = currentPage * maxPerPage + i_;
         if (aspectIndex < aspectList.size()) {
            String aspectName = (String)aspectList.get(aspectIndex);
            boolean hasAspect = currentPokemonAspects.contains(aspectName);
            String displayAspect = aspectName.toUpperCase();
            Pokemon previewPokemon = editingPokemon.clone(false, player.getServer().registryAccess());
            Set<String> tempAspects = new HashSet<>(previewPokemon.getForcedAspects());
            if (!hasAspect) {
               tempAspects.add(aspectName);
               previewPokemon.setForcedAspects(tempAspects);
               previewPokemon.updateAspects();
               previewPokemon.updateForm();
            }

            ItemStack pokeModel = PokemonItem.from(previewPokemon);
            pokeModel.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String previewFormat = EditorConfigManager.lang.aspects_preview != null ? EditorConfigManager.lang.aspects_preview : "<aqua>Preview: <aspect></aqua>";
            GooeyButton modelBtn = GooeyButton.builder().display(pokeModel).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(previewFormat.replace("<aspect>", displayAspect))).build();
            String toggleIconStr = hasAspect ? EditorConfigManager.guiconfig.icons.toggle_on : EditorConfigManager.guiconfig.icons.toggle_off;
            ItemStack toggleIcon = new ItemStack(getSafeItem(toggleIconStr, Items.GLASS_PANE));
            toggleIcon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String onFormat = EditorConfigManager.lang.aspects_toggled_on != null ? EditorConfigManager.lang.aspects_toggled_on : "<green>Enabled: <aspect></green>";
            String offFormat = EditorConfigManager.lang.aspects_toggled_off != null ? EditorConfigManager.lang.aspects_toggled_off : "<gray>Disabled: <aspect></gray>";
            String toggleText = hasAspect ? onFormat.replace("<aspect>", displayAspect) : offFormat.replace("<aspect>", displayAspect);
            GooeyButton toggleBtn = GooeyButton.builder().display(toggleIcon).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(toggleText)).onClick((a) -> {
               Set<String> forced = new HashSet<>(editingPokemon.getForcedAspects());
               if (hasAspect) {
                  forced.remove(aspectName);
               } else {
                  forced.add(aspectName);
               }

               editingPokemon.setForcedAspects(forced);
               editingPokemon.updateAspects();
               editingPokemon.updateForm();
               editingPokemon.heal();
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               open(player, originalPokemon, editingPokemon, isDevMode, currentPage);
            }).build();
            template.set(modelSlot, modelBtn);
            template.set(toggleSlot, toggleBtn);
         } else {
            ItemStack emptySlot = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_light : null, Items.LIGHT_GRAY_STAINED_GLASS_PANE));
            emptySlot.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
            GooeyButton emptyBtn = GooeyButton.builder().display(emptySlot).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
            template.set(modelSlot, emptyBtn);
            template.set(toggleSlot, emptyBtn);
         }
      }

      String titleFormat = EditorConfigManager.lang.aspects_gui_title != null ? EditorConfigManager.lang.aspects_gui_title : "<dark_gray>Aspects (<current_page>/<total_pages>)</dark_gray>";
      String title = titleFormat.replace("<current_page>", String.valueOf(currentPage + 1)).replace("<total_pages>", String.valueOf(totalPages));
      GooeyPage guiPage = GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(title)).build();
      UIManager.openUIForcefully(player, guiPage);
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
