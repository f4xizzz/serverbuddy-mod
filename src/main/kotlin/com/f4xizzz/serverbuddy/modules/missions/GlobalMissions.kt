package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Texts
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

enum class StartResult { OK, DISABLED, ALREADY_ACTIVE, UNKNOWN, NO_MISSIONS }

enum class JoinResult { OK, NONE_ACTIVE, ALREADY, NOT_ELIGIBLE, NOT_NEEDED }

/** Quem está na frente da corrida: o nome e quanto já fez ("3/10"). */
class TopInfo(val name: String, val progress: String)

/**
 * Missões globais: uma missão das categorias com `global: true` por vez, valendo para o servidor inteiro, numa corrida. De tempos em
 * tempos o servidor sorteia uma, avisa todo mundo e abre a corrida; o primeiro a completar leva o prêmio e ela acaba para os outros (ou
 * acaba quando o tempo passa, sem vencedor). O estado (qual missão, até quando, quem entrou) fica no banco, para sobreviver a um reinício.
 * Tudo aqui roda na thread do servidor.
 */
object GlobalMissions {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Missions")

    private class Active(val key: String, val startedAt: Long, val endsAt: Long, val participants: MutableSet<UUID>, val accepted: MutableSet<UUID>)

    private enum class Outcome { WON, EXPIRED, STOPPED, SILENT }

    @Volatile
    private var active: Active? = null

    @Volatile
    private var nextAt: Long = 0L

    private var topCache: TopInfo? = null
    private var topCacheAt = 0L

    private val settings get() = MissionsService.config.global
    private val messages get() = settings.messages

    private fun everyone() = settings.target.equals("all", ignoreCase = true)
    private fun ui() = settings.assignment.equals("ui", ignoreCase = true)

    private fun intervalMs(): Long = (Durations.parseMillis(settings.interval) ?: 3_600_000L).coerceAtLeast(1000L)

    /** Quanto a missão dura (0 = sem limite). */
    private fun limitMs(): Long {
        val text = settings.timeLimit.trim()
        if (text.isEmpty() || text == "0") return 0L
        return (Durations.parseMillis(text) ?: 0L).coerceAtLeast(0L)
    }

    private fun plain(text: String): String = Texts.parse(text).string

    // ---------------------------------------------------------------- estado guardado

    /** Lê do banco a missão que estava valendo antes do reinício. Chame depois de criar a tabela. */
    fun loadState() {
        val saved = MissionsRepository.loadGlobal()
        nextAt = saved["next_at"]?.toLongOrNull() ?: 0L
        val key = saved["mission"]
        active = if (key.isNullOrBlank()) null else Active(
            key, saved["started_at"]?.toLongOrNull() ?: 0L, saved["ends_at"]?.toLongOrNull() ?: 0L,
            ids(saved["participants"]), ids(saved["accepted"])
        )
    }

    private fun ids(text: String?): MutableSet<UUID> =
        (text ?: "").split(',').mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toMutableSet()

    private fun persist() {
        val map = LinkedHashMap<String, String>()
        map["next_at"] = nextAt.toString()
        active?.let { a ->
            map["mission"] = a.key
            map["started_at"] = a.startedAt.toString()
            map["ends_at"] = a.endsAt.toString()
            map["participants"] = a.participants.joinToString(",")
            map["accepted"] = a.accepted.joinToString(",")
        }
        MissionsRepository.saveGlobal(map)
    }

    // ---------------------------------------------------------------- perguntas

    /** A chave da missão global em jogo ("categoria.missão"), ou null. */
    fun activeKey(): String? = if (settings.enabled) active?.key else null

    /** Identifica esta rodada: as linhas de uma rodada antiga não valem mais. */
    fun periodKey(): String = "g${active?.startedAt ?: 0}"

    /** Os ids da missão global em jogo que são da [category] (no máximo um). */
    fun activeIds(category: LoadedCategory): Set<String> {
        val key = activeKey() ?: return emptySet()
        return if (key.substringBefore('.') == category.id) setOf(key.substringAfter('.')) else emptySet()
    }

    /** O jogador pode ver (e, no modo "ui", entrar na) a missão global em jogo? */
    fun canSee(uuid: UUID): Boolean {
        val a = active ?: return false
        if (!settings.enabled) return false
        return everyone() || uuid in a.participants
    }

    /** A missão global conta para o jogador (a linha dele existe e o progresso anda)? */
    fun participates(uuid: UUID): Boolean {
        val a = active ?: return false
        return canSee(uuid) && (!ui() || uuid in a.accepted)
    }

