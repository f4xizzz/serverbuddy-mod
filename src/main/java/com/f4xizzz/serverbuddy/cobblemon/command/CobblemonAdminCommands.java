package com.f4xizzz.serverbuddy.cobblemon.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossDebug;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossManager;
import com.f4xizzz.serverbuddy.cobblemon.boss.BossSpawner;
import com.f4xizzz.serverbuddy.cobblemon.boss.item.BossItemManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.BannedSpeciesConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiIconsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaGuiSoundsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.catcharea.CatchAreaRegionManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboSoundsConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.features.CobblemonFeatures;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntManager;
import com.f4xizzz.serverbuddy.cobblemon.hunt.HuntSpeciesIndex;
import com.f4xizzz.serverbuddy.command.ServerBuddyCommand;
import com.f4xizzz.serverbuddy.cobblemon.util.RaycastUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Subcomandos de /serverbuddy que dependem do Cobblemon (so sao registrados quando o Cobblemon esta instalado). */
public class CobblemonAdminCommands {
   private static final double MAX_DISTANCE = 12.0;
   private static final Random RANDOM = new Random();
   private static final Map<String, String> POKEGIVE_CATEGORIES = Map.of("legendary", "legendary", "pseudo", "powerhouse", "ultrabeast", "ultra_beast", "paradox", "paradox");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("serverbuddy");
      if (root == null) {
         return;
      }

