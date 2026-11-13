package com.f4xizzz.serverbuddy.cobblemon.command;

import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaOccupancyManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaSession;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaTimers;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RandomPoolManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.RentalFlowManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.gui.MainCatchAreaGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Optional;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CatchAreaCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("catcharea").executes((context) -> executeStart(context, (String)null))).then(((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).requires((source) -> SbPermissions.check(source, "serverbuddy.catcharea.others", 2))).executes((context) -> executeStart(context, "player")))).then(Commands.literal("leave").executes(CatchAreaCommand::executeLeave))).then(((LiteralArgumentBuilder)Commands.literal("leaveconfirm").requires(CatchAreaCommand::hasActiveSessionOrOp)).executes(CatchAreaCommand::executeLeaveConfirm))).then(((LiteralArgumentBuilder)Commands.literal("leavecancel").requires(CatchAreaCommand::hasActiveSessionOrOp)).executes(CatchAreaCommand::executeLeaveCancel))).then(Commands.literal("poll").then(((LiteralArgumentBuilder)Commands.literal("reset").requires((source) -> SbPermissions.check(source, "serverbuddy.catcharea.pollreset", 2))).executes(CatchAreaCommand::executePollReset))));
   }

   private static boolean hasActiveSessionOrOp(CommandSourceStack source) {
      if (source.hasPermission(2)) {
         return true;
      } else {
         ServerPlayer player = source.getPlayer();
         return player != null && CatchAreaOccupancyManager.isPlayerRenting(player.getUUID());
      }
   }

   private static int executePollReset(CommandContext<CommandSourceStack> context) {
      RandomPoolManager.rotate();
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u2714 ").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}).append(Component.literal("Random catch pool reset!").withStyle(ChatFormatting.GREEN)), true);
      return 1;
   }

   private static int executeStart(CommandContext<CommandSourceStack> context, String playerArg) {
      ServerPlayer target;
      try {
         target = playerArg != null ? EntityArgument.getPlayer(context, playerArg) : ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var6) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Specify a valid player or run this command as a player."));
         return 0;
      }

      if (CatchAreaOccupancyManager.isPlayerRenting(target.getUUID())) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal(target.getName().getString() + " is already using a catch area! Use /catcharea leave to leave."));
         return 0;
      } else {
         Optional<String> freeArea = CatchAreaOccupancyManager.getFreeArea();
         if (freeArea.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("All catch areas are in use right now. Try again later."));
            return 0;
         } else {
            String areaName = (String)freeArea.get();
            RentalFlowManager.PendingRental rental = RentalFlowManager.getOrCreate(target.getUUID());
            rental.reset();
            rental.areaName = areaName;
            MainCatchAreaGui.open(target, areaName);
            return 1;
         }
      }
   }

   private static int executeLeave(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var4) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      if (!CatchAreaOccupancyManager.isPlayerRenting(player.getUUID())) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("You are not using any catch area."));
         return 0;
      } else {
         Component yes = Component.literal("[YES]").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}).withStyle((style) -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/catcharea leaveconfirm")));
         Component no = Component.literal("[NO]").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).withStyle((style) -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/catcharea leavecancel")));
         player.displayClientMessage(Component.literal("\u26a0 Cancel Catch Area").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
         player.displayClientMessage(Component.literal("Are you sure? ").withStyle(ChatFormatting.YELLOW).append(Component.literal("The amount paid will not be refunded.").withStyle(ChatFormatting.RED)).append(Component.literal("  ")).append(yes).append(Component.literal("  ")).append(no).append(Component.literal("\n(click the button in chat to confirm!)").withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC})), false);
         return 1;
      }
   }

   private static int executeLeaveConfirm(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      CatchAreaSession session = CatchAreaOccupancyManager.getSession(player.getUUID());
      if (session == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("You are not using any catch area."));
         return 0;
      } else {
         CatchAreaTimers.endSession(player.getServer(), session, (Component)null);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u2714 Your catch area was cancelled.").withStyle(ChatFormatting.GRAY), false);
         return 1;
      }
   }

   private static int executeLeaveCancel(CommandContext<CommandSourceStack> context) {
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u2714 OK, your catch area is still active.").withStyle(ChatFormatting.GRAY), false);
      return 1;
   }
}
