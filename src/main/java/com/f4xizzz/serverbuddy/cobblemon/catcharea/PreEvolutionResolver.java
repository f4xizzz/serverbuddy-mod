package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.pokemon.Species;

public class PreEvolutionResolver {
   private PreEvolutionResolver() {
   }

   public static Species resolveBase(Species species) {
      Species current = species;

      for(PreEvolution preEvolution = species.getPreEvolution(); preEvolution != null; preEvolution = current.getPreEvolution()) {
         current = preEvolution.getSpecies();
      }

      return current;
   }
}
