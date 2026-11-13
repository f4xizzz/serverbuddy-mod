package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class BossLookTicker {
   private static final double LOOK_RADIUS = 16.0;
   private static final double POSITION_TOLERANCE_SQ = 4.0E-4;

   private BossLookTicker() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(BossLookTicker::onTick);
   }

   private static void onTick(MinecraftServer server) {
      for(BossDefinition boss : BossManager.getAll().values()) {
         if (boss.world != null && boss.entityUuid != null) {
            ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boss.world)));
            if (world != null) {
               UUID uuid;
               try {
                  uuid = UUID.fromString(boss.entityUuid);
               } catch (IllegalArgumentException var8) {
                  continue;
               }

               Entity entity = world.getEntity(uuid);
               if (entity instanceof PokemonEntity) {
                  PokemonEntity pokemonEntity = (PokemonEntity)entity;
                  pokemonEntity.setDeltaMovement(Vec3.ZERO);
                  pokemonEntity.hasImpulse = true;
                  if (!BossBattleManager.isInActiveBattle(boss.id)) {
                     pinPosition(pokemonEntity, boss);
                     if (boss.lookAtPlayer) {
                        Player nearest = world.getNearestPlayer(pokemonEntity, 16.0);
                        if (nearest != null) {
                           lookAt(pokemonEntity, nearest);
                        }
                     }
                  }
               }
            }
         }
      }

   }

   private static void pinPosition(PokemonEntity pokemonEntity, BossDefinition boss) {
      double dx = boss.x - pokemonEntity.getX();
      double dy = boss.y - pokemonEntity.getY();
      double dz = boss.z - pokemonEntity.getZ();
      if (dx * dx + dy * dy + dz * dz > 4.0E-4) {
         pokemonEntity.setPos(boss.x, boss.y, boss.z);
      }

   }

   private static void lookAt(PokemonEntity pokemonEntity, Player target) {
      double dx = target.getX() - pokemonEntity.getX();
      double dz = target.getZ() - pokemonEntity.getZ();
      double dy = target.getEyeY() - pokemonEntity.getEyeY();
      double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Mth.wrapDegrees(Math.toDegrees(Mth.atan2(dz, dx)) - 90.0);
      float pitch = (float)Mth.wrapDegrees(-Math.toDegrees(Mth.atan2(dy, horizontalDistance)));
      pokemonEntity.setYRot(yaw);
      pokemonEntity.setYHeadRot(yaw);
      pokemonEntity.setYBodyRot(yaw);
      pokemonEntity.setXRot(pitch);
   }
}
