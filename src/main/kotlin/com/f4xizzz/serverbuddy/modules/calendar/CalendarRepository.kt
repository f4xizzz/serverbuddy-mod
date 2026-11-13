package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.database.DatabaseManager
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDate
import java.util.UUID

/** A player's login streak. [claimed] holds the streak days already claimed during this streak. */
class StreakRow(
    val uuid: UUID,
    var streak: Int = 0,
    var best: Int = 0,
    var lastDate: LocalDate? = null,
    val claimed: MutableSet<Int> = LinkedHashSet()
)

/** Tables `sb_calendar` (claimed days per calendar and period) and `sb_login_streak` in the ServerBuddy database. */
object CalendarRepository {
    private val log = LoggerFactory.getLogger("ServerBuddy/Calendar")

    fun createTables() {
        run("create the calendar tables") { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_calendar (player_uuid VARCHAR(36) NOT NULL, calendar VARCHAR(16) NOT NULL, " +
                        "period VARCHAR(16) NOT NULL, claimed TEXT NOT NULL, PRIMARY KEY (player_uuid, calendar, period))"
                )
                it.execute(
                    "CREATE TABLE IF NOT EXISTS sb_login_streak (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, streak INTEGER NOT NULL, " +
                        "best INTEGER NOT NULL, last_date VARCHAR(10) NOT NULL, claimed TEXT NOT NULL)"
                )
            }
        }
    }

    private fun <T> run(what: String, block: (Connection) -> T): T? = try {
        DatabaseManager.getConnection().use(block)
    } catch (e: SQLException) {
        log.error("Error trying to $what", e)
        null
    }

    private fun encode(days: Set<Int>): String = days.sorted().joinToString(",")

    private fun decode(text: String?): MutableSet<Int> =
        if (text.isNullOrBlank()) LinkedHashSet() else text.split(',').mapNotNull { it.trim().toIntOrNull() }.toCollection(LinkedHashSet())

    fun claimed(uuid: UUID, calendar: String, period: String): MutableSet<Int> = run("read the calendar of $uuid") { conn ->
        conn.prepareStatement("SELECT claimed FROM sb_calendar WHERE player_uuid = ? AND calendar = ? AND period = ?").use { st ->
            st.setString(1, uuid.toString())
            st.setString(2, calendar)
            st.setString(3, period)
            st.executeQuery().use { rs -> if (rs.next()) decode(rs.getString(1)) else LinkedHashSet() }
        }
    } ?: LinkedHashSet()

    fun saveClaimed(uuid: UUID, calendar: String, period: String, days: Set<Int>): Boolean = run("save the calendar of $uuid") { conn ->
        val updated = conn.prepareStatement("UPDATE sb_calendar SET claimed = ? WHERE player_uuid = ? AND calendar = ? AND period = ?").use { st ->
            st.setString(1, encode(days))
            st.setString(2, uuid.toString())
            st.setString(3, calendar)
            st.setString(4, period)
            st.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement("INSERT INTO sb_calendar (player_uuid, calendar, period, claimed) VALUES (?, ?, ?, ?)").use { st ->
                st.setString(1, uuid.toString())
                st.setString(2, calendar)
                st.setString(3, period)
                st.setString(4, encode(days))
                st.executeUpdate()
            }
        }
        true
    } ?: false

    fun loadStreak(uuid: UUID): StreakRow? = run("read the login streak of $uuid") { conn ->
        conn.prepareStatement("SELECT streak, best, last_date, claimed FROM sb_login_streak WHERE player_uuid = ?").use { st ->
            st.setString(1, uuid.toString())
            st.executeQuery().use { rs ->
                if (rs.next()) StreakRow(uuid, rs.getInt(1), rs.getInt(2), runCatching { LocalDate.parse(rs.getString(3)) }.getOrNull(), decode(rs.getString(4))) else null
            }
        }
    }

    fun saveStreak(row: StreakRow): Boolean = run("save the login streak of ${row.uuid}") { conn ->
        val date = row.lastDate?.toString() ?: ""
        val updated = conn.prepareStatement("UPDATE sb_login_streak SET streak = ?, best = ?, last_date = ?, claimed = ? WHERE player_uuid = ?").use { st ->
            st.setInt(1, row.streak)
            st.setInt(2, row.best)
            st.setString(3, date)
            st.setString(4, encode(row.claimed))
            st.setString(5, row.uuid.toString())
            st.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement("INSERT INTO sb_login_streak (player_uuid, streak, best, last_date, claimed) VALUES (?, ?, ?, ?, ?)").use { st ->
                st.setString(1, row.uuid.toString())
                st.setInt(2, row.streak)
                st.setInt(3, row.best)
                st.setString(4, date)
                st.setString(5, encode(row.claimed))
                st.executeUpdate()
            }
        }
        true
    } ?: false

    /** Forgets what [uuid] claimed in [calendar] ("monthly", "weekly") or the whole streak ("streak"). */
    fun reset(uuid: UUID, calendar: String): Boolean = run("reset the $calendar progress of $uuid") { conn ->
        val sql = if (calendar == "streak") "DELETE FROM sb_login_streak WHERE player_uuid = ?" else "DELETE FROM sb_calendar WHERE player_uuid = ? AND calendar = ?"
        conn.prepareStatement(sql).use { st ->
            st.setString(1, uuid.toString())
            if (calendar != "streak") st.setString(2, calendar)
            st.executeUpdate()
        }
        true
    } ?: false
}
