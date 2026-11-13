package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class GuiIcons {
   private GuiIcons() {
   }

   public static GooeyButton glassButton(Item glassItem) {
      ItemStack stack = new ItemStack(glassItem);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
      stack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      return GooeyButton.builder().display(stack).build();
   }

   public static void hideAdditionalTooltip(ItemStack stack) {
      stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
   }

   public static ItemStack resolveIcon(String identifier) {
      try {
         return new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(ResourceLocation.parse(identifier)));
      } catch (Exception var2) {
         return new ItemStack(Items.BARRIER);
      }
   }
}
