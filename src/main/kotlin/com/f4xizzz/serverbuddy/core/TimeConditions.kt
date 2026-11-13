package com.f4xizzz.serverbuddy.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Condições de data e hora, iguais em todos os módulos que têm condições (MOTD, Blacklist, Missões...):
 *
 *  - `time:18:00-23:30`            horário do servidor (aceita virada de dia: `22:00-02:00`)
 *  - `day:sat,sun`                 dia da semana (`mon,tue,wed,thu,fri,sat,sun` ou `seg,ter,qua,qui,sex,sab,dom`)
 *  - `date:2026-12-24..2026-12-26` datas (uma só ou um intervalo, ambas inclusas)
 */
object TimeConditions {
    private val NAMES = setOf("time", "day", "date")

    private val DAYS: Map<String, DayOfWeek> = buildMap {
        val names = mapOf(
            DayOfWeek.MONDAY to listOf("mon", "monday", "seg", "segunda"),
            DayOfWeek.TUESDAY to listOf("tue", "tuesday", "ter", "terca", "terça"),
            DayOfWeek.WEDNESDAY to listOf("wed", "wednesday", "qua", "quarta"),
            DayOfWeek.THURSDAY to listOf("thu", "thursday", "qui", "quinta"),
            DayOfWeek.FRIDAY to listOf("fri", "friday", "sex", "sexta"),
            DayOfWeek.SATURDAY to listOf("sat", "saturday", "sab", "sábado", "sabado"),
            DayOfWeek.SUNDAY to listOf("sun", "sunday", "dom", "domingo")
        )
        for ((day, aliases) in names) for (alias in aliases) put(alias, day)
    }

    /** [name] (já em minúsculo) é uma condição de tempo? */
    @JvmStatic
    fun isTimeCondition(name: String): Boolean = name in NAMES

    /** Resultado da condição [name] com o argumento [arg], ou `null` se o argumento está mal escrito. */
    @JvmStatic
    fun evaluate(name: String, arg: String, now: LocalDateTime): Boolean? = when (name) {
        "time" -> timeRange(arg, now.toLocalTime())
        "day" -> days(arg, now.dayOfWeek)
        "date" -> dateRange(arg, now.toLocalDate())
        else -> null
    }

    private fun timeRange(arg: String, now: LocalTime): Boolean? {
        val parts = arg.split('-')
        if (parts.size != 2) return null
        val from = runCatching { LocalTime.parse(parts[0].trim().padStart(5, '0')) }.getOrNull() ?: return null
        val to = runCatching { LocalTime.parse(parts[1].trim().padStart(5, '0')) }.getOrNull() ?: return null
        return if (from <= to) now >= from && now <= to else now >= from || now <= to
    }

    private fun days(arg: String, today: DayOfWeek): Boolean? {
        val wanted = arg.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return null
        val set = wanted.map { DAYS[it] ?: return null }.toSet()
        return today in set
    }

    private fun dateRange(arg: String, today: LocalDate): Boolean? {
        val parts = arg.split("..")
        val from = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return null
        val to = if (parts.size > 1) runCatching { LocalDate.parse(parts[1].trim()) }.getOrNull() ?: return null else from
        return !today.isBefore(from) && !today.isAfter(to)
    }
}
