package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.mark.Marks;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

public class MarkGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      open(player, originalPokemon, editingPokemon, isDevMode, 0);
   }

   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, int page) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      List<Mark> allMarks = new ArrayList<>();

      for(Mark m : Marks.all()) {
         if (!m.getIdentifier().getPath().toLowerCase().contains("ribbon")) {
            allMarks.add(m);
         }
      }

      int pageSize = 21;
      int maxPages = (int)Math.ceil((double)allMarks.size() / (double)pageSize);
      if (maxPages == 0) {
         maxPages = 1;
      }

      int start = page * pageSize;
      int end = Math.min(start + pageSize, allMarks.size());
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
         if (i != 0 && i != 3 && i != 5 && i != 8 && i != 9 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7) {
               if ((i < 18 || i > 26) && (i < 45 || i > 53 || i == 48 || i == 49 || i == 50)) {
                  if (i >= 27 && i <= 44 && i != 40) {
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

      Mark currentMark = editingPokemon.getActiveMark();
      ItemStack redCardItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.mark_remove : null, Items.PAPER));
      redCardItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      if (currentMark == null) {
         redCardItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      }

      List<Component> removeLore = new ArrayList<>();
      String loreRemove1 = EditorConfigManager.lang.mark_remove_lore1 != null ? EditorConfigManager.lang.mark_remove_lore1 : "<gray>Removes the current mark from the Pok\u00e9mon.</gray>";
      String loreRemoveFree = EditorConfigManager.lang.mark_remove_lore_free != null ? EditorConfigManager.lang.mark_remove_lore_free : "<green>Cost: Free!</green>";
      removeLore.add(EditorConfigManager.parse(loreRemove1));
      removeLore.add(Component.empty());
      removeLore.add(EditorConfigManager.parse(loreRemoveFree));
      String titleRemoveCurrent = EditorConfigManager.lang.mark_remove_current != null ? EditorConfigManager.lang.mark_remove_current : "<gold>No mark (Current)</gold>";
      String titleRemoveGeneral = EditorConfigManager.lang.mark_remove_title != null ? EditorConfigManager.lang.mark_remove_title : "<red>Remove Mark</red>";
      String removeTitle = currentMark == null ? titleRemoveCurrent : titleRemoveGeneral;
      template.set(4, GooeyButton.builder().display(redCardItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(removeTitle)).with(DataComponents.LORE, new ItemLore(removeLore)).onClick((a) -> {
         if (currentMark != null) {
            editingPokemon.setActiveMark((Mark)null);
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
         }

      }).build());
      ItemStack randomItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.mark_random : null, Items.STRING));
      randomItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      List<Component> randomLore = new ArrayList<>();
      String randomLoreStr = EditorConfigManager.lang.mark_random_lore != null ? EditorConfigManager.lang.mark_random_lore : "<gray>Draws a random mark.</gray>";
      randomLore.add(EditorConfigManager.parse(randomLoreStr));
      String randomTitle = EditorConfigManager.lang.mark_random_title != null ? EditorConfigManager.lang.mark_random_title : "<light_purple>Apply Random Mark</light_purple>";
      template.set(40, GooeyButton.builder().display(randomItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(randomTitle)).with(DataComponents.LORE, new ItemLore(randomLore)).onClick((a) -> {
         if (!allMarks.isEmpty()) {
            editingPokemon.setActiveMark((Mark)allMarks.get((new Random()).nextInt(allMarks.size())));
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
         }

      }).build());
      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

      for(int i = start; i < end; ++i) {
         final int i_ = i;
         Mark mark = (Mark)allMarks.get(i_);
         int slotIndex = i_ - start;
         boolean isCurrent = currentMark != null && currentMark.getIdentifier().equals(mark.getIdentifier());
         String markId = mark.getIdentifier().getPath();
         double cost = EditorConfigManager.prices.change_mark_default_cost;
         if (EditorConfigManager.prices.custom_mark_costs != null) {
            for(Map.Entry<String, Double> entry : EditorConfigManager.prices.custom_mark_costs.entrySet()) {
               if (markId.contains((CharSequence)entry.getKey())) {
                  cost = (Double)entry.getValue();
                  break;
               }
            }
         }

         String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
         ItemStack item = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.mark_item : null, Items.PAPER));
         item.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
         if (isCurrent) {
            item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
         }

         String titleKey = mark.getTitle();
         String rawName;
         if (titleKey != null && !titleKey.trim().isEmpty()) {
            rawName = Component.translatable(titleKey).getString();
            rawName = rawName.replace("%1$s", "").replace("%s", "").trim();
         } else {
            rawName = markId.replace("_", " ");
         }

         if (rawName.length() > 0) {
            String var10000 = rawName.substring(0, 1).toUpperCase();
            rawName = var10000 + rawName.substring(1);
         }

         String nameFormat;
         if (isCurrent) {
            nameFormat = EditorConfigManager.lang.mark_current_name != null ? EditorConfigManager.lang.mark_current_name : "<green><mark> (Current)</green>";
         } else {
            nameFormat = EditorConfigManager.lang.mark_available_name != null ? EditorConfigManager.lang.mark_available_name : "<aqua><mark></aqua>";
         }

         String displayName = nameFormat.replace("<mark>", rawName);
         List<Component> lore = new ArrayList<>();
         if (isCurrent) {
            String loreCurrent = EditorConfigManager.lang.mark_current_lore != null ? EditorConfigManager.lang.mark_current_lore : "<green>Current Mark</green>";
            lore.add(EditorConfigManager.parse(loreCurrent));
         } else {
            String lore1 = EditorConfigManager.lang.mark_select_lore1 != null ? EditorConfigManager.lang.mark_select_lore1 : "<gray>Click to select.</gray>";
            lore.add(EditorConfigManager.parse(lore1));
            lore.add(Component.empty());
            String lore2 = EditorConfigManager.lang.mark_select_lore2 != null ? EditorConfigManager.lang.mark_select_lore2 : "<gray>Cost: $<cost></gray>";
            lore.add(EditorConfigManager.parse(lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
            String lore3 = EditorConfigManager.lang.mark_select_lore3 != null ? EditorConfigManager.lang.mark_select_lore3 : "<gray>Will be charged.</gray>";
            lore.add(EditorConfigManager.parse(lore3));
         }

         template.set(slots[slotIndex], GooeyButton.builder().display(item).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(displayName)).with(DataComponents.LORE, new ItemLore(lore)).onClick((a) -> {
            if (!isCurrent) {
               editingPokemon.setActiveMark(mark);
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
            }
         }).build());
      }

      if (page > 0) {
         ItemStack prevItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.previous_page : null, Items.ARROW));
         String prevName = EditorConfigManager.lang.mark_page_prev != null ? EditorConfigManager.lang.mark_page_prev : "<yellow>Previous</yellow>";
         template.set(48, GooeyButton.builder().display(prevItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            open(player, originalPokemon, editingPokemon, isDevMode, page - 1);
         }).build());
      } else {
         template.set(48, btnBlack);
      }

      if (page < maxPages - 1) {
         ItemStack nextItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.next_page : null, Items.ARROW));
         String nextName = EditorConfigManager.lang.mark_page_next != null ? EditorConfigManager.lang.mark_page_next : "<yellow>Next</yellow>";
         template.set(50, GooeyButton.builder().display(nextItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(nextName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            open(player, originalPokemon, editingPokemon, isDevMode, page + 1);
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
      String titleFormat = EditorConfigManager.lang.mark_gui_title != null ? EditorConfigManager.lang.mark_gui_title : "<dark_gray>Mark Editor (<current_page>/<total_pages>)</dark_gray>";
      String guiTitle = titleFormat.replace("<current_page>", String.valueOf(page + 1)).replace("<total_pages>", String.valueOf(maxPages));
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build());
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
