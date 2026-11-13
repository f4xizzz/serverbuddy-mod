package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Uma missão carregada: o que está escrito na config e os objetivos já lidos. */
class LoadedMission(val categoryId: String, val id: String, val def: MissionDef, val objectives: List<Objective>) {
    val key: String get() = "$categoryId.$id"
    val displayName: String get() = def.name.ifBlank { id }
    val cooldownMs: Long get() = Durations.parseMillis(def.cooldown) ?: 0L
}

class LoadedCategory(val id: String, val def: MissionCategory, val missions: List<LoadedMission>) {
    val displayName: String get() = def.name.ifBlank { id }
}

/** O que o jogador vê de cada missão. OFFER = missão global que ele ainda não aceitou (modo "ui"). */
enum class State { ACTIVE, READY, CLAIMED, COOLDOWN, LOCKED, OFFER }

class ObjectiveView(val objective: Objective, val current: Long) {
    val done: Boolean get() = current >= objective.amount
}

class MissionView(
    val mission: LoadedMission,
    val state: State,
    val objectives: List<ObjectiveView>,
    /** Quanto falta para a missão voltar (COOLDOWN). */
    val cooldownLeftMs: Long = 0,
    /** Por que está bloqueada (LOCKED). */
    val requirement: String = ""
)

enum class ClaimResult { OK, NOT_READY, UNKNOWN }

/**
 * Missions: carrega as categorias, acompanha o progresso de cada jogador (estatísticas do Minecraft a cada segundo e eventos
 * do Cobblemon na hora), entrega os prêmios e guarda tudo no banco.
 */
