package com.f4xizzz.serverbuddy.cobblemon.boss.item;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.f4xizzz.serverbuddy.cobblemon.boss.LenientEnumTypeAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class BossItemManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().serializeNulls().registerTypeAdapterFactory(new LenientEnumTypeAdapterFactory()).create();
   private static final File DIR = ModPaths.subDir("Bosses", "Items");
   private static final Map<String, BossItemDefinition> ITEMS = new LinkedHashMap<>();

   private BossItemManager() {
   }

   public static void loadAll() {
      ITEMS.clear();
      if (!DIR.exists()) {
         DIR.mkdirs();
      } else {
         File[] files = DIR.listFiles((dir, fileName) -> fileName.endsWith(".json"));
         if (files != null) {
            for(File file : files) {
               try {
                  FileReader reader = new FileReader(file);

                  label47: {
                     try {
                        BossItemDefinition item = (BossItemDefinition)GSON.fromJson(reader, BossItemDefinition.class);
                        if (item == null) {
                           break label47;
                        }

                        item.name = file.getName().substring(0, file.getName().length() - ".json".length());
                        ITEMS.put(item.name.toLowerCase(), item);
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
                  ServerBuddy.LOGGER.error("Could not load boss item from file " + file.getName() + "!", e);
               }
            }

            ServerBuddy.LOGGER.info("Boss items loaded: {}", ITEMS.size());
         }
      }
   }

   public static void save(BossItemDefinition item) {
      ITEMS.put(item.name.toLowerCase(), item);

      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         String jsonOutput = GSON.toJson(item);
         File tmpFile = new File(DIR, item.name.toLowerCase() + ".json.tmp");
         Files.writeString(tmpFile.toPath(), jsonOutput);
         File file = new File(DIR, item.name.toLowerCase() + ".json");
         Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to save boss item '" + item.name + "' to disk!", e);
      }

   }

   public static boolean remove(String name) {
      BossItemDefinition removed = (BossItemDefinition)ITEMS.remove(name.toLowerCase());
      if (removed == null) {
         return false;
      } else {
         File file = new File(DIR, name.toLowerCase() + ".json");
         if (file.exists() && !file.delete()) {
            ServerBuddy.LOGGER.error("Could not delete the file of boss item '{}'!", name);
         }

         return true;
      }
   }

   public static BossItemDefinition get(String name) {
      return name == null ? null : (BossItemDefinition)ITEMS.get(name.toLowerCase());
   }

   public static boolean exists(String name) {
      return name != null && ITEMS.containsKey(name.toLowerCase());
   }

   public static Map<String, BossItemDefinition> getAll() {
      return ITEMS;
   }
}
