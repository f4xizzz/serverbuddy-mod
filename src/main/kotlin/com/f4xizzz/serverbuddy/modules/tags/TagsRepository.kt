package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

/** Table `sb_player_tags` in ServerBuddy's database: which tags the player has (won/bought) and which one is equipped. */
object TagsRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Tags")

    fun createTable() {
        val sql = "CREATE TABLE IF NOT EXISTS sb_player_tags (" +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "tag_id VARCHAR(64) NOT NULL, " +
            "owned INTEGER NOT NULL DEFAULT 0, " +
            "equipped INTEGER NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (player_uuid, tag_id))"
        run("create table sb_player_tags") { conn -> conn.createStatement().use { it.execute(sql) } }
    }

    private fun <T> run(what: String, default: T? = null, block: (java.sql.Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Error while trying to $what!", e)
        default
    }

    /** Tags the player won or bought. */
    fun owned(uuid: UUID): Set<String> = run("read the tags of $uuid", emptySet()) { conn ->
        conn.prepareStatement("SELECT tag_id FROM sb_player_tags WHERE player_uuid = ? AND owned = 1").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs ->
                val out = LinkedHashSet<String>()
                while (rs.next()) out.add(rs.getString(1))
                out
            }
        }
    } ?: emptySet()

    fun equipped(uuid: UUID): String? = run("read the equipped tag of $uuid") { conn ->
        conn.prepareStatement("SELECT tag_id FROM sb_player_tags WHERE player_uuid = ? AND equipped = 1").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    /** Gives the tag to the player. false if they already had it. */
    fun grant(uuid: UUID, tagId: String): Boolean = run("dar a tag $tagId a $uuid", false) { conn ->
        if (setOwned(conn, uuid, tagId, 1) > 0) true else false
    } ?: false

    /** Takes the tag ownership away. false if they did not have it. */
    fun revoke(uuid: UUID, tagId: String): Boolean = run("take the tag $tagId away from $uuid", false) { conn ->
        val changed = setOwned(conn, uuid, tagId, 0, onlyIfOwned = true)
        conn.prepareStatement("DELETE FROM sb_player_tags WHERE player_uuid = ? AND tag_id = ? AND owned = 0 AND equipped = 0").use { st ->
            st.setString(1, uuid.toString())
            st.setString(2, tagId)
            st.executeUpdate()
        }
        changed > 0
    } ?: false

    /** Marks [tagId] as the equipped one (and unmarks the others). null = none equipped. */
    fun setEquipped(uuid: UUID, tagId: String?) {
        run("equip the tag of $uuid") { conn ->
            conn.prepareStatement("UPDATE sb_player_tags SET equipped = 0 WHERE player_uuid = ?").use { st ->
                st.setString(1, uuid.toString())
                st.executeUpdate()
            }
            if (tagId != null) {
                val updated = conn.prepareStatement("UPDATE sb_player_tags SET equipped = 1 WHERE player_uuid = ? AND tag_id = ?").use { st ->
                    st.setString(1, uuid.toString())
                    st.setString(2, tagId)
                    st.executeUpdate()
                }
                if (updated == 0) {
                    conn.prepareStatement("INSERT INTO sb_player_tags (player_uuid, tag_id, owned, equipped) VALUES (?, ?, 0, 1)").use { st ->
                        st.setString(1, uuid.toString())
                        st.setString(2, tagId)
                        st.executeUpdate()
                    }
                }
            }
            // clear rows that say nothing anymore (no ownership and no equip)
            conn.prepareStatement("DELETE FROM sb_player_tags WHERE player_uuid = ? AND owned = 0 AND equipped = 0").use { st ->
                st.setString(1, uuid.toString())
                st.executeUpdate()
            }
        }
    }

    /** Removes every record of a tag deleted from the catalog. */
    fun deleteTag(tagId: String) {
        run("delete tag $tagId") { conn ->
            conn.prepareStatement("DELETE FROM sb_player_tags WHERE tag_id = ?").use { st ->
                st.setString(1, tagId)
                st.executeUpdate()
            }
        }
    }

    /** Affected rows: 1 if it really changed, 0 if it already was like that. */
    private fun setOwned(conn: java.sql.Connection, uuid: UUID, tagId: String, value: Int, onlyIfOwned: Boolean = false): Int {
        val updated = conn.prepareStatement("UPDATE sb_player_tags SET owned = ? WHERE player_uuid = ? AND tag_id = ? AND owned <> ?").use { st ->
            st.setInt(1, value)
            st.setString(2, uuid.toString())
            st.setString(3, tagId)
            st.setInt(4, value)
            st.executeUpdate()
        }
        if (updated > 0) return updated
        // the row already exists with the same value, or does not exist
        val exists = conn.prepareStatement("SELECT 1 FROM sb_player_tags WHERE player_uuid = ? AND tag_id = ?").use { st ->
            st.setString(1, uuid.toString())
            st.setString(2, tagId)
            st.executeQuery().use { it.next() }
        }
        if (exists || onlyIfOwned || value == 0) return 0
        return conn.prepareStatement("INSERT INTO sb_player_tags (player_uuid, tag_id, owned, equipped) VALUES (?, ?, 1, 0)").use { st ->
            st.setString(1, uuid.toString())
            st.setString(2, tagId)
            st.executeUpdate()
        }
    }
}