object MissionsService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Missions")

    @Volatile
    var config: MissionsConfig = MissionsConfig()
        private set

    @Volatile
    var categories: Map<String, LoadedCategory> = emptyMap()
        private set

    /** Só fica true depois que a tabela do banco existe. */
    @Volatile
    var ready: Boolean = false

    private val rows = ConcurrentHashMap<UUID, MutableMap<String, Row>>()
    private var tickCount = 0

    // ---------------------------------------------------------------- carregar

    fun load() {
        config = ConfigFiles.load("Missions", "config.json", MissionsConfig::class.java) { MissionsConfig() }
        categories = loadCategories()
        val total = categories.values.sumOf { it.missions.size }
        LOGGER.info("Missions: {} category/categories, {} mission(s).", categories.size, total)
        GlobalMissions.onConfigLoaded()
    }

    private fun loadCategories(): Map<String, LoadedCategory> {
        val dir = File(ConfigFiles.dir("Missions"), "categories")
        if (!dir.exists()) {
            ConfigFiles.load("Missions/categories", "beginner.json", MissionCategory::class.java, track = false) { MissionDefaults.beginner() }
            ConfigFiles.load("Missions/categories", "daily.json", MissionCategory::class.java, track = false) { MissionDefaults.daily() }
            ConfigFiles.load("Missions/categories", "weekly.json", MissionCategory::class.java, track = false) { MissionDefaults.weekly() }
            ConfigFiles.load("Missions/categories", "global.json", MissionCategory::class.java, track = false) { MissionDefaults.globals() }
            // the Pokémon examples are only created when Cobblemon is installed
            if (Integrations.cobblemon()) {
                ConfigFiles.load("Missions/categories", "pokemon.json", MissionCategory::class.java, track = false) { MissionDefaults.pokemon() }
                ConfigFiles.load("Missions/categories", "global_pokemon.json", MissionCategory::class.java, track = false) { MissionDefaults.globalPokemon() }
            }
        }
        val result = LinkedHashMap<String, LoadedCategory>()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()
        for (file in files) {
            val id = file.nameWithoutExtension
            // categorias são conteúdo do usuário: ficam de fora do /serverbuddy config check
            val def = ConfigFiles.load("Missions/categories", file.name, MissionCategory::class.java, track = false) { MissionCategory() }
            if (!def.enabled) continue
            if (def.requiresMod != null && !Integrations.isLoaded(def.requiresMod)) continue
            val missions = ArrayList<LoadedMission>()
            for ((missionId, mission) in def.missions) {
                if (!mission.enabled) continue
                val objectives = ArrayList<Objective>()
                var broken = false
                for (text in mission.objectives) {
                    val objective = Objective.parse(text)
                    if (objective == null) {
                        LOGGER.warn("Missions/categories/{}: mission '{}' has a malformed objective: \"{}\" (format: verb target amount). Verbs: {}", file.name, missionId, text, Objective.verbs())
                        broken = true
                        continue
                    }
                    if (StatReader.targetExists(objective) == false) LOGGER.warn("Missions/categories/{}: Target '{}' does not exist in the game (mission '{}').", file.name, objective.target, missionId)
                    objectives.add(objective)
                }
                if (broken || objectives.isEmpty()) {
                    LOGGER.warn("Missions/categories/{}: mission '{}' was skipped (no valid objectives).", file.name, missionId)
                    continue
                }
                RewardService.check(mission.rewards, "Missions/categories/${file.name} ($missionId)")
                missions.add(LoadedMission(id, missionId, mission, objectives))
            }
            result[id] = LoadedCategory(id, def, missions)
        }
        return result
    }

    fun find(key: String): LoadedMission? {
        val categoryId = key.substringBefore('.')
        val missionId = key.substringAfter('.', "")
        return categories[categoryId]?.missions?.firstOrNull { it.id.equals(missionId, ignoreCase = true) }
    }

    // ---------------------------------------------------------------- tempo e rotação

    private fun now(): LocalDateTime = LocalDateTime.now()

    /** O período da categoria (um valor que não existe no arquivo conta como "none"). */
    private fun periodOf(category: LoadedCategory): MissionPeriod = (category.def.period as MissionPeriod?) ?: MissionPeriod.NONE

    fun periodKey(category: LoadedCategory): String =
        MissionRules.periodKey(periodOf(category), now(), config.reset.hour, config.reset.weekStart)

    /** Quanto falta para a categoria trocar as missões (null se ela não troca). Numa categoria global, quanto falta para a missão em jogo acabar. */
    fun resetIn(category: LoadedCategory): Long? {
        if (category.def.global) return GlobalMissions.timeLeftMs().takeIf { it > 0 && GlobalMissions.activeIds(category).isNotEmpty() }
        val next = MissionRules.nextReset(periodOf(category), now(), config.reset.hour, config.reset.weekStart) ?: return null
        return java.time.Duration.between(now(), next).toMillis().coerceAtLeast(0)
    }

    /** Os ids que aparecem neste período (todos, se a categoria não sorteia). */
    fun inRotation(category: LoadedCategory): Set<String> {
        val ids = category.missions.map { it.id }
        if (periodOf(category) == MissionPeriod.NONE) return ids.toSet()
        return MissionRules.rotation(ids, category.def.pick, periodKey(category) + "/" + category.id)
    }

    // ---------------------------------------------------------------- linhas do jogador

    private fun rowsOf(uuid: UUID): MutableMap<String, Row> = rows.computeIfAbsent(uuid) { LinkedHashMap() }

    fun onJoin(player: ServerPlayer) {
        val loaded = LinkedHashMap<String, Row>()
        for (row in MissionsRepository.loadAll(player.uuid)) loaded[row.key] = row
        rows[player.uuid] = loaded
    }

    fun onLeave(uuid: UUID) {
        flush(uuid)
        rows.remove(uuid)
    }

    fun flush(uuid: UUID) {
        val map = rows[uuid] ?: return
        for (row in map.values.toList()) if (row.dirty) MissionsRepository.save(row)
    }

    fun flushAll() {
        for (uuid in rows.keys.toList()) flush(uuid)
    }

    /** Quantas missões prontas para resgatar o jogador tem (usa só a memória). */
    fun readyCount(uuid: UUID): Int = rows[uuid]?.values?.count { it.status == Status.COMPLETED } ?: 0

    fun claimedCount(uuid: UUID): Int = rows[uuid]?.values?.sumOf { it.completions } ?: 0

    fun activeCount(uuid: UUID): Int = rows[uuid]?.values?.count { it.status == Status.ACTIVE } ?: 0

    // ---------------------------------------------------------------- disponibilidade

    /** null = disponível; senão o motivo de estar bloqueada. */
    private fun lockReason(player: ServerPlayer, mission: LoadedMission, map: Map<String, Row>): String? {
        val requires = mission.def.requires ?: return null
        requires.permission?.takeIf { it.isNotBlank() }?.let { node ->
            if (!SbPermissions.check(player, node, false)) return "requires permission"
        }
        val missing = requires.missions.filter { other ->
            val key = if ('.' in other) other else "${mission.categoryId}.$other"
            (map[key]?.completions ?: 0) <= 0
        }
        if (missing.isNotEmpty()) {
            val names = missing.map { other -> (if ('.' in other) find(other) else find("${mission.categoryId}.$other"))?.displayName ?: other }
            return "complete first: " + names.joinToString(", ")
        }
        return null
    }

    // ---------------------------------------------------------------- ciclo das missões de um jogador

    /**
     * Põe em dia as linhas do jogador (cria as que faltam, troca as de período novo, recomeça as repetíveis) e devolve as
     * missões ativas com a linha de cada uma.
     */
    private fun sync(player: ServerPlayer): List<Pair<LoadedMission, Row>> {
        val map = rowsOf(player.uuid)
        val active = ArrayList<Pair<LoadedMission, Row>>()
        val now = System.currentTimeMillis()
        for (category in categories.values) {
            val global = category.def.global
            // missão global: só existe enquanto o servidor a mantém em jogo, e só para quem participa
            if (global && !GlobalMissions.participates(player.uuid)) continue
            val visible = if (global) GlobalMissions.activeIds(category) else inRotation(category)
            if (visible.isEmpty()) continue
            val rotating = global || periodOf(category) != MissionPeriod.NONE
            val period = if (global) GlobalMissions.periodKey() else periodKey(category)
            for (mission in category.missions) {
                if (mission.id !in visible) continue
                if (lockReason(player, mission, map) != null) continue
                var row = map[mission.key]
                if (row != null && rotating && row.periodKey != period) {
                    // virou o dia (ou a semana): o prêmio de quem já tinha terminado não se perde (numa rodada global antiga, não vale)
                    if (row.status == Status.COMPLETED && !global) claim(player, mission.key, silent = true)
                    row = null
                }
                if (row != null && row.status == Status.CLAIMED && mission.def.repeatable && now >= row.finishedAt + mission.cooldownMs) row = null
                if (row == null) {
                    row = start(player, mission, period, map[mission.key]?.completions ?: 0)
                    map[mission.key] = row
                }
                if (row.status == Status.ACTIVE) active.add(mission to row)
            }
        }
        return active
    }

    private fun start(player: ServerPlayer, mission: LoadedMission, period: String, completions: Int): Row {
        val base = LongArray(mission.objectives.size) { i -> if (mission.objectives[i].kind == Kind.STAT) StatReader.read(player, mission.objectives[i]) else 0L }
        val row = Row(player.uuid, mission.key, Status.ACTIVE, period, System.currentTimeMillis(), 0, completions, base, LongArray(mission.objectives.size))
        row.dirty = true
        return row
    }

    /** A cada segundo: confere o progresso de quem está online. */
    fun tick(server: net.minecraft.server.MinecraftServer) {
        if (++tickCount % 20 != 0) return
        if (!ready) return
        try {
            GlobalMissions.tick(server)
        } catch (e: Exception) {
            LOGGER.error("Error in the global missions timer", e)
        }
        for (player in server.playerList.players) {
            try {
                for ((mission, row) in sync(player)) update(player, mission, row)
            } catch (e: Exception) {
                LOGGER.error("Error checking missions for ${player.gameProfile.name}", e)
            }
        }
        if (tickCount % 100 == 0) flushAll()
    }

    private fun update(player: ServerPlayer, mission: LoadedMission, row: Row) {
        var changed = false
        mission.objectives.forEachIndexed { i, objective ->
            when (objective.kind) {
                Kind.STAT -> {
                    val current = StatReader.read(player, objective)
                    if (current < row.base[i]) {
                        row.base[i] = current // as estatísticas do jogador foram zeradas
                        changed = true
                    }
                    val value = (current - row.base[i]).coerceIn(0, objective.amount)
                    if (value != row.value[i]) {
                        row.value[i] = value
                        changed = true
                    }
                }
                Kind.STATE -> {
                    val value = StatReader.read(player, objective).coerceIn(0, objective.amount)
                    if (value != row.value[i]) {
                        row.value[i] = value
                        changed = true
                    }
                }
                Kind.EVENT -> {}
            }
        }
        if (changed) row.dirty = true
        checkComplete(player, mission, row)
    }

    private fun checkComplete(player: ServerPlayer, mission: LoadedMission, row: Row) {
        if (row.status != Status.ACTIVE) return
        if (mission.objectives.indices.any { row.value[it] < mission.objectives[it].amount }) return
        // missão global: quem completa primeiro vence a corrida e a missão acaba para os outros
        if (categories[mission.categoryId]?.def?.global == true) {
            GlobalMissions.onComplete(player, mission, row)
            return
        }
        row.status = Status.COMPLETED
        row.finishedAt = System.currentTimeMillis()
        row.dirty = true
        val auto = mission.def.autoClaim ?: config.autoClaim
        notifyCompleted(player, mission, auto)
        if (auto) claim(player, mission.key, silent = true)
        else MissionsRepository.save(row)
    }

    // ---------------------------------------------------------------- eventos (Cobblemon, comando manual)

    /**
     * Soma [amount] aos objetivos do verbo [verb] das missões ativas do jogador cujo objetivo passa em [matches].
     * Chamado pelos eventos do Cobblemon e por `/missions progress`.
     */
    fun eventProgress(player: ServerPlayer, verb: String, amount: Long = 1, matches: (Objective) -> Boolean = { true }) {
        if (!ready || amount <= 0) return
        for ((mission, row) in sync(player)) {
            var changed = false
            mission.objectives.forEachIndexed { i, objective ->
                if (objective.kind == Kind.EVENT && objective.verb.canonical == verb && matches(objective)) {
                    val value = (row.value[i] + amount).coerceAtMost(objective.amount)
                    if (value != row.value[i]) {
                        row.value[i] = value
                        changed = true
                    }
                }
            }
            if (changed) {
                row.dirty = true
                checkComplete(player, mission, row)
            }
        }
    }

    // ---------------------------------------------------------------- resgatar

    /** Entrega o prêmio de uma missão pronta. */
    fun claim(player: ServerPlayer, key: String, silent: Boolean = false): ClaimResult {
        val mission = find(key) ?: return ClaimResult.UNKNOWN
        val row = rows[player.uuid]?.get(mission.key)
        if (row == null || row.status != Status.COMPLETED) return ClaimResult.NOT_READY
        row.status = Status.CLAIMED
        row.completions += 1
        row.finishedAt = System.currentTimeMillis()
        // o banco muda ANTES do prêmio sair: se algo falhar, ninguém recebe duas vezes
        MissionsRepository.save(row)
        val vars = mapOf("mission" to Texts.escape(plain(mission.displayName)), "category" to Texts.escape(plain(categories[mission.categoryId]?.displayName ?: mission.categoryId)))
        val given = RewardService.give(player, mission.def.rewards, vars, "missions:${mission.key}")
        if (!silent) {
            val reward = RewardService.describeAllText(given).ifEmpty { "—" }
            player.sendSystemMessage(Texts.render(config.messages.claimed, vars + ("reward" to reward)))
        }
        return ClaimResult.OK
    }

    /** Resgata todas as prontas ([categoryId] = só dessa categoria). Devolve quantas saíram. */
    fun claimAll(player: ServerPlayer, categoryId: String? = null): Int {
        val ready = rows[player.uuid]?.values?.filter { it.status == Status.COMPLETED && (categoryId == null || it.key.substringBefore('.') == categoryId) }?.map { it.key } ?: return 0
        var count = 0
        for (key in ready) if (claim(player, key) == ClaimResult.OK) count++
        return count
    }

    // ---------------------------------------------------------------- vistas para o menu

    /** As categorias que o jogador vê (as que têm alguma missão visível). */
    fun visibleCategories(player: ServerPlayer): List<LoadedCategory> {
        sync(player)
        return categories.values.filter { category -> views(player, category.id).isNotEmpty() }
    }

    fun views(player: ServerPlayer, categoryId: String): List<MissionView> {
        val category = categories[categoryId] ?: return emptyList()
        val map = rowsOf(player.uuid)
        val global = category.def.global
        val visible = if (global) (if (GlobalMissions.canSee(player.uuid)) GlobalMissions.activeIds(category) else emptySet()) else inRotation(category)
        val now = System.currentTimeMillis()
        return category.missions.filter { it.id in visible }.map { mission ->
            val reason = lockReason(player, mission, map)
            // a linha de uma rodada global antiga não conta
            val row = map[mission.key]?.takeIf { !global || it.periodKey == GlobalMissions.periodKey() }
            if (reason != null && row == null) return@map MissionView(mission, State.LOCKED, mission.objectives.map { ObjectiveView(it, 0) }, requirement = reason)
            // modo "ui": a missão global espera o jogador clicar para participar
            if (global && row == null && !GlobalMissions.participates(player.uuid)) return@map MissionView(mission, State.OFFER, mission.objectives.map { ObjectiveView(it, 0) })
            val values = mission.objectives.mapIndexed { i, o -> ObjectiveView(o, row?.value?.getOrNull(i) ?: 0L) }
            when {
                row == null -> MissionView(mission, State.ACTIVE, values)
                row.status == Status.COMPLETED -> MissionView(mission, State.READY, values.map { ObjectiveView(it.objective, it.objective.amount) })
                row.status == Status.CLAIMED && mission.def.repeatable -> {
                    val left = row.finishedAt + mission.cooldownMs - now
                    if (left > 0) MissionView(mission, State.COOLDOWN, values.map { ObjectiveView(it.objective, it.objective.amount) }, cooldownLeftMs = left) else MissionView(mission, State.ACTIVE, values)
                }
                row.status == Status.CLAIMED -> MissionView(mission, State.CLAIMED, values.map { ObjectiveView(it.objective, it.objective.amount) })
                else -> MissionView(mission, State.ACTIVE, values)
            }
        }
    }

    // ---------------------------------------------------------------- apoio das missões globais

    /** Põe em dia as linhas do jogador agora (cria a da missão global que ele acabou de aceitar). */
    fun refresh(player: ServerPlayer) {
        sync(player)
    }

    /** Apaga a missão [key] de todos (os online da memória e o banco), menos de [except]. */
    fun purgeMission(key: String, except: UUID?) {
        for ((uuid, map) in rows) if (uuid != except) map.remove(key)
        MissionsRepository.deleteMissionForAll(key, except)
    }

    /** Apaga a linha de um jogador (memória e banco). */
    fun dropRow(uuid: UUID, key: String) {
        rows[uuid]?.remove(key)
        MissionsRepository.delete(uuid, key)
    }

    /** O progresso do jogador na missão: (soma do que fez, soma do que precisa). null se ele não tem a linha. */
    fun progressOf(uuid: UUID, mission: LoadedMission): Pair<Long, Long>? {
        val row = rows[uuid]?.get(mission.key) ?: return null
        val total = mission.objectives.sumOf { it.amount }
        val done = mission.objectives.indices.sumOf { (row.value.getOrNull(it) ?: 0L).coerceAtMost(mission.objectives[it].amount) }
        return done to total
    }

    /** Título grande na tela de um jogador (os textos são MiniMessage com %valores%). */
    fun sendTitle(player: ServerPlayer, title: String, subtitle: String, vars: Map<String, Any?>) {
        if (title.isBlank() && subtitle.isBlank()) return
        player.connection.send(ClientboundSetTitlesAnimationPacket(10, 60, 20))
        player.connection.send(ClientboundSetSubtitleTextPacket(Texts.render(subtitle, vars)))
        player.connection.send(ClientboundSetTitleTextPacket(Texts.render(title, vars)))
    }

    // ---------------------------------------------------------------- administração

    /** Termina a missão na hora (como se o jogador tivesse feito tudo). */
    fun forceComplete(player: ServerPlayer, key: String): Boolean {
        val mission = find(key) ?: return false
        sync(player)
        val row = rows[player.uuid]?.get(mission.key) ?: return false
        if (row.status != Status.ACTIVE) return false
        mission.objectives.forEachIndexed { i, o -> row.value[i] = o.amount }
        checkComplete(player, mission, row)
        return true
    }

    /** Apaga o progresso: de uma missão ([key]), de uma categoria (id) ou de tudo (null). */
    fun reset(player: ServerPlayer, key: String?): Int {
        val map = rowsOf(player.uuid)
        val targets = map.keys.filter { key == null || it.equals(key, ignoreCase = true) || it.substringBefore('.').equals(key, ignoreCase = true) }
        for (k in targets) {
            map.remove(k)
            MissionsRepository.delete(player.uuid, k)
        }
        return targets.size
    }

    // ---------------------------------------------------------------- avisos

    private fun notifyCompleted(player: ServerPlayer, mission: LoadedMission, auto: Boolean) {
        val vars = mapOf("mission" to Texts.escape(plain(mission.displayName)))
        val messages = config.messages
        val notify = config.notify
        if (notify.chat) player.sendSystemMessage(Texts.render(if (auto) messages.completedAuto else messages.completed, vars))
        if (notify.title) {
            player.connection.send(ClientboundSetTitlesAnimationPacket(10, 50, 20))
            player.connection.send(ClientboundSetSubtitleTextPacket(Texts.render(messages.completedSubtitle, vars + ("mission" to mission.displayName))))
            player.connection.send(ClientboundSetTitleTextPacket(Texts.render(messages.completedTitle, vars)))
        }
        notify.sound?.play(player)
    }

    /** O texto sem as tags do MiniMessage (para mensagens que não podem ter o estilo do nome da missão). */
    private fun plain(text: String): String = Texts.parse(text).string

    fun onJoinReminder(player: ServerPlayer) {
        GlobalMissions.onJoin(player)
        val text = config.messages.joinReminder ?: return
        val ready = readyCount(player.uuid)
        if (ready > 0) Texts.send(player, text, mapOf("ready" to ready))
    }
}
