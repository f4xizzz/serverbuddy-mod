package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.redeem.RedeemCode;
import com.f4xizzz.serverbuddy.redeem.RedeemConfig;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemManager;
import com.f4xizzz.serverbuddy.redeem.RedeemMessages;
import com.f4xizzz.serverbuddy.redeem.RedeemRules;
import com.f4xizzz.serverbuddy.storage.StorageService;
import com.f4xizzz.serverbuddy.util.CommandRunner;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RedeemCommand {
   private RedeemCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      for(String alias : resolveAliases()) {
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias).requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.use", 0))).then(Commands.argument("code", StringArgumentType.word()).executes(RedeemCommand::executeRedeem)));
      }

   }

   private static List<String> resolveAliases() {
      List<String> configured = RedeemConfigManager.getConfig().aliases;
      List<String> clean = new ArrayList<>();
      if (configured != null) {
         for(String alias : configured) {
            if (alias != null) {
               String trimmed;
               for(trimmed = alias.trim(); trimmed.startsWith("/"); trimmed = trimmed.substring(1)) {
               }

               if (!trimmed.isBlank()) {
                  clean.add(trimmed);
               }
            }
         }
      }

      return clean.isEmpty() ? List.of("redeem") : clean;
   }

   private static int executeRedeem(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var7) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      String typed = StringArgumentType.getString(context, "code");
      RedeemConfig config = RedeemConfigManager.getConfig();
      RedeemMessages messages = config.messages;
      String playerName = player.getName().getString();
      // The name from the config is the one claims are kept under, so "BoasVindas" and "boasvindas" are the same code.
      String code = RedeemConfigManager.canonicalName(typed);
      RedeemCode codeDef = code == null ? null : config.codes.get(code);
      if (codeDef == null) {
         reply(context, RedeemRules.fill(messages.invalidCode, typed, playerName, ""));
         return 0;
      }

      String permission = codeDef.permission == null ? "" : codeDef.permission.trim();
      if (!permission.isEmpty() && !SbPermissions.check((SharedSuggestionProvider)context.getSource(), permission, 2)) {
         reply(context, RedeemRules.fill(messages.noPermission, code, playerName, permission));
         ServerBuddy.LOGGER.info("[Redeem] {} was refused the code '{}': it needs the permission '{}'.", playerName, code, permission);
         return 0;
      }

      if (RedeemRules.isExpired(RedeemConfigManager.expiryOf(codeDef), System.currentTimeMillis())) {
         reply(context, RedeemRules.fill(messages.expired, code, playerName, permission));
         return 0;
      }

      RedeemManager.RedeemResult result = RedeemManager.redeem(player.getUUID(), code, codeDef.maxUses);
      switch (result) {
         case ALREADY_REDEEMED:
            reply(context, RedeemRules.fill(messages.alreadyRedeemed, code, playerName, permission));
            return 0;
         case MAX_USES:
            reply(context, RedeemRules.fill(messages.usageLimitReached, code, playerName, permission));
            return 0;
         case SUCCESS:
            if (!StorageService.deliverRedeem(player, code, codeDef)) {
               CommandRunner.runAll(((CommandSourceStack)context.getSource()).getServer(), codeDef.commands, playerName);
            }

            reply(context, RedeemRules.fill(messages.success, code, playerName, permission));
            ServerBuddy.LOGGER.info("[Redeem] {} redeemed the code '{}'.", playerName, code);
            return 1;
         default:
            return 0;
      }
   }

   private static void reply(CommandContext<CommandSourceStack> context, String miniMessage) {
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(miniMessage), false);
   }
}
