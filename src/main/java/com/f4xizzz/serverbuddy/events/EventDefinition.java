package com.f4xizzz.serverbuddy.events;

import com.google.gson.annotations.SerializedName;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EventDefinition {
   public transient String _nome_arquivo_id;
   public String nome_evento = "2x_shiny";
   public int status = 1;
   @SerializedName(
      value = "dia_inicio",
      alternate = {"dias_semana", "dia_inicio_semana"}
   )
   public String dia_inicio = "segunda, quarta, sabado";
   @SerializedName(
      value = "dia_fim",
      alternate = {"dia_fim_semana"}
   )
   public String dia_fim = null;
   @SerializedName(
      value = "data_inicio",
      alternate = {"data_inicio_exata", "datas_especificas"}
   )
   public String data_inicio = null;
   @SerializedName(
      value = "data_fim",
      alternate = {"data_fim_exata"}
   )
   public String data_fim = null;
   public String horario_inicio = "00:00";
   public String horario_fim = "23:59";
   public float cobblemon_shiny_rate = 16000.0F;
   public String comando_inicio = null;
   public String comando_fim = null;
   public String sidebar_linha = "<yellow><b>2X SHINY <white>%tempo%";
   public String webhook_url = null;
   // true = post to the shared webhook.conf; false = use webhook_url above
   public boolean default_settings = true;
   public String mensagem_inicio = "<yellow>[Events] The 2X SHINY event has started!";
   public String mensagem_fim = "<red>[Events] The 2X SHINY event has ended!";
   public String mensagem_anuncio_recorrente = "<yellow>[Events] The 2X SHINY is on! Enjoy!";
   public int intervalo_anuncio_minutos = 5;
   public transient boolean isActive = false;
   public transient int currentAnnouncementSeconds = 0;
   private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   public String getEventId() {
      if (this.nome_evento != null && !this.nome_evento.trim().isEmpty()) {
         return this.nome_evento.trim();
      } else {
         return this._nome_arquivo_id != null ? this._nome_arquivo_id : "evento";
      }
   }

   public List<LocalDate> getDatasInicioList() {
      List<LocalDate> result = new ArrayList<>();
      if (this.data_inicio != null && !this.data_inicio.trim().isEmpty()) {
         String[] parts = this.data_inicio.split("[,;]");

         for(String part : parts) {
            String cleanPart = part.trim().replace("\"", "").replace("'", "");
            if (!cleanPart.isEmpty()) {
               try {
                  result.add(LocalDate.parse(cleanPart, DATE_FORMAT));
               } catch (Exception var9) {
               }
            }
         }
      }

      return result;
   }

   public LocalDate getLocalDateFim() {
      if (this.data_fim != null && !this.data_fim.trim().isEmpty()) {
         try {
            String clean = this.data_fim.trim().replace("\"", "").replace("'", "");
            return clean.isEmpty() ? null : LocalDate.parse(clean, DATE_FORMAT);
         } catch (Exception var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   public List<DayOfWeek> getDiasSemanaList() {
      List<DayOfWeek> result = new ArrayList<>();
      if (this.dia_inicio != null && !this.dia_inicio.trim().isEmpty()) {
         String[] parts = this.dia_inicio.split("[,;]");

         for(String part : parts) {
            DayOfWeek day = this.parseDay(part);
            if (day != null && !result.contains(day)) {
               result.add(day);
            }
         }
      }

      return result;
   }

   public DayOfWeek getDiaFim() {
      return this.parseDay(this.dia_fim);
   }

   private DayOfWeek parseDay(String day) {
      if (day != null && !day.trim().isEmpty()) {
         switch (day.toLowerCase().trim().replace("\"", "").replace("'", "")) {
            case "segunda":
            case "segunda-feira":
               return DayOfWeek.MONDAY;
            case "terca":
            case "ter\u00e7a":
            case "terca-feira":
            case "ter\u00e7a-feira":
               return DayOfWeek.TUESDAY;
            case "quarta":
            case "quarta-feira":
               return DayOfWeek.WEDNESDAY;
            case "quinta":
            case "quinta-feira":
               return DayOfWeek.THURSDAY;
            case "sexta":
            case "sexta-feira":
               return DayOfWeek.FRIDAY;
            case "sabado":
            case "s\u00e1bado":
               return DayOfWeek.SATURDAY;
            case "domingo":
               return DayOfWeek.SUNDAY;
            default:
               try {
                  return DayOfWeek.valueOf(day.toLowerCase().trim().replace("\"", "").replace("'", "").toUpperCase());
               } catch (Exception var6) {
                  return null;
               }
         }
      } else {
         return null;
      }
   }

   public LocalTime getHorarioInicio() {
      if (this.horario_inicio != null && !this.horario_inicio.trim().isEmpty()) {
         try {
            String startStr = this.horario_inicio.trim().replace("\"", "").replace("'", "");
            return LocalTime.parse(startStr);
         } catch (Exception var2) {
            return LocalTime.MIN;
         }
      } else {
         return LocalTime.MIN;
      }
   }

   public LocalTime getHorarioFim() {
      if (this.horario_fim != null && !this.horario_fim.trim().isEmpty()) {
         try {
            String endStr = this.horario_fim.trim().replace("\"", "").replace("'", "");
            return endStr.equals("23:59") ? LocalTime.of(23, 59, 59) : LocalTime.parse(endStr);
         } catch (Exception var2) {
            return LocalTime.MAX;
         }
      } else {
         return LocalTime.MAX;
      }
   }
}
