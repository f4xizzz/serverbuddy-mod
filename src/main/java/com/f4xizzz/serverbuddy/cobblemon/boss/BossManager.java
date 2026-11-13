package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class BossManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().serializeNulls().registerTypeAdapterFactory(new LenientEnumTypeAdapterFactory()).create();
   private static final File DIR = ModPaths.subDir("Bosses");
   private static final Map<String, BossDefinition> BOSSES = new LinkedHashMap<>();

   private BossManager() {
   }

   public static void loadAll() {
      BOSSES.clear();
      if (!DIR.exists()) {
         DIR.mkdirs();
      } else {
         File[] files = DIR.listFiles((dir, fileName) -> fileName.endsWith(".json"));
         if (files != null) {
            for(File file : files) {
               try {
                  FileReader reader = new FileReader(file);

                  label50: {
                     try {
                        BossDefinition boss = (BossDefinition)GSON.fromJson(reader, BossDefinition.class);
                        if (boss == null || boss.entityUuid == null) {
                           break label50;
                        }

                        boss.id = file.getName().substring(0, file.getName().length() - ".json".length());
                        boss.level = Math.max(1, Math.min(999, boss.level));
                        BOSSES.put(boss.id, boss);
                     } catch (Throwable var9) {
                        try {
                           reader.close();
                        } catch (Throwable var8) {
                           var9.addSuppressed(var8);
                        }

                        throw var9;
                     }

                     reader.close();
                     continue;
                  }

                  reader.close();
               } catch (Exception e) {
                  ServerBuddy.LOGGER.error("Could not load boss from file " + file.getName() + "!", e);
               }
            }

            ServerBuddy.LOGGER.info("Bosses loaded: {}", BOSSES.size());
         }
      }
   }

   public static void save(BossDefinition boss) {
      boss.level = Math.max(1, Math.min(999, boss.level));
      BOSSES.put(boss.id, boss);

      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(boss);
         File tmpFile = new File(DIR, boss.id + ".json.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         File file = new File(DIR, boss.id + ".json");
         Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save boss '" + boss.id + "' to disk!", e);
      }

   }

   public static boolean remove(String id) {
      BossDefinition removed = (BossDefinition)BOSSES.remove(id);
      if (removed == null) {
         return false;
      } else {
         File file = new File(DIR, id + ".json");
         if (file.exists() && !file.delete()) {
            ServerBuddy.LOGGER.error("Could not delete the file of boss '{}'!", id);
         }

         return true;
      }
   }

   public static BossDefinition get(String id) {
      return (BossDefinition)BOSSES.get(id);
   }

   public static Map<String, BossDefinition> getAll() {
      return BOSSES;
   }
}
