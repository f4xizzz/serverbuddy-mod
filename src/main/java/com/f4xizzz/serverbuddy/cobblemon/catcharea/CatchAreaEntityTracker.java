package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CatchAreaEntityTracker {
   private static final Map<String, Set<UUID>> ENTITIES_BY_AREA = new HashMap<>();

   private CatchAreaEntityTracker() {
   }

   public static void track(String areaName, UUID entityId) {
      ((Set)ENTITIES_BY_AREA.computeIfAbsent(areaName, (k) -> new HashSet<>())).add(entityId);
   }

   public static void untrack(String areaName, UUID entityId) {
      Set<UUID> set = (Set)ENTITIES_BY_AREA.get(areaName);
      if (set != null) {
         set.remove(entityId);
      }

   }

   public static Set<UUID> getEntities(String areaName) {
      return (Set)ENTITIES_BY_AREA.getOrDefault(areaName, Collections.emptySet());
   }

   public static String getAreaForEntity(UUID entityId) {
      for(Map.Entry<String, Set<UUID>> entry : ENTITIES_BY_AREA.entrySet()) {
         if (((Set)entry.getValue()).contains(entityId)) {
            return (String)entry.getKey();
         }
      }

      return null;
   }

   public static void clearArea(String areaName) {
      ENTITIES_BY_AREA.remove(areaName);
   }
}
