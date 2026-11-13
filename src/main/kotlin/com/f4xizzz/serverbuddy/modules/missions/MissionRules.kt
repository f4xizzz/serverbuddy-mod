package com.f4xizzz.serverbuddy.modules.missions

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/** Regras de calendário e sorteio das missões (lógica pura, dá para testar sem o Minecraft). */
object MissionRules {
    /**
     * Identifica o período de [period] em que [now] cai: o dia (para diárias) ou o dia em que a semana começou (semanais).
     * A virada acontece às [resetHour] horas, não à meia-noite. Para `NONE` devolve "".
     */
    @JvmStatic
    fun periodKey(period: MissionPeriod, now: LocalDateTime, resetHour: Int, weekStart: String): String {
        val shifted = now.minusHours(resetHour.coerceIn(0, 23).toLong())
        return when (period) {
            MissionPeriod.NONE -> ""
            MissionPeriod.DAILY -> shifted.toLocalDate().toString()
            MissionPeriod.WEEKLY -> {
                val day = runCatching { DayOfWeek.valueOf(weekStart.trim().uppercase()) }.getOrDefault(DayOfWeek.MONDAY)
                shifted.toLocalDate().with(TemporalAdjusters.previousOrSame(day)).toString()
            }
        }
    }

    /** Quando a [periodKey] atual termina (para mostrar "troca em 5h"). null para `NONE`. */
    @JvmStatic
    fun nextReset(period: MissionPeriod, now: LocalDateTime, resetHour: Int, weekStart: String): LocalDateTime? {
        val hour = resetHour.coerceIn(0, 23)
        val shifted = now.minusHours(hour.toLong())
        return when (period) {
            MissionPeriod.NONE -> null
            MissionPeriod.DAILY -> shifted.toLocalDate().plusDays(1).atStartOfDay().plusHours(hour.toLong())
            MissionPeriod.WEEKLY -> {
                val day = runCatching { DayOfWeek.valueOf(weekStart.trim().uppercase()) }.getOrDefault(DayOfWeek.MONDAY)
                shifted.toLocalDate().with(TemporalAdjusters.next(day)).atStartOfDay().plusHours(hour.toLong())
            }
        }
    }

    /** Quais [ids] aparecem neste período: [pick] deles, sempre os mesmos para o mesmo [seed]. [pick] 0 (ou maior que a lista) = todos. */
    @JvmStatic
    fun rotation(ids: List<String>, pick: Int, seed: String): Set<String> {
        if (pick <= 0 || pick >= ids.size) return ids.toSet()
        return ids.sorted().shuffled(Random(seed.hashCode().toLong())).take(pick).toSet()
    }
}
