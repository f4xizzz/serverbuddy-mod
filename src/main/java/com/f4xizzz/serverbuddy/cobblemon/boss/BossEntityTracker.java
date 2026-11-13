package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossEntityTracker {
   private static final Map<UUID, String> BOSS_ID_BY_ENTITY = new HashMap<>();

   private BossEntityTracker() {
   }

   public static void track(UUID entityUuid, String bossId) {
      BOSS_ID_BY_ENTITY.put(entityUuid, bossId);
   }

   public static void untrack(UUID entityUuid) {
      BOSS_ID_BY_ENTITY.remove(entityUuid);
   }

   public static BossDefinition getBoss(UUID entityUuid) {
      String id = (String)BOSS_ID_BY_ENTITY.get(entityUuid);
      return id == null ? null : BossManager.get(id);
   }

   public static boolean isBoss(UUID entityUuid) {
      return BOSS_ID_BY_ENTITY.containsKey(entityUuid);
   }

   public static void reset() {
      BOSS_ID_BY_ENTITY.clear();
   }

   public static void onEntityLoad(PokemonEntity entity) {
      if (!isBoss(entity.getUUID())) {
         String uuidStr = entity.getUUID().toString();

         for(BossDefinition boss : BossManager.getAll().values()) {
            if (uuidStr.equals(boss.entityUuid)) {
               track(entity.getUUID(), boss.id);
               return;
            }
         }

      }
   }
}
