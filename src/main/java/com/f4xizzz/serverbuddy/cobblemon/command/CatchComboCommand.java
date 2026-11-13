package com.f4xizzz.serverbuddy.cobblemon.command;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfig;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboLeaderboardManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboLeaderboardRepository;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CatchComboCommand {
   private static final SuggestionProvider<CommandSourceStack> SPECIES_SUGGESTIONS = (context, builder) -> {
      String remaining = builder.getRemaining().toLowerCase();

      for(Species species : PokemonSpecies.getSpecies()) {
         String name = species.getName().toLowerCase();
         if (name.startsWith(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("catchcombo").requires((source) -> SbPermissions.check(source, "serverbuddy.catchcombo", 0))).executes(CatchComboCommand::executeSelfInfo)).then(Commands.literal("info").executes(CatchComboCommand::executeConfigInfo))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("test").requires((source) -> SbPermissions.check(source, "serverbuddy.catchcombo.test", 2))).then(Commands.literal("set").then(Commands.argument("especie", StringArgumentType.word()).suggests(SPECIES_SUGGESTIONS).then(((RequiredArgumentBuilder)Commands.argument("quantidade", IntegerArgumentType.integer(0)).executes((context) -> executeTestSet(context, (String)null))).then(Commands.argument("player", EntityArgument.player()).executes((context) -> executeTestSet(context, "player"))))))).then(Commands.literal("simulate").then(((RequiredArgumentBuilder)Commands.argument("especie", StringArgumentType.word()).suggests(SPECIES_SUGGESTIONS).executes((context) -> executeTestSimulate(context, (String)null))).then(Commands.argument("player", EntityArgument.player()).executes((context) -> executeTestSimulate(context, "player")))))).then(((LiteralArgumentBuilder)Commands.literal("reset").executes((context) -> executeTestReset(context, (String)null))).then(Commands.argument("player", EntityArgument.player()).executes((context) -> executeTestReset(context, "player")))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("leaderboard").requires((source) -> SbPermissions.check(source, "serverbuddy.catchcombo.leaderboard", 2))).then(Commands.argument("direcao", StringArgumentType.word()).executes(CatchComboCommand::executeLeaderboardCreate))).then(Commands.literal("remove").then(Commands.argument("nick", StringArgumentType.word()).executes(CatchComboCommand::executeLeaderboardRemove)))));
   }

   private static int executeLeaderboardCreate(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var4) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      String direcao = StringArgumentType.getString(context, "direcao");
      Float yaw = CatchComboLeaderboardManager.parseDirection(direcao);
      if (yaw == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Invalid direction! Use n, s, e or w."));
         return 0;
      } else {
         CatchComboLeaderboardManager.createAt(player, yaw);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("✔ Catch combo leaderboard created at this position, facing '" + direcao.toLowerCase() + "'!").withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }

   private static int executeLeaderboardRemove(CommandContext<CommandSourceStack> context) {
      String nick = StringArgumentType.getString(context, "nick");
      boolean removed = CatchComboLeaderboardRepository.removeByName(nick);
      if (removed) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u2714 " + nick + " was removed from the catch combo leaderboard (record reset)!").withStyle(ChatFormatting.GREEN), true);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal(nick + " is not on the catch combo leaderboard."));
         return 0;
      }
   }

   private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> context, String argName) throws Exception {
      return argName != null ? EntityArgument.getPlayer(context, argName) : ((CommandSourceStack)context.getSource()).getPlayerOrException();
   }

   private static int executeSelfInfo(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var6) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      CommandSourceStack source = (CommandSourceStack)context.getSource();
      CatchComboState state = CatchComboManager.getState(player.getUUID());
      if (state.speciesId != null && state.comboCount > 0) {
         CatchComboConfig.ComboTier tier = CatchComboManager.getActiveTier(state.comboCount);
         source.sendSuccess(() -> Component.literal("===== Your Catch Combo =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
         source.sendSuccess(() -> Component.literal("Species: ").withStyle(ChatFormatting.GRAY).append(Component.literal(state.speciesDisplayName).withStyle(ChatFormatting.AQUA)), false);
         source.sendSuccess(() -> Component.literal("Combo atual: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(state.comboCount)).withStyle(ChatFormatting.YELLOW)), false);
         if (tier == null) {
            source.sendSuccess(() -> Component.literal("No bonus active yet.").withStyle(ChatFormatting.DARK_GRAY), false);
         } else {
            source.sendSuccess(() -> Component.literal("Active bonuses (from " + tier.combo + " catches):").withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> describeTier(tier), false);
         }

         return 1;
      } else {
         source.sendSuccess(() -> Component.literal("You don't have an active catch combo right now.").withStyle(ChatFormatting.GRAY), false);
         return 1;
      }
   }

   private static int executeConfigInfo(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      CatchComboConfig config = CatchComboConfigManager.getConfig();
      source.sendSuccess(() -> Component.literal("===== Catch Combo Settings =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
      source.sendSuccess(() -> Component.literal("Status: ").withStyle(ChatFormatting.GRAY).append(Component.literal(config.enabled ? "Ativado" : "Desativado").withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
      source.sendSuccess(() -> Component.literal("Action bar shows after: ").withStyle(ChatFormatting.GRAY).append(Component.literal(config.actionBarStartAt + " catches in a row of the same species").withStyle(ChatFormatting.AQUA)), false);

      for(CatchComboConfig.ComboTier tier : config.tiers) {
         source.sendSuccess(() -> Component.literal("- A partir de " + tier.combo + " capturas:").withStyle(ChatFormatting.YELLOW), false);
         source.sendSuccess(() -> describeTier(tier), false);
      }

      return 1;
   }

   private static Component describeTier(CatchComboConfig.ComboTier tier) {
      return Component.literal("  IVs perfeitos garantidos: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(tier.guaranteedPerfectIvs)).withStyle(ChatFormatting.AQUA)).append(Component.literal(" | Chance extra por IV: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(percent(tier.chancePerfectIv)).withStyle(ChatFormatting.AQUA)).append(Component.literal(" | Chance de shiny: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(percent(tier.shinyChance)).withStyle(ChatFormatting.AQUA)).append(Component.literal(" | Chance de Hidden Ability: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(percent(tier.hiddenAbilityChance)).withStyle(ChatFormatting.AQUA));
   }

   private static String percent(double value) {
      return String.format("%.2f%%", value * 100.0);
   }

   private static int executeTestSet(CommandContext<CommandSourceStack> context, String playerArg) {
      try {
         ServerPlayer target = resolvePlayer(context, playerArg);
         String especieInput = StringArgumentType.getString(context, "especie");
         int quantidade = IntegerArgumentType.getInteger(context, "quantidade");
         Species species = PokemonSpecies.getByName(especieInput.toLowerCase());
         if (species == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Species '" + especieInput + "' not found."));
            return 0;
         } else {
            CatchComboManager.setState(target.getUUID(), species, quantidade);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Combo de " + target.getName().getString() + " set to " + quantidade + "x " + species.getName() + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
         }
      } catch (Exception e) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not set combo: " + e.getMessage()));
         return 0;
      }
   }

   private static int executeTestSimulate(CommandContext<CommandSourceStack> context, String playerArg) {
      try {
         ServerPlayer target = resolvePlayer(context, playerArg);
         String especieInput = StringArgumentType.getString(context, "especie");
         Species species = PokemonSpecies.getByName(especieInput.toLowerCase());
         if (species == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Species '" + especieInput + "' not found."));
            return 0;
         } else {
            int novoCombo = CatchComboManager.registerCapture(target, species);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Simulated catch! Combo of " + target.getName().getString() + " is now " + novoCombo + "x " + species.getName() + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
         }
      } catch (Exception e) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not simulate capture: " + e.getMessage()));
         return 0;
      }
   }

   private static int executeTestReset(CommandContext<CommandSourceStack> context, String playerArg) {
      try {
         ServerPlayer target = resolvePlayer(context, playerArg);
         CatchComboManager.resetState(target.getUUID());
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Combo de " + target.getName().getString() + " resetado.").withStyle(ChatFormatting.GREEN), false);
         return 1;
      } catch (Exception e) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reset combo: " + e.getMessage()));
         return 0;
      }
   }
}
