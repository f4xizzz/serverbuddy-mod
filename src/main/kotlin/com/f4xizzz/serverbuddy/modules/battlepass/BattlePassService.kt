package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.TimeConditions
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ClaimOutcome { OK, NOT_REACHED, ALREADY, PREMIUM_REQUIRED, NO_REWARD, INACTIVE }

enum class BuyOutcome { OK, ALREADY, NOT_FOR_SALE, NO_ECONOMY, NOT_ENOUGH }

/** A temporada atual (guardada em BattlePass/season.json). [ended] = a equipe encerrou antes da hora (não renova sozinha). */
class SeasonState(val season: Int = 0, val start: Long = 0, val end: Long = 0, val ended: Boolean = false)

/**
 * O passe de batalha: temporada, XP, níveis, trilha premium e resgate de prêmios. O progresso fica em memória e vai para o
 * banco ao resgatar, ao sair e de tempos em tempos.
 */
object BattlePassService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/BattlePass")

    @Volatile
    var config: BattlePassConfig = BattlePassConfig()
        private set

    @Volatile
    var tiers: Map<Int, BpTier> = emptyMap()
        private set

    @Volatile
    var season: SeasonState? = null
        private set

    /** Só fica true depois que a tabela do banco existe. */
    @Volatile
    var ready: Boolean = false

    private val passes = ConcurrentHashMap<UUID, PlayerPass>()
    private val lastOnlineXp = ConcurrentHashMap<UUID, Long>()
    private var tickCount = 0

    // ---------------------------------------------------------------- carregar

    fun load() {
        config = ConfigFiles.load("BattlePass", "config.json", BattlePassConfig::class.java) { BattlePassConfig() }
        val file = ConfigFiles.load("BattlePass", "tiers.json", BpTiersFile::class.java) { BattlePassDefaults.tiers(config.levels) }
        tiers = file.tiers.filter { it.level in 1..config.levels }.associateBy { it.level }
        for (tier in file.tiers) {
            RewardService.check(tier.free, "BattlePass/tiers.json (level ${tier.level}, free)")
            RewardService.check(tier.premium, "BattlePass/tiers.json (level ${tier.level}, premium)")
        }
        season = readSeason()
        if (season == null && config.season.autoStart) startSeason(null)
        LOGGER.info("Battle pass: {} tier(s) with a reward, season {}.", tiers.size, season?.season ?: "none")
    }

    private fun seasonFile(): File = File(ConfigFiles.dir("BattlePass"), "season.json")

    private fun readSeason(): SeasonState? = try {
        seasonFile().takeIf { it.exists() }?.reader(Charsets.UTF_8)?.use { SbGson.pretty.fromJson(it, SeasonState::class.java) }?.takeIf { it.season > 0 }
    } catch (e: Exception) {
        LOGGER.error("Could not read BattlePass/season.json", e)
        null
    }

    private fun writeSeason(state: SeasonState) {
        try {
            seasonFile().parentFile.mkdirs()
            seasonFile().writeText(SbGson.pretty.toJson(state), Charsets.UTF_8)
        } catch (e: Exception) {
            LOGGER.error("Could not write BattlePass/season.json", e)
        }
    }

    // ---------------------------------------------------------------- temporada

    fun isActive(): Boolean = season?.let { System.currentTimeMillis() < it.end } ?: false

    fun timeLeftMs(): Long = season?.let { (it.end - System.currentTimeMillis()).coerceAtLeast(0) } ?: 0

    /** Começa uma temporada nova (o número sobe) com [days] dias (null = o do config). Todo mundo recomeça do nível 1. */
    fun startSeason(days: Int?): SeasonState {
        flushAll()
        val now = System.currentTimeMillis()
        val length = (days ?: config.season.days).coerceAtLeast(1)
        val state = SeasonState((season?.season ?: 0) + 1, now, now + length * 86_400_000L)
        season = state
        writeSeason(state)
        passes.clear()
        lastOnlineXp.clear()
        for (player in ServerHolder.server?.playerList?.players.orEmpty()) onJoin(player)
        LOGGER.info("Season {} of the battle pass started ({} days).", state.season, length)
        return state
    }

    /** Encerra a temporada agora (ninguém ganha mais XP; os prêmios já liberados continuam podendo ser resgatados). */
    fun endSeason(): Boolean {
        val current = season ?: return false
        if (!isActive()) return false
        val ended = SeasonState(current.season, current.start, System.currentTimeMillis(), ended = true)
        season = ended
        writeSeason(ended)
        return true
    }

    // ---------------------------------------------------------------- dados do jogador

    fun onJoin(player: ServerPlayer) {
        val current = season ?: return
        if (!ready) return
        passes[player.uuid] = BattlePassRepository.load(player.uuid, current.season) ?: PlayerPass(player.uuid, current.season)
        lastOnlineXp[player.uuid] = System.currentTimeMillis()
    }

    fun onLeave(uuid: UUID) {
        flush(uuid)
        passes.remove(uuid)
        lastOnlineXp.remove(uuid)
    }

    fun flush(uuid: UUID) {
        val pass = passes[uuid] ?: return
        if (pass.dirty) BattlePassRepository.save(pass)
    }

    fun flushAll() {
        for (uuid in passes.keys.toList()) flush(uuid)
    }

    /** O passe do jogador na temporada atual (cria um zerado se ainda não tem). null se não há temporada. */
    fun passOf(player: ServerPlayer): PlayerPass? {
        val current = season ?: return null
        val existing = passes[player.uuid]
        if (existing != null && existing.season == current.season) return existing
        val created = BattlePassRepository.load(player.uuid, current.season) ?: PlayerPass(player.uuid, current.season)
        passes[player.uuid] = created
        return created
    }

    fun passOf(uuid: UUID): PlayerPass? = passes[uuid]

    fun isPremium(player: ServerPlayer): Boolean {
        val premium = config.premium
        if (!premium.enabled) return false
        if (passOf(player)?.premium == true) return true
        return premium.permission.isNotBlank() && SbPermissions.check(player, premium.permission, false)
    }

    fun xpNeeded(pass: PlayerPass): Long = if (pass.level >= config.levels) 0 else BpMath.xpForNext(pass.level, config.xp.curve)

    // ---------------------------------------------------------------- XP e níveis

    private fun timeMatches(condition: String, now: LocalDateTime): Boolean {
        var text = condition.trim()
        var negate = false
        while (text.startsWith("!")) {
            negate = !negate
            text = text.substring(1).trim()
        }
        val name = text.substringBefore(':').trim().lowercase()
        val result = TimeConditions.evaluate(name, text.substringAfter(':', "").trim(), now) ?: false
        return result != negate
    }

    /** O multiplicador de XP que vale para o jogador agora. */
    fun multiplierFor(player: ServerPlayer): Double {
        val now = LocalDateTime.now()
        return BpMath.multiplier(config.boosts) { boost ->
            boost.conditions.all { timeMatches(it, now) } && (boost.permission.isNullOrBlank() || SbPermissions.check(player, boost.permission, false))
        }
    }

    /** Dá XP do passe (com os bônus). false se não há temporada em andamento. */
    fun addXp(player: ServerPlayer, base: Long, source: String): Boolean {
        if (!ready || !isActive() || base <= 0) return false
        val pass = passOf(player) ?: return false
        val gained = (base * multiplierFor(player)).toLong().coerceAtLeast(1)
        val result = BpMath.add(pass.level, pass.xp, gained, config.levels, config.xp.curve)
        pass.level = result.level
        pass.xp = result.xp
        pass.dirty = true
        config.messages.xpGain?.let { player.displayClientMessage(Texts.render(it, mapOf("xp" to gained, "source" to source)), true) }
        if (result.levelsGained > 0) onLevelUp(player, pass)
        return true
    }

    /** Sobe níveis direto (comando de admin). */
    fun addLevels(player: ServerPlayer, amount: Int): Boolean {
        val pass = passOf(player) ?: return false
        val before = pass.level
        pass.level = (pass.level + amount).coerceIn(1, config.levels)
        pass.xp = 0
        pass.dirty = true
        if (pass.level > before) onLevelUp(player, pass)
        return true
    }

    fun removeLevels(player: ServerPlayer, amount: Int): Boolean {
        val pass = passOf(player) ?: return false
        pass.level = (pass.level - amount).coerceAtLeast(1)
        pass.xp = 0
        pass.freeClaimed.removeIf { it > pass.level }
        pass.premiumClaimed.removeIf { it > pass.level }
        pass.dirty = true
        return true
    }

    fun reset(player: ServerPlayer): Boolean {
        val pass = passOf(player) ?: return false
        pass.level = 1
        pass.xp = 0
        pass.freeClaimed.clear()
        pass.premiumClaimed.clear()
        pass.dirty = true
        return true
    }

    fun resetRewards(player: ServerPlayer): Boolean {
        val pass = passOf(player) ?: return false
        pass.freeClaimed.clear()
        pass.premiumClaimed.clear()
        pass.dirty = true
        return true
    }

    fun setPremium(player: ServerPlayer, value: Boolean): Boolean {
        val pass = passOf(player) ?: return false
        pass.premium = value
        pass.dirty = true
        BattlePassRepository.save(pass)
        return true
    }

    private fun onLevelUp(player: ServerPlayer, pass: PlayerPass) {
        val vars = mapOf("level" to pass.level)
        val messages = config.messages
        val notify = config.notify
        if (notify.chat) player.sendSystemMessage(Texts.render(messages.levelUp, vars))
        if (notify.title) {
            player.connection.send(ClientboundSetTitlesAnimationPacket(10, 50, 20))
            player.connection.send(ClientboundSetSubtitleTextPacket(Texts.render(messages.levelUpSubtitle, vars)))
            player.connection.send(ClientboundSetTitleTextPacket(Texts.render(messages.levelUpTitle, vars)))
        }
        notify.sound?.play(player)
        if (config.autoClaim) claimAll(player, silent = true)
    }

    // ---------------------------------------------------------------- resgatar

    /** O prêmio do [level] na trilha pedida, se o jogador pode pegar. */
    fun claim(player: ServerPlayer, level: Int, premiumTrack: Boolean, silent: Boolean = false): ClaimOutcome {
        val pass = passOf(player) ?: return ClaimOutcome.INACTIVE
        val tier = tiers[level] ?: return ClaimOutcome.NO_REWARD
        val rewards = if (premiumTrack) tier.premium else tier.free
        if (rewards.isEmpty()) return ClaimOutcome.NO_REWARD
        if (level > pass.level) return ClaimOutcome.NOT_REACHED
        if (premiumTrack && !isPremium(player)) return ClaimOutcome.PREMIUM_REQUIRED
        val claimed = if (premiumTrack) pass.premiumClaimed else pass.freeClaimed
        if (!claimed.add(level)) return ClaimOutcome.ALREADY
        pass.dirty = true
        // o banco muda ANTES do prêmio sair: se algo falhar, ninguém recebe duas vezes
        BattlePassRepository.save(pass)
        val vars = mapOf("level" to level, "track" to if (premiumTrack) "premium" else "free")
        val given = RewardService.give(player, rewards, vars, "battlepass:${pass.season}:$level:${if (premiumTrack) "premium" else "free"}")
        if (!silent) {
            val text = RewardService.describeAllText(given).ifEmpty { "—" }
            player.sendSystemMessage(Texts.render(config.messages.claimed, vars + ("reward" to text)))
        }
        return ClaimOutcome.OK
    }

    /** Pode resgatar o prêmio do [level] na trilha? (para o menu) */
    fun state(player: ServerPlayer, pass: PlayerPass, level: Int, premiumTrack: Boolean): ClaimOutcome {
        val tier = tiers[level] ?: return ClaimOutcome.NO_REWARD
        val rewards = if (premiumTrack) tier.premium else tier.free
        if (rewards.isEmpty()) return ClaimOutcome.NO_REWARD
        if ((if (premiumTrack) pass.premiumClaimed else pass.freeClaimed).contains(level)) return ClaimOutcome.ALREADY
        if (level > pass.level) return ClaimOutcome.NOT_REACHED
        if (premiumTrack && !isPremium(player)) return ClaimOutcome.PREMIUM_REQUIRED
        return ClaimOutcome.OK
    }

    fun claimableCount(player: ServerPlayer): Int {
        val pass = passOf(player) ?: return 0
        var count = 0
        for (level in 1..pass.level) {
            if (state(player, pass, level, false) == ClaimOutcome.OK) count++
            if (state(player, pass, level, true) == ClaimOutcome.OK) count++
        }
        return count
    }

    /** Resgata tudo que está liberado. Devolve quantos prêmios saíram. */
    fun claimAll(player: ServerPlayer, silent: Boolean = false): Int {
        val pass = passOf(player) ?: return 0
        var count = 0
        for (level in 1..pass.level) {
            if (claim(player, level, false, silent = true) == ClaimOutcome.OK) count++
            if (claim(player, level, true, silent = true) == ClaimOutcome.OK) count++
        }
        if (count > 0 && !silent) player.sendSystemMessage(Texts.render(config.messages.claimed, mapOf("level" to pass.level, "reward" to "$count reward(s)")))
        return count
    }

    // ---------------------------------------------------------------- premium

    fun buyPremium(player: ServerPlayer): BuyOutcome {
        val premium = config.premium
        val pass = passOf(player) ?: return BuyOutcome.NOT_FOR_SALE
        if (isPremium(player)) return BuyOutcome.ALREADY
        if (!premium.enabled || premium.price <= 0) return BuyOutcome.NOT_FOR_SALE
        val spec = premium.economy.ifBlank { null }
        if (!Economy.available(spec)) return BuyOutcome.NO_ECONOMY
        if ((Economy.balance(player, spec) ?: 0.0) < premium.price) return BuyOutcome.NOT_ENOUGH
        if (!Economy.withdraw(player, premium.price, spec)) return BuyOutcome.NOT_ENOUGH
        pass.premium = true
        pass.dirty = true
        BattlePassRepository.save(pass)
        return BuyOutcome.OK
    }

    // ---------------------------------------------------------------- relógio

    /** A cada segundo: fim da temporada e XP por tempo online. */
    fun tick(server: MinecraftServer) {
        if (++tickCount % 20 != 0) return
        if (!ready) return
        val current = season
        if (current != null && !current.ended && System.currentTimeMillis() >= current.end && config.season.autoRenew) {
            // a nova temporada tem fim no futuro, então isto só acontece uma vez por temporada
            startSeason(null)
            return
        }
        val online = config.xp.online
        if (online.everyMinutes > 0 && isActive()) {
            val interval = online.everyMinutes * 60_000L
            val now = System.currentTimeMillis()
            for (player in server.playerList.players) {
                val last = lastOnlineXp.getOrPut(player.uuid) { now }
                if (now - last >= interval) {
                    lastOnlineXp[player.uuid] = now
                    addXp(player, online.xp.toLong(), "online")
                }
            }
        }
        if (tickCount % 100 == 0) flushAll()
    }

    fun onJoinReminder(player: ServerPlayer) {
        val text = config.messages.joinReminder ?: return
        val count = claimableCount(player)
        if (count > 0) Texts.send(player, text, mapOf("claimable" to count))
    }

    fun seasonSummary(): Component {
        val current = season ?: return Component.literal("No season.")
        val state = if (isActive()) "termina em ${Durations.format(timeLeftMs())}" else "ended"
        return Component.literal("Temporada ${current.season} ($state)")
    }
}
