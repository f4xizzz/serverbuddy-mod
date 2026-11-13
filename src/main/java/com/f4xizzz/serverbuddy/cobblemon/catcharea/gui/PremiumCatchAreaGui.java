package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
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

public class PremiumCatchAreaGui {
   private static final int[][] T_SLOTS = new int[][]{{1, 3}, {1, 4}, {1, 5}, {2, 3}, {2, 4}, {2, 5}};

   private PremiumCatchAreaGui() {
   }

   public static void open(ServerPlayer player, String areaName) {
      ChestTemplate.Builder template = ChestTemplate.builder(5);
      buildBorders(template);
      buildPartyButtons(template, player, areaName);
      CatchAreaGuiIconsConfig icons = CatchAreaGuiIconsConfigManager.getConfig();
      ItemStack pcIcon = GuiIcons.resolveIcon(icons.pcIcon);
      pcIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Open PC").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}));
      GuiIcons.hideAdditionalTooltip(pcIcon);
      template.set(0, 4, GooeyButton.builder().display(pcIcon).onClick((action) -> {
         GuiSounds.playClick(player);
         PcCatchAreaGui.open(player, areaName, 0);
      }).build());
      ItemStack exitIcon = GuiIcons.resolveIcon(icons.exitIcon);
      exitIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(ChatFormatting.RED));
      GuiIcons.hideAdditionalTooltip(exitIcon);
      template.set(4, 4, GooeyButton.builder().display(exitIcon).onClick((action) -> {
         GuiSounds.playCancel(player);
         MainCatchAreaGui.open(player, areaName);
      }).build());
      GuiSounds.playOpen(player);
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("Catch Area Premium")).build());
   }

   private static void buildBorders(ChestTemplate.Builder template) {
      for(int col = 0; col <= 8; ++col) {
         template.set(0, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE));
         template.set(3, col, GuiIcons.glassButton(col != 0 && col != 8 ? Items.GRAY_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE));
      }

      for(int row : new int[]{1, 2}) {
         template.set(row, 0, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));
         template.set(row, 1, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 2, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 6, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 7, GuiIcons.glassButton(Items.YELLOW_STAINED_GLASS_PANE));
         template.set(row, 8, GuiIcons.glassButton(Items.WHITE_STAINED_GLASS_PANE));
      }

      for(int col = 0; col <= 8; ++col) {
         template.set(4, col, GuiIcons.glassButton(Items.BLACK_STAINED_GLASS_PANE));
      }

   }

   private static void buildPartyButtons(ChestTemplate.Builder template, ServerPlayer player, String areaName) {
      PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

      for(int i = 0; i < T_SLOTS.length; ++i) {
         Pokemon pokemon = party.get(i);
         template.set(T_SLOTS[i][0], T_SLOTS[i][1], buildPartyButton(player, areaName, pokemon));
      }

   }

   private static Button buildPartyButton(ServerPlayer player, String areaName, Pokemon pokemon) {
      if (pokemon == null) {
         ItemStack empty = GuiIcons.resolveIcon(CatchAreaGuiIconsConfigManager.getConfig().emptySlotIcon);
         empty.set(DataComponents.CUSTOM_NAME, Component.literal("Vazio").withStyle(ChatFormatting.DARK_GRAY));
         GuiIcons.hideAdditionalTooltip(empty);
         return GooeyButton.builder().display(empty).build();
      } else {
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
}
