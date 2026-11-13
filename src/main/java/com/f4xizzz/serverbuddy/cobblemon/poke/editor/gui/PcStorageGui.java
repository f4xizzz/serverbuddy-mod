package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.PokemonTooltipUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.Map;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class PcStorageGui {
   private static ItemLore getPokemonTooltip(Pokemon p) {
      return PokemonTooltipUtils.build(EditorConfigManager.lang.pokemon_tooltip, p, EditorConfigManager.tooltipStrings(), Map.of());
   }

   public static void open(ServerPlayer player, int boxIndex) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      PCStore pcStore = Cobblemon.INSTANCE.getStorage().getPC(player);
      int totalBoxes = pcStore.getBoxes().size();
      if (boxIndex < 0) {
         boxIndex = totalBoxes - 1;
      }

      if (boxIndex >= totalBoxes) {
         boxIndex = 0;
      }

      PCBox currentBox = (PCBox)pcStore.getBoxes().get(boxIndex);
      ItemStack bgGray = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_light, Items.LIGHT_GRAY_STAINED_GLASS_PANE));
      bgGray.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnGray = GooeyButton.builder().display(bgGray).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.background_dark, Items.BLACK_STAINED_GLASS_PANE));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i >= 45 && i <= 53 && i != 48 && i != 49 && i != 50) {
            template.set(i, btnBlack);
         } else if (i == 0 || i == 8 || i == 9 || i == 17 || i == 18 || i == 26 || i == 27 || i == 35 || i >= 36 && i <= 38 || i >= 42 && i <= 44) {
            template.set(i, btnGray);
         }
      }

      String boxNameFormat = EditorConfigManager.lang.pc_box_name != null ? EditorConfigManager.lang.pc_box_name : "<aqua>Box: <box_number></aqua>";
      ItemStack pcIcon = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.pc_button, Items.CHEST));
      pcIcon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      template.set(40, GooeyButton.builder().display(pcIcon).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(boxNameFormat.replace("<box_number>", String.valueOf(boxIndex + 1)))).build());
      int[] guiSlots = new int[]{1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 39, 41};

      for(int i = 0; i < 30; ++i) {
         final int i_ = i;
         int guiIndex = guiSlots[i_];
         Pokemon pokemon = currentBox.get(i_);
         GooeyButton btn;
         if (pokemon != null) {
            ItemStack pokeItem = PokemonItem.from(pokemon);
            pokeItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String var10000 = pokemon.getShiny() ? "<gold>\u2605 </gold>" : "";
            String name = var10000 + pokemon.getDisplayName(false).getString();
            btn = GooeyButton.builder().display(pokeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(name)).with(DataComponents.LORE, getPokemonTooltip(pokemon)).onClick((action) -> {
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               boolean hasDevAccess = player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.editor.devmode");
               PokemonEditorGui.open(player, pokemon, hasDevAccess);
            }).build();
         } else {
            String emptyName = EditorConfigManager.lang.pc_empty_slot != null ? EditorConfigManager.lang.pc_empty_slot : "<gray>Empty</gray>";
            ItemStack ball = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.empty_slot, Items.GLASS_PANE));
            ball.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            btn = GooeyButton.builder().display(ball).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(emptyName)).build();
         }

         template.set(guiIndex, btn);
      }

      final int boxIndexFinal = boxIndex;
      String prevName = EditorConfigManager.lang.pc_page_prev != null ? EditorConfigManager.lang.pc_page_prev : "<yellow>Previous</yellow>";
      ItemStack leftArrow = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.previous_page, Items.ARROW));
      template.set(48, GooeyButton.builder().display(leftArrow).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         open(player, boxIndexFinal == 0 ? totalBoxes - 1 : boxIndexFinal - 1);
      }).build());
      String nextName = EditorConfigManager.lang.pc_page_next != null ? EditorConfigManager.lang.pc_page_next : "<yellow>Next</yellow>";
      ItemStack rightArrow = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.next_page, Items.ARROW));
      template.set(50, GooeyButton.builder().display(rightArrow).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(nextName)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
         open(player, boxIndexFinal == totalBoxes - 1 ? 0 : boxIndexFinal + 1);
      }).build());
      String backName = EditorConfigManager.lang.pc_button_back != null ? EditorConfigManager.lang.pc_button_back : "<red>Back</red>";
      ItemStack barrier = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons.close_button, Items.BARRIER));
      template.set(49, GooeyButton.builder().display(barrier).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backName)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         MainMenuGui.open(player);
      }).build());
      String guiTitle = EditorConfigManager.lang.pc_gui_title != null ? EditorConfigManager.lang.pc_gui_title : "<dark_gray>PC Storage</dark_gray>";
      GooeyPage page = GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build();
      UIManager.openUIForcefully(player, page);
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
