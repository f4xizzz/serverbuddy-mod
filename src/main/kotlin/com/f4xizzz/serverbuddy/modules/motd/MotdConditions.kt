package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.TimeConditions
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/** O que as condições de um perfil de MOTD olham no momento da consulta. */
class MotdContext(
    val online: Int,
    val max: Int,
    val whitelist: Boolean,
    val maintenance: Boolean,
    val now: LocalDateTime
)

/**
 * Condições dos perfis de MOTD (`"when"` e `"when_any"`): `maintenance`, `whitelist`, `full`, `players>=10`,
 * `time:18:00-23:30`, `day:sat,sun`, `date:2026-12-24..2026-12-26`. Qualquer uma aceita `!` na frente para negar.
 * Outros módulos podem acrescentar as suas com [register] (o `event` é registrado pelo módulo de MOTD).
 */
object MotdConditions {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Motd")
    private val custom = ConcurrentHashMap<String, (String, MotdContext) -> Boolean>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    private val PLAYERS = Regex("""^players\s*(>=|<=|==|>|<)\s*(\d+)$""")

    /** Acrescenta uma condição `nome` ou `nome:argumento`. */
    @JvmStatic
    fun register(name: String, check: (String, MotdContext) -> Boolean) {
        custom[name.lowercase()] = check
    }

    /** Uma condição isolada (com `!` opcional na frente). */
    @JvmStatic
    fun matches(raw: String, ctx: MotdContext): Boolean {
        var text = raw.trim()
        var negate = false
        while (text.startsWith("!")) {
            negate = !negate
            text = text.substring(1).trim()
        }
        return evaluate(text, ctx) != negate
    }

    /** Todas as condições de [all] casam e, se [any] não está vazia, pelo menos uma dela também. */
    @JvmStatic
    fun profileMatches(all: List<String>, any: List<String>, ctx: MotdContext): Boolean =
        all.all { matches(it, ctx) } && (any.isEmpty() || any.any { matches(it, ctx) })

    private fun evaluate(text: String, ctx: MotdContext): Boolean {
        val lower = text.lowercase()
        val name = lower.substringBefore(':')
        val arg = text.substringAfter(':', "").trim()
        PLAYERS.matchEntire(lower)?.let { match ->
            val limit = match.groupValues[2].toInt()
            return when (match.groupValues[1]) {
                ">=" -> ctx.online >= limit
                "<=" -> ctx.online <= limit
                "==" -> ctx.online == limit
                ">" -> ctx.online > limit
                else -> ctx.online < limit
            }
        }
        return when (name) {
            "maintenance" -> ctx.maintenance
            "whitelist" -> ctx.whitelist
            "full" -> ctx.max > 0 && ctx.online >= ctx.max
            "time", "day", "date" -> TimeConditions.evaluate(name, arg, ctx.now) ?: unknown(text)
            else -> custom[name]?.invoke(arg, ctx) ?: unknown(text)
        }
    }

    private fun unknown(text: String): Boolean {
        if (warned.add(text)) LOGGER.warn("Unknown or badly written MOTD condition: '{}' (counts as false).", text)
        return false
    }
}
