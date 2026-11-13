package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Vitórias, sequência e "silenciado" de cada jogador (tabela `sb_trivia_scores`), com um cache para os placeholders. */
object TriviaScores {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Trivia")

    class Stats(
        val uuid: UUID,
        var name: String,
        var wins: Int = 0,
        var streak: Int = 0,
        var bestStreak: Int = 0,
        var fastestMs: Long = 0,
        var muted: Boolean = false
    )

    private val cache = ConcurrentHashMap<UUID, Stats>()

    @Volatile
    private var topCache: List<Stats> = emptyList()

    @Volatile
    private var topAt = 0L

    fun createTable() {
        val sql = "CREATE TABLE IF NOT EXISTS sb_trivia_scores (" +
            "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
            "player_name VARCHAR(32) NOT NULL DEFAULT '', " +
            "wins INTEGER NOT NULL DEFAULT 0, " +
            "streak INTEGER NOT NULL DEFAULT 0, " +
            "best_streak INTEGER NOT NULL DEFAULT 0, " +
            "fastest_ms BIGINT NOT NULL DEFAULT 0, " +
            "muted INTEGER NOT NULL DEFAULT 0)"
        run("create table sb_trivia_scores") { conn -> conn.createStatement().use { it.execute(sql) } }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Failed to $what!", e)
        null
    }

    private fun read(rs: ResultSet) = Stats(
        UUID.fromString(rs.getString("player_uuid")), rs.getString("player_name") ?: "", rs.getInt("wins"), rs.getInt("streak"),
        rs.getInt("best_streak"), rs.getLong("fastest_ms"), rs.getInt("muted") == 1
    )

    private fun load(conn: Connection, uuid: UUID): Stats? =
        conn.prepareStatement("SELECT * FROM sb_trivia_scores WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }

    /** Chamado quando o jogador entra: guarda o nome atual e deixa os números no cache. */
    fun onJoin(uuid: UUID, name: String) {
        val stats = run("read trivia stats for $uuid") { conn -> load(conn, uuid) } ?: Stats(uuid, name)
        stats.name = name
        cache[uuid] = stats
    }

    fun onLeave(uuid: UUID) {
        cache.remove(uuid)
    }

    /** Os números do jogador (do cache se ele está online; senão do banco). Sem registro = tudo zero. */
    fun stats(uuid: UUID, name: String = ""): Stats =
        cache[uuid] ?: run("read trivia stats for $uuid") { conn -> load(conn, uuid) } ?: Stats(uuid, name)

    /** Procura pelo nome (para o /trivia stats de quem está offline). */
    fun byName(name: String): Stats? = run("look up $name in trivia") { conn ->
        conn.prepareStatement("SELECT * FROM sb_trivia_scores WHERE LOWER(player_name) = ?").use { st ->
            st.setString(1, name.lowercase())
            st.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }
    }

    fun isMuted(uuid: UUID): Boolean = cache[uuid]?.muted ?: false

    fun setMuted(uuid: UUID, name: String, muted: Boolean) {
        val flag = if (muted) 1 else 0
        run("mute trivia for $uuid") { conn ->
            val changed = conn.prepareStatement("UPDATE sb_trivia_scores SET muted = ?, player_name = ? WHERE player_uuid = ?").use { st ->
                st.setInt(1, flag)
                st.setString(2, name)
                st.setString(3, uuid.toString())
                st.executeUpdate()
            }
            if (changed == 0) {
                conn.prepareStatement("INSERT INTO sb_trivia_scores (player_uuid, player_name, muted) VALUES (?, ?, ?)").use { st ->
                    st.setString(1, uuid.toString())
                    st.setString(2, name)
                    st.setInt(3, flag)
                    st.executeUpdate()
                }
            }
        }
        cache.computeIfAbsent(uuid) { Stats(uuid, name) }.muted = muted
    }

    /** Conta uma vitória: soma, aumenta a sequência do vencedor e zera a dos outros. Devolve os números novos do vencedor. */
    fun recordWin(uuid: UUID, name: String, timeMs: Long): Stats {
        val updated = run("save the trivia win for $name") { conn ->
            val current = load(conn, uuid)
            if (current == null) {
                conn.prepareStatement("INSERT INTO sb_trivia_scores (player_uuid, player_name, wins, streak, best_streak, fastest_ms) VALUES (?, ?, 1, 1, 1, ?)").use { st ->
                    st.setString(1, uuid.toString())
                    st.setString(2, name)
                    st.setLong(3, timeMs)
                    st.executeUpdate()
                }
            } else {
                val streak = current.streak + 1
                val fastest = if (current.fastestMs <= 0 || timeMs < current.fastestMs) timeMs else current.fastestMs
                conn.prepareStatement("UPDATE sb_trivia_scores SET player_name = ?, wins = ?, streak = ?, best_streak = ?, fastest_ms = ? WHERE player_uuid = ?").use { st ->
                    st.setString(1, name)
                    st.setInt(2, current.wins + 1)
                    st.setInt(3, streak)
                    st.setInt(4, maxOf(current.bestStreak, streak))
                    st.setLong(5, fastest)
                    st.setString(6, uuid.toString())
                    st.executeUpdate()
                }
            }
            conn.prepareStatement("UPDATE sb_trivia_scores SET streak = 0 WHERE player_uuid <> ? AND streak > 0").use { st ->
                st.setString(1, uuid.toString())
                st.executeUpdate()
            }
            load(conn, uuid)
        } ?: Stats(uuid, name, 1, 1, 1, timeMs)
        cache.forEach { (id, other) -> if (id != uuid) other.streak = 0 }
        cache[uuid] = updated
        topAt = 0
        return updated
    }

    /** Os [n] jogadores com mais vitórias (guarda o resultado por 20 segundos). */
    fun top(n: Int): List<Stats> {
        val now = System.currentTimeMillis()
        if (now - topAt > 20_000 || topCache.size < n) {
            topCache = run("read trivia rankings") { conn ->
                conn.prepareStatement("SELECT * FROM sb_trivia_scores WHERE wins > 0 ORDER BY wins DESC, fastest_ms ASC LIMIT ?").use { st ->
                    st.setInt(1, maxOf(n, 10))
                    st.executeQuery().use { rs ->
                        val out = ArrayList<Stats>()
                        while (rs.next()) out.add(read(rs))
                        out
                    }
                }
            } ?: topCache
            topAt = now
        }
        return topCache.take(n)
    }

    /** Posição do jogador no ranking de vitórias (1 = primeiro). 0 se não está entre os 100 primeiros (usa o cache, serve para placeholders). */
    fun rank(uuid: UUID): Int = top(100).indexOfFirst { it.uuid == uuid } + 1
}
