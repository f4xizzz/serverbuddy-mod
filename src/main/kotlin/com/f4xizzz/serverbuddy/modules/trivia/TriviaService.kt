package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * A rodada de trivia: sorteia a pool e a pergunta, anuncia, dá as dicas, confere as respostas do chat e entrega o prêmio.
 * Tudo roda na thread do servidor (o chat só avisa quem acertou primeiro, por uma fila).
 */
object TriviaService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Trivia")

    /** Uma pool pronta para uso: só com as perguntas que estão bem escritas. */
    class ActivePool(val id: String, val pool: TriviaPool, val questions: List<com.google.gson.JsonObject>) {
        val displayName: String get() = pool.name?.takeIf { it.isNotBlank() } ?: id
    }

    /** Uma pergunta no ar. */
    class Round(val pool: ActivePool, val question: Question, val startedAt: Long, val endsAt: Long, val hintSeed: Long) {
        val claimed = AtomicBoolean(false)
        var hintsShown = 0
        val windowMs: Long get() = (endsAt - startedAt).coerceAtLeast(1)
    }

    private class Win(val round: Round, val player: ServerPlayer, val at: Long)

    @Volatile
    var config: TriviaConfig = TriviaConfig()
        private set

    @Volatile
    var pools: Map<String, ActivePool> = emptyMap()
        private set

    /** Só fica true depois que a tabela do banco existe. */
    @Volatile
    var ready: Boolean = false

    @Volatile
    var round: Round? = null
        private set

    @Volatile
    private var nextAt = 0L

    private val recent = ArrayDeque<String>()
    private val wins = ConcurrentLinkedQueue<Win>()
    private var bossbar: ServerBossEvent? = null
    private var tickCount = 0

    // ---------------------------------------------------------------- carregar

    fun load() {
        config = ConfigFiles.load("Trivia", "config.json", TriviaConfig::class.java) { TriviaConfig.example() }
        RewardService.check(config.fastBonus.rewards, "Trivia/config.json (fast_bonus)")
        config.streakBonus.forEach { (key, rewards) -> RewardService.check(rewards, "Trivia/config.json (streak_bonus $key)") }
        pools = loadPools()
        if (round == null) scheduleNext()
        LOGGER.info("Trivia: {} pool(s) with questions ({}).", pools.size, pools.values.joinToString { "${it.id}=${it.questions.size}" })
    }

    private fun loadPools(): Map<String, ActivePool> {
        val dir = File(ConfigFiles.dir("Trivia"), "pools")
        if (!dir.exists()) {
            // primeira vez: cria os exemplos (a pool de Pokémon só se o Cobblemon está instalado)
            ConfigFiles.load("Trivia/pools", "geral.json", TriviaPool::class.java, track = false) { TriviaDefaults.general() }
            if (Integrations.cobblemon()) ConfigFiles.load("Trivia/pools", "pokemon.json", TriviaPool::class.java, track = false) { TriviaDefaults.pokemon() }
        }
        val result = LinkedHashMap<String, ActivePool>()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()
        for (file in files) {
            val id = file.nameWithoutExtension
            // pools são conteúdo do usuário (cada uma só traz o que quer): ficam de fora do /serverbuddy config check
            val pool = ConfigFiles.load("Trivia/pools", file.name, TriviaPool::class.java, track = false) { TriviaPool() }
            if (!pool.enabled) continue
            val valid = pool.questions.filter { Questions.isValid(it) }
            val skipped = pool.questions.size - valid.size
            if (skipped > 0) LOGGER.warn("Pool '{}': {} question(s) skipped (malformed or from an unknown generator; Cobblemon generators require Cobblemon).", id, skipped)
            RewardService.check(pool.rewards + pool.extraRewards, "Trivia/pools/${file.name}")
            if (valid.isEmpty()) {
                LOGGER.warn("Pool '{}' has no valid questions: it will not be used.", id)
                continue
            }
            result[id] = ActivePool(id, pool, valid)
        }
        return result
    }

    // ---------------------------------------------------------------- relógio

    private fun scheduleNext() {
        val interval = config.interval
        val min = interval.min.coerceAtLeast(5)
        val max = interval.max.coerceAtLeast(min)
        nextAt = System.currentTimeMillis() + Random.nextInt(min, max + 1) * 1000L
    }

    /** Segundos até a próxima pergunta (0 se há uma no ar). */
    fun secondsUntilNext(): Long = if (round != null) 0 else ((nextAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    fun secondsLeft(): Long = round?.let { ((it.endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0) } ?: 0

    /** Chamado a cada tick do servidor. */
    fun tick() {
        while (true) {
            val win = wins.poll() ?: break
            finish(win)
        }
        if (++tickCount < 20) return
        tickCount = 0
        val now = System.currentTimeMillis()
        val current = round
        if (current == null) {
            if (ready && pools.isNotEmpty() && now >= nextAt) start(null, force = false)
            return
        }
        if (now >= current.endsAt) {
            timeout(current)
            return
        }
        showHintsIfDue(current, now)
        updateBossbar(current, now)
    }

    // ---------------------------------------------------------------- começar

    /** Começa uma pergunta. Devolve o motivo se não deu (null = começou). [force] = ignora o mínimo de jogadores. */
    fun start(poolId: String?, force: Boolean): String? {
        if (round != null) return "A question is already active."
        val server = ServerHolder.server ?: return "The server is not ready yet."
        if (!ready) return "The database is not ready yet."
        val listeners = server.playerList.players
        if (!force && listeners.size < config.minPlayers) {
            scheduleNext()
            return "Not enough players online (minimum ${config.minPlayers})."
        }
        val pool = if (poolId != null) pools[poolId] ?: return "Pool '$poolId' does not exist (or is disabled)." else pickPool()
        if (pool == null) {
            scheduleNext()
            return "No pools with questions."
        }
        val question = generate(pool)
        if (question == null) {
            scheduleNext()
            LOGGER.warn("Could not generate a question from pool '{}'.", pool.id)
            return "Could not generate a question from pool '${pool.id}' (see the log)."
        }
        val seconds = (pool.pool.answerTime ?: config.answerTime).coerceAtLeast(5)
        val now = System.currentTimeMillis()
        val started = Round(pool, question, now, now + seconds * 1000L, question.text.hashCode().toLong() xor now)
        round = started
        remember(question.text)
        val vars = vars(started)
        val lines = pool.pool.messages?.start ?: config.messages.start
        val sounds = soundsFor(pool)
        for (player in server.playerList.players) {
            if (TriviaScores.isMuted(player.uuid)) continue
            send(player, lines, vars)
            sounds.start?.play(player)
        }
        openBossbar(started)
        return null
    }

    /** Os sons desta pool: os dela, e o que ela não define vem do config.json. */
    private fun soundsFor(pool: ActivePool): TriviaSounds {
        val own = pool.pool.sounds ?: return config.sounds
        val all = config.sounds
        return TriviaSounds(own.start ?: all.start, own.hint ?: all.hint, own.win ?: all.win, own.lose ?: all.lose, own.timedOut ?: all.timedOut)
    }

    private fun pickPool(): ActivePool? {
        val all = pools.values.filter { it.pool.weight > 0 }
        if (all.isEmpty()) return null
        var roll = Random.nextInt(all.sumOf { it.pool.weight })
        for (pool in all) {
            roll -= pool.pool.weight
            if (roll < 0) return pool
        }
        return all.last()
    }

    private fun generate(pool: ActivePool): Question? {
        var fallback: Question? = null
        repeat(12) {
            val question = Questions.create(pool.questions.random()) ?: return@repeat
            if (config.avoidRepeating <= 0 || question.text !in recent) return question
            fallback = question
        }
        return fallback
    }

    private fun remember(text: String) {
        if (config.avoidRepeating <= 0) return
        recent.addLast(text)
        while (recent.size > config.avoidRepeating) recent.removeFirst()
    }

    // ---------------------------------------------------------------- durante a pergunta

    private fun showHintsIfDue(current: Round, now: Long) {
        val hints = config.hints
        if (!hints.enabled || current.question.hint == "") return
        val marks = hints.atPercent
        val percent = (now - current.startedAt) * 100 / current.windowMs
        while (current.hintsShown < marks.size && percent >= marks[current.hintsShown]) {
            val level = current.hintsShown + 1
            current.hintsShown = level
            val custom = current.question.hint
            if (custom != null && level > 1) continue
            val text = custom ?: AnswerMatcher.hint(current.question.answers.first(), level, marks.size, current.hintSeed)
            val vars = vars(current) + ("hint" to Texts.escape(text))
            val lines = current.pool.pool.messages?.hint ?: config.messages.hint
            val sounds = soundsFor(current.pool)
            for (player in ServerHolder.server?.playerList?.players.orEmpty()) {
                if (TriviaScores.isMuted(player.uuid)) continue
                send(player, lines, vars)
                sounds.hint?.play(player)
            }
        }
    }

    private fun openBossbar(current: Round) {
        closeBossbar()
        val settings = config.bossbar
        if (!settings.enabled) return
        val color = runCatching { BossEvent.BossBarColor.valueOf(settings.color.uppercase()) }.getOrDefault(BossEvent.BossBarColor.PURPLE)
        val bar = ServerBossEvent(Texts.render(settings.text, vars(current)), color, BossEvent.BossBarOverlay.PROGRESS)
        bar.progress = 1f
        for (player in ServerHolder.server?.playerList?.players.orEmpty()) if (!TriviaScores.isMuted(player.uuid)) bar.addPlayer(player)
        bossbar = bar
    }

    private fun updateBossbar(current: Round, now: Long) {
        val bar = bossbar ?: return
        bar.progress = ((current.endsAt - now).toFloat() / current.windowMs).coerceIn(0f, 1f)
        bar.name = Texts.render(config.bossbar.text, vars(current))
    }

    private fun closeBossbar() {
        bossbar?.removeAllPlayers()
        bossbar = null
    }

    // ---------------------------------------------------------------- respostas

    /** Chamado para cada mensagem do chat. Devolve false para a mensagem NÃO aparecer no chat. */
    /** O que o chat deve fazer com uma mensagem depois de o trivia olhar para ela. */
    enum class ChatResult {
        /** Não era a resposta certa de uma rodada aberta: segue como chat comum. */
        IGNORED,

        /** Era a resposta certa e vence a rodada; a mensagem aparece no chat. */
        ANSWER,

        /** Era a resposta certa e vence a rodada; a mensagem NÃO aparece no chat (`hide_correct_answer`). */
        HIDDEN_ANSWER
    }

    /** Chamado pela etapa ANSWER do chat ([com.f4xizzz.serverbuddy.core.ChatPipeline]) para cada mensagem pública. */
    fun onChat(player: ServerPlayer, text: String): ChatResult {
        val current = round ?: return ChatResult.IGNORED
        if (current.claimed.get() || TriviaScores.isMuted(player.uuid)) return ChatResult.IGNORED
        if (!AnswerMatcher.matches(text, current.question.answers, config.matching.typoTolerance)) return ChatResult.IGNORED
        // o primeiro a chegar fica com a rodada; quem chegar junto só é ignorado
        if (!current.claimed.compareAndSet(false, true)) return ChatResult.IGNORED
        wins.add(Win(current, player, System.currentTimeMillis()))
        return if (config.hideCorrectAnswer) ChatResult.HIDDEN_ANSWER else ChatResult.ANSWER
    }

    private fun finish(win: Win) {
        val current = round
        if (current == null || current !== win.round) return
        round = null
        closeBossbar()
        val server = ServerHolder.server ?: return
        val winner = win.player
        val timeMs = (win.at - current.startedAt).coerceAtLeast(0)
        val stats = TriviaScores.recordWin(winner.uuid, winner.gameProfile.name, timeMs)
        val vars = vars(current) + mapOf(
            "winner" to Texts.escape(winner.gameProfile.name),
            "time_taken" to AnswerMatcher.formatMillis(timeMs),
            "streak" to stats.streak
        )

        // prêmios primeiro (a mensagem de vitória mostra o que saiu)
        val source = "trivia:${current.pool.id}"
        val given = ArrayList<Reward>()
        RewardService.pick(current.pool.pool.rewards)?.let { given += RewardService.give(winner, listOf(it), vars, source) }
        given += RewardService.give(winner, current.pool.pool.extraRewards, vars, source)
        val fast = config.fastBonus
        if (fast.enabled && timeMs * 100 <= current.windowMs * fast.withinPercent) given += RewardService.give(winner, fast.rewards, vars, source)
        config.streakBonus[stats.streak.toString()]?.let { given += RewardService.give(winner, it, vars, source) }

        val reward = RewardService.describeAll(given).takeIf { it.siblings.isNotEmpty() || it.string.isNotEmpty() } ?: Component.literal("—")
        val messages = current.pool.pool.messages
        val sounds = soundsFor(current.pool)
        for (player in server.playerList.players) {
            if (player === winner) {
                send(player, messages?.win ?: config.messages.win, vars, mapOf("reward" to reward))
                sounds.win?.play(player)
            } else if (!TriviaScores.isMuted(player.uuid)) {
                send(player, messages?.lose ?: config.messages.lose, vars)
                sounds.lose?.play(player)
            }
        }
        scheduleNext()
    }

    private fun timeout(current: Round) {
        if (!current.claimed.compareAndSet(false, true)) return // alguém acertou no último instante: a vitória é processada no próximo tick
        round = null
        closeBossbar()
        val server = ServerHolder.server
        val vars = vars(current)
        val lines = current.pool.pool.messages?.timedOut ?: config.messages.timedOut
        val sounds = soundsFor(current.pool)
        for (player in server?.playerList?.players.orEmpty()) {
            if (TriviaScores.isMuted(player.uuid)) continue
            send(player, lines, vars)
            sounds.timedOut?.play(player)
        }
        scheduleNext()
    }

    /** Encerra a pergunta no ar sem vencedor nem aviso de fim (usado por /trivia stop). false = não tinha nenhuma. */
    fun stop(): Boolean {
        val current = round ?: return false
        if (!current.claimed.compareAndSet(false, true)) return false
        round = null
        closeBossbar()
        scheduleNext()
        return true
    }

    // ---------------------------------------------------------------- jogadores

    fun onJoin(player: ServerPlayer) {
        TriviaScores.onJoin(player.uuid, player.gameProfile.name)
        val current = round ?: return
        if (TriviaScores.isMuted(player.uuid)) return
        bossbar?.addPlayer(player)
        // quem entra no meio da pergunta vê a pergunta e o tempo que falta
        send(player, current.pool.pool.messages?.start ?: config.messages.start, vars(current))
    }

    fun onLeave(player: ServerPlayer) {
        bossbar?.removePlayer(player)
        TriviaScores.onLeave(player.uuid)
    }

    /** Liga ou desliga a trivia só para [player]. Devolve true se ficou silenciada. */
    fun toggleMuted(player: ServerPlayer): Boolean {
        val muted = !TriviaScores.isMuted(player.uuid)
        TriviaScores.setMuted(player.uuid, player.gameProfile.name, muted)
        if (muted) bossbar?.removePlayer(player) else if (round != null) bossbar?.addPlayer(player)
        return muted
    }

    // ---------------------------------------------------------------- textos

    private fun vars(current: Round): Map<String, Any?> = mapOf(
        "question" to Texts.escape(current.question.text),
        "answer" to Texts.escape(current.question.answers.take(3).joinToString(" / ")),
        "pool" to Texts.escape(current.pool.displayName),
        "time" to AnswerMatcher.formatSeconds(maxOf(1, (current.endsAt - System.currentTimeMillis() + 500) / 1000)),
        "seconds" to maxOf(0, (current.endsAt - System.currentTimeMillis() + 500) / 1000),
        "hint" to ""
    )

    private fun send(player: ServerPlayer, lines: List<String>, vars: Map<String, Any?>, parts: Map<String, Component> = emptyMap()) {
        for (line in lines) player.sendSystemMessage(Texts.render(line, vars, parts))
    }
}
