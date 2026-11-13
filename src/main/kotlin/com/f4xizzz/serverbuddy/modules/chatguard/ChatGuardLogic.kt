package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.Durations
import java.text.Normalizer

/** Uma mensagem recente de um jogador: o texto já limpo e quando foi tentada. */
class RecentMessage(val normalized: String, val at: Long)

/** O estado de chat de um jogador (o que fica no banco). */
class GuardState(
    /** Avisos de agora (zera no ban e depois de um tempo sem erros). */
    var warnings: Int = 0,
    var lastViolation: Long = 0,
    /** Bans de chat que a pessoa tomou (dentro da memória). */
    var bans: Int = 0,
    /** Avisos acumulados (dentro da memória): contam no tempo do próximo ban. */
    var totalWarnings: Int = 0,
    /** Até quando está banido (0 = não está). */
    var banUntil: Long = 0,
    var lastBan: Long = 0
) {
    fun bannedAt(now: Long): Boolean = banUntil > now

    fun copy() = GuardState(warnings, lastViolation, bans, totalWarnings, banUntil, lastBan)
}

/** O que acontece quando alguém comete uma infração. */
sealed class Outcome {
    /** [count] de [max]: o aviso de agora. */
    class Warned(val count: Int, val max: Int) : Outcome()

    /** Ban de chat por [millis]; é o [bans]º da pessoa e ela tem [totalWarnings] avisos acumulados. */
    class Banned(val millis: Long, val bans: Int, val totalWarnings: Int) : Outcome()
}

/** As regras do gerenciador de chat, sem nada do Minecraft (dá para testar sozinhas). */
object ChatGuardLogic {
    private val MARKS = Regex("\\p{M}+")

    /**
     * O texto limpo para comparar: minúsculas, sem acentos, só letras e números, e a mesma letra repetida mais de 2 vezes vira 2
     * ("ooooi!!!" = "ooi"). Espaços e pontuação não contam: "Oi, pessoal!!!" = "oi pessoal".
     */
    @JvmStatic
    fun normalize(text: String): String {
        val plain = MARKS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
        val out = StringBuilder()
        var last: Char? = null
        var run = 0
        for (c in plain) {
            if (!c.isLetterOrDigit()) continue
            if (c == last) {
                run++
                if (run > 2) continue
            } else {
                last = c
                run = 1
            }
            out.append(c)
        }
        return out.toString()
    }

    private fun bigrams(text: String): Map<String, Int> {
        val map = HashMap<String, Int>()
        for (i in 0 until text.length - 1) map.merge(text.substring(i, i + 2), 1, Int::plus)
        return map
    }

    /** O quanto dois textos já limpos se parecem, de 0.0 a 1.0 (coeficiente de Dice sobre pares de letras). */
    @JvmStatic
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val ba = bigrams(a)
        val bb = bigrams(b)
        var shared = 0
        for ((key, count) in ba) shared += minOf(count, bb[key] ?: 0)
        return 2.0 * shared / ((a.length - 1) + (b.length - 1))
    }

    /**
     * A mensagem [normalized] repete o que o jogador mandou há pouco? Conta quantas das últimas mensagens dentro da janela são iguais
     * ou parecidas e compara com `max_repeats`. Mensagens curtas (menos letras que `min_length`) nunca são repetidas.
     */
    @JvmStatic
    fun isDuplicate(history: List<RecentMessage>, normalized: String, now: Long, settings: DuplicateSettings): Boolean {
        if (!settings.enabled || normalized.length < settings.minLength) return false
        val windowMs = settings.windowSeconds.coerceAtLeast(1) * 1000L
        val threshold = settings.similarity.coerceIn(0.5, 1.0)
        val recent = history.filter { now - it.at <= windowMs }.takeLast(settings.historySize.coerceAtLeast(1))
        val same = recent.count { similarity(it.normalized, normalized) >= threshold }
        return same >= settings.maxRepeats.coerceAtLeast(1)
    }

    /** Mensagens demais num intervalo curto? [times] são os instantes das mensagens recentes (inclui as barradas). */
    @JvmStatic
    fun isFlooding(times: List<Long>, now: Long, settings: FloodSettings): Boolean {
        if (!settings.enabled) return false
        val windowMs = settings.seconds.coerceAtLeast(1) * 1000L
        return times.count { now - it <= windowMs } >= settings.messages.coerceAtLeast(1)
    }

    /** Aplica o "esquecimento": avisos somem depois de um tempo sem erros, e o histórico de bans depois de muitos dias. */
    @JvmStatic
    fun decay(state: GuardState, now: Long, warnings: WarningSettings, ban: ChatBanSettings) {
        val resetMs = Durations.parseMillis(warnings.resetAfter) ?: 0L
        if (resetMs > 0 && state.warnings > 0 && now - state.lastViolation >= resetMs) state.warnings = 0
        val memoryMs = ban.memoryDays.coerceAtLeast(0) * 86_400_000L
        if (memoryMs > 0 && (state.bans > 0 || state.totalWarnings > 0) && now - maxOf(state.lastBan, state.lastViolation) >= memoryMs) {
            state.bans = 0
            state.totalWarnings = 0
        }
    }

    /**
     * O tempo do próximo ban: o valor da escada para o nº do ban ([previousBans] = quantos já tomou; passando do fim da lista, repete
     * o último) mais `extra_per_warning` por aviso acumulado, no máximo `max`.
     */
    @JvmStatic
    fun banDuration(settings: ChatBanSettings, previousBans: Int, totalWarnings: Int): Long {
        val ladder = settings.durations.mapNotNull { Durations.parseMillis(it) }.filter { it > 0 }.ifEmpty { listOf(300_000L) }
        val base = ladder[previousBans.coerceIn(0, ladder.size - 1)]
        val extra = (Durations.parseMillis(settings.extraPerWarning) ?: 0L) * totalWarnings.coerceAtLeast(0)
        val cap = Durations.parseMillis(settings.max) ?: 0L
        val total = base + extra
        return if (cap > 0) minOf(total, cap) else total
    }

    /**
     * Registra uma infração em [state] (já com o esquecimento aplicado): aviso enquanto houver avisos para dar, e depois ban.
     * Com `max_warnings = 3`: 1º, 2º e 3º erros são avisos 1/3, 2/3 e 3/3; o 4º vira ban.
     */
    @JvmStatic
    fun violation(state: GuardState, now: Long, warnings: WarningSettings, ban: ChatBanSettings): Outcome {
        state.lastViolation = now
        val max = warnings.maxWarnings.coerceAtLeast(0)
        if (state.warnings < max) {
            state.warnings++
            state.totalWarnings++
            return Outcome.Warned(state.warnings, max)
        }
        return applyBan(state, now, banDuration(ban, state.bans, state.totalWarnings))
    }

    /** Põe [state] em ban por [millis] (conta como mais um ban e zera os avisos de agora). */
    @JvmStatic
    fun applyBan(state: GuardState, now: Long, millis: Long): Outcome.Banned {
        state.bans++
        state.warnings = 0
        state.banUntil = now + millis
        state.lastBan = now
        return Outcome.Banned(millis, state.bans, state.totalWarnings)
    }
}