    /** Falta quanto para a missão em jogo acabar (ms); -1 se não há limite; 0 se não há missão. */
    fun timeLeftMs(): Long {
        val a = active ?: return 0L
        if (a.endsAt <= 0) return -1L
        return (a.endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun timeLeftText(): String = timeLeftMs().let { if (it < 0) "no limit" else Durations.format(it) }

    /** Depois de recarregar a config: um intervalo menor vale já para a próxima missão que está esperando (um maior só vale depois dela). */
    fun onConfigLoaded() {
        if (!MissionsService.ready || !settings.enabled || active != null || nextAt <= 0) return
        val soonest = System.currentTimeMillis() + intervalMs()
        if (nextAt > soonest) {
            nextAt = soonest
            persist()
        }
    }

    // ---------------------------------------------------------------- o relógio

    /** A cada segundo: encerra o que passou do tempo e começa a próxima quando chega a hora. */
    fun tick(server: MinecraftServer) {
        if (!settings.enabled) return
        val now = System.currentTimeMillis()
        val a = active
        if (a != null) {
            if (MissionsService.find(a.key) == null) {
                LOGGER.warn("Global mission '{}' no longer exists in the configuration: stopped.", a.key)
                end(server, Outcome.SILENT, null)
            } else if (a.endsAt > 0 && now >= a.endsAt) {
                end(server, Outcome.EXPIRED, null)
            }
            return
        }
        if (nextAt <= 0) {
            nextAt = now + intervalMs()
            persist()
        } else if (now >= nextAt) {
            start(server, null)
        }
    }

    /** As missões que podem ser sorteadas: as das categorias globais carregadas. */
    private fun pool(): List<LoadedMission> = MissionsService.categories.values.filter { it.def.global }.flatMap { it.missions }

    /** Começa uma missão global agora ([key] = uma específica; null = sorteia). */
    fun start(server: MinecraftServer, key: String?): StartResult {
        if (!settings.enabled) return StartResult.DISABLED
        if (active != null) return StartResult.ALREADY_ACTIVE
        val missions = pool()
        // aceita "categoria.missao" ou só o id da missão (se um só id casar)
        val chosen = if (key == null) missions.randomOrNull()
        else missions.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: missions.filter { it.id.equals(key, ignoreCase = true) }.singleOrNull()
        val mission = chosen ?: return if (key != null) StartResult.UNKNOWN else StartResult.NO_MISSIONS
        val now = System.currentTimeMillis()
        val limit = limitMs()
        // sobras de uma rodada anterior desta missão (um desligamento no meio) não podem valer
        MissionsService.purgeMission(mission.key, null)
        active = Active(
            mission.key, now, if (limit > 0) now + limit else 0L,
            if (everyone()) mutableSetOf() else server.playerList.players.map { it.uuid }.toMutableSet(), mutableSetOf()
        )
        nextAt = 0L
        persist()
        val vars = mapOf("mission" to Texts.escape(plain(mission.displayName)), "time" to timeLeftText())
        val text = if (ui()) messages.startedUi else messages.started
        for (player in server.playerList.players) {
            Texts.send(player, text, vars)
            MissionsService.sendTitle(player, messages.startedTitle, messages.startedSubtitle, vars)
        }
        LOGGER.info("Global mission started: {} (limit: {}).", mission.key, if (limit > 0) Durations.format(limit) else "no limit")
        return StartResult.OK
    }

    /** A equipe encerra a missão em jogo sem vencedor. false se não havia. */
    fun stop(server: MinecraftServer): Boolean {
        if (active == null) return false
        end(server, Outcome.STOPPED, null)
        return true
    }

    private fun end(server: MinecraftServer, outcome: Outcome, winner: ServerPlayer?) {
        val a = active ?: return
        val mission = MissionsService.find(a.key)
        val name = mission?.let { plain(it.displayName) } ?: a.key
        active = null
        nextAt = System.currentTimeMillis() + intervalMs()
        topCache = null
        MissionsService.purgeMission(a.key, winner?.uuid)
        persist()
        val vars = mapOf("mission" to Texts.escape(name))
        when (outcome) {
            Outcome.EXPIRED -> server.playerList.broadcastSystemMessage(Texts.render(messages.expired, vars), false)
            Outcome.STOPPED -> server.playerList.broadcastSystemMessage(Texts.render(messages.stopped, vars), false)
            Outcome.WON, Outcome.SILENT -> {}
        }
    }

    /** Um jogador completou os objetivos de uma missão global: se a corrida ainda vale, ele venceu. */
    fun onComplete(player: ServerPlayer, mission: LoadedMission, row: Row) {
        val a = active
        if (a == null || a.key != mission.key || !settings.enabled) {
            // a corrida já acabou: esta linha não vale mais
            MissionsService.dropRow(player.uuid, mission.key)
            return
        }
        row.status = Status.COMPLETED
        row.finishedAt = System.currentTimeMillis()
        row.dirty = true
        val server = player.server
        end(server, Outcome.WON, player)
        // o prêmio da missão vai só para o vencedor
        MissionsService.claim(player, mission.key)
        val vars = mapOf("mission" to Texts.escape(plain(mission.displayName)), "player" to Texts.escape(player.gameProfile.name))
        server.playerList.broadcastSystemMessage(Texts.render(messages.won, vars), false)
        MissionsService.sendTitle(player, messages.wonTitle, messages.wonSubtitle, vars)
        LOGGER.info("Global mission {} won by {}.", mission.key, player.gameProfile.name)
    }

    // ---------------------------------------------------------------- jogador

    /** O jogador entra na missão global em jogo (modo "ui"). */
    fun accept(player: ServerPlayer): JoinResult {
        val a = active
        if (a == null || !settings.enabled) return JoinResult.NONE_ACTIVE
        if (!ui()) return JoinResult.NOT_NEEDED
        if (!canSee(player.uuid)) return JoinResult.NOT_ELIGIBLE
        if (player.uuid in a.accepted) return JoinResult.ALREADY
        a.accepted.add(player.uuid)
        persist()
        MissionsService.refresh(player)
        val mission = MissionsService.find(a.key)
        Texts.send(player, messages.joined, mapOf("mission" to Texts.escape(plain(mission?.displayName ?: a.key))))
        return JoinResult.OK
    }

    fun onJoin(player: ServerPlayer) {
        val a = active ?: return
        if (!settings.enabled || !canSee(player.uuid) || messages.reminder.isBlank()) return
        val mission = MissionsService.find(a.key) ?: return
        Texts.send(player, messages.reminder, mapOf("mission" to Texts.escape(plain(mission.displayName)), "time" to timeLeftText()))
    }

    // ---------------------------------------------------------------- placar

    /** Quem está na frente entre os jogadores online que participam (mais perto de terminar). Guarda por 1 segundo. */
    fun top(server: MinecraftServer): TopInfo? {
        val a = active ?: return null
        val now = System.currentTimeMillis()
        if (now - topCacheAt < 1000 && topCache != null) return topCache
        val mission = MissionsService.find(a.key) ?: return null
        var best: TopInfo? = null
        var bestFraction = -1.0
        for (player in server.playerList.players) {
            if (!participates(player.uuid)) continue
            val (done, total) = MissionsService.progressOf(player.uuid, mission) ?: continue
            if (total <= 0) continue
            val fraction = done.toDouble() / total
            if (fraction > bestFraction) {
                bestFraction = fraction
                best = TopInfo(player.gameProfile.name, "$done/$total")
            }
        }
        topCache = best
        topCacheAt = now
        return best
    }

    private fun statusVars(player: ServerPlayer?, mission: LoadedMission, server: MinecraftServer): Map<String, Any?> {
        val top = top(server)
        val mine = player?.let { MissionsService.progressOf(it.uuid, mission) }
        return mapOf(
            "mission" to Texts.escape(plain(mission.displayName)), "time" to timeLeftText(),
            "top" to (top?.let { Texts.escape(it.name) } ?: "—"), "top_progress" to (top?.progress ?: "—"),
            "progress" to (mine?.let { "${it.first}/${it.second}" } ?: "—")
        )
    }

    /** O texto do /missions global: as linhas da missão em jogo, ou o aviso de que não há. */
    fun statusText(player: ServerPlayer?, server: MinecraftServer): List<String> {
        val a = active
        if (a == null || !settings.enabled) {
            val wait = nextAt - System.currentTimeMillis()
            return listOf(Texts.fill(if (settings.enabled && nextAt > 0) messages.none else messages.noneScheduled, mapOf("time" to Durations.format(wait))))
        }
        val mission = MissionsService.find(a.key) ?: return listOf(messages.noneScheduled)
        val vars = statusVars(player, mission, server)
        return messages.status.map { Texts.fill(it, vars) }
    }

    /** Valores para o menu e os placeholders. */
    fun menuVars(player: ServerPlayer, server: MinecraftServer): Map<String, Any?> {
        val a = active ?: return emptyMap()
        val mission = MissionsService.find(a.key) ?: return emptyMap()
        return statusVars(player, mission, server)
    }

    /** Para o placeholder %serverbuddy:missions_global_next%: quanto falta para a próxima começar ("—" se há uma em jogo ou desligado). */
    fun nextText(): String = if (!settings.enabled || active != null || nextAt <= 0) "—" else Durations.format(nextAt - System.currentTimeMillis())

    fun activeName(): String = active?.let { a -> MissionsService.find(a.key)?.let { plain(it.displayName) } ?: a.key } ?: "—"
}
