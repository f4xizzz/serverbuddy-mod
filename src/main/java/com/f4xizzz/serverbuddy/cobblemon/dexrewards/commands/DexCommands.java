package com.f4xizzz.serverbuddy.cobblemon.dexrewards.commands;

import com.cobblemon.mod.common.api.pokedex.entry.DexEntries;
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.Messages;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.PlayerData;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.utils.TextUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Map;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class DexCommands {
   public DexCommands() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registry, environment) -> {
         LiteralCommandNode<CommandSourceStack> node = dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dex").then(((LiteralArgumentBuilder)Commands.literal("reload").requires(SbPermissions.require("dexrewards.reload", 4))).executes((ctx) -> {
            DexRewards.INSTANCE.reload();
            ((CommandSourceStack)ctx.getSource()).sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.reload)));
            return 1;
         }))).then(Commands.literal("rewards").executes(this::rewards))).then(((LiteralArgumentBuilder)Commands.literal("generate").requires(SbPermissions.require("dexrewards.generate", 4))).executes(this::generate))).then(((LiteralArgumentBuilder)Commands.literal("reset").requires(SbPermissions.require("dexrewards.reset", 4))).then(Commands.argument("player", EntityArgument.player()).executes(this::reset)))).then(((LiteralArgumentBuilder)Commands.literal("update").requires(SbPermissions.require("dexrewards.update", 4))).then(Commands.argument("player", EntityArgument.player()).executes(this::update))));
         dispatcher.register((LiteralArgumentBuilder)Commands.literal("dexrewards").redirect(node));
      });
   }

   private int rewards(CommandContext<CommandSourceStack> ctx) {
      if (((CommandSourceStack)ctx.getSource()).isPlayer()) {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
         if (player != null) {
            DexRewards.INSTANCE.config().gui_layout.create_gui(player, 0);
         }
      }

      return 0;
   }

   private int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      if (target != null) {
         PlayerData data = DexRewards.INSTANCE.config().getPlayerData(target.getUUID());
         if (data != null) {
            data.caught_count = 0;
            data.claimable_rewards.clear();
            data.claimed_rewards.clear();
            data.updateCaughtCount();
            data.updateClaimableRewards();
            DexRewards.INSTANCE.config().updatePlayerData(data);
            ((CommandSourceStack)ctx.getSource()).sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.reset_sender, target.getUUID())));
            target.sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.reset_target, target.getUUID())));
         }
      }

      return 1;
   }

   private int update(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      if (target != null) {
         PlayerData data = DexRewards.INSTANCE.config().getPlayerData(target.getUUID());
         if (data != null) {
            data.updateCaughtCount();
            data.updateClaimableRewards();
            DexRewards.INSTANCE.config().updatePlayerData(data);
            ((CommandSourceStack)ctx.getSource()).sendSystemMessage(TextUtils.deserialize(Messages.parse(Messages.update_command, target.getUUID())));
         }
      }

      return 1;
   }

   private int generate(CommandContext<CommandSourceStack> ctx) {
      int unimplemented_total = 0;

      for(Map.Entry<ResourceLocation, PokedexEntry> entry : DexEntries.INSTANCE.getEntries().entrySet()) {
         PokemonSpecies var10000 = PokemonSpecies.INSTANCE;
         Species species = PokemonSpecies.getByIdentifier((ResourceLocation)entry.getKey());
         if (species != null && !species.getImplemented()) {
            DexRewards.INSTANCE.logInfo("[DexRewards] Unimplemented: " + String.valueOf(entry.getKey()));
            ++unimplemented_total;
            String generation = "none";
            if (species.getLabels().contains("gen1")) {
               generation = "generation1";
            } else if (species.getLabels().contains("gen2")) {
               generation = "generation2";
            } else if (species.getLabels().contains("gen3")) {
               generation = "generation3";
            } else if (species.getLabels().contains("gen4")) {
               generation = "generation4";
            } else if (species.getLabels().contains("gen5")) {
               generation = "generation5";
            } else if (species.getLabels().contains("gen6")) {
               generation = "generation6";
            } else if (!species.getLabels().contains("gen7") && !species.getLabels().contains("gen7b")) {
               if (!species.getLabels().contains("gen8") && !species.getLabels().contains("gen8a")) {
                  if (species.getLabels().contains("gen9")) {
                     generation = "generation9";
                  }
               } else {
                  generation = "generation8";
               }
            } else {
               generation = "generation7";
            }

            DexRewards.INSTANCE.config().generateImplementation(((ResourceLocation)entry.getKey()).toString(), generation);
         }
      }

      DexRewards.INSTANCE.logInfo("[DexRewards] Total Unimplemented: " + unimplemented_total);
      return 1;
   }
}
