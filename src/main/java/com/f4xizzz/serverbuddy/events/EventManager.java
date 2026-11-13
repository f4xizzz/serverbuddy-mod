package com.f4xizzz.serverbuddy.events;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.cobblemon.events.ShinyRateController;
import com.f4xizzz.serverbuddy.core.Integrations;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class EventManager {
   private static final Map<String, Boolean> ACTIVE_EVENTS = new java.util.concurrent.ConcurrentHashMap<>();
   private static volatile String currentTimerText = "";
   private static String currentSidebarLine = null;
   private static boolean firstCheckDone = false;
   private static boolean initialized = false;

   private EventManager() {
   }

   /** true se o evento [eventId] está ativo agora (pode ser chamado de qualquer thread). */
   public static boolean isActive(String eventId) {
      return ACTIVE_EVENTS.getOrDefault(eventId, false);
   }

   /** true se existe algum evento ativo agora. */
   public static boolean anyActive() {
      return ACTIVE_EVENTS.containsValue(true);
   }

   public static void init() {
      if (!initialized) {
         initialized = true;
         Placeholders.register(ResourceLocation.fromNamespaceAndPath("serverbuddy", "event_timer"), (ctx, arg) -> PlaceholderResult.value(currentTimerText));
      }
   }

   public static void checkEvents(MinecraftServer server) {
      LocalDateTime now = LocalDateTime.now();
      boolean stateChanged = false;
      boolean anyEventActive = false;
      float targetShinyRate = -1.0F;
      EventDefinition activeSidebarEvent = null;
      String newTimerText = "";
      if (Integrations.cobblemonFeatures()) {
         ShinyRateController.captureDefaultOnce();
      }

      for(EventDefinition event : EventConfigManager.getEventos()) {
         boolean shouldBeActive = isWithinSchedule(now, event);
         String eventKey = event.getEventId();
         boolean wasActive = (Boolean)ACTIVE_EVENTS.getOrDefault(eventKey, false);
         if (shouldBeActive != wasActive) {
            ACTIVE_EVENTS.put(eventKey, shouldBeActive);
            event.isActive = shouldBeActive;
            stateChanged = true;
            ServerBuddy.LOGGER.info("[Events] Event '{}' changed to: {}", eventKey, shouldBeActive ? "ATIVO" : "INATIVO");
            EventConfigManager.exportStatus(event, shouldBeActive);
            runLifecycleCommand(server, shouldBeActive ? event.comando_inicio : event.comando_fim, eventKey, shouldBeActive);
            broadcastLifecycleMessage(server, shouldBeActive ? event.mensagem_inicio : event.mensagem_fim);
         } else {
            event.isActive = shouldBeActive;
         }

         if (shouldBeActive) {
            anyEventActive = true;
            if (event.cobblemon_shiny_rate > 0.0F) {
               targetShinyRate = event.cobblemon_shiny_rate;
            }

            if (event.sidebar_linha != null && !event.sidebar_linha.isBlank()) {
               activeSidebarEvent = event;
            }

            LocalDateTime endDateTime = getEndDateTime(now, event);
            if (endDateTime != null) {
               Duration remaining = Duration.between(now, endDateTime);
               if (!remaining.isNegative()) {
                  newTimerText = formatDuration(remaining);
               }
            }

            if (event.intervalo_anuncio_minutos > 0 && event.mensagem_anuncio_recorrente != null && !event.mensagem_anuncio_recorrente.isBlank()) {
               ++event.currentAnnouncementSeconds;
               if (event.currentAnnouncementSeconds >= event.intervalo_anuncio_minutos * 60) {
                  broadcastLifecycleMessage(server, event.mensagem_anuncio_recorrente);
                  event.currentAnnouncementSeconds = 0;
               }
            }
         } else {
            event.currentAnnouncementSeconds = 0;
         }
      }

      currentTimerText = newTimerText;
      String lineToShow = activeSidebarEvent != null ? activeSidebarEvent.sidebar_linha.replace("%tempo%", "%serverbuddy:event_timer%") : null;
      if (!firstCheckDone || !Objects.equals(lineToShow, currentSidebarLine)) {
         EventSidebarManager.updateSidebar(server, lineToShow);
         currentSidebarLine = lineToShow;
      }

      firstCheckDone = true;
      if (Integrations.cobblemonFeatures()) {
         ShinyRateController.apply(anyEventActive, targetShinyRate);
      }
   }

   private static String formatDuration(Duration remaining) {
      long hours = remaining.toHours();
      long minutes = (long)remaining.toMinutesPart();
      long seconds = (long)remaining.toSecondsPart();
      return hours > 0L ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
   }

   private static void runLifecycleCommand(MinecraftServer server, String command, String eventKey, boolean started) {
      if (server != null && command != null && !command.isBlank()) {
         try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command.trim());
            ServerBuddy.LOGGER.info("[Events] Ran the {} command of event '{}': {}", new Object[]{started ? "start" : "end", eventKey, command});
         } catch (Exception e) {
            ServerBuddy.LOGGER.error("[Events] Error running the " + (started ? "start" : "end") + " command of event '" + eventKey + "'!", e);
         }

      }
   }

   private static void broadcastLifecycleMessage(MinecraftServer server, String miniMessage) {
      if (server != null && miniMessage != null && !miniMessage.isBlank() && server.getPlayerList() != null) {
         server.getPlayerList().broadcastSystemMessage(MiniMessageUtil.parse(miniMessage), false);
      }
   }

   private static LocalDateTime getEndDateTime(LocalDateTime now, EventDefinition event) {
      List<LocalDate> exactDateList = event.getDatasInicioList();
      LocalDate exactEndDate = event.getLocalDateFim();
      LocalTime endTime = event.getHorarioFim();
      if (!exactDateList.isEmpty()) {
         if (exactDateList.size() == 1 && exactEndDate != null) {
            return LocalDateTime.of(exactEndDate, endTime);
         }

         if (exactDateList.contains(now.toLocalDate())) {
            return LocalDateTime.of(now.toLocalDate(), endTime);
         }
      } else {
         List<DayOfWeek> daysList = event.getDiasSemanaList();
         if (!daysList.isEmpty()) {
            DayOfWeek endDay = event.getDiaFim();
            boolean isRange = daysList.size() == 1 && endDay != null && endDay != daysList.get(0);
            if (isRange) {
               int endDayValue = endDay.getValue();
               int currentDayValue = now.getDayOfWeek().getValue();
               int daysToAdd = endDayValue - currentDayValue;
               if (daysToAdd < 0) {
                  daysToAdd += 7;
               } else if (daysToAdd == 0 && now.toLocalTime().isAfter(endTime)) {
                  daysToAdd += 7;
               }

               return LocalDateTime.of(now.toLocalDate().plusDays((long)daysToAdd), endTime);
            }

            if (daysList.contains(now.getDayOfWeek())) {
               LocalDate streakEnd = findStreakEnd(now.toLocalDate(), daysList);
               return LocalDateTime.of(streakEnd, endTime);
            }
         }
      }

      return null;
   }

   private static LocalDate findStreakEnd(LocalDate today, List<DayOfWeek> daysList) {
      LocalDate end = today;

      for(DayOfWeek next = today.getDayOfWeek().plus(1L); daysList.contains(next) && !end.isAfter(today.plusDays(6L)); next = next.plus(1L)) {
         end = end.plusDays(1L);
      }

      return end;
   }

   private static LocalDate findStreakStart(LocalDate today, List<DayOfWeek> daysList) {
      LocalDate start = today;

      for(DayOfWeek previous = today.getDayOfWeek().minus(1L); daysList.contains(previous) && !start.isBefore(today.minusDays(6L)); previous = previous.minus(1L)) {
         start = start.minusDays(1L);
      }

      return start;
   }

   private static boolean isWithinSchedule(LocalDateTime now, EventDefinition event) {
      if (event.status == 0) {
         return false;
      } else {
         LocalTime currentTime = now.toLocalTime();
         LocalTime startTime = event.getHorarioInicio();
         LocalTime endTime = event.getHorarioFim();
         LocalDate currentDate = now.toLocalDate();
         List<LocalDate> exactDateList = event.getDatasInicioList();
         if (!exactDateList.isEmpty()) {
            LocalDate exactEndDate = event.getLocalDateFim();
            if (exactDateList.size() == 1 && exactEndDate != null) {
               LocalDate exactStartDate = (LocalDate)exactDateList.get(0);
               LocalDateTime startDateTime = LocalDateTime.of(exactStartDate, startTime);
               LocalDateTime endDateTime = LocalDateTime.of(exactEndDate, endTime);
               return !now.isBefore(startDateTime) && !now.isAfter(endDateTime);
            } else if (!exactDateList.contains(currentDate)) {
               return false;
            } else {
               return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
            }
         } else {
            List<DayOfWeek> daysList = event.getDiasSemanaList();
            if (!daysList.isEmpty()) {
               DayOfWeek endDay = event.getDiaFim();
               if (daysList.size() > 1 || endDay == null || endDay == daysList.get(0)) {
                  if (!daysList.contains(now.getDayOfWeek())) {
                     return false;
                  }

                  LocalDateTime windowStart = LocalDateTime.of(findStreakStart(currentDate, daysList), startTime);
                  LocalDateTime windowEnd = LocalDateTime.of(findStreakEnd(currentDate, daysList), endTime);
                  return !now.isBefore(windowStart) && !now.isAfter(windowEnd);
               }

               int currentDayValue = now.getDayOfWeek().getValue();
               int startDayValue = ((DayOfWeek)daysList.get(0)).getValue();
               int endDayValue = endDay.getValue();
               if (startDayValue == endDayValue) {
                  if (currentDayValue != startDayValue) {
                     return false;
                  }

                  return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
               }

               if (startDayValue < endDayValue) {
                  if (currentDayValue > startDayValue && currentDayValue < endDayValue) {
                     return true;
                  }

                  if (currentDayValue == startDayValue) {
                     return !currentTime.isBefore(startTime);
                  }

                  if (currentDayValue == endDayValue) {
                     return !currentTime.isAfter(endTime);
                  }
               } else {
                  if (currentDayValue > startDayValue || currentDayValue < endDayValue) {
                     return true;
                  }

                  if (currentDayValue == startDayValue) {
                     return !currentTime.isBefore(startTime);
                  }

                  if (currentDayValue == endDayValue) {
                     return !currentTime.isAfter(endTime);
                  }
               }
            }

            return false;
         }
      }
   }
}
