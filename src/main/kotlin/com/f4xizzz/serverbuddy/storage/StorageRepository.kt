package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

/** Tabela `storage_entries` no banco do ServerBuddy (SQLite ou MySQL). Todas as operações são imediatas (sem fila). */
object StorageRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Storage")

    class Row(
        val id: String,
        val owner: UUID,
        val type: String,
        val data: String,
        val source: String?,
        val createdAt: Long
    )

    fun createTable() {
        val sql = "CREATE TABLE IF NOT EXISTS storage_entries (" +
            "id VARCHAR(36) NOT NULL, " +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "type VARCHAR(16) NOT NULL, " +
            "data MEDIUMTEXT NOT NULL, " +
            "source VARCHAR(128), " +
            "created_at BIGINT NOT NULL, " +
            "PRIMARY KEY (player_uuid, id))"
        try {
            DatabaseManager.getConnection().use { conn -> conn.createStatement().use { it.execute(sql) } }
        } catch (e: SQLException) {
            LOGGER.error("Error creating the storage_entries table!", e)
        }
    }

    fun insert(row: Row): Boolean {
        val sql = "INSERT INTO storage_entries (id, player_uuid, type, data, source, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, row.id)
                    st.setString(2, row.owner.toString())
                    st.setString(3, row.type)
                    st.setString(4, row.data)
                    st.setString(5, row.source)
                    st.setLong(6, row.createdAt)
                    st.executeUpdate() == 1
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error saving the reward ${row.id} of ${row.owner}!", e)
            false
        }
    }

    /** Recompensas do jogador, das mais antigas para as mais novas. */
    fun list(owner: UUID): List<Row> {
        val sql = "SELECT id, type, data, source, created_at FROM storage_entries WHERE player_uuid = ? ORDER BY created_at ASC, id ASC"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, owner.toString())
                    st.executeQuery().use { rs ->
                        val rows = ArrayList<Row>()
                        while (rs.next()) {
                            rows.add(Row(rs.getString("id"), owner, rs.getString("type"), rs.getString("data"), rs.getString("source"), rs.getLong("created_at")))
                        }
                        rows
                    }
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error reading the storage of $owner!", e)
            emptyList()
        }
    }

    fun countByType(owner: UUID): Map<String, Int> {
        val sql = "SELECT type, COUNT(*) AS total FROM storage_entries WHERE player_uuid = ? GROUP BY type"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, owner.toString())
                    st.executeQuery().use { rs ->
                        val result = HashMap<String, Int>()
                        while (rs.next()) result[rs.getString("type")] = rs.getInt("total")
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error counting the storage of $owner!", e)
            emptyMap()
        }
    }

    /** Apaga uma recompensa. Só devolve true se ela existia (evita resgatar duas vezes). */
    fun delete(id: String, owner: UUID): Boolean {
        val sql = "DELETE FROM storage_entries WHERE player_uuid = ? AND id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, owner.toString())
                    st.setString(2, id)
                    st.executeUpdate() == 1
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error deleting the reward $id of $owner!", e)
            false
        }
    }

    fun updateData(id: String, owner: UUID, data: String): Boolean {
        val sql = "UPDATE storage_entries SET data = ? WHERE player_uuid = ? AND id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, data)
                    st.setString(2, owner.toString())
                    st.setString(3, id)
                    st.executeUpdate() == 1
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error updating the reward $id of $owner!", e)
            false
        }
    }

    fun deleteAll(owner: UUID, type: String?): Int {
        val sql = if (type == null) "DELETE FROM storage_entries WHERE player_uuid = ?"
        else "DELETE FROM storage_entries WHERE player_uuid = ? AND type = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { st ->
                    st.setString(1, owner.toString())
                    if (type != null) st.setString(2, type)
                    st.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("Error clearing the storage of $owner!", e)
            0
        }
    }
}
