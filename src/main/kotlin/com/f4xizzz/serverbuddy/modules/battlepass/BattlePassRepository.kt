package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** O passe de um jogador numa temporada. */
class PlayerPass(
    val uuid: UUID,
    val season: Int,
    var level: Int = 1,
    var xp: Long = 0,
    var premium: Boolean = false,
    val freeClaimed: MutableSet<Int> = LinkedHashSet(),
    val premiumClaimed: MutableSet<Int> = LinkedHashSet()
) {
    @Volatile
    var dirty = false
}

/** Tabela `sb_battlepass` no banco do ServerBuddy (uma linha por jogador por temporada). */
object BattlePassRepository {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/BattlePass")

    fun createTable() {
        val sql = "CREATE TABLE IF NOT EXISTS sb_battlepass (" +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "season INTEGER NOT NULL, " +
            "level INTEGER NOT NULL DEFAULT 1, " +
            "xp BIGINT NOT NULL DEFAULT 0, " +
            "premium INTEGER NOT NULL DEFAULT 0, " +
            "claimed_free TEXT NOT NULL, " +
            "claimed_premium TEXT NOT NULL, " +
            "PRIMARY KEY (player_uuid, season))"
        run("create table sb_battlepass") { conn -> conn.createStatement().use { it.execute(sql) } }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        LOGGER.error("Failed to $what!", e)
        null
    }

    @JvmStatic
    fun encode(levels: Set<Int>): String = levels.sorted().joinToString(",")

    @JvmStatic
    fun decode(text: String?): MutableSet<Int> =
        if (text.isNullOrBlank()) LinkedHashSet() else text.split(',').mapNotNull { it.trim().toIntOrNull() }.toCollection(LinkedHashSet())

    fun load(uuid: UUID, season: Int): PlayerPass? = run("read battle pass for $uuid") { conn ->
        conn.prepareStatement("SELECT * FROM sb_battlepass WHERE player_uuid = ? AND season = ?").use { st ->
            st.setString(1, uuid.toString())
            st.setInt(2, season)
            st.executeQuery().use { rs ->
                if (rs.next()) {
                    PlayerPass(uuid, season, rs.getInt("level"), rs.getLong("xp"), rs.getInt("premium") == 1, decode(rs.getString("claimed_free")), decode(rs.getString("claimed_premium")))
                } else {
                    null
                }
            }
        }
    }

    fun save(pass: PlayerPass): Boolean = run("save battle pass for ${pass.uuid}") { conn ->
        val updated = conn.prepareStatement("UPDATE sb_battlepass SET level = ?, xp = ?, premium = ?, claimed_free = ?, claimed_premium = ? WHERE player_uuid = ? AND season = ?").use { st ->
            st.setInt(1, pass.level)
            st.setLong(2, pass.xp)
            st.setInt(3, if (pass.premium) 1 else 0)
            st.setString(4, encode(pass.freeClaimed))
            st.setString(5, encode(pass.premiumClaimed))
            st.setString(6, pass.uuid.toString())
            st.setInt(7, pass.season)
            st.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement("INSERT INTO sb_battlepass (player_uuid, season, level, xp, premium, claimed_free, claimed_premium) VALUES (?, ?, ?, ?, ?, ?, ?)").use { st ->
                st.setString(1, pass.uuid.toString())
                st.setInt(2, pass.season)
                st.setInt(3, pass.level)
                st.setLong(4, pass.xp)
                st.setInt(5, if (pass.premium) 1 else 0)
                st.setString(6, encode(pass.freeClaimed))
                st.setString(7, encode(pass.premiumClaimed))
                st.executeUpdate()
            }
        }
        pass.dirty = false
        true
    } ?: false

    /** Quantos jogadores têm registro na temporada (para o /battlepass season info). */
    fun countPlayers(season: Int): Int = run("count battle pass players") { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM sb_battlepass WHERE season = ?").use { st ->
            st.setInt(1, season)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    } ?: 0
}
