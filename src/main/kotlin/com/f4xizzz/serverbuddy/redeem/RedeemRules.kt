package com.f4xizzz.serverbuddy.redeem

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The pure rules of the redeem codes (no Minecraft in here, so they are unit tested). */
object RedeemRules {
    /** What the `expires_at` text of a code means. */
    sealed interface Expiry {
        /** Empty: the code never expires. */
        data object Never : Expiry

        /** The code can be redeemed until [millis] (epoch milliseconds, inclusive: the last moment it works). */
        data class At(val millis: Long) : Expiry

        /** The text could not be read. The code is treated as expired (and reported), never as eternal. */
        data class Invalid(val text: String) : Expiry
    }

    private val zoneFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")

    /**
     * Reads an `expires_at`: "2026-12-31" (until the end of that day), "2026-12-31 18:30" or "2026-12-31T18:30:00" (in [zone], the server's
     * time zone), "2026-12-31T18:30:00Z" or with an offset ("-03:00"), or a number of epoch seconds (up to 11 digits) or milliseconds.
     */
    @JvmStatic
    @JvmOverloads
    fun expiry(text: String?, zone: ZoneId = ZoneId.systemDefault()): Expiry {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return Expiry.Never
        if (raw.all { it.isDigit() }) {
            val number = raw.toLongOrNull() ?: return Expiry.Invalid(raw)
            return Expiry.At(if (raw.length <= 11) number * 1000 else number)
        }
        try {
            return Expiry.At(OffsetDateTime.parse(raw).toInstant().toEpochMilli())
        } catch (_: DateTimeParseException) {
            // not a date with a zone: try the local forms below
        }
        try {
            // "2026-12-31 18:30" is accepted like "2026-12-31T18:30"
            val local = LocalDateTime.parse(raw.replaceFirst(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return Expiry.At(local.atZone(zone).toInstant().toEpochMilli())
        } catch (_: DateTimeParseException) {
            // not a date with a time: try a plain date
        }
        try {
            // a date alone means "until the end of that day"
            val end = LocalDate.parse(raw).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            return Expiry.At(end)
        } catch (_: DateTimeParseException) {
            return Expiry.Invalid(raw)
        }
    }

    @JvmStatic
    fun isExpired(expiry: Expiry, now: Long): Boolean = when (expiry) {
        Expiry.Never -> false
        is Expiry.At -> now > expiry.millis
        is Expiry.Invalid -> true
    }

    /** "2026-12-31 23:59 BRT" style text (the last moment the code works) for the admin list; "never" when there is no expiration. */
    @JvmStatic
    @JvmOverloads
    fun describe(expiry: Expiry, zone: ZoneId = ZoneId.systemDefault()): String = when (expiry) {
        Expiry.Never -> "never"
        is Expiry.At -> zoneFormat.format(java.time.Instant.ofEpochMilli(expiry.millis).atZone(zone))
        is Expiry.Invalid -> "invalid (${expiry.text})"
    }

    /**
     * The name a code is stored under for what the player typed: the exact name when it exists, otherwise the first one that only differs
     * in capital letters. null when there is no such code. Claims are always kept under the name from the config, so "BoasVindas" and
     * "boasvindas" are the same code and cannot be redeemed twice.
     */
    @JvmStatic
    fun canonicalName(names: Collection<String>, typed: String): String? =
        if (typed in names) typed else names.firstOrNull { it.equals(typed, ignoreCase = true) }

    /** Replaces {code} (and the old {codigo}), {player} and {permission} in a message of redeem.conf. */
    @JvmStatic
    @JvmOverloads
    fun fill(template: String?, code: String, player: String = "", permission: String = ""): String =
        (template ?: "").replace("{code}", code).replace("{codigo}", code).replace("{player}", player).replace("{permission}", permission)
}
