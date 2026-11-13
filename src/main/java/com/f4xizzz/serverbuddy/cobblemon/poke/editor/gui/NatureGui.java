package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.List;
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

public class NatureGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
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
               if (i != 18 && i != 26 && (i < 45 || i > 53 || i == 49)) {
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

      List<Nature> filteredNatures = new ArrayList<>();
      Nature seriousNature = null;

      for(Nature n : Natures.all()) {
         String name = n.getName().getPath();
         if (!name.equals("hardy") && !name.equals("docile") && !name.equals("bashful") && !name.equals("quirky")) {
            if (name.equals("serious")) {
               seriousNature = n;
            } else {
               filteredNatures.add(n);
            }
         }
      }

      if (seriousNature != null) {
         filteredNatures.add(seriousNature);
      }

      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

      for(int i = 0; i < slots.length; ++i) {
         final int i_ = i;
         if (i_ < filteredNatures.size()) {
            Nature nature = (Nature)filteredNatures.get(i_);
            boolean isCurrent = nature.getName().equals(editingPokemon.getNature().getName());
            String natureName = nature.getName().getPath();
            String var10000 = natureName.substring(0, 1).toUpperCase();
            String rawName = var10000 + natureName.substring(1).replace("_", " ");
            String nameFormat;
            if (isCurrent) {
               nameFormat = EditorConfigManager.lang.nature_current_name != null ? EditorConfigManager.lang.nature_current_name : "<green><nature> (Current)</green>";
            } else {
               nameFormat = EditorConfigManager.lang.nature_available_name != null ? EditorConfigManager.lang.nature_available_name : "<aqua><nature></aqua>";
            }

            String displayName = nameFormat.replace("<nature>", rawName);
            Item fallbackMint = getSafeItem("cobblemon:serious_mint", Items.PAPER);
            Item mintItem = getSafeItem("cobblemon:" + natureName + "_mint", fallbackMint);
            ItemStack stack = new ItemStack(mintItem);
            stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            if (isCurrent) {
               stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            List<Component> lore = new ArrayList<>();
            if (isCurrent) {
               String currentLore = EditorConfigManager.lang.nature_current_lore != null ? EditorConfigManager.lang.nature_current_lore : "<green>Current Nature</green>";
               lore.add(EditorConfigManager.parse(currentLore));
            } else {
               double cost = EditorConfigManager.prices.change_nature;
               String formattedCost = cost == (double)((long)cost) ? String.format("%d", (long)cost) : String.format("%s", cost);
               String lore1 = EditorConfigManager.lang.nature_select_lore1 != null ? EditorConfigManager.lang.nature_select_lore1 : "<gray>Click to select.</gray>";
               lore.add(EditorConfigManager.parse(lore1));
               lore.add(Component.empty());
               String lore2 = EditorConfigManager.lang.nature_select_lore2 != null ? EditorConfigManager.lang.nature_select_lore2 : "<gray>Cost: $<cost></gray>";
               lore.add(EditorConfigManager.parse(lore2.replace("<cost>", EditorConfigManager.formatCurrency(formattedCost))));
               String lore3 = EditorConfigManager.lang.nature_select_lore3 != null ? EditorConfigManager.lang.nature_select_lore3 : "<gray>Will be charged.</gray>";
               lore.add(EditorConfigManager.parse(lore3));
            }

            template.set(slots[i_], GooeyButton.builder().display(stack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(displayName)).with(DataComponents.LORE, new ItemLore(lore)).onClick((a) -> {
               if (!isCurrent) {
                  editingPokemon.setNature(nature);
                  editingPokemon.updateForm();
                  editingPokemon.heal();
                  SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
                  PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
               }
            }).build());
         }
      }

      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String guiTitle = EditorConfigManager.lang.nature_gui_title != null ? EditorConfigManager.lang.nature_gui_title : "<dark_gray>Nature Editor</dark_gray>";
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
