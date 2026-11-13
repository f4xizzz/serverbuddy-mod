package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

enum class CalendarView(val id: String) {
    MONTHLY("monthly"), WEEKLY("weekly"), STREAK("streak");

    companion object {
        fun parse(text: String?): CalendarView? = entries.firstOrNull { it.id.equals(text?.trim(), ignoreCase = true) }
    }
}

enum class CellState { CLAIMED, CLAIMABLE, MISSED, LOCKED, EMPTY }

/** One day of a calendar as the menu shows it. [number] is what the player sees (day of the month, week slot or streak day). */
class Cell(val number: Int, val date: LocalDate?, val weekday: DayOfWeek?, val day: DayConfig?, val rewards: List<Reward>, val state: CellState)

enum class ClaimResult { OK, ALREADY, NOT_AVAILABLE, MISSED, EMPTY, OFF }

/**
 * The monthly and weekly calendars and the login streak: which cells exist today, what state each one is in, and claiming. The
 * calendars are computed from the date (so a month or week that rolls over starts clean) and only the claimed days are stored.
 */
object CalendarService {
    const val FOLDER = "Calendar"
    private val log = LoggerFactory.getLogger("ServerBuddy/Calendar")

    @Volatile var config = CalendarConfig()
        private set
    @Volatile var monthly = CalendarFile.monthly()
        private set
    @Volatile var weekly = CalendarFile.weekly()
        private set
    @Volatile var streak = StreakFile()
        private set

    @Volatile var ready = false

    /** Set by `/calendar admin clock` to pretend it is another date. */
    @Volatile var clockOverride: LocalDate? = null

    fun load() {
        config = ConfigFiles.load(FOLDER, "config.json", CalendarConfig::class.java) { CalendarConfig() }
        monthly = ConfigFiles.load(FOLDER, "monthly.json", CalendarFile::class.java) { CalendarFile.monthly() }
        weekly = ConfigFiles.load(FOLDER, "weekly.json", CalendarFile::class.java) { CalendarFile.weekly() }
        streak = ConfigFiles.load(FOLDER, "streak.json", StreakFile::class.java) { StreakFile() }
        for ((name, days) in listOf("monthly" to monthly.days, "weekly" to weekly.days, "streak" to streak.days)) {
            for ((key, day) in days) RewardService.check(day.rewards, "Calendar/$name.json day $key")
        }
        RewardService.check(monthly.defaultRewards, "Calendar/monthly.json default_rewards")
        RewardService.check(weekly.defaultRewards, "Calendar/weekly.json default_rewards")
    }

    // ---------------------------------------------------------------- time

    private fun zone(): ZoneId = try {
        if (config.timeZone.isBlank()) ZoneId.systemDefault() else ZoneId.of(config.timeZone.trim())
    } catch (e: Exception) {
        log.warn("Unknown time_zone '{}' in Calendar/config.json; using the server's time zone.", config.timeZone)
        ZoneId.systemDefault()
    }

    fun today(): LocalDate = clockOverride ?: LocalDate.now(zone())

    private fun weekStart(): DayOfWeek = runCatching { DayOfWeek.valueOf(config.weekStart.trim().uppercase(Locale.ROOT)) }.getOrDefault(DayOfWeek.MONDAY)

    fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun weekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** The week number the weekly period is named after (changes on [weekStart]). */
    fun weekNumber(date: LocalDate): Int = date.get(WeekFields.of(weekStart(), 1).weekOfWeekBasedYear())

    private fun periodKey(view: CalendarView, date: LocalDate): String = when (view) {
        CalendarView.MONTHLY -> "%04d-%02d".format(date.year, date.monthValue)
        CalendarView.WEEKLY -> "%04d-W%02d".format(date.get(WeekFields.of(weekStart(), 1).weekBasedYear()), weekNumber(date))
        CalendarView.STREAK -> "streak"
    }

    fun file(view: CalendarView): CalendarFile? = when (view) {
        CalendarView.MONTHLY -> monthly
        CalendarView.WEEKLY -> weekly
        CalendarView.STREAK -> null
    }

