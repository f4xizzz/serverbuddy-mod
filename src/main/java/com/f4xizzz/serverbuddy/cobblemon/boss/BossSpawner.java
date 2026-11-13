package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class BossSpawner {
   private BossSpawner() {
   }

   public static PokemonEntity spawn(ServerLevel world, BossDefinition boss) {
      Pokemon pokemon = PokemonProperties.Companion.parse(boss.spawnQuery).create();
      applyMetadata(pokemon, boss);
      PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
      boss.entityUuid = entity.getUUID().toString();
      if (boss.id == null || boss.id.isBlank()) {
         String var10001 = boss.speciesId.replace(':', '_');
         boss.id = var10001 + "_" + boss.entityUuid.substring(0, 8);
      }

      entity.moveTo(boss.x, boss.y, boss.z, boss.yaw, 0.0F);
      entity.setPersistenceRequired();
      entity.setNoAi(true);
      world.addFreshEntity(entity);
      BossEntityTracker.track(entity.getUUID(), boss.id);
      return entity;
   }

   public static void applyMetadata(Pokemon pokemon, BossDefinition boss) {
      if (boss.nickname != null && !boss.nickname.isBlank()) {
         pokemon.setNickname(Component.literal(boss.nickname));
      }

      pokemon.setLevel(Math.max(1, Math.min(999, boss.level)));
      pokemon.setScaleModifier(boss.scale);
      applyMoves(pokemon, boss);
   }

   private static void applyMoves(Pokemon pokemon, BossDefinition boss) {
      if (boss.moves != null && !boss.moves.isEmpty()) {
         pokemon.getMoveSet().clear();
         int slot = 0;

         for(String moveName : boss.moves) {
            if (slot >= 4) {
               break;
            }

            if (moveName != null && !moveName.isBlank()) {
               MoveTemplate template = Moves.getByName(moveName.trim());
               if (template == null) {
                  ServerBuddy.LOGGER.warn("Boss '{}' has the invalid move '{}' in its moveset, ignoring.", boss.id, moveName);
               } else {
                  Move move = template.create();
                  pokemon.getMoveSet().setMove(slot, move);
                  ++slot;
               }
            }
         }

      }
   }

   public static void refreshAllMetadata(MinecraftServer server) {
      for(BossDefinition boss : BossManager.getAll().values()) {
         if (boss.world != null && boss.entityUuid != null) {
            ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
            if (world != null) {
               UUID uuid;
               try {
                  uuid = UUID.fromString(boss.entityUuid);
               } catch (IllegalArgumentException var9) {
                  continue;
               }

               Entity entity = world.getEntity(uuid);
               if (entity instanceof PokemonEntity) {
                  PokemonEntity pokemonEntity = (PokemonEntity)entity;

                  try {
                     applyMetadata(pokemonEntity.getPokemon(), boss);
                  } catch (Exception e) {
                     ServerBuddy.LOGGER.error("Could not update metadata for boss '" + boss.id + "'!", e);
                  }
               }
            }
         }
      }

   }
}
