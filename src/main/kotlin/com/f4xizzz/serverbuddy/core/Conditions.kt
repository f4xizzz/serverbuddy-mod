package com.f4xizzz.serverbuddy.core

import java.time.LocalDateTime

/**
 * Condições escritas em texto, iguais em todos os módulos que olham o jogador (hoje o bloqueio de comandos):
 *
 *  - `tag:vip`                      o jogador tem a tag (do scoreboard, dada com /tag, ou do menu /tags)
 *  - `equipped:vip`                 a tag vip do /tags está equipada agora
 *  - `permission:meuserver.vip`     o jogador tem a permissão (LuckPerms ou a de uma claim do Flan)
 *  - `dimension:minecraft:the_nether`
 *  - `time:18:00-23:30`, `day:sat,sun`, `date:2026-12-24..2026-12-26`  (ver [TimeConditions])
 *
 * Qualquer condição aceita `!` na frente para negar (`!tag:vip`). Uma condição que não existe ou está mal escrita nunca vale
 * (nem negada): fica inerte e [matches] avisa quem chamou.
 */
object Conditions {
    /** O que as condições olham no jogador (separado do Minecraft para dar para testar sozinho). */
    class Subject(
        val hasTag: (String) -> Boolean,
        val hasPermission: (String) -> Boolean,
        /** O id da tag do /tags que está equipada, ou null. */
        val equipped: String? = null,
        val dimension: String = "",
        val now: LocalDateTime = LocalDateTime.now()
    )

    /** Os nomes de condição que existem (para a documentação e os avisos). */
    @JvmStatic
    fun names(): List<String> = listOf("tag", "equipped", "permission", "dimension", "time", "day", "date")

    /** `true`/`false`, ou `null` se a condição não existe ou está mal escrita. */
    @JvmStatic
    fun evaluate(raw: String, subject: Subject): Boolean? {
        var text = raw.trim()
        var negate = false
        while (text.startsWith("!")) {
            negate = !negate
            text = text.substring(1).trim()
        }
        val name = text.substringBefore(':').trim().lowercase()
        val arg = text.substringAfter(':', "").trim()
        val value: Boolean = when {
            name == "tag" -> arg.isNotEmpty() && subject.hasTag(arg)
            name == "equipped" || name == "tag_equipped" -> arg.isNotEmpty() && subject.equipped.equals(arg, ignoreCase = true)
            name == "permission" -> arg.isNotEmpty() && subject.hasPermission(arg)
            name == "dimension" -> arg.isNotEmpty() && subject.dimension.equals(arg, ignoreCase = true)
            TimeConditions.isTimeCondition(name) -> TimeConditions.evaluate(name, arg, subject.now) ?: return null
            else -> return null
        }
        return value != negate
    }

    /** Como [evaluate], mas uma condição inválida conta como falsa e é entregue a [onUnknown] (para avisar uma vez no log). */
    @JvmStatic
    fun matches(raw: String, subject: Subject, onUnknown: (String) -> Unit = {}): Boolean {
        val result = evaluate(raw, subject)
        if (result == null) onUnknown(raw)
        return result ?: false
    }

    /** TODAS as condições de [all] verdadeiras e, se [any] não está vazia, pelo menos UMA de [any]. Sem nenhuma condição, nunca vale. */
    @JvmStatic
    fun test(all: List<String>, any: List<String>, subject: Subject, onUnknown: (String) -> Unit = {}): Boolean {
        if (all.isEmpty() && any.isEmpty()) return false
        return all.all { matches(it, subject, onUnknown) } && (any.isEmpty() || any.any { matches(it, subject, onUnknown) })
    }
}
