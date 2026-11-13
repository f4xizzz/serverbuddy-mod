package com.f4xizzz.serverbuddy.cobblemon.util;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RaycastUtil {
   private RaycastUtil() {
   }

   public static PokemonEntity getTargetedPokemon(ServerPlayer player, double maxDistance) {
      Vec3 cameraPos = player.getEyePosition(1.0F);
      Vec3 rotation = player.getViewVector(1.0F);
      Vec3 endPos = cameraPos.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);
      AABB box = player.getBoundingBox().expandTowards(rotation.scale(maxDistance)).inflate(1.0, 1.0, 1.0);
      PokemonEntity closest = null;
      double closestDistance = maxDistance * maxDistance;

      for(Entity entity : player.level().getEntities(player, box)) {
         if (entity instanceof PokemonEntity pokemonEntity) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = entityBox.clip(cameraPos, endPos);
            if (hit.isPresent()) {
               double dist = cameraPos.distanceToSqr((Vec3)hit.get());
               if (dist < closestDistance) {
                  closestDistance = dist;
                  closest = pokemonEntity;
               }
            }
         }
      }

      return closest;
   }
}
