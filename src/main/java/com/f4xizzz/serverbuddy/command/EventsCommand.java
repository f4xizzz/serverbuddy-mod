package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.events.EventConfigManager;
import com.f4xizzz.serverbuddy.events.EventManager;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class EventsCommand {
   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("events").requires((source) -> SbPermissions.check(source, "serverbuddy.events", 2))).then(((LiteralArgumentBuilder)Commands.literal("reload").requires((source) -> SbPermissions.check(source, "serverbuddy.events.reload", 2))).executes(EventsCommand::executeReload));
   }

   private static int executeReload(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      EventConfigManager.loadConfig();
      EventManager.checkEvents(source.getServer());
      source.sendSuccess(() -> MiniMessageUtil.parse("<green><b>\u2714 Event settings reloaded and events recalculated successfully!"), false);
      ServerBuddy.LOGGER.info("[Events] Settings reloaded via /serverbuddy events reload.");
      return 1;
   }
}
