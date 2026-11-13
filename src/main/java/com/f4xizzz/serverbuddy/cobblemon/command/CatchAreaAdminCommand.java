package com.f4xizzz.serverbuddy.cobblemon.command;

import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaEconomyConfig;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegion;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegionManager;
import com.f4xizzz.serverbuddy.economy.EconomyManager;
import com.f4xizzz.serverbuddy.economy.PermissionPriceResolver;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class CatchAreaAdminCommand {
   private static final Map<UUID, BlockPos> PENDING_POS1 = new HashMap<>();

   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("catcharea").requires((source) -> SbPermissions.check(source, "serverbuddy.catcharea.admin", 2))).then(Commands.literal("list").executes(CatchAreaAdminCommand::executeList))).then(Commands.literal("economytest").executes(CatchAreaAdminCommand::executeEconomyTest))).then(((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word()).then(Commands.literal("pos1").executes(CatchAreaAdminCommand::executePos1))).then(Commands.literal("pos2").executes(CatchAreaAdminCommand::executePos2))).then(Commands.literal("remove").executes(CatchAreaAdminCommand::executeRemove)));
   }

   private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> context) throws Exception {
      return ((CommandSourceStack)context.getSource()).getPlayerOrException();
   }

   private static int executePos1(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = requirePlayer(context);
         String nome = StringArgumentType.getString(context, "name");
         PENDING_POS1.put(player.getUUID(), player.blockPosition());
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Position 1 of area '" + nome + "' set at " + player.blockPosition().toShortString() + ". Now go to the other corner and use 'pos2'.").withStyle(ChatFormatting.GREEN), false);
         return 1;
      } catch (Exception var3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }
   }

   private static int executePos2(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = requirePlayer(context);
         String nome = StringArgumentType.getString(context, "name");
         BlockPos pos1 = (BlockPos)PENDING_POS1.get(player.getUUID());
         if (pos1 == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("You need to mark position 1 first (/serverbuddy catcharea " + nome + " pos1)."));
            return 0;
         } else {
            BlockPos pos2 = player.blockPosition();
            String dimension = player.serverLevel().dimension().location().toString();
            CatchAreaRegion region = new CatchAreaRegion(nome, dimension, pos1, pos2);
            region.ensureMinHeight(CatchAreaConfigManager.getConfig().minHeight);
            CatchAreaRegionManager.save(region);
            PENDING_POS1.remove(player.getUUID());
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> {
               MutableComponent var10000 = Component.literal("Catch area '" + nome + "' created/updated! ").withStyle(ChatFormatting.GREEN);
               int var10001 = region.getMinX();
               return var10000.append(Component.literal("(" + var10001 + "," + region.getMinY() + "," + region.getMinZ() + ") -> (" + region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + ") em " + dimension).withStyle(ChatFormatting.GRAY));
            }, false);
            return 1;
         }
      } catch (Exception var7) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }
   }

   private static int executeRemove(CommandContext<CommandSourceStack> context) {
      String nome = StringArgumentType.getString(context, "name");
      boolean removed = CatchAreaRegionManager.remove(nome);
      if (removed) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Catch area '" + nome + "' removed.").withStyle(ChatFormatting.GREEN), false);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Catch area '" + nome + "' does not exist."));
         return 0;
      }
   }

   private static int executeEconomyTest(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = requirePlayer(context);
         CatchAreaEconomyConfig economy = new CatchAreaEconomyConfig();
         BigDecimal balance = EconomyManager.getBalance(player, economy.currencyId);
         double choicePrice = PermissionPriceResolver.resolve(player, economy.choiceEntryPrice, economy.choiceEntryPricePermissions);
         double randomPrice = PermissionPriceResolver.resolve(player, economy.randomEntryPrice, economy.randomEntryPricePermissions);
         CommandSourceStack source = (CommandSourceStack)context.getSource();
         source.sendSuccess(() -> Component.literal("===== Teste de Economia (Fase 2) =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
         source.sendSuccess(() -> Component.literal("Currency: ").withStyle(ChatFormatting.GRAY).append(Component.literal(economy.currencyId).withStyle(ChatFormatting.AQUA)), false);
         source.sendSuccess(() -> Component.literal("Your balance: ").withStyle(ChatFormatting.GRAY).append(Component.literal(balance.toPlainString()).withStyle(ChatFormatting.AQUA)), false);
         source.sendSuccess(() -> Component.literal("Entry price (choice): ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(choicePrice)).withStyle(ChatFormatting.AQUA)), false);
         source.sendSuccess(() -> Component.literal("Entry price (random): ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(randomPrice)).withStyle(ChatFormatting.AQUA)), false);

         for(CatchAreaEconomyConfig.DurationPackage pkg : economy.durationPackages) {
            source.sendSuccess(() -> Component.literal("- Pacote " + pkg.label + ": ").withStyle(ChatFormatting.YELLOW).append(Component.literal(pkg.minutes + " min por " + pkg.price).withStyle(ChatFormatting.AQUA)), false);
         }

         return 1;
      } catch (Exception e) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Economy test failed: " + e.getMessage()));
         return 0;
      }
   }

   private static int executeList(CommandContext<CommandSourceStack> context) {
      Map<String, CatchAreaRegion> regions = CatchAreaRegionManager.getAll();
      if (regions.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No catch areas created yet.").withStyle(ChatFormatting.GRAY), false);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("===== Catch Areas (" + regions.size() + ") =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);

         for(CatchAreaRegion region : regions.values()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> {
               String var10000 = region.name;
               MutableComponent var1 = Component.literal("- " + var10000).withStyle(ChatFormatting.YELLOW);
               String var10001 = region.dimension;
               return var1.append(Component.literal(" em " + var10001 + " (" + region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() + ") -> (" + region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + ")" + (region.enabled ? "" : " [DISABLED]")).withStyle(ChatFormatting.GRAY));
            }, false);
         }

         return 1;
      }
   }
}
