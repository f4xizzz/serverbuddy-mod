package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.cobblemon.command.CobblemonAdminCommands;
import com.f4xizzz.serverbuddy.core.Integrations;
import com.f4xizzz.serverbuddy.core.Modules;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import com.f4xizzz.serverbuddy.events.EventConfigManager;
import com.f4xizzz.serverbuddy.events.EventManager;
import com.f4xizzz.serverbuddy.logs.ServerConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;

/**
 * Comando raiz /serverbuddy (alias /sb). Aqui ficam so os subcomandos que nao dependem do Cobblemon;
 * os de Pokemon sao acrescentados por CobblemonAdminCommands e os dos modulos por ModuleCommands.
 */
public class ServerBuddyCommand {
   private static final int DEFAULT_PURGE_RADIUS = 32;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
         Commands.literal("serverbuddy")
            .then(Commands.literal("reload")
               .requires((source) -> SbPermissions.check(source, "serverbuddy.reload", 2))
               .executes(ServerBuddyCommand::executeReload))
            .then(Commands.literal("purgedisplays")
               .requires((source) -> SbPermissions.check(source, "serverbuddy.purgedisplays", 2))
               .executes((context) -> executePurgeDisplays(context, DEFAULT_PURGE_RADIUS))
               .then(Commands.argument("raio", IntegerArgumentType.integer(1, 256))
                  .executes((context) -> executePurgeDisplays(context, IntegerArgumentType.getInteger(context, "raio")))))
            .then(com.f4xizzz.serverbuddy.dashboard.DashboardCommands.build())
            .then(com.f4xizzz.serverbuddy.core.CoreCommands.modules())
            .then(com.f4xizzz.serverbuddy.core.CoreCommands.config())
            .then(EventsCommand.build())
            .then(RedeemAdminCommand.build()));
      dispatcher.register(Commands.literal("sb").redirect(root));
   }

   private static int executeReload(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      List<String> failed = new ArrayList<>();
      reloadOne(failed, "banco de dados", DatabaseManager::init);
      reloadOne(failed, "server configuration", ServerConfigManager::loadConfig);
      reloadOne(failed, "eventos", () -> {
         EventConfigManager.loadConfig();
         EventManager.checkEvents(source.getServer());
      });
      reloadOne(failed, "redeem codes", () -> {
         RedeemConfigManager.loadConfig();
         RedeemCommand.register(source.getServer().getCommands().getDispatcher());

         for(ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            source.getServer().getCommands().sendCommands(online);
         }

      });
      if (Integrations.cobblemonFeatures()) {
         CobblemonAdminCommands.reload(source, failed);
      }

      Modules.reloadAll(failed::add);

      if (failed.isEmpty()) {
         source.sendSuccess(() -> Component.literal("✔ All ServerBuddy settings were reloaded!").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), true);
         return 1;
      } else {
         source.sendFailure(Component.literal("⚠ Some settings failed to reload: ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append(Component.literal(String.join(", ", failed) + ". See the console for the full error.").withStyle(ChatFormatting.RED)));
         return 0;
      }
   }

   private static int executePurgeDisplays(CommandContext<CommandSourceStack> context, int radius) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var9) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      ServerLevel world = player.serverLevel();
      AABB box = player.getBoundingBox().inflate((double)radius);
      List<Display.TextDisplay> found = world.getEntitiesOfClass(Display.TextDisplay.class, box, (entity) -> true);
      int count = found.size();

      for(Display.TextDisplay display : found) {
         display.discard();
      }

      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("✔ Removidas " + count + " entidades de texto num raio de " + radius + " blocos.").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), true);
      ServerBuddy.LOGGER.info("{} removeu {} TextDisplayEntity num raio de {} blocos via /serverbuddy purgedisplays.", new Object[]{player.getName().getString(), count, radius});
      return count;
   }

   /** Roda uma recarga e anota o rotulo em [failed] se ela der erro. Usado tambem pelas recargas dos outros pacotes. */
   public static void reloadOne(List<String> failed, String label, Runnable action) {
      try {
         action.run();
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Could not reload " + label + "!", e);
         failed.add(label);
      }

   }
}
