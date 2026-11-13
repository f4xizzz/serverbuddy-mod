package com.f4xizzz.serverbuddy.cobblemon.command;

import com.cobblemon.mod.common.api.drop.DropEntry;
import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossDefinition;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossDialogueDefinition;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossEntityTracker;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossRespawnScheduler;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossSpawner;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemDefinition;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemFactory;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Map;
import java.util.UUID;
import kotlin.ranges.IntRange;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class BossCommand {
   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("boss").requires((source) -> SbPermissions.check(source, "serverbuddy.boss", 2))).then(((LiteralArgumentBuilder)Commands.literal("list").requires((source) -> SbPermissions.check(source, "serverbuddy.boss.list", 2))).executes(BossCommand::executeList))).then(((LiteralArgumentBuilder)Commands.literal("remove").requires((source) -> SbPermissions.check(source, "serverbuddy.boss.remove", 2))).then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BossManager.getAll().keySet(), builder)).executes(BossCommand::executeRemove)))).then(((LiteralArgumentBuilder)Commands.literal("respawn").requires((source) -> SbPermissions.check(source, "serverbuddy.boss.respawn", 2))).then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BossManager.getAll().keySet(), builder)).executes(BossCommand::executeRespawn)))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("item").requires((source) -> SbPermissions.check(source, "serverbuddy.boss.item", 2))).then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(BossCommand::executeItemCreate)))).then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BossItemManager.getAll().keySet(), builder)).executes(BossCommand::executeItemGive)))))).then(((RequiredArgumentBuilder)Commands.argument("query", StringArgumentType.greedyString()).requires((source) -> SbPermissions.check(source, "serverbuddy.boss.create", 2))).executes(BossCommand::executeCreate));
   }

   private static int executeCreate(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var13) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      String query = StringArgumentType.getString(context, "query");
      PokemonProperties properties = PokemonProperties.Companion.parse(query);
      if (!properties.hasSpecies()) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Invalid species. Use the same syntax as /pokegive, e.g.: /serverbuddy boss tyranitar level=100 shiny"));
         return 0;
      } else {
         BossDefinition boss = new BossDefinition();
         boss.speciesId = properties.getSpecies().toLowerCase();
         boss.spawnQuery = query;
         boss.nickname = null;
         boss.level = properties.getLevel() != null ? properties.getLevel() : 1;
         boss.scale = 1.0F;
         boss.world = player.serverLevel().dimension().location().toString();
         boss.x = player.getX();
         boss.y = player.getY();
         boss.z = player.getZ();
         boss.yaw = player.getYRot();
         boss.blockedDialogue = defaultBlockedDialogue();
         boss.missingItemDialogue = defaultMissingItemDialogue();

         PokemonEntity entity;
         try {
            entity = BossSpawner.spawn(player.serverLevel(), boss);
         } catch (Exception e) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not create boss: " + e.getMessage()));
            return 0;
         }

         for(Move move : entity.getPokemon().getMoveSet().getMoves()) {
            boss.moves.add(move.getName());
         }

         DropTable dropTable = entity.getPokemon().getForm().getDrops();

         for(DropEntry dropEntry : dropTable.getEntries()) {
            if (dropEntry instanceof ItemDropEntry) {
               ItemDropEntry itemDrop = (ItemDropEntry)dropEntry;
               BossDefinition.DropEntry converted = new BossDefinition.DropEntry();
               converted.item = itemDrop.getItem().toString();
               converted.percentage = itemDrop.getPercentage();
               IntRange range = itemDrop.getQuantityRange();
               if (range != null) {
                  converted.minQuantity = range.getFirst();
                  converted.maxQuantity = range.getLast();
               } else {
                  converted.minQuantity = itemDrop.getQuantity();
                  converted.maxQuantity = itemDrop.getQuantity();
               }

               boss.drops.add(converted);
            }
         }

         BossManager.save(boss);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Boss '" + boss.id + "' created! ").withStyle(ChatFormatting.GREEN).append(entity.getPokemon().getDisplayName(false).copy().withStyle(ChatFormatting.YELLOW)).append(Component.literal(" — edit config/ServerBuddy/Bosses/" + boss.id + ".json and run /serverbuddy reload to adjust nickname, victoryCommands/defeatCommands/lossCommands, required item, etc. The blockedDialogue and missingItemDialogue dialogues come with a filled-in example, and drops comes with the species' default drop table (delete entries or leave [] to drop nothing).").withStyle(ChatFormatting.GRAY)), true);
         return 1;
      }
   }

   private static BossDialogueDefinition defaultBlockedDialogue() {
      BossDialogueDefinition dialogue = new BossDialogueDefinition();
      dialogue.lines.add("You can't challenge me right now.");
      dialogue.lines.add("Volte mais tarde.");
      return dialogue;
   }

   private static BossDialogueDefinition defaultMissingItemDialogue() {
      BossDialogueDefinition dialogue = new BossDialogueDefinition();
      dialogue.lines.add("You need the right item to challenge me!");
      BossDialogueDefinition.Option option = new BossDialogueDefinition.Option();
      option.text = "Entendi.";
      dialogue.options.add(option);
      return dialogue;
   }

   private static int executeList(CommandContext<CommandSourceStack> context) {
      Map<String, BossDefinition> bosses = BossManager.getAll();
      if (bosses.isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("No bosses created yet.").withStyle(ChatFormatting.GRAY), false);
         return 1;
      } else {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("===== Bosses (" + bosses.size() + ") =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);

         for(BossDefinition boss : bosses.values()) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("- " + boss.id).withStyle(ChatFormatting.YELLOW).append(Component.literal(" (" + boss.speciesId + ", lvl " + boss.level + ") em " + boss.world).withStyle(ChatFormatting.GRAY)), false);
         }

         return 1;
      }
   }

   private static int executeRemove(CommandContext<CommandSourceStack> context) {
      String id = StringArgumentType.getString(context, "id");
      BossDefinition boss = BossManager.get(id);
      boolean removed = BossManager.remove(id);
      if (!removed) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Boss '" + id + "' does not exist."));
         return 0;
      } else {
         boolean entityDeleted = deleteWorldEntity(context, boss);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Boss '" + id + "' removed from the configuration" + (entityDeleted ? " and its entity was removed from the world." : ".")).withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }

   private static int executeRespawn(CommandContext<CommandSourceStack> context) {
      String id = StringArgumentType.getString(context, "id");
      BossDefinition boss = BossManager.get(id);
      if (boss == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Boss '" + id + "' does not exist."));
         return 0;
      } else {
         boolean ok = BossRespawnScheduler.forceRespawn(boss, ((CommandSourceStack)context.getSource()).getServer());
         if (!ok) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not respawn '" + id + "' — check that the world '" + boss.world + "' exists (see the console for details)."));
            return 0;
         } else {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Boss '" + id + "' respawnado!").withStyle(ChatFormatting.GREEN), true);
            return 1;
         }
      }
   }

   private static boolean deleteWorldEntity(CommandContext<CommandSourceStack> context, BossDefinition boss) {
      if (boss != null && boss.world != null && boss.entityUuid != null) {
         ServerLevel world = ((CommandSourceStack)context.getSource()).getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
         if (world == null) {
            return false;
         } else {
            UUID uuid;
            try {
               uuid = UUID.fromString(boss.entityUuid);
            } catch (IllegalArgumentException var5) {
               return false;
            }

            Entity entity = world.getEntity(uuid);
            if (entity == null) {
               return false;
            } else {
               BossEntityTracker.untrack(uuid);
               entity.discard();
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private static int executeItemCreate(CommandContext<CommandSourceStack> context) {
      String nome = StringArgumentType.getString(context, "name");
      if (BossItemManager.exists(nome)) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("There is already a boss item called '" + nome + "'."));
         return 0;
      } else {
         BossItemDefinition item = new BossItemDefinition();
         item.name = nome;
         BossItemManager.save(item);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Boss item '" + nome + "' created! ").withStyle(ChatFormatting.GREEN).append(Component.literal("Edit config/ServerBuddy/Bosses/Items/" + nome + ".json and run /serverbuddy reload.").withStyle(ChatFormatting.GRAY)), true);
         return 1;
      }
   }

   private static int executeItemGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(context, "player");
      String nome = StringArgumentType.getString(context, "name");
      BossItemDefinition item = BossItemManager.get(nome);
      if (item == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Boss item '" + nome + "' does not exist. Create it with /serverbuddy boss item create " + nome));
         return 0;
      } else {
         ItemStack stack = BossItemFactory.create(item);
         boolean added = target.addItem(stack);
         if (!added) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("The inventory of " + target.getName().getString() + " is full."));
            return 0;
         } else {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Item '" + nome + "' given to ").withStyle(ChatFormatting.GREEN).append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW)), true);
            return 1;
         }
      }
   }
}