    fun isOn(view: CalendarView): Boolean = config.enabled && when (view) {
        CalendarView.MONTHLY -> monthly.enabled
        CalendarView.WEEKLY -> weekly.enabled
        CalendarView.STREAK -> streak.enabled
    }

    fun menu(view: CalendarView): CalendarMenu = when (view) {
        CalendarView.MONTHLY -> monthly.menu
        CalendarView.WEEKLY -> weekly.menu
        CalendarView.STREAK -> streak.menu
    }

    // ---------------------------------------------------------------- monthly and weekly

    private fun dates(view: CalendarView, today: LocalDate): List<LocalDate> = when (view) {
        CalendarView.MONTHLY -> (1..today.lengthOfMonth()).map { today.withDayOfMonth(it) }
        else -> {
            val start = today.with(TemporalAdjusters.previousOrSame(weekStart()))
            (0 until 7).map { start.plusDays(it.toLong()) }
        }
    }

    private fun periodCells(uuid: UUID, view: CalendarView): List<Cell> {
        val file = file(view) ?: return emptyList()
        val today = today()
        val claimed = CalendarRepository.claimed(uuid, view.id, periodKey(view, today))
        return dates(view, today).mapIndexed { index, date ->
            val number = if (view == CalendarView.MONTHLY) date.dayOfMonth else index + 1
            val key = if (view == CalendarView.MONTHLY) date.dayOfMonth.toString() else date.dayOfWeek.name.lowercase(Locale.ROOT)
            val day = file.days[key]
            val rewards = day?.rewards ?: file.defaultRewards
            val state = when {
                number in claimed -> CellState.CLAIMED
                rewards.isEmpty() -> CellState.EMPTY
                date == today -> CellState.CLAIMABLE
                date.isAfter(today) -> CellState.LOCKED
                file.claimMode.equals("catch_up", ignoreCase = true) -> CellState.CLAIMABLE
                else -> CellState.MISSED
            }
            Cell(number, date, date.dayOfWeek, day, rewards, state)
        }
    }

    // ---------------------------------------------------------------- streak

    /** The streak days the menu shows: the current cycle (or days 1..N without a cycle). */
    private fun streakWindow(row: StreakRow): IntRange {
        val size = streakCycleSize()
        if (size <= 0) return 1..0
        if (!streak.cycle) return 1..size
        val start = ((row.streak.coerceAtLeast(1) - 1) / size) * size + 1
        return start until start + size
    }

    private fun streakCycleSize(): Int = streak.days.keys.mapNotNull { it.trim().toIntOrNull() }.maxOrNull() ?: 0

    private fun streakDay(day: Int): DayConfig? {
        val size = streakCycleSize()
        if (size <= 0) return null
        val key = if (streak.cycle) ((day - 1) % size) + 1 else day
        return streak.days[key.toString()]
    }

    private fun streakCells(uuid: UUID): List<Cell> {
        val row = CalendarRepository.loadStreak(uuid) ?: StreakRow(uuid)
        return streakWindow(row).map { day ->
            val config = streakDay(day)
            val rewards = config?.rewards.orEmpty()
            val state = when {
                day in row.claimed -> CellState.CLAIMED
                rewards.isEmpty() -> CellState.EMPTY
                day <= row.streak -> CellState.CLAIMABLE
                else -> CellState.LOCKED
            }
            Cell(day, null, null, config, rewards, state)
        }
    }

    fun streakOf(uuid: UUID): StreakRow = CalendarRepository.loadStreak(uuid) ?: StreakRow(uuid)

    // ---------------------------------------------------------------- shared

    fun cells(uuid: UUID, view: CalendarView): List<Cell> = if (view == CalendarView.STREAK) streakCells(uuid) else periodCells(uuid, view)

    fun claimableCount(uuid: UUID, view: CalendarView): Int = if (!isOn(view)) 0 else cells(uuid, view).count { it.state == CellState.CLAIMABLE }

    fun totalClaimable(uuid: UUID): Int = CalendarView.entries.sumOf { claimableCount(uuid, it) }

