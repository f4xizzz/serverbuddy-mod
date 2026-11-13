package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import com.f4xizzz.serverbuddy.testutil.MinecraftBootstrap

class CalendarConfigTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrap() = MinecraftBootstrap.ensure()
    }

    private inline fun <reified T : Any> roundTrip(config: T): T {
        val text = ConfigDocs.render(config, SbGson.defaultsWriter)
        return SbGson.pretty.fromJson(text, T::class.java)
    }

    @Test
    fun `the monthly calendar defaults have a reward for every day and survive a write and read`() {
        val file = roundTrip(CalendarFile.monthly())
        assertEquals((1..31).map { it.toString() }, file.days.keys.toList())
        assertTrue(file.days.values.all { it.rewards.isNotEmpty() })
        assertEquals(31, file.menu.daySlots.size)
        assertEquals("today", file.claimMode)
    }

    @Test
    fun `the weekly calendar defaults cover the seven weekdays`() {
        val file = roundTrip(CalendarFile.weekly())
        assertEquals(listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"), file.days.keys.toList())
        assertEquals(7, file.menu.daySlots.size)
    }

    @Test
    fun `the streak defaults have seven days`() {
        val file = roundTrip(StreakFile())
        assertEquals((1..7).map { it.toString() }, file.days.keys.toList())
        assertTrue(file.cycle)
        assertEquals(0, file.graceDays)
    }

    @Test
    fun `the shared config keeps its messages`() {
        val config = roundTrip(CalendarConfig())
        assertTrue(config.messages.rewardClaimed.contains("%day%"))
        assertEquals("monday", config.weekStart)
    }

    @Test
    fun `view names parse in any case`() {
        assertEquals(CalendarView.MONTHLY, CalendarView.parse("Monthly"))
        assertEquals(CalendarView.STREAK, CalendarView.parse(" streak "))
        assertEquals(null, CalendarView.parse("yearly"))
    }
}
