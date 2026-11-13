package com.f4xizzz.serverbuddy.cobblemon.command;

import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntSpeciesIndex;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class HuntAdminCommand {
   private HuntAdminCommand() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("hunt").requires((source) -> SbPermissions.check(source, "serverbuddy.hunt.admin", 2))).then(((LiteralArgumentBuilder)Commands.literal("reload").requires((source) -> SbPermissions.check(source, "serverbuddy.hunt.admin.reload", 2))).executes(HuntAdminCommand::executeReload))).then(((LiteralArgumentBuilder)Commands.literal("refresh").requires((source) -> SbPermissions.check(source, "serverbuddy.hunt.admin.refresh", 2))).then(Commands.argument("jogador", EntityArgument.player()).executes(HuntAdminCommand::executeRefresh)));
   }

   private static int executeReload(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      HuntConfigManager.loadConfig();
      HuntSpeciesIndex.refresh();
      HuntManager.init();
      source.sendSuccess(() -> MiniMessageUtil.parse("<green><b>\u2714 Hunt settings reloaded!"), false);
      return 1;
   }

   private static int executeRefresh(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
      if (!HuntConfigManager.getConfig().individual) {
         source.sendFailure(Component.literal("This only works with individual hunts (\"individual\": true in hunt.conf) — the current pool is shared by everyone."));
         return 0;
      } else {
         boolean reset = HuntManager.resetPlayer(source.getServer(), target.getUUID());
         if (reset) {
            source.sendSuccess(() -> MiniMessageUtil.parse("<green>Hunts of <gold>" + target.getName().getString() + "</gold> reset."), false);
            return 1;
         } else {
            return 0;
         }
      }
   }
}
