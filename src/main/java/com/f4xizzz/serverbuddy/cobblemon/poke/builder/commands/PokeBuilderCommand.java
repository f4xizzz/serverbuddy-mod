package com.f4xizzz.serverbuddy.cobblemon.poke.builder.commands;

import com.cobblemon.mod.common.command.argument.SpeciesArgumentType;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.gui.BuilderGui;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.utils.SmogonAPIUtils;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class PokeBuilderCommand {
   public static void register() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         registerCommandTree(dispatcher, "pokebuilder");

         for(String alias : BuilderConfigManager.serverconfig.command_aliases) {
            registerCommandTree(dispatcher, alias);
         }

      });
   }

   private static void registerCommandTree(CommandDispatcher<CommandSourceStack> dispatcher, String literalName) {
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(literalName).requires((source) -> SbPermissions.check(source, BuilderConfigManager.serverconfig.permission_gui, 2))).then(Commands.argument("pokemon", SpeciesArgumentType.Companion.species()).executes((context) -> {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
         if (player == null) {
            return 1;
         } else {
            Species species = SpeciesArgumentType.Companion.getPokemon(context, "pokemon");
            String pokemonName = species.getName().toLowerCase();
            SoundUtils.playAnySound(player, BuilderConfigManager.sounds.gui_open);
            player.sendSystemMessage(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_searching.replace("<pokemon_name>", pokemonName)));
            SmogonAPIUtils.getBuildsAsync(pokemonName).thenAccept((builds) -> player.getServer().execute(() -> {
                  if (builds.isEmpty()) {
                     player.sendSystemMessage(BuilderConfigManager.parse(BuilderConfigManager.lang.builder_no_builds));
                     SoundUtils.playAnySound(player, BuilderConfigManager.sounds.error);
                  } else {
                     BuilderGui.open(player, pokemonName, builds);
                  }

               }));
            return 1;
         }
      })));
   }
}
