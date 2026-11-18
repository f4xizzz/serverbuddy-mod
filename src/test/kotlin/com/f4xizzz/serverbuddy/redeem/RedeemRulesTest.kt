package com.f4xizzz.serverbuddy.redeem

import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.testutil.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RedeemRulesTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, nano: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, second, nano, saoPaulo).toInstant().toEpochMilli()

    // ---------------------------------------------------------------- expires_at

    @Test
    fun `no expires_at means the code never expires`() {
        for (text in listOf(null, "", "   ")) {
            val expiry = RedeemRules.expiry(text, saoPaulo)
            assertEquals(RedeemRules.Expiry.Never, expiry)
            assertFalse(RedeemRules.isExpired(expiry, Long.MAX_VALUE))
        }
    }

    @Test
    fun `a date alone lasts until the end of that day`() {
        val expiry = RedeemRules.expiry("2026-12-31", saoPaulo)
        assertEquals(RedeemRules.Expiry.At(at(2027, 1, 1) - 1), expiry)
        assertFalse(RedeemRules.isExpired(expiry, at(2026, 12, 31, 23, 59, 59, 999_000_000)))
        assertTrue(RedeemRules.isExpired(expiry, at(2027, 1, 1)))
    }

    @Test
    fun `a date with a time is read in the server time zone`() {
        val expected = RedeemRules.Expiry.At(at(2026, 12, 31, 18, 30))
        assertEquals(expected, RedeemRules.expiry("2026-12-31 18:30", saoPaulo))
        assertEquals(expected, RedeemRules.expiry("2026-12-31T18:30", saoPaulo))
        assertEquals(RedeemRules.Expiry.At(at(2026, 12, 31, 18, 30, 15)), RedeemRules.expiry("2026-12-31T18:30:15", saoPaulo))
        // the same wall clock in another zone is another moment
        assertEquals(RedeemRules.Expiry.At(at(2026, 12, 31, 18, 30) - 3 * 3_600_000L), RedeemRules.expiry("2026-12-31 18:30", ZoneId.of("UTC")))
    }

    @Test
    fun `a date with its own zone ignores the server zone`() {
        val moment = 1_798_759_800_000L // 2026-12-31T23:30:00Z
        assertEquals(RedeemRules.Expiry.At(moment), RedeemRules.expiry("2026-12-31T23:30:00Z", saoPaulo))
        assertEquals(RedeemRules.Expiry.At(moment), RedeemRules.expiry("2026-12-31T20:30:00-03:00", ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `numbers are epoch seconds or milliseconds`() {
        assertEquals(RedeemRules.Expiry.At(1_798_759_800_000L), RedeemRules.expiry("1798759800", saoPaulo))
        assertEquals(RedeemRules.Expiry.At(1_798_759_800_000L), RedeemRules.expiry("1798759800000", saoPaulo))
    }

    @Test
    fun `a text that cannot be read blocks the code instead of letting it live forever`() {
        for (text in listOf("31/12/2026", "amanha", "2026-13-40", "2026-12-31 25:00", "2026-12-31T18:30:00+0300")) {
            val expiry = RedeemRules.expiry(text, saoPaulo)
            assertEquals(RedeemRules.Expiry.Invalid(text), expiry, text)
            assertTrue(RedeemRules.isExpired(expiry, 0L), text)
        }
    }

    @Test
    fun `the admin list writes the last moment the code works`() {
        assertEquals("never", RedeemRules.describe(RedeemRules.Expiry.Never))
        assertTrue(RedeemRules.describe(RedeemRules.expiry("2026-12-31", saoPaulo), saoPaulo).startsWith("2026-12-31 23:59"))
        assertTrue(RedeemRules.describe(RedeemRules.expiry("2026-12-31 18:30", saoPaulo), saoPaulo).startsWith("2026-12-31 18:30"))
        assertTrue(RedeemRules.describe(RedeemRules.Expiry.Invalid("x")).contains("invalid"))
    }

    // ---------------------------------------------------------------- code names and messages

    @Test
    fun `the code is found whatever the capital letters, under the name of the config`() {
        val names = listOf("welcome", "VIP", "vip2")
        assertEquals("welcome", RedeemRules.canonicalName(names, "welcome"))
        assertEquals("welcome", RedeemRules.canonicalName(names, "WeLCome"))
        assertEquals("VIP", RedeemRules.canonicalName(names, "vip"))
        assertEquals("vip2", RedeemRules.canonicalName(names, "VIP2"))
        assertNull(RedeemRules.canonicalName(names, "other"))
        // the exact name wins when two names differ only in capitals
        assertEquals("vip", RedeemRules.canonicalName(listOf("VIP", "vip"), "vip"))
    }

    @Test
    fun `messages take the new and the old placeholders`() {
        assertEquals("a X b", RedeemRules.fill("a {code} b", "X"))
        assertEquals("a X b", RedeemRules.fill("a {codigo} b", "X"))
        assertEquals("X by Steve needs vip.codes", RedeemRules.fill("{code} by {player} needs {permission}", "X", "Steve", "vip.codes"))
        assertEquals("", RedeemRules.fill(null, "X"))
    }

    // ---------------------------------------------------------------- redeem.conf with the old and the new keys

    private fun parse(json: String): RedeemConfig {
        MinecraftBootstrap.ensure()
        return SbGson.pretty.fromJson(json, RedeemConfig::class.java)
    }

    @Test
    fun `a redeem conf written with the old Portuguese keys still works`() {
        val config = parse(
            """{ "aliases": ["resgatar"],
                 "mensagens": { "codigo_invalido": "x {codigo}", "ja_resgatado": "j", "limite_atingido": "l", "sem_permissao_codigo": "sp", "sucesso": "ok" },
                 "codigos": { "vip": { "max_usos": 3, "permissao": "vip.codes", "comandos": ["say oi"],
                                       "itens": [ { "item": "minecraft:diamond", "amount": 2 } ],
                                       "exibicao": { "title": "t", "icon": "minecraft:cookie" },
                                       "entradas": [ { "title": "e", "commands": ["say e"] } ] } } }"""
        )
        assertEquals(listOf("resgatar"), config.aliases)
        assertEquals("x {codigo}", config.messages.invalidCode)
        assertEquals("sp", config.messages.noPermission)
        assertEquals("ok", config.messages.success)
        // a key that is missing keeps the default (the "expired" text did not exist in the old files)
        assertTrue(config.messages.expired.contains("expired"))
        val vip = config.codes["vip"]!!
        assertEquals(3, vip.maxUses)
        assertEquals("vip.codes", vip.permission)
        assertEquals(listOf("say oi"), vip.commands)
        assertEquals(1, vip.items.size)
        assertEquals(2, vip.items[0].amount)
        assertEquals("t", vip.display!!.title)
        assertEquals(1, vip.entries.size)
        assertNull(vip.expiresAt)
    }

    @Test
    fun `a redeem conf written with the English keys works the same, with the new expires_at`() {
        val config = parse(
            """{ "messages": { "no_permission": "denied {code}", "usage_limit_reached": "full" },
                 "codes": { "vip": { "max_uses": 5, "permission": "group.vip", "expires_at": "2026-12-31",
                                     "commands": ["say hi"], "items": [ { "item": "minecraft:emerald" } ],
                                     "display": { "title": "d" }, "entries": [ { "commands": ["say e"] } ] } } }"""
        )
        assertEquals("denied {code}", config.messages.noPermission)
        assertEquals("full", config.messages.usageLimitReached)
        val vip = config.codes["vip"]!!
        assertEquals(5, vip.maxUses)
        assertEquals("group.vip", vip.permission)
        assertEquals("2026-12-31", vip.expiresAt)
        assertEquals(listOf("say hi"), vip.commands)
        assertEquals("minecraft:emerald", vip.items[0].item)
        assertEquals("d", vip.display!!.title)
        assertEquals(1, vip.entries.size)
    }

    @Test
    fun `a new config file is written with the English keys`() {
        val config = RedeemConfig()
        config.codes["welcome"] = RedeemCode().also { it.commands = listOf("say hi") }
        MinecraftBootstrap.ensure()
        val text = SbGson.pretty.toJson(config)
        for (key in listOf("\"messages\"", "\"codes\"", "\"max_uses\"", "\"invalid_code\"", "\"no_permission\"", "\"commands\"")) assertTrue(text.contains(key), key)
        for (old in listOf("mensagens", "codigos", "max_usos", "permissao", "comandos")) assertFalse(text.contains(old), old)
    }
}
