package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaEconomyConfig;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RentalFlowManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class DurationPickerGui {
   private DurationPickerGui() {
   }

   public static void open(ServerPlayer player) {
      List<CatchAreaEconomyConfig.DurationPackage> packages = CatchAreaConfigManager.getConfig().economy.durationPackages;
      ChestTemplate.Builder template = ChestTemplate.builder(3);
      int slot = 0;

      for(CatchAreaEconomyConfig.DurationPackage pkg : packages) {
         if (slot >= 27) {
            break;
         }

         ItemStack icon = new ItemStack(Items.CLOCK);
         icon.set(DataComponents.CUSTOM_NAME, Component.literal(pkg.label).withStyle(ChatFormatting.YELLOW));
         icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Price: " + pkg.price).withStyle(ChatFormatting.GRAY))));
         template.set(slot, GooeyButton.builder().display(icon).onClick((action) -> {
            GuiSounds.playSelect(player);
            RentalFlowManager.getOrCreate(player.getUUID()).duration = pkg;
            CatchAreaConfirmGui.open(player);
         }).build());
         ++slot;
      }

      GuiSounds.playOpen(player);
      UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("Choose duration")).build());
   }
}
