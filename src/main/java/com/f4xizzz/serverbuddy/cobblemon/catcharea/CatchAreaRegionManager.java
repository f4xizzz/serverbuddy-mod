package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class CatchAreaRegionManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File DIR = ModPaths.subDir("CatchArea", "areas");
   private static final Map<String, CatchAreaRegion> REGIONS = new LinkedHashMap<>();

   private CatchAreaRegionManager() {
   }

   public static void loadAll() {
      REGIONS.clear();
      if (!DIR.exists()) {
         DIR.mkdirs();
      } else {
         File[] files = DIR.listFiles((dir, fileName) -> fileName.endsWith(".json"));
         if (files != null) {
            for(File file : files) {
               try {
                  FileReader reader = new FileReader(file);

                  try {
                     CatchAreaRegion region = (CatchAreaRegion)GSON.fromJson(reader, CatchAreaRegion.class);
                     if (region != null && region.name != null) {
                        REGIONS.put(region.name.toLowerCase(), region);
                     }
                  } catch (Throwable var9) {
                     try {
                        reader.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }

                     throw var9;
                  }

                  reader.close();
               } catch (Exception e) {
                  ServerBuddy.LOGGER.error("Error loading the catch area from file " + file.getName() + "!", e);
               }
            }

            ServerBuddy.LOGGER.info("Catch areas loaded: {}", REGIONS.size());
         }
      }
   }

   public static void save(CatchAreaRegion region) {
      REGIONS.put(region.name.toLowerCase(), region);

      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         File file = new File(DIR, region.name.toLowerCase() + ".json");
         FileWriter writer = new FileWriter(file);

         try {
            GSON.toJson(region, writer);
         } catch (Throwable var6) {
            try {
               writer.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         writer.close();
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Error saving the catch area '" + region.name + "'!", e);
      }

   }

   public static boolean remove(String name) {
      CatchAreaRegion removed = (CatchAreaRegion)REGIONS.remove(name.toLowerCase());
      if (removed == null) {
         return false;
      } else {
         File file = new File(DIR, name.toLowerCase() + ".json");
         if (file.exists() && !file.delete()) {
            ServerBuddy.LOGGER.error("Could not delete the file of catch area '{}'!", name);
         }

         return true;
      }
   }

   public static CatchAreaRegion get(String name) {
      return (CatchAreaRegion)REGIONS.get(name.toLowerCase());
   }

   public static boolean exists(String name) {
      return REGIONS.containsKey(name.toLowerCase());
   }

   public static Map<String, CatchAreaRegion> getAll() {
      return REGIONS;
   }
}
