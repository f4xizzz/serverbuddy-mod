package com.f4xizzz.serverbuddy.cobblemon.hunt;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HuntPool {
   private static final int MAX_GENERATION_ATTEMPTS = 200;
   private final UUID owner;
   private final Map<UUID, HuntTarget> targets = new LinkedHashMap<>();
   private final Set<String> speciesInUse = new HashSet<>();

   public HuntPool(UUID owner) {
      this.owner = owner;
   }

   public Map<UUID, HuntTarget> getTargets() {
      return this.targets;
   }

   public void ensureFilled() {
      HuntConfig config = HuntConfigManager.getConfig();

      while(this.targets.size() < config.quantidadeHunts) {
         HuntTarget target = this.generateTarget();
         if (target == null) {
            break;
         }

         this.targets.put(target.id, target);
         this.speciesInUse.add(target.pokemon.getSpecies().getName().toLowerCase());
      }

   }

   public UUID matches(Pokemon captured) {
      for(HuntTarget target : this.targets.values()) {
         if (target.matches(captured)) {
            return target.id;
         }
      }

      return null;
   }

   public HuntTarget getTarget(UUID id) {
      return (HuntTarget)this.targets.get(id);
   }

   public UUID getOwner() {
      return this.owner;
   }

   public Replacement replace(UUID id) {
      HuntTarget removed = (HuntTarget)this.targets.remove(id);
      if (removed == null) {
         return null;
      } else {
         this.speciesInUse.remove(removed.pokemon.getSpecies().getName().toLowerCase());
         HuntTarget novo = this.generateTarget();
         if (novo != null) {
            this.targets.put(novo.id, novo);
            this.speciesInUse.add(novo.pokemon.getSpecies().getName().toLowerCase());
         }

         return new Replacement(removed, novo);
      }
   }

   public void clear() {
      this.targets.clear();
      this.speciesInUse.clear();
   }

   private HuntTarget generateTarget() {
      HuntConfig config = HuntConfigManager.getConfig();

      for(int attempt = 0; attempt < 200; ++attempt) {
         HuntBucket bucket = config.raridade.getRandomBucket();
         String speciesId = HuntSpeciesIndex.pickRandomSpecies(bucket);
         if (speciesId != null && !this.speciesInUse.contains(speciesId) && !isBlacklisted(speciesId, config.listaNegra)) {
            Pokemon pokemon = PokemonProperties.Companion.parse(speciesId).create();
            HuntReward reward = this.resolveReward(bucket, pokemon, config);
            long expiresAt = System.currentTimeMillis() + (long)config.duracaoMinutos * 60000L;
            return new HuntTarget(this.owner, reward.preco, reward.comandos, pokemon, expiresAt, bucket);
         }
      }

      ServerBuddy.LOGGER.warn("[Hunt] Could not generate a new hunt after {} attempts \u2014 check that the blacklist (lista_negra) is not blocking the whole pool of a bucket.", 200);
      return null;
   }

   private HuntReward resolveReward(HuntBucket bucket, Pokemon pokemon, HuntConfig config) {
      String speciesId = pokemon.getSpecies().getName();
      String formaId = pokemon.getForm().getName();

      for(HuntCustomPrice custom : config.precosCustomizados) {
         if (custom.matches(speciesId, formaId)) {
            return custom.recompensa;
         }
      }

      return config.recompensas.get(bucket);
   }

   private static boolean isBlacklisted(String speciesId, List<String> blacklist) {
      for(String blocked : blacklist) {
         if (blocked != null && blocked.trim().equalsIgnoreCase(speciesId.trim())) {
            return true;
         }
      }

      return false;
   }

   public static record Replacement(HuntTarget removed, HuntTarget added) {
   }
}
