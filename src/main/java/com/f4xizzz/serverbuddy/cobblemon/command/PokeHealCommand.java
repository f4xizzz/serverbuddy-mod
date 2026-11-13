package com.f4xizzz.serverbuddy.cobblemon.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.logs.ServerConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PokeHealCommand {
   private static final Map<UUID, Long> LAST_USE_MILLIS = new HashMap<>();

   private PokeHealCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      String commandName = resolveCommandName();
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires((source) -> SbPermissions.check(source, resolvePermission(), 2))).executes(PokeHealCommand::executeSelf)).then(((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("jogador", EntityArgument.player()).requires((source) -> SbPermissions.check(source, resolvePermission() + ".others", 2))).executes((context) -> executeOthers(context, false))).then(Commands.literal("force").executes((context) -> executeOthers(context, true)))));
   }

   private static String resolveCommandName() {
      String configured = ServerConfigManager.getConfig().pokeHealCommand;
      if (configured != null && !configured.isBlank()) {
         String trimmed;
         for(trimmed = configured.trim(); trimmed.startsWith("/"); trimmed = trimmed.substring(1)) {
         }

         return trimmed.isBlank() ? "pokeheal" : trimmed;
      } else {
         return "pokeheal";
      }
   }

   private static String resolvePermission() {
      String configured = ServerConfigManager.getConfig().pokeHealPermission;
      return configured != null && !configured.isBlank() ? configured.trim() : "serverbuddy.pokeheal";
   }

   private static int executeSelf(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player. To heal another player, use: /" + resolveCommandName() + " <player>"));
         return 0;
      }

      return healPlayer((CommandSourceStack)context.getSource(), player, true, false);
   }

   private static int executeOthers(CommandContext<CommandSourceStack> context, boolean force) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
      return healPlayer((CommandSourceStack)context.getSource(), target, false, force);
   }

   private static int healPlayer(CommandSourceStack source, ServerPlayer target, boolean self, boolean force) {
      int cooldownSeconds = ServerConfigManager.getConfig().pokeHealCooldownSeconds;
      long now = System.currentTimeMillis();
      if (!force && cooldownSeconds > 0) {
         Long lastUse = (Long)LAST_USE_MILLIS.get(target.getUUID());
         if (lastUse != null) {
            long elapsedSeconds = (now - lastUse) / 1000L;
            if (elapsedSeconds < (long)cooldownSeconds) {
               long remaining = (long)cooldownSeconds - elapsedSeconds;
               String who = self ? "your party" : target.getName().getString() + " has to wait";
               source.sendFailure(Component.literal(self ? "⏳ Wait " + formatDuration(remaining) + " more to heal " + who + " again." : "⏳ " + who + " for " + formatDuration(remaining) + " more (use \"force\" to skip the cooldown)."));
               return 0;
            }
         }
      }

      PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(target);
      int count = 0;

      for(Pokemon pokemon : party) {
         if (pokemon != null) {
            pokemon.heal();
            ++count;
         }
      }

      if (cooldownSeconds > 0) {
         LAST_USE_MILLIS.put(target.getUUID(), now);
      }

      final int healed = count;
      source.sendSuccess(() -> Component.literal(self ? "\u2714 Your party (" + healed + " pokémon) was healed!" : "\u2714 The party of " + target.getName().getString() + " (" + healed + " pokémon) was healed!").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), true);
      return 1;
   }

   private static String formatDuration(long totalSeconds) {
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return minutes > 0L ? minutes + "m" + seconds + "s" : seconds + "s";
   }
}
