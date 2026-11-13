package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

enum class Status { ACTIVE, COMPLETED, CLAIMED }

/** O estado de uma missão para um jogador. [base] é o valor das estatísticas quando a missão começou; [value] é o progresso de cada objetivo. */
class Row(
    val uuid: UUID,
    val key: String,
    var status: Status,
    var periodKey: String,
    var startedAt: Long,
    var finishedAt: Long,
    var completions: Int,
    var base: LongArray,
    var value: LongArray
) {
    @Volatile
    var dirty = false
}

/** Tabela `sb_missions` no banco do ServerBuddy. */
object MissionsRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Missions")

    fun createTable() {
        val sql = "CREATE TABLE IF NOT EXISTS sb_missions (" +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "mission_key VARCHAR(160) NOT NULL, " +
            "status VARCHAR(12) NOT NULL, " +
            "period_key VARCHAR(24) NOT NULL DEFAULT '', " +
            "started_at BIGINT NOT NULL DEFAULT 0, " +
            "finished_at BIGINT NOT NULL DEFAULT 0, " +
            "completions INTEGER NOT NULL DEFAULT 0, " +
            "base TEXT NOT NULL, " +
            "progress TEXT NOT NULL, " +
            "PRIMARY KEY (player_uuid, mission_key))"
        run("create table sb_missions") { conn -> conn.createStatement().use { it.execute(sql) } }
        run("create table sb_missions_global") { conn ->
            conn.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS sb_missions_global (state_key VARCHAR(40) NOT NULL PRIMARY KEY, state_value TEXT NOT NULL)") }
        }
    }

    // ---------------------------------------------------------------- missão global (um estado só para o servidor)

    /** O estado guardado da missão global (chave → valor). Vazio se nunca houve. */
    fun loadGlobal(): Map<String, String> = run("read global mission state") { conn ->
        conn.prepareStatement("SELECT state_key, state_value FROM sb_missions_global").use { st ->
            st.executeQuery().use { rs ->
                val out = LinkedHashMap<String, String>()
                while (rs.next()) out[rs.getString(1)] = rs.getString(2) ?: ""
                out
            }
        }
    } ?: emptyMap()

    /** Troca o estado guardado inteiro por [state]. */
    fun saveGlobal(state: Map<String, String>) {
        run("save global mission state") { conn ->
            conn.prepareStatement("DELETE FROM sb_missions_global").use { it.executeUpdate() }
            conn.prepareStatement("INSERT INTO sb_missions_global (state_key, state_value) VALUES (?, ?)").use { st ->
                for ((key, value) in state) {
                    st.setString(1, key)
                    st.setString(2, value)
                    st.executeUpdate()
                }
            }
        }
    }

    /** Apaga a missão [key] de todos os jogadores, menos de [except] (quem venceu, que fica com o histórico). */
    fun deleteMissionForAll(key: String, except: UUID?) {
        run("delete global mission $key for everyone") { conn ->
            val sql = if (except == null) "DELETE FROM sb_missions WHERE mission_key = ?" else "DELETE FROM sb_missions WHERE mission_key = ? AND player_uuid <> ?"
            conn.prepareStatement(sql).use { st ->
                st.setString(1, key)
                if (except != null) st.setString(2, except.toString())
                st.executeUpdate()
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Failed to $what!", e)
        null
    }

    private fun encode(values: LongArray): String = values.joinToString(",")

    private fun decode(text: String?): LongArray =
        if (text.isNullOrBlank()) LongArray(0) else text.split(',').map { it.trim().toLongOrNull() ?: 0L }.toLongArray()

    fun loadAll(uuid: UUID): List<Row> = run("read missions for $uuid") { conn ->
        conn.prepareStatement("SELECT * FROM sb_missions WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs ->
                val out = ArrayList<Row>()
                while (rs.next()) {
                    val status = runCatching { Status.valueOf(rs.getString("status")) }.getOrDefault(Status.ACTIVE)
                    out.add(
                        Row(
                            uuid, rs.getString("mission_key"), status, rs.getString("period_key") ?: "", rs.getLong("started_at"),
                            rs.getLong("finished_at"), rs.getInt("completions"), decode(rs.getString("base")), decode(rs.getString("progress"))
                        )
                    )
                }
                out
            }
        }
    } ?: emptyList()

    /** Grava a linha (atualiza se existe, senão insere). Devolve false se o banco falhou. */
    fun save(row: Row): Boolean = run("save mission ${row.key} for ${row.uuid}") { conn ->
        val updated = conn.prepareStatement(
            "UPDATE sb_missions SET status = ?, period_key = ?, started_at = ?, finished_at = ?, completions = ?, base = ?, progress = ? WHERE player_uuid = ? AND mission_key = ?"
        ).use { st ->
            st.setString(1, row.status.name)
            st.setString(2, row.periodKey)
            st.setLong(3, row.startedAt)
            st.setLong(4, row.finishedAt)
            st.setInt(5, row.completions)
            st.setString(6, encode(row.base))
            st.setString(7, encode(row.value))
            st.setString(8, row.uuid.toString())
            st.setString(9, row.key)
            st.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement(
                "INSERT INTO sb_missions (player_uuid, mission_key, status, period_key, started_at, finished_at, completions, base, progress) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { st ->
                st.setString(1, row.uuid.toString())
                st.setString(2, row.key)
                st.setString(3, row.status.name)
                st.setString(4, row.periodKey)
                st.setLong(5, row.startedAt)
                st.setLong(6, row.finishedAt)
                st.setInt(7, row.completions)
                st.setString(8, encode(row.base))
                st.setString(9, encode(row.value))
                st.executeUpdate()
            }
        }
        row.dirty = false
        true
    } ?: false

    /** Apaga o registro de uma missão (ou de todas, se [key] é null) de um jogador. */
    fun delete(uuid: UUID, key: String?) {
        run("delete missions for $uuid") { conn ->
            val sql = if (key == null) "DELETE FROM sb_missions WHERE player_uuid = ?" else "DELETE FROM sb_missions WHERE player_uuid = ? AND mission_key = ?"
            conn.prepareStatement(sql).use { st ->
                st.setString(1, uuid.toString())
                if (key != null) st.setString(2, key)
                st.executeUpdate()
            }
        }
    }
}
