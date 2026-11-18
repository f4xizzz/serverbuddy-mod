package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.redeem.RedeemCode;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemManager;
import com.f4xizzz.serverbuddy.redeem.RedeemRules;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Map;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RedeemAdminCommand {
   private RedeemAdminCommand() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("redeem").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem", 2))).then(((LiteralArgumentBuilder)Commands.literal("reload").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.reload", 2))).executes(RedeemAdminCommand::executeReload))).then(((LiteralArgumentBuilder)Commands.literal("list").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.list", 2))).executes(RedeemAdminCommand::executeList))).then(((LiteralArgumentBuilder)Commands.literal("reset").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.reset", 2))).then(Commands.argument("player", GameProfileArgument.gameProfile()).then(Commands.argument("code", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RedeemConfigManager.getConfig().codes.keySet(), builder)).executes(RedeemAdminCommand::executeReset))));
   }

   private static int executeReload(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      RedeemConfigManager.loadConfig();
      RedeemCommand.register(source.getServer().getCommands().getDispatcher());

      for(ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
         source.getServer().getCommands().sendCommands(online);
      }

      source.sendSuccess(() -> MiniMessageUtil.parse("<green><b>\u2714 Redeem code settings reloaded!"), false);
      return 1;
   }

   private static int executeList(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      Map<String, RedeemCode> codes = RedeemConfigManager.getConfig().codes;
      if (codes.isEmpty()) {
         source.sendSuccess(() -> Component.literal("No codes configured."), false);
         return 0;
      } else {
         long now = System.currentTimeMillis();
         for(Map.Entry<String, RedeemCode> entry : codes.entrySet()) {
            String code = (String)entry.getKey();
            RedeemCode def = (RedeemCode)entry.getValue();
            int uses = RedeemManager.getUses(code);
            String maxLabel = def.maxUses <= 0 ? "∞" : String.valueOf(def.maxUses);
            RedeemRules.Expiry expiry = RedeemConfigManager.expiryOf(def);
            String expires = expiry == RedeemRules.Expiry.Never.INSTANCE ? "" : (RedeemRules.isExpired(expiry, now) ? " (EXPIRED " : " (expires ") + RedeemRules.describe(expiry) + ")";
            String permission = def.permission != null && !def.permission.isBlank() ? " (permission: " + def.permission + ")" : "";
            source.sendSuccess(() -> Component.literal("- " + code + ": " + uses + "/" + maxLabel + permission + expires), false);
         }

         return codes.size();
      }
   }

   private static int executeReset(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      String typed = StringArgumentType.getString(context, "code");
      String code = RedeemConfigManager.canonicalName(typed);
      if (code == null) {
         code = typed;
      }

      Collection<GameProfile> profiles;
      try {
         profiles = GameProfileArgument.getGameProfiles(context, "player");
      } catch (CommandSyntaxException var7) {
         source.sendFailure(Component.literal("Player not found (they must have joined the server at least once)."));
         return 0;
      }

      int resetCount = 0;

      for(GameProfile profile : profiles) {
         if (RedeemManager.resetForPlayer(profile.getId(), code)) {
            ++resetCount;
         }
      }

      if (resetCount == 0) {
         final String shown = code;
         source.sendSuccess(() -> MiniMessageUtil.parse("<red>None of the given players had redeemed the code '<dark_red>" + shown + "</dark_red>'."), false);
         return 0;
      } else {
         final int resetCountFinal = resetCount;
         final String shown = code;
         source.sendSuccess(() -> MiniMessageUtil.parse("<green>Code '<dark_green>" + shown + "</dark_green>' reset for " + resetCountFinal + " player(s) — they can redeem it again."), false);
         ServerBuddy.LOGGER.info("[Redeem] Code '{}' reset via /serverbuddy redeem reset ({} player(s)).", code, resetCount);
         return resetCount;
      }
   }
}