    private fun vars(view: CalendarView, cell: Cell): Map<String, Any?> = mapOf(
        "day" to cell.number, "date" to cell.date?.toString().orEmpty(), "weekday" to (cell.weekday?.let { weekdayName(it) } ?: ""),
        "label" to (cell.day?.label ?: ""), "view" to view.id
    )

    /** Claims the cell numbered [number]. [silent] skips the chat message (used by "claim all"). */
    fun claim(player: ServerPlayer, view: CalendarView, number: Int, silent: Boolean = false): ClaimResult {
        if (!isOn(view)) return ClaimResult.OFF
        val cell = cells(player.uuid, view).firstOrNull { it.number == number } ?: return ClaimResult.NOT_AVAILABLE
        val result = when (cell.state) {
            CellState.CLAIMABLE -> ClaimResult.OK
            CellState.CLAIMED -> ClaimResult.ALREADY
            CellState.MISSED -> ClaimResult.MISSED
            CellState.EMPTY -> ClaimResult.EMPTY
            CellState.LOCKED -> ClaimResult.NOT_AVAILABLE
        }
        if (result != ClaimResult.OK) return result
        // save first: a crash after giving must not let the player claim the same day twice
        if (!markClaimed(player.uuid, view, number)) return ClaimResult.NOT_AVAILABLE
        val given = RewardService.give(player, cell.rewards, vars(view, cell) + ("streak" to streakOf(player.uuid).streak), "calendar:${view.id}")
        if (!silent) {
            val text = RewardService.describeAllText(given).ifBlank { "-" }
            Texts.send(player, config.messages.rewardClaimed, vars(view, cell) + ("rewards" to text))
        }
        return ClaimResult.OK
    }

    private fun markClaimed(uuid: UUID, view: CalendarView, number: Int): Boolean {
        if (view == CalendarView.STREAK) {
            val row = streakOf(uuid)
            row.claimed.add(number)
            return CalendarRepository.saveStreak(row)
        }
        val period = periodKey(view, today())
        val claimed = CalendarRepository.claimed(uuid, view.id, period)
        claimed.add(number)
        return CalendarRepository.saveClaimed(uuid, view.id, period, claimed)
    }

    /** Claims every ready cell of [view]. Returns how many were claimed. */
    fun claimAll(player: ServerPlayer, view: CalendarView): Int {
        var count = 0
        for (cell in cells(player.uuid, view).filter { it.state == CellState.CLAIMABLE }) {
            if (claim(player, view, cell.number, silent = true) == ClaimResult.OK) count++
        }
        return count
    }

    // ---------------------------------------------------------------- login streak

    /** Called when a player joins: grows or restarts the streak for today. Returns the row after the update. */
    fun onJoin(player: ServerPlayer) {
        if (!isOn(CalendarView.STREAK)) return
        val today = today()
        val row = CalendarRepository.loadStreak(player.uuid) ?: StreakRow(player.uuid)
        val last = row.lastDate
        if (last == today) return
        val gap = if (last == null) null else ChronoUnit.DAYS.between(last, today)
        val old = row.streak
        val continued = gap != null && gap in 1..(1L + streak.graceDays.coerceAtLeast(0))
        if (continued) {
            row.streak = old + 1
        } else {
            row.streak = 1
            row.claimed.clear()
        }
        row.best = maxOf(row.best, row.streak)
        row.lastDate = today
        CalendarRepository.saveStreak(row)
        val vars = mapOf("streak" to row.streak, "best" to row.best, "old" to old)
        when {
            continued -> Texts.send(player, config.messages.streakContinued, vars)
            old > 1 && gap != null -> Texts.send(player, config.messages.streakLost, vars)
            else -> Texts.send(player, config.messages.streakStarted, vars)
        }
        if (streak.autoClaim) claimAll(player, CalendarView.STREAK)
    }

    /** Staff: sets the streak (and counts today as logged in). */
    fun setStreak(uuid: UUID, days: Int) {
        val row = streakOf(uuid)
        row.streak = days.coerceAtLeast(0)
        row.best = maxOf(row.best, row.streak)
        row.lastDate = today()
        row.claimed.clear()
        CalendarRepository.saveStreak(row)
    }

    fun reset(uuid: UUID, which: String): Boolean = CalendarRepository.reset(uuid, which)
}
