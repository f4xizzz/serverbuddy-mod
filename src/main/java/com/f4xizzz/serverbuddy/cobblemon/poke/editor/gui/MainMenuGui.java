package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public class MainMenuGui {
   private static ItemLore getPokemonTooltip(Pokemon p) {
      return PokemonTooltipUtils.build(EditorConfigManager.lang.pokemon_tooltip, p, EditorConfigManager.tooltipStrings(), Map.of());
   }

   public static void open(ServerPlayer player) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      ItemStack blackGlassItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_dark)));
      blackGlassItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton blackGlass = GooeyButton.builder().display(blackGlassItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack redGlassItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_secondary)));
      redGlassItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton redGlass = GooeyButton.builder().display(redGlassItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack grayGlassItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.background_white)));
      grayGlassItem.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton grayGlass = GooeyButton.builder().display(grayGlassItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i <= 17) {
            template.set(i, redGlass);
         } else if (i >= 18 && i <= 26) {
            template.set(i, blackGlass);
         } else if (i >= 27 && i <= 44) {
            template.set(i, grayGlass);
         } else {
            template.set(i, blackGlass);
         }
      }

      boolean hasDevAccess = player.hasPermissions(2) || SbPermissions.check(player, "serverbuddy.editor.devmode");
      int[] partySlots = new int[]{12, 14, 21, 23, 30, 32};
      int[] editSlots = new int[]{11, 15, 20, 24, 29, 33};
      PlayerPartyStore partyStore = Cobblemon.INSTANCE.getStorage().getParty(player);

      for(int i = 0; i < 6; ++i) {
         final int i_ = i;
         int slotIndex = partySlots[i_];
         int editSlotIndex = editSlots[i_];
         Pokemon pokemon = partyStore.get(new PartyPosition(i_));
         if (pokemon != null) {
            ItemStack pokemonItem = PokemonItem.from(pokemon);
            pokemonItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String var10000 = pokemon.getShiny() ? "<gold>\u2605 </gold>" : "";
            String name = var10000 + pokemon.getDisplayName(false).getString();
            GooeyButton pokemonBtn = GooeyButton.builder().display(pokemonItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(name)).with(DataComponents.LORE, getPokemonTooltip(pokemon)).build();
            template.set(slotIndex, pokemonBtn);
            ItemStack editItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.edit_button)));
            if (editItem.isEmpty() || editItem.is(Items.AIR)) {
               editItem = new ItemStack(Items.PAPER);
            }

            editItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String editTitle = EditorConfigManager.lang.party_edit_button.replace("<pokemon>", pokemon.getDisplayName(false).getString());
            GooeyButton editBtn = GooeyButton.builder().display(editItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(editTitle)).onClick((action) -> {
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               PokemonEditorGui.open(player, pokemon, hasDevAccess);
            }).build();
            template.set(editSlotIndex, editBtn);
         } else {
            ItemStack emptyBall = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.empty_slot)));
            if (emptyBall.isEmpty() || emptyBall.is(Items.AIR)) {
               emptyBall = new ItemStack(Items.GLASS_PANE);
            }

            emptyBall.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            String emptyTitle = EditorConfigManager.lang.party_empty_slot.replace("<slot>", String.valueOf(i_ + 1));
            GooeyButton emptyBtn = GooeyButton.builder().display(emptyBall).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(emptyTitle)).build();
            template.set(slotIndex, emptyBtn);
         }
      }

      ItemStack pcItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.pc_button)));
      if (pcItem.isEmpty() || pcItem.is(Items.AIR)) {
         pcItem = new ItemStack(Items.CHEST);
      }

      pcItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      GooeyButton pcButton = GooeyButton.builder().display(pcItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.pc_button_name)).onClick((action) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.pc);
         PcStorageGui.open(player, 0);
      }).build();
      template.set(8, pcButton);
      ItemStack economyItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.serverconfig.currency_icon_item)));
      if (economyItem.isEmpty() || economyItem.is(Items.AIR)) {
         economyItem = new ItemStack(Items.SUNFLOWER);
      }

      economyItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      GooeyButton walletBtn = GooeyButton.builder().display(economyItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parseWalletBalance(player)).build();
      template.set(0, walletBtn);
      ItemStack closeItem = new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(EditorConfigManager.guiconfig.icons.close_button)));
      if (closeItem.isEmpty() || closeItem.is(Items.AIR)) {
         closeItem = new ItemStack(Items.BARRIER);
      }

      closeItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
      GooeyButton closeBtn = GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(EditorConfigManager.lang.close_button)).onClick((action) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.gui_close);
         UIManager.closeUI(player);
      }).build();
      template.set(49, closeBtn);
      GooeyPage page = GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(EditorConfigManager.lang.main_menu_title)).build();
      UIManager.openUIForcefully(player, page);
   }
}
