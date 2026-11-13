package com.f4xizzz.serverbuddy.cobblemon.catcharea.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaEconomyConfig;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaOccupancyManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegion;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegionManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaSession;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaSessionRepository;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RentalFlowManager;
import com.f4xizzz.serverbuddy.economy.EconomyManager;
import com.f4xizzz.serverbuddy.economy.PermissionPriceResolver;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

public class CatchAreaConfirmGui {
   private CatchAreaConfirmGui() {
   }

   public static void open(ServerPlayer player) {
      RentalFlowManager.PendingRental rental = RentalFlowManager.getOrCreate(player.getUUID());
      if (rental.areaName != null && rental.baseSpecies != null && rental.duration != null) {
         CatchAreaEconomyConfig economy = CatchAreaConfigManager.getConfig().economy;
         double entryPrice = rental.choiceMode ? PermissionPriceResolver.resolve(player, economy.choiceEntryPrice, economy.choiceEntryPricePermissions) : PermissionPriceResolver.resolve(player, economy.randomEntryPrice, economy.randomEntryPricePermissions);
         double totalPrice = entryPrice + rental.duration.price;
         Pokemon preview = new Pokemon();
         preview.setSpecies(rental.baseSpecies);
         preview.initialize();
         ChestTemplate.Builder template = ChestTemplate.builder(3);
         ItemStack previewIcon = PokemonItem.from(preview);
         previewIcon.set(DataComponents.CUSTOM_NAME, rental.baseSpecies.getTranslatedName().copy().withStyle(ChatFormatting.AQUA));
         previewIcon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Area: " + rental.areaName).withStyle(ChatFormatting.GRAY), Component.literal("Time: " + rental.duration.label).withStyle(ChatFormatting.GRAY), Component.literal("Total price: " + totalPrice + " (" + economy.currencyId + ")").withStyle(ChatFormatting.GOLD))));
         template.set(13, GooeyButton.builder().display(previewIcon).build());
         ItemStack confirmIcon = new ItemStack(Items.LIME_DYE);
         confirmIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Confirm").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}));
         template.set(21, GooeyButton.builder().display(confirmIcon).onClick((action) -> confirm(player, rental, totalPrice)).build());
         ItemStack cancelIcon = new ItemStack(Items.RED_DYE);
         cancelIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
         template.set(23, GooeyButton.builder().display(cancelIcon).onClick((action) -> {
            GuiSounds.playCancel(player);
            UIManager.closeUI(player);
            RentalFlowManager.clear(player.getUUID());
            player.displayClientMessage(Component.literal("Rental cancelled.").withStyle(ChatFormatting.GRAY), false);
         }).build());
         GuiSounds.playOpen(player);
         UIManager.openUIForcefully(player, GooeyPage.builder().template(template.build()).title(Component.literal("Confirm Catch Area")).build());
      } else {
         player.displayClientMessage(Component.literal("Something went wrong with your selection, try /catcharea again.").withStyle(ChatFormatting.RED), false);
         RentalFlowManager.clear(player.getUUID());
      }
   }

   private static void confirm(ServerPlayer player, RentalFlowManager.PendingRental rental, double totalPrice) {
      CatchAreaEconomyConfig economy = CatchAreaConfigManager.getConfig().economy;
      if (CatchAreaOccupancyManager.getRenterOf(rental.areaName) != null) {
         GuiSounds.playError(player);
         player.displayClientMessage(Component.literal("This area was just taken by another player! Try /catcharea again.").withStyle(ChatFormatting.RED), false);
         RentalFlowManager.clear(player.getUUID());
      } else if (!EconomyManager.hasBalance(player, economy.currencyId, totalPrice)) {
         GuiSounds.playError(player);
         player.displayClientMessage(Component.literal("Not enough balance! You need " + totalPrice + " (" + economy.currencyId + ").").withStyle(ChatFormatting.RED), false);
      } else if (!EconomyManager.charge(player, economy.currencyId, totalPrice)) {
         GuiSounds.playError(player);
         player.displayClientMessage(Component.literal("The payment could not be charged. Try again.").withStyle(ChatFormatting.RED), false);
      } else {
         GuiSounds.playConfirm(player);
         UIManager.closeUI(player);
         long now = System.currentTimeMillis();
         CatchAreaSession session = new CatchAreaSession();
         session.playerId = player.getUUID();
         session.areaName = rental.areaName;
         session.baseSpeciesId = rental.baseSpecies.getResourceIdentifier().toString();
         session.expiresAtMillis = now + (long)rental.duration.minutes * 60000L;
         session.lastSpawnAtMillis = now - (long)CatchAreaConfigManager.getConfig().spawnIntervalSeconds * 1000L;
         CatchAreaOccupancyManager.occupy(session);
         CatchAreaSessionRepository.saveAsync(session);
         CatchAreaRegion region = CatchAreaRegionManager.get(rental.areaName);
         if (region != null) {
            Vec3 center = region.getCenter();
            player.teleportTo(player.serverLevel(), center.x, center.y, center.z, player.getYRot(), player.getXRot());
         }

         player.displayClientMessage(Component.literal("Catch area rented successfully! ").withStyle(ChatFormatting.GREEN).append(Component.literal(rental.baseSpecies.getName() + " will start spawning soon.").withStyle(ChatFormatting.GRAY)), false);
         RentalFlowManager.clear(player.getUUID());
      }
   }
}
