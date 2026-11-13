package com.f4xizzz.serverbuddy.cobblemon.hunt;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import java.util.UUID;

public class HuntTarget {
   public final UUID id = UUID.randomUUID();
   public final UUID owner;
   public final double preco;
   public final List<String> comandos;
   public final Pokemon pokemon;
   public final long expiresAtMillis;
   public final HuntBucket bucket;

   public HuntTarget(UUID owner, double preco, List<String> comandos, Pokemon pokemon, long expiresAtMillis, HuntBucket bucket) {
      this.owner = owner;
      this.preco = preco;
      this.comandos = comandos;
      this.pokemon = pokemon;
      this.expiresAtMillis = expiresAtMillis;
      this.bucket = bucket;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() >= this.expiresAtMillis;
   }

   public boolean matches(Pokemon captured) {
      if (!captured.getSpecies().getName().equalsIgnoreCase(this.pokemon.getSpecies().getName())) {
         return false;
      } else if (!captured.getForm().getName().equalsIgnoreCase(this.pokemon.getForm().getName())) {
         return false;
      } else {
         HuntMatchProperties props = HuntConfigManager.getConfig().propriedadesVerificadas;
         if (props.habilidade && !captured.getAbility().getName().equalsIgnoreCase(this.pokemon.getAbility().getName())) {
            return false;
         } else if (props.genero && captured.getGender() != this.pokemon.getGender()) {
            return false;
         } else if (props.natureza && !captured.getNature().getName().getPath().equalsIgnoreCase(this.pokemon.getNature().getName().getPath())) {
            return false;
         } else if (props.shiny) {
            return captured.getShiny() == this.pokemon.getShiny();
         } else {
            return true;
         }
      }
   }
}
