package com.f4xizzz.serverbuddy.events;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.f4xizzz.serverbuddy.core.Integrations;
import com.f4xizzz.serverbuddy.core.ResolvedWebhook;
import com.f4xizzz.serverbuddy.core.Webhooks;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final File DIR = ModPaths.subDir("Events");
   private static final File STATUS_FILE;
   private static final List<EventDefinition> LOADED_EVENTS;

   private EventConfigManager() {
   }

   public static void init() {
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      loadConfig();
      updateAllStatus();
   }

   public static void loadConfig() {
      LOADED_EVENTS.clear();
      if (!DIR.exists()) {
         DIR.mkdirs();
      }

      File[] files = DIR.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("status_evento.json"));
      if (files != null && files.length != 0) {
         for(File file : files) {
            try {
               FileReader reader = new FileReader(file);

               try {
                  JsonElement jsonElement = JsonParser.parseReader(reader);
                  readEventosFrom(jsonElement, file.getName());
               } catch (Throwable var9) {
                  try {
                     reader.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }

                  throw var9;
               }

               reader.close();
            } catch (IOException e) {
               ServerBuddy.LOGGER.error("[Events] Error loading event file: " + file.getName(), e);
            }
         }

         ServerBuddy.LOGGER.info("[Events] Events loaded: {}", LOADED_EVENTS.size());
      } else if (Integrations.cobblemonFeatures()) {
         // the sample events change the shiny rate, which only exists with Cobblemon (and in the Plus and Pro jars)
         createDefaultConfig();
      } else {
         ServerBuddy.LOGGER.info("[Events] No event files yet. Add your own in config/ServerBuddy/Events.");
      }
   }

   private static void readEventosFrom(JsonElement jsonElement, String fileName) {
      if (jsonElement.isJsonObject()) {
         JsonObject obj = jsonElement.getAsJsonObject();
         if (obj.has("eventos") && obj.get("eventos").isJsonArray()) {
            for(JsonElement elem : obj.getAsJsonArray("eventos")) {
               addEvent((EventDefinition)GSON.fromJson(elem, EventDefinition.class));
            }
         } else {
            EventDefinition event = (EventDefinition)GSON.fromJson(obj, EventDefinition.class);
            if (event != null) {
               event._nome_arquivo_id = fileName.replace(".json", "");
               LOADED_EVENTS.add(event);
            }
         }
      } else if (jsonElement.isJsonArray()) {
         for(JsonElement elem : jsonElement.getAsJsonArray()) {
            addEvent((EventDefinition)GSON.fromJson(elem, EventDefinition.class));
         }
      }

   }

   private static void addEvent(EventDefinition event) {
      if (event != null) {
         if (event._nome_arquivo_id == null) {
            event._nome_arquivo_id = event.getEventId();
         }

         LOADED_EVENTS.add(event);
      }
   }

   private static void createDefaultConfig() {
      EventListConfig defaultConfig = new EventListConfig();
      EventDefinition shinyEvent = new EventDefinition();
      shinyEvent.nome_evento = "2x_shiny";
      shinyEvent.status = 0;
      shinyEvent.dia_inicio = "segunda, quarta, sabado";
      shinyEvent.horario_inicio = "00:00";
      shinyEvent.horario_fim = "23:59";
      shinyEvent.cobblemon_shiny_rate = 4096.0F;
      shinyEvent.sidebar_linha = "<yellow><b>2X SHINY <white>%tempo%";
      shinyEvent.mensagem_inicio = "<yellow>[Events] The 2X SHINY event has started!";
      shinyEvent.mensagem_fim = "<red>[Events] The 2X SHINY event has ended!";
      shinyEvent.mensagem_anuncio_recorrente = "<yellow>[Events] The 2X SHINY is on! Enjoy!";
      shinyEvent.intervalo_anuncio_minutos = 5;
      EventDefinition datasEvent = new EventDefinition();
      datasEvent.nome_evento = "super_shiny_datas";
      datasEvent.status = 1;
      datasEvent.dia_inicio = null;
      datasEvent.data_inicio = "12/08/2026, 30/10/2026";
      datasEvent.horario_inicio = "19:38";
      datasEvent.horario_fim = "19:48";
      datasEvent.cobblemon_shiny_rate = 1638.4F;
      datasEvent.sidebar_linha = "<gold><b>SUPER SHINY <white>%tempo%";
      datasEvent.mensagem_inicio = "<gold>[Events] SUPER SHINY has started for the special dates!";
      datasEvent.mensagem_fim = "<red>[Events] O SUPER SHINY encerrou!";
      datasEvent.mensagem_anuncio_recorrente = null;
      datasEvent.intervalo_anuncio_minutos = 0;
      defaultConfig.eventos.add(shinyEvent);
      defaultConfig.eventos.add(datasEvent);
      File defaultFile = new File(DIR, "shiny.json");

      try {
         FileWriter writer = new FileWriter(defaultFile);

         try {
            GSON.toJson(defaultConfig, writer);
         } catch (Throwable var8) {
            try {
               writer.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         writer.close();
      } catch (IOException e) {
         ServerBuddy.LOGGER.error("[Events] Failed to create the default shiny.json!", e);
      }

      LOADED_EVENTS.add(shinyEvent);
      LOADED_EVENTS.add(datasEvent);
   }

   public static List<EventDefinition> getEventos() {
      return LOADED_EVENTS;
   }

   public static void updateAllStatus() {
      try {
         JsonObject jsonObject = new JsonObject();
         JsonArray activeArray = new JsonArray();

         for(EventDefinition ev : LOADED_EVENTS) {
            String key = ev.getEventId();
            boolean isRunning = ev.isActive && ev.status == 1;
            jsonObject.addProperty(key, isRunning);
            if (isRunning) {
               activeArray.add(key);
            }
         }

         jsonObject.add("eventos_ativos", activeArray);
         jsonObject.addProperty("ultima_atualizacao", Instant.now().getEpochSecond());
         FileWriter writer = new FileWriter(STATUS_FILE);

         try {
            GSON.toJson(jsonObject, writer);
         } catch (Throwable var7) {
            try {
               writer.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }

            throw var7;
         }

         writer.close();
      } catch (IOException e) {
         ServerBuddy.LOGGER.error("[Events] Failed to save status_evento.json!", e);
      }

   }

   public static void exportStatus(EventDefinition event, boolean status) {
      try {
         JsonObject jsonObject = new JsonObject();
         if (STATUS_FILE.exists()) {
            try {
               FileReader reader = new FileReader(STATUS_FILE);

               try {
                  JsonObject existing = (JsonObject)GSON.fromJson(reader, JsonObject.class);
                  if (existing != null) {
                     jsonObject = existing;
                  }
               } catch (Throwable var10) {
                  try {
                     reader.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }

                  throw var10;
               }

               reader.close();
            } catch (Exception var11) {
            }
         }

         jsonObject.addProperty(event.getEventId(), status);
         JsonArray activeArray = new JsonArray();

         for(EventDefinition ev : LOADED_EVENTS) {
            if (ev.isActive) {
               activeArray.add(ev.getEventId());
            }
         }

         jsonObject.add("eventos_ativos", activeArray);
         jsonObject.addProperty("ultima_atualizacao", Instant.now().getEpochSecond());
         FileWriter writer = new FileWriter(STATUS_FILE);

         try {
            GSON.toJson(jsonObject, writer);
         } catch (Throwable var8) {
            try {
               writer.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         writer.close();
         ResolvedWebhook hook = Webhooks.resolve(event.default_settings, event.webhook_url, null, null);
         if (hook.getEnabled()) {
            sendWebhook(hook.getUrl(), jsonObject);
         }
      } catch (IOException e) {
         ServerBuddy.LOGGER.error("[Events] Failed to export status for event '" + event.getEventId() + "'!", e);
      }

   }

   private static void sendWebhook(String url, JsonObject payload) {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").POST(BodyPublishers.ofString(GSON.toJson(payload))).build();
         client.sendAsync(request, BodyHandlers.ofString());
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("[Events] Failed to send status webhook!", e);
      }

   }

   static {
      STATUS_FILE = new File(DIR, "status_evento.json");
      LOADED_EVENTS = new ArrayList<>();
   }
}
