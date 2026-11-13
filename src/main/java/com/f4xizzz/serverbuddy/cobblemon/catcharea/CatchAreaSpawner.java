package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.cobblemon.combo.CatchComboManager;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CatchAreaSpawner {
   private static final Random RANDOM = new Random();

   private CatchAreaSpawner() {
   }

   public static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      CatchAreaConfig config = CatchAreaConfigManager.getConfig();
      long intervalMillis = (long)config.spawnIntervalSeconds * 1000L;

      for(CatchAreaSession session : CatchAreaOccupancyManager.getAllSessions()) {
         if (now - session.lastSpawnAtMillis >= intervalMillis && CatchAreaEntityTracker.getEntities(session.areaName).size() < config.maxSpawnedPokemon) {
            CatchAreaRegion region = CatchAreaRegionManager.get(session.areaName);
            if (region != null) {
               ServerLevel world = region.resolveWorld(server);
               if (world != null) {
                  ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
                  if (player != null) {
                     Species species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(session.baseSpeciesId));
                     if (species != null) {
                        spawnOne(world, region, session.areaName, player, species);
                        session.lastSpawnAtMillis = now;
                        CatchAreaSessionRepository.saveAsync(session);
                     }
                  }
               }
            }
         }
      }

   }

   private static void spawnOne(ServerLevel world, CatchAreaRegion region, String areaName, ServerPlayer player, Species species) {
      int spanX = Math.max(1, region.getMaxX() - region.getMinX() + 1);
      int spanZ = Math.max(1, region.getMaxZ() - region.getMinZ() + 1);
      int x = region.getMinX() + RANDOM.nextInt(spanX);
      int z = region.getMinZ() + RANDOM.nextInt(spanZ);
      int y = canFly(species) ? findFlyingY(region) : findSpawnY(world, region, x, z);
      Pokemon pokemon = PokemonProperties.Companion.parse(species.getName().toLowerCase() + " level=1").create();
      CatchComboManager.applySpawnBuffs(player, pokemon);
      PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
      entity.moveTo((double)x + 0.5, (double)y, (double)z + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);
      entity.setPersistenceRequired();
      world.addFreshEntity(entity);
      CatchAreaEntityTracker.track(areaName, entity.getUUID());
   }

   private static int findSpawnY(ServerLevel world, CatchAreaRegion region, int x, int z) {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, region.getMaxY(), z);

      for(int y = region.getMaxY(); y > region.getMinY(); --y) {
         pos.setY(y);
         if (!world.getBlockState(pos).isAir() && world.getBlockState(pos.above()).isAir()) {
            return y + 1;
         }
      }

      return region.getMinY() + 1;
   }

   private static boolean canFly(Species species) {
      try {
         return species.getStandardForm().getBehaviour().getMoving().getFly().getCanFly();
      } catch (Exception var2) {
         return false;
      }
   }

   private static int findFlyingY(CatchAreaRegion region) {
      int minY = region.getMinY() + 2;
      int maxY = Math.max(minY, region.getMaxY() - 1);
      int span = Math.max(1, maxY - minY + 1);
      return minY + RANDOM.nextInt(span);
   }
}
