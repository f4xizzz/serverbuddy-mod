package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.redeem.RedeemCode
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the Redeem section says about a code (its state) and what it refuses to save. */
class RedeemViewTest {
    private val now = ZonedDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun code(maxUses: Int = 0, expiresAt: String? = null) = RedeemCode().also {
        it.maxUses = maxUses
        it.expiresAt = expiresAt
    }

    private fun problems(json: String) = RedeemView.problems(JsonParser.parseString(json))

    // ---------------------------------------------------------------- the state of a code

    @Test
    fun `a code with room left and no date is active`() {
        assertEquals("active", RedeemView.stateOf(code(), 0, now))
        assertEquals("active", RedeemView.stateOf(code(maxUses = 5), 4, now))
    }

    @Test
    fun `a code with every use taken is exhausted, and 0 means no limit`() {
        assertEquals("exhausted", RedeemView.stateOf(code(maxUses = 5), 5, now))
        assertEquals("exhausted", RedeemView.stateOf(code(maxUses = 5), 7, now))
        assertEquals("active", RedeemView.stateOf(code(maxUses = 0), 9999, now))
    }

    @Test
    fun `a code past its date is expired, a date alone lasts the whole day`() {
        assertEquals("expired", RedeemView.stateOf(code(expiresAt = "2026-12-30"), 0, now))
        assertEquals("active", RedeemView.stateOf(code(expiresAt = "2026-12-31"), 0, now))
        assertEquals("active", RedeemView.stateOf(code(expiresAt = "2027-01-02"), 0, now))
    }

    @Test
    fun `a date the mod cannot read makes the code invalid, because nobody can redeem it`() {
        assertEquals("invalid", RedeemView.stateOf(code(expiresAt = "next friday"), 0, now))
    }

    @Test
    fun `invalid wins over expired and expired wins over exhausted`() {
        assertEquals("invalid", RedeemView.stateOf(code(maxUses = 1, expiresAt = "garbage"), 1, now))
        assertEquals("expired", RedeemView.stateOf(code(maxUses = 1, expiresAt = "2026-01-01"), 1, now))
    }

    // ---------------------------------------------------------------- what the form refuses

    @Test
    fun `a good code has nothing to fix`() {
        val found = problems("""{"max_uses":3,"permission":"vip.codes","expires_at":"2026-12-31 18:30","commands":["give %player% minecraft:diamond 1"],"items":[],"entries":[]}""")
        assertTrue(found.none { !it.warning }, found.joinToString { "${it.path}: ${it.message}" })
    }

    @Test
    fun `an unreadable date is refused at the date, and an empty one is fine`() {
        val bad = problems("""{"expires_at":"soon"}""").filter { !it.warning }
        assertEquals(listOf("expires_at"), bad.map { it.path })
        assertEquals("expiry", bad.single().code)
        assertTrue(problems("""{"expires_at":""}""").none { !it.warning })
    }

    @Test
    fun `a negative limit is refused at max_uses`() {
        val bad = problems("""{"max_uses":-1}""").filter { !it.warning }
        assertEquals(listOf("max_uses"), bad.map { it.path })
    }

    @Test
    fun `something that is not an object is refused as a whole`() {
        val bad = problems("[1,2]").filter { !it.warning }
        assertEquals(listOf(""), bad.map { it.path })
    }
}
