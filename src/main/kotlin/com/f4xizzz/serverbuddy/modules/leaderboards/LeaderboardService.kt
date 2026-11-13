package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.google.gson.JsonParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Builds the lists. Everything slow (reading the stats files, asking the economy about offline balances) runs on a background
 * thread; the server thread only collects what must be read there (online players) and swaps in the finished lists.
 */
object LeaderboardService {
    const val HIDDEN = "serverbuddy.leaderboards.hidden"
    private val log = LoggerFactory.getLogger("ServerBuddy/Leaderboards")

    class Entry(val uuid: UUID, val name: String, val value: Double)

    class Snapshot(val entries: List<Entry>) {
        val ranks: Map<UUID, Int> by lazy { entries.withIndex().associate { it.value.uuid to it.index + 1 } }
    }

    @Volatile var config = LeaderboardsConfig()
        private set

    /** Configured boards plus the automatic ones (one per Impactor currency). */
    @Volatile var boards: Map<String, BoardConfig> = emptyMap()
        private set

    @Volatile private var snapshots: Map<String, Snapshot> = emptyMap()

    @Volatile var ready = false

    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "serverbuddy-leaderboards").also { it.isDaemon = true } }
    private val running = AtomicBoolean(false)
    private var ticks = 0L

    fun load(newConfig: LeaderboardsConfig) {
        config = newConfig
        boards = effectiveBoards(newConfig)
    }

    fun snapshot(board: String): Snapshot? = snapshots[board]

    fun isOn(): Boolean = config.enabled

    // ---------------------------------------------------------------- boards

    private fun effectiveBoards(cfg: LeaderboardsConfig): Map<String, BoardConfig> {
        val result = LinkedHashMap<String, BoardConfig>()
        for ((id, board) in cfg.boards) if (board.enabled) result[id.lowercase(Locale.ROOT)] = board
        if (cfg.autoCurrencyBoards && Integrations.isLoaded("impactor")) {
            val covered = result.values.filter { it.type.equals("money", true) }.map { ImpactorBalances.resolveKey(it.currency) }.toSet()
            for (currency in ImpactorBalances.currencies()) {
                if (currency.key in covered) continue
                val id = "money_" + currency.key.substringAfter(':').lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")
                if (id !in result) result[id] = BoardConfig(type = "money", title = "<green>Richest players <gray>(${currency.name})", currency = currency.key, valueFormat = "%symbol%%value%")
            }
        }
        return result
    }

    /** Re-reads the Impactor currencies (they are registered after the server starts). */
    fun refreshBoards() {
        boards = effectiveBoards(config)
    }

    // ---------------------------------------------------------------- refresh

    fun tick(server: MinecraftServer) {
        if (!ready || !config.enabled) return
        if (++ticks % (config.refreshSeconds.coerceAtLeast(10) * 20L) == 0L) refresh(server)
    }

    /** Collects the online data on this (server) thread, then builds the lists in the background. */
    fun refresh(server: MinecraftServer) {
        if (!config.enabled || !running.compareAndSet(false, true)) return
        val online = HashMap<UUID, OnlinePlayer>()
        try {
            refreshBoards()
            for (player in server.playerList.players) {
                val hidden = SbPermissions.check(player, HIDDEN, false)
                val playtime = player.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)).toLong()
                online[player.uuid] = OnlinePlayer(player.gameProfile.name, playtime, hidden)
                LeaderboardRepository.save(player.uuid, player.gameProfile.name, "hidden", if (hidden) 1.0 else 0.0)
                if (Integrations.cobblemon()) rememberDex(player)
            }
        } catch (e: Throwable) {
            log.error("Error collecting the online players for the leaderboards", e)
            running.set(false)
            return
        }
        val cfg = config
        val currentBoards = boards
        val playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile()
        val statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile()
        val names: (UUID) -> String? = { uuid -> server.profileCache?.get(uuid)?.orElse(null)?.name }
        executor.execute {
            try {
                val result = build(cfg, currentBoards, online, playerData, statsDir, names)
                snapshots = result
            } catch (e: Throwable) {
                log.error("Error building the leaderboards", e)
            } finally {
                running.set(false)
            }
        }
    }

    class OnlinePlayer(val name: String, val playtimeTicks: Long, val hidden: Boolean)

    private fun rememberDex(player: ServerPlayer) {
        val dex = CobblemonDexStats.read(player) ?: return
        LeaderboardRepository.save(player.uuid, player.gameProfile.name, "dex_caught", dex.caught.toDouble())
        LeaderboardRepository.save(player.uuid, player.gameProfile.name, "dex_seen", dex.seen.toDouble())
        LeaderboardRepository.save(player.uuid, player.gameProfile.name, "dex_percent", dex.percent)
    }

    /** Also called when a player leaves, so the last Pokédex values are the ones at logout. */
    fun onLeave(player: ServerPlayer) {
        if (!ready || !config.enabled) return
        if (Integrations.cobblemon()) rememberDex(player)
    }

    // ---------------------------------------------------------------- building (background thread)

    private fun build(
        cfg: LeaderboardsConfig, boards: Map<String, BoardConfig>, online: Map<UUID, OnlinePlayer>,
        playerData: File, statsDir: File, names: (UUID) -> String?
    ): Map<String, Snapshot> {
        val excluded = cfg.exclude.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val hiddenStored = LeaderboardRepository.all("hidden", 100000).filter { it.third > 0 }.map { it.first }.toSet()
        val hidden = hiddenStored + online.filterValues { it.hidden }.keys - online.filterValues { !it.hidden }.keys
        val nameCache = HashMap<UUID, String>()
        fun nameOf(uuid: UUID, remembered: String? = null): String =
            nameCache.getOrPut(uuid) { online[uuid]?.name ?: names(uuid) ?: remembered ?: uuid.toString().take(8) }

        fun allowed(uuid: UUID, name: String): Boolean = uuid !in hidden && uuid.toString().lowercase(Locale.ROOT) !in excluded && name.lowercase(Locale.ROOT) !in excluded

        // every player who ever joined (and was seen recently), newest first
        val cutoff = if (cfg.activeDays > 0) System.currentTimeMillis() - cfg.activeDays * 86_400_000L else 0L
        val candidates = (playerData.listFiles { f -> f.name.endsWith(".dat") }?.toList() ?: emptyList())
            .filter { it.lastModified() >= cutoff }
            .sortedByDescending { it.lastModified() }
            .take(cfg.maxPlayers)
            .mapNotNull { runCatching { UUID.fromString(it.name.removeSuffix(".dat")) }.getOrNull() }
        val pool = (candidates + online.keys).distinct()

        val result = HashMap<String, Snapshot>()
        for ((id, board) in boards) {
            val entries = ArrayList<Entry>()
            when (board.type.lowercase(Locale.ROOT)) {
                "playtime" -> for (uuid in pool) {
                    val ticks = online[uuid]?.playtimeTicks ?: readPlaytime(File(statsDir, "$uuid.json")) ?: continue
                    val name = nameOf(uuid)
                    if (ticks > 0 && allowed(uuid, name)) entries.add(Entry(uuid, name, ticks.toDouble()))
                }
                "money" -> if (Integrations.isLoaded("impactor")) {
                    val key = ImpactorBalances.resolveKey(board.currency)
                    for (uuid in pool) {
                        val name = nameOf(uuid)
                        if (!allowed(uuid, name)) continue
                        val balance = ImpactorBalances.balance(key, uuid) ?: continue
                        if (balance > 0) entries.add(Entry(uuid, name, balance))
                    }
                }
                "pokedex_caught", "pokedex_seen", "pokedex_percent" -> {
                    val stat = when (board.type.lowercase(Locale.ROOT)) { "pokedex_seen" -> "dex_seen"; "pokedex_percent" -> "dex_percent"; else -> "dex_caught" }
                    for ((uuid, remembered, value) in LeaderboardRepository.all(stat, cfg.maxPlayers)) {
                        val name = nameOf(uuid, remembered)
                        if (value > 0 && allowed(uuid, name)) entries.add(Entry(uuid, name, value))
                    }
                }
                else -> log.warn("Board '{}' has an unknown type '{}' (use playtime, money, pokedex_caught, pokedex_seen or pokedex_percent).", id, board.type)
            }
            entries.sortByDescending { it.value }
            result[id] = Snapshot(entries)
        }
        return result
    }

    private fun readPlaytime(file: File): Long? {
        if (!file.isFile) return null
        return try {
            val custom = file.reader().use { JsonParser.parseReader(it) }.asJsonObject.getAsJsonObject("stats")?.getAsJsonObject("minecraft:custom") ?: return null
            (custom.get("minecraft:play_time") ?: custom.get("minecraft:play_one_minute"))?.asLong
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- formatting

    fun formatValue(board: BoardConfig, value: Double): String {
        val formatted = when (board.type.lowercase(Locale.ROOT)) {
            "playtime" -> Durations.format((value * 50).toLong())
            "money" -> Economy.format(value)
            "pokedex_percent" -> "%.1f".format(Locale.ROOT, value)
            else -> value.toLong().toString()
        }
        val symbol = if (board.type.equals("money", true) && Integrations.isLoaded("impactor")) ImpactorBalances.symbol(board.currency) else ""
        return Texts.fill(board.valueFormat, mapOf("value" to formatted, "raw" to Economy.format(value), "symbol" to symbol))
    }

    /**
     * The argument of `%serverbuddy:lb/...%`: `<board>_<position>_<name|value|raw|uuid>` or `<board>_rank` (the board id may
     * contain underscores). Null when it is not a leaderboard placeholder.
     */
    fun resolvePlaceholder(viewer: UUID?, argument: String?): String? {
        val text = argument?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (text.endsWith("_rank")) {
            val snapshot = snapshot(text.removeSuffix("_rank")) ?: return null
            return viewer?.let { snapshot.ranks[it]?.toString() } ?: "-"
        }
        val parts = text.split('_')
        if (parts.size < 3) return null
        val field = parts.last()
        val position = parts[parts.size - 2].toIntOrNull() ?: return null
        val boardId = parts.dropLast(2).joinToString("_")
        val board = board(boardId) ?: return null
        val entry = snapshot(boardId)?.entries?.getOrNull(position - 1) ?: return "-"
        return when (field) {
            "name" -> Texts.escape(entry.name)
            "value" -> formatValue(board, entry.value)
            "raw" -> entry.value.toLong().toString()
            "uuid" -> entry.uuid.toString()
            else -> null
        }
    }

    /** For tests: installs boards and finished lists without any server. */
    internal fun installForTest(testBoards: Map<String, BoardConfig>, testSnapshots: Map<String, Snapshot>) {
        boards = testBoards
        snapshots = testSnapshots
    }

    /** A board by id or null; ids are case-insensitive. */
    fun board(id: String?): BoardConfig? = boards[id?.lowercase(Locale.ROOT)]
}
