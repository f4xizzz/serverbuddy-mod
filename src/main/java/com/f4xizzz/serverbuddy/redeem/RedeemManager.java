package com.f4xizzz.serverbuddy.redeem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RedeemManager {
   private static final Map<UUID, Set<String>> CLAIMS_BY_PLAYER = new HashMap<>();
   private static final Map<String, Integer> USES_BY_CODE = new HashMap<>();

   private RedeemManager() {
   }

   public static void loadAll() {
      CLAIMS_BY_PLAYER.clear();
      USES_BY_CODE.clear();
      CLAIMS_BY_PLAYER.putAll(RedeemRepository.loadAll());

      for(Set<String> codes : CLAIMS_BY_PLAYER.values()) {
         for(String code : codes) {
            USES_BY_CODE.merge(code, 1, Integer::sum);
         }
      }

   }

   public static boolean hasRedeemed(UUID playerId, String code) {
      Set<String> codes = (Set)CLAIMS_BY_PLAYER.get(playerId);
      return codes != null && codes.contains(code);
   }

   public static int getUses(String code) {
      return (Integer)USES_BY_CODE.getOrDefault(code, 0);
   }

   public static RedeemResult redeem(UUID playerId, String code, int maxUsos) {
      if (hasRedeemed(playerId, code)) {
         return RedeemManager.RedeemResult.ALREADY_REDEEMED;
      } else if (maxUsos > 0 && getUses(code) >= maxUsos) {
         return RedeemManager.RedeemResult.MAX_USES;
      } else {
         ((Set)CLAIMS_BY_PLAYER.computeIfAbsent(playerId, (id) -> new HashSet<>())).add(code);
         USES_BY_CODE.merge(code, 1, Integer::sum);
         RedeemRepository.saveAsync(playerId, code);
         return RedeemManager.RedeemResult.SUCCESS;
      }
   }

   public static boolean resetForPlayer(UUID playerId, String code) {
      Set<String> codes = (Set)CLAIMS_BY_PLAYER.get(playerId);
      if (codes != null && codes.remove(code)) {
         USES_BY_CODE.computeIfPresent(code, (key, uses) -> Math.max(0, uses - 1));
         RedeemRepository.deleteAsync(playerId, code);
         return true;
      } else {
         return false;
      }
   }

   /** Forgets every redemption of [code]: everybody can redeem it again. Returns how many players had redeemed it. */
   public static int resetAll(String code) {
      int players = 0;

      for(Set<String> codes : CLAIMS_BY_PLAYER.values()) {
         if (codes.remove(code)) {
            ++players;
         }
      }

      USES_BY_CODE.remove(code);
      RedeemRepository.deleteAllOf(code);
      return players;
   }

   public static enum RedeemResult {
      SUCCESS,
      ALREADY_REDEEMED,
      MAX_USES;
   }
}