      root.addChild(Commands.literal("copypoke")
         .requires((source) -> SbPermissions.check(source, "serverbuddy.copypoke", 2))
         .executes(CobblemonAdminCommands::executeCopyPoke)
         .build());
      root.addChild(Commands.literal("infopoke")
         .requires((source) -> SbPermissions.check(source, "serverbuddy.infopoke", 2))
         .executes(CobblemonAdminCommands::executeInfoPoke)
         .build());
      root.addChild(Commands.literal("pokegive")
         .requires((source) -> SbPermissions.check(source, "serverbuddy.pokegive", 2))
         .then(Commands.argument("categoria", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(POKEGIVE_CATEGORIES.keySet(), builder))
            .executes((ctx) -> executePokegive(ctx, 1, (String)null))
            .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 64))
               .executes((ctx) -> executePokegive(ctx, IntegerArgumentType.getInteger(ctx, "quantidade"), (String)null))
               .then(Commands.argument("propriedades", StringArgumentType.greedyString())
                  .executes((ctx) -> executePokegive(ctx, IntegerArgumentType.getInteger(ctx, "quantidade"), StringArgumentType.getString(ctx, "propriedades")))))
            .then(Commands.argument("propriedades", StringArgumentType.greedyString())
               .executes((ctx) -> executePokegive(ctx, 1, StringArgumentType.getString(ctx, "propriedades")))))
         .build());
      root.addChild(Commands.literal("debug")
         .requires((source) -> SbPermissions.check(source, "serverbuddy.debug", 2))
         .executes(CobblemonAdminCommands::executeDebugToggle)
         .build());
      root.addChild(CatchAreaAdminCommand.build().build());
      root.addChild(BossCommand.build().build());
      root.addChild(HuntAdminCommand.build().build());
   }

   /** Recarrega as configs dos recursos de Pokemon (chamado pelo /serverbuddy reload quando o Cobblemon existe). */
   public static void reload(CommandSourceStack source, List<String> failed) {
      ServerBuddyCommand.reloadOne(failed, "catch combo", CatchComboConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "catch combo sounds", CatchComboSoundsConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "banned species", BannedSpeciesConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "catch area", CatchAreaConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "catch area GUI sounds", CatchAreaGuiSoundsConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "catch area GUI icons", CatchAreaGuiIconsConfigManager::loadConfig);
      ServerBuddyCommand.reloadOne(failed, "catch areas", CatchAreaRegionManager::loadAll);
      ServerBuddyCommand.reloadOne(failed, "bosses", BossManager::loadAll);
      ServerBuddyCommand.reloadOne(failed, "itens de boss", BossItemManager::loadAll);
      ServerBuddyCommand.reloadOne(failed, "metadata of live bosses", () -> BossSpawner.refreshAllMetadata(source.getServer()));
      ServerBuddyCommand.reloadOne(failed, "hunt", () -> {
         HuntConfigManager.loadConfig();
         HuntSpeciesIndex.refresh();
         HuntManager.init();
      });
      ServerBuddyCommand.reloadOne(failed, "fichas e menus de Pokémon", CobblemonFeatures::reload);
      ServerBuddyCommand.reloadOne(failed, "healing command", () -> {
         PokeHealCommand.register(source.getServer().getCommands().getDispatcher());

         for(ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            source.getServer().getCommands().sendCommands(online);
         }

      });
   }

   private static int executeDebugToggle(CommandContext<CommandSourceStack> context) {
      boolean enabled = BossDebug.toggle();
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(enabled ? "✔ Boss debug ENABLED — [Boss] logs will show up in the console." : "✔ Boss debug DISABLED — [Boss] logs stopped.").withStyle(new ChatFormatting[]{enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY, ChatFormatting.BOLD}), true);
      ServerBuddy.LOGGER.info("[Boss] Debug {} via /serverbuddy debug.", enabled ? "ativado" : "desativado");
      return 1;
   }

   private static int executeCopyPoke(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      PokemonEntity targetEntity = RaycastUtil.getTargetedPokemon(player, 12.0);
      if (targetEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("You need to be looking straight at a Pokémon."));
         return 0;
      } else {
         Pokemon original = targetEntity.getPokemon();
         RegistryAccess registryManager = player.getServer().registryAccess();
         Pokemon copy = original.clone(true, registryManager);
         PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
         boolean added = party.add(copy);
         if (!added) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Your party is full! Free up a slot before copying."));
            return 0;
         } else {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("You copied ").withStyle(ChatFormatting.GREEN).append(copy.getDisplayName(false).copy().withStyle(ChatFormatting.YELLOW)).append(Component.literal(" to your party!").withStyle(ChatFormatting.GREEN)), false);
            ServerBuddy.LOGGER.info("{} copied a {} (original uuid: {}, copy uuid: {}) via /serverbuddy copypoke", new Object[]{player.getName().getString(), original.getSpecies().getName(), original.getUuid(), copy.getUuid()});
            return 1;
         }
      }
   }

   private static int executePokegive(CommandContext<CommandSourceStack> context, int count, String extraProperties) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var15) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      String categoria = StringArgumentType.getString(context, "categoria").toLowerCase();
      String label = (String)POKEGIVE_CATEGORIES.get(categoria);
      if (label == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Invalid category. Use: " + String.join(", ", POKEGIVE_CATEGORIES.keySet())));
         return 0;
      } else {
         List<Species> pool = (List)PokemonSpecies.getSpecies().stream().filter((speciesx) -> speciesx.getLabels().contains(label)).collect(Collectors.toList());
         if (pool.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No species found for category '" + categoria + "'."));
            return 0;
         } else {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            List<Component> given = new ArrayList<>();

            for(int i = 0; i < count; ++i) {
               Species species = (Species)pool.get(RANDOM.nextInt(pool.size()));
               String speciesId = species.getResourceIdentifier().getPath();
               String query = extraProperties != null && !extraProperties.isBlank() ? speciesId + " " + extraProperties : speciesId;
               PokemonProperties properties = PokemonProperties.Companion.parse(query);
               if (!properties.hasSpecies()) {
                  ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Invalid properties: '" + extraProperties + "'."));
                  break;
               }

               Pokemon pokemon = properties.create();
               if (!party.add(pokemon)) {
                  CommandSourceStack var10000 = (CommandSourceStack)context.getSource();
                  int var10001 = given.size();
                  var10000.sendFailure(Component.literal("Your party is full! I delivered " + var10001 + " of " + count + "."));
                  break;
               }

               given.add(pokemon.getDisplayName(false).copy().withStyle(ChatFormatting.YELLOW));
            }

            int total = given.size();
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("You received " + total + " " + categoria + "(s) at random!").withStyle(ChatFormatting.GREEN), true);
            ServerBuddy.LOGGER.info("{} received {} pokémon of category '{}' via /serverbuddy pokegive (extra properties: '{}').", new Object[]{player.getName().getString(), total, categoria, extraProperties});
            return total;
         }
      }
   }

   private static int executeInfoPoke(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var13) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      PokemonEntity targetEntity = RaycastUtil.getTargetedPokemon(player, 12.0);
      if (targetEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("You need to be looking straight at a Pokémon."));
         return 0;
      } else {
         Pokemon pokemon = targetEntity.getPokemon();
         CommandSourceStack source = (CommandSourceStack)context.getSource();
         send(source, Component.literal("===== Pokémon Info =====").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}));
         MutableComponent var10002 = pokemon.getSpecies().getTranslatedName().copy().withStyle(ChatFormatting.AQUA);
         int var10003 = pokemon.getSpecies().getNationalPokedexNumber();
         sendLine(source, "Species", var10002.append(Component.literal(" (#" + var10003 + ")").withStyle(ChatFormatting.DARK_GRAY)));
         FormData form = pokemon.getForm();
         sendLine(source, "Forma", Component.literal(form.getName()).withStyle(ChatFormatting.AQUA));
         sendLine(source, "Level", Component.literal(String.valueOf(pokemon.getLevel())).withStyle(ChatFormatting.AQUA));
         sendLine(source, "UUID", Component.literal(pokemon.getUuid().toString()).withStyle(ChatFormatting.GRAY));
         String aspects = String.join(", ", pokemon.getAspects());
         sendLine(source, "Aspectos", Component.literal(aspects.isEmpty() ? "nenhum" : aspects).withStyle(ChatFormatting.AQUA));
         sendLine(source, "Sexo", Component.literal(pokemon.getGender().name()).withStyle(ChatFormatting.AQUA));
         Nature nature = pokemon.getNature();
         Component natureText = Component.translatable(nature.getDisplayName()).withStyle(ChatFormatting.AQUA);
         if (pokemon.getMintedNature() != null && pokemon.getMintedNature() != nature) {
            natureText = natureText.copy().append(Component.literal(" (mintada: ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable(pokemon.getMintedNature().getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
         }

         sendLine(source, "Natureza", natureText);
         Ability ability = pokemon.getAbility();
         Component abilityText = Component.translatable(ability.getDisplayName()).withStyle(ChatFormatting.AQUA);
         if (ability.getForced()) {
            abilityText = abilityText.copy().append(Component.literal(" (forced)").withStyle(ChatFormatting.DARK_GRAY));
         }

         sendLine(source, "Habilidade", abilityText);
         sendLine(source, "Shiny", Component.literal(pokemon.getShiny() ? "Yes" : "No").withStyle(pokemon.getShiny() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
         sendLine(source, "IVs", formatStats(pokemon.getIvs()));
         sendLine(source, "EVs", formatStats(pokemon.getEvs()));
         int var14 = pokemon.getCurrentHealth();
         sendLine(source, "Vida", Component.literal(var14 + " / " + pokemon.getMaxHealth()).withStyle(ChatFormatting.AQUA));
         sendLine(source, "Amizade", Component.literal(String.valueOf(pokemon.getFriendship())).withStyle(ChatFormatting.AQUA));
         ItemStack heldItem = pokemon.heldItem();
         sendLine(source, "Item segurado", heldItem.isEmpty() ? Component.literal("nenhum").withStyle(ChatFormatting.GRAY) : heldItem.getHoverName().copy().withStyle(ChatFormatting.AQUA));
         String trainer = pokemon.getOriginalTrainerName();
         sendLine(source, "Treinador original", Component.literal(trainer != null && !trainer.isEmpty() ? trainer : "Selvagem").withStyle(ChatFormatting.AQUA));
         return 1;
      }
   }

   private static Component formatStats(PokemonStats stats) {
      Stat[] order = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
      String[] labels = new String[]{"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
      Component result = Component.empty();

      for(int i = 0; i < order.length; ++i) {
         if (i > 0) {
            result = result.copy().append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
         }

         result = result.copy().append(Component.literal(labels[i] + " ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(stats.getOrDefault(order[i]))).withStyle(ChatFormatting.AQUA));
      }

      Component var5 = result.copy().append(Component.literal(" (Total: " + stats.total() + ")").withStyle(ChatFormatting.DARK_GRAY));
      return var5;
   }

   private static void sendLine(CommandSourceStack source, String label, Component value) {
      send(source, Component.literal(label + ": ").withStyle(ChatFormatting.GRAY).append(value));
   }

   private static void send(CommandSourceStack source, Component text) {
      source.sendSuccess(() -> text, false);
   }
}
