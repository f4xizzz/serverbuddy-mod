package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CatchAreaOccupancyManager {
   private static final Map<String, UUID> OCCUPIED_BY_AREA = new HashMap<>();
   private static final Map<UUID, CatchAreaSession> SESSIONS_BY_PLAYER = new HashMap<>();

   private CatchAreaOccupancyManager() {
   }

   public static void loadAll() {
      OCCUPIED_BY_AREA.clear();
      SESSIONS_BY_PLAYER.clear();

      for(CatchAreaSession session : CatchAreaSessionRepository.loadAll()) {
         OCCUPIED_BY_AREA.put(session.areaName, session.playerId);
         SESSIONS_BY_PLAYER.put(session.playerId, session);
      }

   }

   public static Optional<String> getFreeArea() {
      for(String name : CatchAreaRegionManager.getAll().keySet()) {
         CatchAreaRegion region = CatchAreaRegionManager.get(name);
         if (region != null && region.enabled && !OCCUPIED_BY_AREA.containsKey(name)) {
            return Optional.of(name);
         }
      }

      return Optional.empty();
   }

   public static boolean isPlayerRenting(UUID playerId) {
      return SESSIONS_BY_PLAYER.containsKey(playerId);
   }

   public static CatchAreaSession getSession(UUID playerId) {
      return (CatchAreaSession)SESSIONS_BY_PLAYER.get(playerId);
   }

   public static UUID getRenterOf(String areaName) {
      return (UUID)OCCUPIED_BY_AREA.get(areaName);
   }

   public static void occupy(CatchAreaSession session) {
      OCCUPIED_BY_AREA.put(session.areaName, session.playerId);
      SESSIONS_BY_PLAYER.put(session.playerId, session);
   }

   public static void release(UUID playerId) {
      CatchAreaSession session = (CatchAreaSession)SESSIONS_BY_PLAYER.remove(playerId);
      if (session != null) {
         OCCUPIED_BY_AREA.remove(session.areaName);
         CatchAreaEntityTracker.clearArea(session.areaName);
         CatchAreaSessionRepository.deleteAsync(playerId);
      }

   }

   public static List<CatchAreaSession> getAllSessions() {
      return new ArrayList<>(SESSIONS_BY_PLAYER.values());
   }
}
