package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.BannedSpeciesConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfig;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.PreEvolutionResolver;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RentalFlowManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class PcCatchAreaGui {
   private static final int ITEMS_PER_PAGE = 21;
   private static final int[][] T_SLOTS = buildTSlots();

   private PcCatchAreaGui() {
   }

   private static int[][] buildTSlots() {
      int[][] slots = new int[21][2];
      int idx = 0;

      for(int row = 1; row <= 3; ++row) {
         for(int col = 1; col <= 7; ++col) {
            slots[idx][0] = row;
            slots[idx][1] = col;
            ++idx;
         }
      }

      return slots;
   }

   public static void open(ServerPlayer player, String areaName, int pageIndex) {
      List<Pokemon> allPc = new ArrayList<>();
      PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

      for(PCBox box : pc.getBoxes()) {
         for(Pokemon pokemon : box) {
            if (pokemon != null) {
               allPc.add(pokemon);
            }
         }
      }

      int totalPages = Math.max(1, (int)Math.ceil((double)allPc.size() / 21.0));
      int page = Math.max(0, Math.min(pageIndex, totalPages - 1));
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      buildBorders(template);
      buildPcButtons(template, player, areaName, allPc, page);
      buildFooterNav(template, player, areaName, page, totalPages);
      GuiSounds.playOpen(player);
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("PC (" + (page + 1) + "/" + totalPages + ")")).build());
   }

   private static void buildBorders(ChestTemplate.Builder template) {
      for(int col = 0; col <= 8; ++col) {
         template.set(0, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE));
         template.set(4, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE));
      }

      for(int row = 1; row <= 3; ++row) {
         template.set(row, 0, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 8, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
      }

      for(int col = 0; col <= 8; ++col) {
         template.set(5, col, GuiIcons.glassButton(Items.BLACK_STAINED_GLASS_PANE));
      }

   }

   private static void buildPcButtons(ChestTemplate.Builder template, ServerPlayer player, String areaName, List<Pokemon> allPc, int page) {
      int start = page * 21;

      for(int i = 0; i < T_SLOTS.length; ++i) {
         int pokeIndex = start + i;
         if (pokeIndex < allPc.size()) {
            template.set(T_SLOTS[i][0], T_SLOTS[i][1], buildPcButton(player, areaName, (Pokemon)allPc.get(pokeIndex)));
         } else {
            ItemStack empty = GuiIcons.resolveIcon(CatchAreaGuiIconsConfigManager.getConfig().emptySlotIcon);
            empty.set(DataComponents.CUSTOM_NAME, Component.literal("Vazio").withStyle(ChatFormatting.DARK_GRAY));
            GuiIcons.hideAdditionalTooltip(empty);
            template.set(T_SLOTS[i][0], T_SLOTS[i][1], GooeyButton.builder().display(empty).build());
         }
      }

   }

   private static void buildFooterNav(ChestTemplate.Builder template, ServerPlayer player, String areaName, int page, int totalPages) {
      int prevPage = Math.max(0, page - 1);
      int nextPage = Math.min(totalPages - 1, page + 1);
      CatchAreaGuiIconsConfig icons = CatchAreaGuiIconsConfigManager.getConfig();
      ItemStack prevIcon = GuiIcons.resolveIcon(icons.previousPageIcon);
      prevIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Previous page").withStyle(ChatFormatting.YELLOW));
      GuiIcons.hideAdditionalTooltip(prevIcon);
      template.set(5, 0, GooeyButton.builder().display(prevIcon).onClick((action) -> {
         GuiSounds.playClick(player);
         open(player, areaName, prevPage);
      }).build());
      ItemStack nextIcon = GuiIcons.resolveIcon(icons.nextPageIcon);
      nextIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Next page").withStyle(ChatFormatting.YELLOW));
      GuiIcons.hideAdditionalTooltip(nextIcon);
      template.set(5, 8, GooeyButton.builder().display(nextIcon).onClick((action) -> {
         GuiSounds.playClick(player);
         open(player, areaName, nextPage);
      }).build());
      ItemStack exitIcon = GuiIcons.resolveIcon(icons.exitIcon);
      exitIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(ChatFormatting.RED));
      GuiIcons.hideAdditionalTooltip(exitIcon);
      template.set(5, 4, GooeyButton.builder().display(exitIcon).onClick((action) -> {
         GuiSounds.playCancel(player);
         PremiumCatchAreaGui.open(player, areaName);
      }).build());
   }

   private static Button buildPcButton(ServerPlayer player, String areaName, Pokemon pokemon) {
      boolean blocked = BannedSpeciesConfigManager.isBlocked(pokemon.getSpecies());
      ItemStack icon = PokemonItem.from(pokemon);
      icon.set(DataComponents.CUSTOM_NAME, pokemon.getDisplayName(false));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.literal("Level " + pokemon.getLevel()).withStyle(ChatFormatting.GRAY));
      if (blocked) {
         lore.add(Component.literal("Species blocked!").withStyle(ChatFormatting.RED));
      } else {
         Species base = PreEvolutionResolver.resolveBase(pokemon.getSpecies());
         lore.add(Component.literal("Will spawn: ").withStyle(ChatFormatting.GRAY).append(base.getTranslatedName().copy().withStyle(ChatFormatting.YELLOW)));
      }

      icon.set(DataComponents.LORE, new ItemLore(lore));
      GuiIcons.hideAdditionalTooltip(icon);
      if (blocked) {
         return GooeyButton.builder().display(icon).onClick((action) -> GuiSounds.playError(player)).build();
      } else {
         Species base = PreEvolutionResolver.resolveBase(pokemon.getSpecies());
         return GooeyButton.builder().display(icon).onClick((action) -> {
            GuiSounds.playSelect(player);
            RentalFlowManager.PendingRental rental = RentalFlowManager.getOrCreate(player.getUUID());
            rental.areaName = areaName;
            rental.choiceMode = true;
            rental.chosenSpecies = pokemon.getSpecies();
            rental.baseSpecies = base;
            DurationPickerGui.open(player);
         }).build();
      }
   }
}
