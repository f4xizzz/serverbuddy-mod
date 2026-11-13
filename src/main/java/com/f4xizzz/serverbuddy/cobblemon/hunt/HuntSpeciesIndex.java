package com.f4xizzz.serverbuddy.cobblemon.hunt;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class HuntSpeciesIndex {
   private static final Random RANDOM = new Random();
   private static final Map<HuntBucket, List<String>> SPECIES_BY_BUCKET = new EnumMap(HuntBucket.class);

   private HuntSpeciesIndex() {
   }

   public static void refresh() {
      Map<HuntBucket, Set<String>> collected = new EnumMap(HuntBucket.class);

      for(HuntBucket bucket : HuntBucket.values()) {
         collected.put(bucket, new LinkedHashSet<>());
      }

      for(SpawnDetail detail : CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL()) {
         if (detail instanceof PokemonSpawnDetail pokemonDetail) {
            HuntBucket bucket = resolveBucket(detail.getBucket());
            if (bucket != null) {
               String speciesId = pokemonDetail.getPokemon().getSpecies();
               if (speciesId != null && !speciesId.isBlank() && !isLegendary(speciesId)) {
                  ((Set)collected.get(bucket)).add(speciesId.toLowerCase());
               }
            }
         }
      }

      SPECIES_BY_BUCKET.clear();
      int total = 0;

      for(Map.Entry<HuntBucket, Set<String>> entry : collected.entrySet()) {
         SPECIES_BY_BUCKET.put((HuntBucket)entry.getKey(), new ArrayList<>((Collection)entry.getValue()));
         total += ((Set)entry.getValue()).size();
      }

      ServerBuddy.LOGGER.info("[Hunt] Species index by rarity updated: {} species (common={}, uncommon={}, rare={}, ultra-rare={}).", new Object[]{total, ((List)SPECIES_BY_BUCKET.get(HuntBucket.COMMON)).size(), ((List)SPECIES_BY_BUCKET.get(HuntBucket.UNCOMMON)).size(), ((List)SPECIES_BY_BUCKET.get(HuntBucket.RARE)).size(), ((List)SPECIES_BY_BUCKET.get(HuntBucket.ULTRA_RARE)).size()});
   }

   public static String pickRandomSpecies(HuntBucket bucket) {
      List<String> pool = (List)SPECIES_BY_BUCKET.get(bucket);
      return pool != null && !pool.isEmpty() ? (String)pool.get(RANDOM.nextInt(pool.size())) : null;
   }

   private static HuntBucket resolveBucket(String bucket) {
      if (bucket == null) {
         return null;
      } else {
         HuntBucket var10000;
         switch (bucket.trim().toLowerCase()) {
            case "common":
               var10000 = HuntBucket.COMMON;
               break;
            case "uncommon":
               var10000 = HuntBucket.UNCOMMON;
               break;
            case "rare":
               var10000 = HuntBucket.RARE;
               break;
            case "ultra-rare":
            case "ultra_rare":
            case "ultrarare":
               var10000 = HuntBucket.ULTRA_RARE;
               break;
            default:
               var10000 = null;
         }

         return var10000;
      }
   }

   private static boolean isLegendary(String speciesId) {
      Species species = PokemonSpecies.getByName(speciesId);
      return species != null && species.getLabels().contains("legendary");
   }
}
