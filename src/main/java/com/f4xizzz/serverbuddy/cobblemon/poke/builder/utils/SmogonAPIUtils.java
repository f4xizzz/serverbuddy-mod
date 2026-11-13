package com.f4xizzz.serverbuddy.cobblemon.poke.builder.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SmogonAPIUtils {
   private static final Map<Integer, JsonObject> cachedGenerations = new HashMap<>();
   private static final String SMOGON_BASE_URL = "https://pkmn.github.io/smogon/data/sets/gen";

   public static CompletableFuture<List<SmogonBuild>> getBuildsAsync(String pokemonName) {
      return CompletableFuture.supplyAsync(() -> {
         List<SmogonBuild> finalBuilds = new ArrayList<>();
         List<SmogonBuild> allDiscoveredBuilds = new ArrayList<>();

         try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();

            for(int gen = 9; gen >= 1; --gen) {
               JsonObject genData = (JsonObject)cachedGenerations.get(gen);
               if (genData == null) {
                  String url = "https://pkmn.github.io/smogon/data/sets/gen" + gen + ".json";
                  HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                  HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                  if (response.statusCode() != 200) {
                     continue;
                  }

                  try {
                     genData = JsonParser.parseString((String)response.body()).getAsJsonObject();
                     cachedGenerations.put(gen, genData);
                  } catch (Exception var10) {
                     continue;
                  }
               }

               if (genData != null) {
                  boolean found = searchAndExtractBuilds(genData, pokemonName, allDiscoveredBuilds, gen);
                  if (found && !allDiscoveredBuilds.isEmpty()) {
                     finalBuilds = filterDiverseBuilds(allDiscoveredBuilds);
                     break;
                  }
               }
            }

            if (finalBuilds.size() == 2) {
               finalBuilds.remove(1);
            }
         } catch (Exception e) {
            System.err.println("[PokeBuilder] Internal error processing Smogon API JSON: " + e.getMessage());
            e.printStackTrace();
         }

         return finalBuilds;
      });
   }

   private static List<SmogonBuild> filterDiverseBuilds(List<SmogonBuild> allBuilds) {
      List<SmogonBuild> selectedBuilds = new ArrayList<>();
      List<String> registeredTypes = new ArrayList<>();

      for(SmogonBuild build : allBuilds) {
         if (selectedBuilds.size() >= 3) {
            break;
         }

         String rawType = build.battleType.replaceAll("<[^>]*>", "");
         if (!registeredTypes.contains(rawType)) {
            selectedBuilds.add(build);
            registeredTypes.add(rawType);
         }
      }

      if (selectedBuilds.size() < 3) {
         for(SmogonBuild build : allBuilds) {
            if (selectedBuilds.size() >= 3) {
               break;
            }

            if (!selectedBuilds.contains(build)) {
               selectedBuilds.add(build);
            }
         }
      }

      return selectedBuilds;
   }

   private static boolean searchAndExtractBuilds(JsonObject genData, String pokemonName, List<SmogonBuild> builds, int gen) {
      boolean foundAny = false;
      String normalizedInput = normalizeString(pokemonName);

      for(Map.Entry<String, JsonElement> rootEntry : genData.entrySet()) {
         if (((JsonElement)rootEntry.getValue()).isJsonObject()) {
            JsonObject formatObj = ((JsonElement)rootEntry.getValue()).getAsJsonObject();

            for(String pokeKey : formatObj.keySet()) {
               if (normalizeString(pokeKey).equals(normalizedInput)) {
                  extractSets((String)rootEntry.getKey(), formatObj.get(pokeKey), builds, gen);
                  foundAny = true;
               }
            }
         }
      }

      if (!foundAny) {
         for(String pokeKey : genData.keySet()) {
            if (normalizeString(pokeKey).equals(normalizedInput)) {
               JsonElement pokeElement = genData.get(pokeKey);
               if (pokeElement.isJsonObject()) {
                  for(Map.Entry<String, JsonElement> formatEntry : pokeElement.getAsJsonObject().entrySet()) {
                     extractSets((String)formatEntry.getKey(), (JsonElement)formatEntry.getValue(), builds, gen);
                  }

                  foundAny = true;
               }
            }
         }
      }

      return foundAny;
   }

   private static void extractSets(String formatName, JsonElement setsElement, List<SmogonBuild> builds, int gen) {
      String battleType = determineBattleType(formatName);
      if (setsElement.isJsonArray()) {
         for(JsonElement setElement : setsElement.getAsJsonArray()) {
            if (setElement.isJsonObject()) {
               builds.add(parseBuild(setElement.getAsJsonObject(), battleType, gen, "Build Competitiva"));
            }
         }
      } else if (setsElement.isJsonObject()) {
         for(Map.Entry<String, JsonElement> setEntry : setsElement.getAsJsonObject().entrySet()) {
            if (((JsonElement)setEntry.getValue()).isJsonObject()) {
               builds.add(parseBuild(((JsonElement)setEntry.getValue()).getAsJsonObject(), battleType, gen, (String)setEntry.getKey()));
            }
         }
      }

   }

   private static SmogonBuild parseBuild(JsonObject setObj, String battleType, int gen, String fallbackName) {
      String name = getSafeString(setObj, "name", fallbackName);
      String item = getSafeString(setObj, "item", "Nenhum");
      String ability = getSafeString(setObj, "ability", "Qualquer");
      String nature = getSafeString(setObj, "nature", "Serious");
      SmogonBuild build = new SmogonBuild(name, item, ability, nature);
      build.battleType = battleType;
      build.generation = gen;
      if (setObj.has("evs")) {
         JsonElement evsElement = setObj.get("evs");
         if (evsElement.isJsonObject()) {
            for(Map.Entry<String, JsonElement> evEntry : evsElement.getAsJsonObject().entrySet()) {
               try {
                  build.evs.put(((String)evEntry.getKey()).toLowerCase(), ((JsonElement)evEntry.getValue()).getAsInt());
               } catch (Exception var15) {
               }
            }
         }
      }

      if (setObj.has("moves")) {
         JsonElement movesElement = setObj.get("moves");
         if (movesElement.isJsonArray()) {
            JsonArray movesArray = movesElement.getAsJsonArray();

            for(int i = 0; i < movesArray.size(); ++i) {
               try {
                  JsonElement moveElement = movesArray.get(i);
                  if (moveElement.isJsonArray()) {
                     JsonArray alternativeMoves = moveElement.getAsJsonArray();
                     if (!alternativeMoves.isEmpty()) {
                        build.moves.add(alternativeMoves.get(0).getAsString());
                     }
                  } else if (moveElement.isJsonPrimitive()) {
                     build.moves.add(moveElement.getAsString());
                  }
               } catch (Exception var14) {
               }
            }
         }
      }

      return build;
   }

   private static String getSafeString(JsonObject obj, String key, String defaultValue) {
      if (!obj.has(key)) {
         return defaultValue;
      } else {
         JsonElement element = obj.get(key);

         try {
            if (element.isJsonPrimitive()) {
               return element.getAsString();
            }

            if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
               return element.getAsJsonArray().get(0).getAsString();
            }
         } catch (Exception var5) {
         }

         return defaultValue;
      }
   }

   private static String determineBattleType(String formatName) {
      formatName = formatName.toLowerCase();
      if (!formatName.contains("vgc") && !formatName.contains("doubles") && !formatName.contains("2v2")) {
         if (!formatName.contains("triples") && !formatName.contains("3v3")) {
            return !formatName.contains("national") && !formatName.contains("natdex") && !formatName.contains("champions") && !formatName.contains("monotype") ? "<aqua>1v1 (Singles)</aqua>" : "<aqua>1v1 (National Dex / Monotype)</aqua>";
         } else {
            return "<gold>3v3 (Triples)</gold>";
         }
      } else {
         return "<light_purple>2v2 (Doubles / VGC)</light_purple>";
      }
   }

   public static void clearCache() {
      cachedGenerations.clear();
   }

   private static String normalizeString(String input) {
      return input == null ? "" : input.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
   }

   public static class SmogonBuild {
      public String name;
      public String item;
      public String ability;
      public String nature;
      public String battleType;
      public int generation;
      public Map<String, Integer> evs = new HashMap<>();
      public List<String> moves = new ArrayList<>();

      public SmogonBuild(String name, String item, String ability, String nature) {
         this.name = name;
         this.item = item;
         this.ability = ability;
         this.nature = nature;
      }
   }
}
