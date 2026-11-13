package com.f4xizzz.serverbuddy.events;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public class EventSidebarManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File SIDEBAR_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "styled-sidebars/styles");
   private static final File SIDEBAR_FILE;
   private static final File STATE_FILE;
   private static final String LEGACY_MARKER = "\u200b";

   private EventSidebarManager() {
   }

   public static void updateSidebar(MinecraftServer server, String newLine) {
      // the integration only exists with Styled Sidebars installed: without it no config/styled-sidebars folder is created
      if (!FabricLoader.getInstance().isModLoaded("styled-sidebars")) {
         return;
      }
      try {
         if (!SIDEBAR_DIR.exists()) {
            SIDEBAR_DIR.mkdirs();
         }

         JsonObject root = readOrCreateRoot();
         JsonArray lines = getOrCreateLinesArray(root);
         removeLegacyMarkedLines(lines);
         String previousLine = readState();
         if (previousLine != null) {
            removeLine(lines, previousLine);
         }

         if (newLine != null && !containsLine(lines, newLine)) {
            lines.add(newLine);
         }

         FileWriter writer = new FileWriter(SIDEBAR_FILE);

         try {
            GSON.toJson(root, writer);
         } catch (Throwable var9) {
            try {
               writer.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }

            throw var9;
         }

         writer.close();
         writeState(newLine);
         ServerBuddy.LOGGER.info("[Events] Sidebar updated: {}", newLine == null ? "(line removed)" : newLine);
         reloadStyledSidebars(server);
      } catch (IOException e) {
         ServerBuddy.LOGGER.error("[Events] Could not update the Styled Sidebars sidebar!", e);
      }

   }

   private static String readState() {
      if (!STATE_FILE.exists()) {
         return null;
      } else {
         try {
            String content = Files.readString(STATE_FILE.toPath());
            return content.isEmpty() ? null : content;
         } catch (IOException e) {
            ServerBuddy.LOGGER.warn("[Events] Could not read the previous sidebar state.", e);
            return null;
         }
      }
   }

   private static void writeState(String line) {
      try {
         File dir = STATE_FILE.getParentFile();
         if (!dir.exists()) {
            dir.mkdirs();
         }

         if (line == null) {
            Files.deleteIfExists(STATE_FILE.toPath());
         } else {
            Files.writeString(STATE_FILE.toPath(), line);
         }
      } catch (IOException e) {
         ServerBuddy.LOGGER.error("[Events] Failed to save sidebar state!", e);
      }

   }

   private static JsonObject readOrCreateRoot() {
      if (SIDEBAR_FILE.exists()) {
         try {
            label43: {
               FileReader reader = new FileReader(SIDEBAR_FILE);

               JsonObject var2;
               label37: {
                  try {
                     JsonElement parsed = JsonParser.parseReader(reader);
                     if (parsed != null && parsed.isJsonObject()) {
                        var2 = parsed.getAsJsonObject();
                        break label37;
                     }
                  } catch (Throwable var4) {
                     try {
                        reader.close();
                     } catch (Throwable var3) {
                        var4.addSuppressed(var3);
                     }

                     throw var4;
                  }

                  reader.close();
                  break label43;
               }

               reader.close();
               return var2;
            }
         } catch (Exception e) {
            ServerBuddy.LOGGER.warn("[Events] Styled Sidebars default.json is invalid, recreating it from scratch.", e);
         }
      }

      JsonObject root = new JsonObject();
      root.add("lines", new JsonArray());
      return root;
   }

   private static JsonArray getOrCreateLinesArray(JsonObject root) {
      if (root.has("pages") && root.get("pages").isJsonArray()) {
         JsonArray pages = root.getAsJsonArray("pages");
         if (pages.isEmpty()) {
            JsonArray firstPage = new JsonArray();
            pages.add(firstPage);
            return firstPage;
         } else {
            JsonElement firstPage = pages.get(0);
            if (firstPage.isJsonArray()) {
               return firstPage.getAsJsonArray();
            } else if (!firstPage.isJsonObject()) {
               return pages;
            } else {
               JsonObject pageObj = firstPage.getAsJsonObject();
               if (!pageObj.has("lines") || !pageObj.get("lines").isJsonArray()) {
                  pageObj.add("lines", new JsonArray());
               }

               return pageObj.getAsJsonArray("lines");
            }
         }
      } else {
         if (!root.has("lines") || !root.get("lines").isJsonArray()) {
            root.add("lines", new JsonArray());
         }

         return root.getAsJsonArray("lines");
      }
   }

   private static boolean containsLine(JsonArray lines, String target) {
      for(JsonElement elem : lines) {
         if (target.equals(textOf(elem))) {
            return true;
         }
      }

      return false;
   }

   private static void removeLine(JsonArray lines, String target) {
      for(int i = lines.size() - 1; i >= 0; --i) {
         if (target.equals(textOf(lines.get(i)))) {
            lines.remove(i);
         }
      }

   }

   private static void removeLegacyMarkedLines(JsonArray lines) {
      for(int i = lines.size() - 1; i >= 0; --i) {
         String text = textOf(lines.get(i));
         if (text != null && text.startsWith("\u200b")) {
            lines.remove(i);
         }
      }

   }

   private static String textOf(JsonElement elem) {
      if (elem == null) {
         return null;
      } else if (elem.isJsonPrimitive()) {
         return elem.getAsString();
      } else if (elem.isJsonArray() && !elem.getAsJsonArray().isEmpty()) {
         JsonElement first = elem.getAsJsonArray().get(0);
         return first.isJsonPrimitive() ? first.getAsString() : null;
      } else {
         if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("value") && obj.get("value").isJsonPrimitive()) {
               return obj.get("value").getAsString();
            }

            if (obj.has("values") && obj.get("values").isJsonPrimitive()) {
               return obj.get("values").getAsString();
            }
         }

         return null;
      }
   }

   private static void reloadStyledSidebars(MinecraftServer server) {
      if (server != null) {
         try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "styledsidebars reload");
         } catch (Exception var2) {
            ServerBuddy.LOGGER.warn("[Events] Could not reload Styled Sidebars (is the mod installed on the server?).");
         }

      }
   }

   static {
      SIDEBAR_FILE = new File(SIDEBAR_DIR, "default.json");
      STATE_FILE = new File(ModPaths.subDir("Events"), "sidebar_line.state");
   }
}
