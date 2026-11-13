package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

/** Um registro do histórico: AVISO, BAN, UNBAN ou RESET. */
class GuardLogEntry(val at: Long, val kind: String, val detail: String, val durationMs: Long)

/** Quem está banido do chat agora. */
class BannedEntry(val uuid: UUID, val name: String, val until: Long)

/** As tabelas `sb_chatguard*` do gerenciador de chat no banco do ServerBuddy. */
object ChatGuardRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/ChatGuard")

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Error trying to $what!", e)
        null
    }

    private fun exec(conn: Connection, sql: String, vararg args: Any?): Int = conn.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, value ->
            when (value) {
                null -> st.setObject(i + 1, null)
                is Int -> st.setInt(i + 1, value)
                is Long -> st.setLong(i + 1, value)
                else -> st.setString(i + 1, value.toString())
            }
        }
        st.executeUpdate()
    }

    private fun <T> query(conn: Connection, sql: String, vararg args: Any?, read: (ResultSet) -> T): List<T> = conn.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, value -> if (value is Long) st.setLong(i + 1, value) else st.setString(i + 1, value.toString()) }
        st.executeQuery().use { rs ->
            val out = ArrayList<T>()
            while (rs.next()) out.add(read(rs))
            out
        }
    }

    fun createTables() {
        run("create the chat guard tables") { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE IF NOT EXISTS sb_chatguard (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, warnings INT NOT NULL DEFAULT 0, last_violation BIGINT NOT NULL DEFAULT 0, bans INT NOT NULL DEFAULT 0, total_warnings INT NOT NULL DEFAULT 0, ban_until BIGINT NOT NULL DEFAULT 0, last_ban BIGINT NOT NULL DEFAULT 0)")
                st.execute("CREATE TABLE IF NOT EXISTS sb_chatguard_log (id VARCHAR(36) NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, name VARCHAR(32) NOT NULL, at BIGINT NOT NULL, kind VARCHAR(16) NOT NULL, detail VARCHAR(300), duration_ms BIGINT NOT NULL DEFAULT 0)")
            }
        }
    }

    private fun state(rs: ResultSet) = GuardState(
        rs.getInt("warnings"), rs.getLong("last_violation"), rs.getInt("bans"), rs.getInt("total_warnings"), rs.getLong("ban_until"), rs.getLong("last_ban")
    )

    fun load(uuid: UUID): GuardState? = run("read the chat state of $uuid") { conn ->
        query(conn, "SELECT * FROM sb_chatguard WHERE player_uuid = ?", uuid.toString()) { state(it) }.firstOrNull()
    }

    /** O jogador pelo nome (já visto pelo gerenciador), para os comandos da equipe com gente offline. */
    fun findByName(name: String): Pair<UUID, String>? = run("look up $name") { conn ->
        query(conn, "SELECT player_uuid, name FROM sb_chatguard WHERE LOWER(name) = ?", name.lowercase()) { UUID.fromString(it.getString("player_uuid")) to it.getString("name") }.firstOrNull()
    }

    fun save(uuid: UUID, name: String, state: GuardState) {
        run("save the chat state of $uuid") { conn ->
            val updated = exec(
                conn, "UPDATE sb_chatguard SET name = ?, warnings = ?, last_violation = ?, bans = ?, total_warnings = ?, ban_until = ?, last_ban = ? WHERE player_uuid = ?",
                name, state.warnings, state.lastViolation, state.bans, state.totalWarnings, state.banUntil, state.lastBan, uuid.toString()
            )
            if (updated == 0) {
                exec(
                    conn, "INSERT INTO sb_chatguard (player_uuid, name, warnings, last_violation, bans, total_warnings, ban_until, last_ban) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    uuid.toString(), name, state.warnings, state.lastViolation, state.bans, state.totalWarnings, state.banUntil, state.lastBan
                )
            }
        }
    }

    fun delete(uuid: UUID) {
        run("delete the chat state of $uuid") { conn -> exec(conn, "DELETE FROM sb_chatguard WHERE player_uuid = ?", uuid.toString()) }
    }

    /** Quem está com o ban de chat valendo (offline também). */
    fun banned(now: Long): List<BannedEntry> = run("read the players banned from chat") { conn ->
        query(conn, "SELECT player_uuid, name, ban_until FROM sb_chatguard WHERE ban_until > ? ORDER BY ban_until", now) {
            BannedEntry(UUID.fromString(it.getString("player_uuid")), it.getString("name"), it.getLong("ban_until"))
        }
    } ?: emptyList()

    fun log(uuid: UUID, name: String, kind: String, detail: String, durationMs: Long = 0) {
        run("write to the chat history") { conn ->
            exec(conn, "INSERT INTO sb_chatguard_log (id, player_uuid, name, at, kind, detail, duration_ms) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), uuid.toString(), name, System.currentTimeMillis(), kind, detail.take(300), durationMs)
        }
    }

    /** Os registros mais recentes de [uuid] (o mais novo primeiro). */
    fun history(uuid: UUID, limit: Int): List<GuardLogEntry> = run("read the chat history of $uuid") { conn ->
        query(conn, "SELECT at, kind, detail, duration_ms FROM sb_chatguard_log WHERE player_uuid = ? ORDER BY at DESC", uuid.toString()) {
            GuardLogEntry(it.getLong("at"), it.getString("kind"), it.getString("detail") ?: "", it.getLong("duration_ms"))
        }.take(limit)
    } ?: emptyList()

    fun clearHistory(uuid: UUID) {
        run("delete the chat history of $uuid") { conn -> exec(conn, "DELETE FROM sb_chatguard_log WHERE player_uuid = ?", uuid.toString()) }
    }
}
