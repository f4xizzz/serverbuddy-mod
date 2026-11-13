package com.f4xizzz.serverbuddy.modules.leaderboards

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.f4xizzz.serverbuddy.testutil.MinecraftBootstrap
import java.util.UUID

class LeaderboardLogicTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrap() = MinecraftBootstrap.ensure()
    }

    private val alice = UUID.nameUUIDFromBytes("alice".toByteArray())
    private val bob = UUID.nameUUIDFromBytes("bob".toByteArray())

    @BeforeEach
    fun install() {
        val boards = mapOf(
            "playtime" to BoardConfig(type = "playtime"),
            "pokedex_percent" to BoardConfig(type = "pokedex_percent", valueFormat = "%value%%"),
            "money_dollars" to BoardConfig(type = "pokedex_caught", valueFormat = "%value% caught")
        )
        val snapshots = mapOf(
            // play time is stored in ticks: 72000 ticks = 1 hour
            "playtime" to LeaderboardService.Snapshot(listOf(LeaderboardService.Entry(alice, "Alice", 72000.0), LeaderboardService.Entry(bob, "Bob", 36000.0))),
            "pokedex_percent" to LeaderboardService.Snapshot(listOf(LeaderboardService.Entry(bob, "Bob", 42.5))),
            "money_dollars" to LeaderboardService.Snapshot(listOf(LeaderboardService.Entry(alice, "Alice", 1250.0)))
        )
        LeaderboardService.installForTest(boards, snapshots)
    }

    @Test
    fun `name and value of a position`() {
        assertEquals("Alice", LeaderboardService.resolvePlaceholder(null, "playtime_1_name"))
        assertEquals("1h", LeaderboardService.resolvePlaceholder(null, "playtime_1_value"))
        assertEquals("Bob", LeaderboardService.resolvePlaceholder(null, "playtime_2_name"))
        assertEquals("30m", LeaderboardService.resolvePlaceholder(null, "playtime_2_value"))
    }

    @Test
    fun `board ids may contain underscores`() {
        assertEquals("Alice", LeaderboardService.resolvePlaceholder(null, "money_dollars_1_name"))
        assertEquals("1250 caught", LeaderboardService.resolvePlaceholder(null, "money_dollars_1_value"))
        assertEquals("42.5%", LeaderboardService.resolvePlaceholder(null, "pokedex_percent_1_value"))
    }

    @Test
    fun `raw and uuid fields`() {
        assertEquals("72000", LeaderboardService.resolvePlaceholder(null, "playtime_1_raw"))
        assertEquals(alice.toString(), LeaderboardService.resolvePlaceholder(null, "playtime_1_uuid"))
    }

    @Test
    fun `an empty position shows a dash`() {
        assertEquals("-", LeaderboardService.resolvePlaceholder(null, "playtime_5_name"))
        assertEquals("-", LeaderboardService.resolvePlaceholder(null, "pokedex_percent_2_value"))
    }

    @Test
    fun `rank of the viewing player`() {
        assertEquals("2", LeaderboardService.resolvePlaceholder(bob, "playtime_rank"))
        assertEquals("1", LeaderboardService.resolvePlaceholder(bob, "pokedex_percent_rank"))
        assertEquals("-", LeaderboardService.resolvePlaceholder(UUID.randomUUID(), "playtime_rank"))
        assertEquals("-", LeaderboardService.resolvePlaceholder(null, "playtime_rank"))
    }

    @Test
    fun `things that are not leaderboard placeholders are ignored`() {
        assertNull(LeaderboardService.resolvePlaceholder(null, null))
        assertNull(LeaderboardService.resolvePlaceholder(null, "nothing_1_name"))
        assertNull(LeaderboardService.resolvePlaceholder(null, "playtime_x_name"))
        assertNull(LeaderboardService.resolvePlaceholder(null, "playtime_1_unknown"))
        assertNull(LeaderboardService.resolvePlaceholder(null, "playtime"))
    }
}
