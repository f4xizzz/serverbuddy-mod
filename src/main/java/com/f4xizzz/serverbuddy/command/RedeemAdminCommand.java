package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.redeem.RedeemCode;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemManager;
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
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("redeem").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem", 2))).then(((LiteralArgumentBuilder)Commands.literal("reload").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.reload", 2))).executes(RedeemAdminCommand::executeReload))).then(((LiteralArgumentBuilder)Commands.literal("list").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.list", 2))).executes(RedeemAdminCommand::executeList))).then(((LiteralArgumentBuilder)Commands.literal("reset").requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.reset", 2))).then(Commands.argument("jogador", GameProfileArgument.gameProfile()).then(Commands.argument("codigo", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RedeemConfigManager.getConfig().codigos.keySet(), builder)).executes(RedeemAdminCommand::executeReset))));
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
      Map<String, RedeemCode> codigos = RedeemConfigManager.getConfig().codigos;
      if (codigos.isEmpty()) {
         source.sendSuccess(() -> Component.literal("No codes configured."), false);
         return 0;
      } else {
         for(Map.Entry<String, RedeemCode> entry : codigos.entrySet()) {
            String code = (String)entry.getKey();
            RedeemCode def = (RedeemCode)entry.getValue();
            int uses = RedeemManager.getUses(code);
            String maxLabel = def.max_usos <= 0 ? "\u221e" : String.valueOf(def.max_usos);
            source.sendSuccess(() -> Component.literal("- " + code + ": " + uses + "/" + maxLabel + (def.permissao != null && !def.permissao.isBlank() ? " (permission: " + def.permissao + ")" : "")), false);
         }

         return codigos.size();
      }
   }

   private static int executeReset(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      String code = StringArgumentType.getString(context, "codigo");

      Collection<GameProfile> profiles;
      try {
         profiles = GameProfileArgument.getGameProfiles(context, "jogador");
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
         source.sendSuccess(() -> MiniMessageUtil.parse("<red>None of the given players had redeemed the code '<dark_red>" + code + "</dark_red>'."), false);
         return 0;
      } else {
         final int resetCountFinal = resetCount;
         source.sendSuccess(() -> MiniMessageUtil.parse("<green>Code '<dark_green>" + code + "</dark_green>' reset for " + resetCountFinal + " player(s) \u2014 they can redeem it again."), false);
         ServerBuddy.LOGGER.info("[Redeem] Code '{}' reset via /serverbuddy redeem reset ({} player(s)).", code, resetCount);
         return resetCount;
      }
   }
}
