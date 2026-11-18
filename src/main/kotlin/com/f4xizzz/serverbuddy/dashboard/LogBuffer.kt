package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

/**
 * Keeps the last console lines in memory for the dashboard's "recent logs" and "live logs". Installed once, early, so the lines
 * from the boot are there when somebody connects.
 *
 * Every line remembers the name of the logger that wrote it, so the dashboard can show only what ServerBuddy wrote ("ServerBuddy",
 * "serverbuddy" and "ServerBuddy/<Module>") or a single module. ServerBuddy's own lines also go to a second, smaller buffer: with
 * the server busy (Cobblemon logs a lot) the last [ALL_CAPACITY] lines of everything may hold no ServerBuddy line at all.
 */
class LogBuffer private constructor() : AbstractAppender("serverbuddyLogBuffer", null, null, true, Property.EMPTY_ARRAY) {
    /** One console line. [logger] is the full logger name ("ServerBuddy/Trivia"); [module] is "Trivia" (or "core") for ServerBuddy's own lines. */
    class Line(val ts: Long, val level: String, val text: String, val logger: String) {
        val own: Boolean = isOwn(logger)
        val module: String? = if (own) moduleOf(logger) else null
    }

    override fun append(event: LogEvent) {
        try {
            var message = event.message.formattedMessage ?: return
            if (message.isEmpty()) return
            // an error with a stack trace: keep the first line of what was thrown (the full trace stays in the server console)
            event.thrown?.let { message += "\n    ${it}" }
            val line = Line(event.timeMillis, event.level.name(), ConfigSecrets.redactKnown(message).take(MAX_TEXT), event.loggerName ?: "")
            synchronized(lock) {
                all.addLast(line)
                while (all.size > ALL_CAPACITY) all.removeFirst()
                if (line.own) {
                    own.addLast(line)
                    while (own.size > OWN_CAPACITY) own.removeFirst()
                }
            }
            for (listener in listeners) listener(line)
        } catch (e: Exception) {
            // a logging problem must never reach the server
        }
    }

    /** What the dashboard asks for in `logs.tail`. Every field is optional. */
    class Filter(
        /** "WARN" keeps WARN and ERROR; null or "ALL" keeps everything. */
        val minLevel: String? = null,
        /** true = only the lines ServerBuddy wrote. */
        val ownOnly: Boolean = false,
        /** A ServerBuddy module ("Trivia", or "core" for the general loggers); implies [ownOnly]. */
        val module: String? = null,
        /** Plain text, case-insensitive; matched in the line text and in the logger name. */
        val query: String? = null
    ) {
        private val minimum: Level? = minLevel?.takeIf { it.isNotBlank() && !it.equals("ALL", ignoreCase = true) }?.let { Level.toLevel(it, Level.INFO) }
        private val needle: String? = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

        fun matches(line: Line): Boolean {
            if ((ownOnly || module != null) && !line.own) return false
            if (module != null && !line.module.equals(module, ignoreCase = true)) return false
            if (minimum != null && !Level.toLevel(line.level).isMoreSpecificThan(minimum)) return false
            if (needle != null && !ConfigSecrets.redactEverywhere(line.text).lowercase().contains(needle) && !line.logger.lowercase().contains(needle)) return false
            return true
        }
    }

    companion object {
        const val ALL_CAPACITY = 2000
        const val OWN_CAPACITY = 1000
        private const val MAX_TEXT = 2000

        private val lock = Any()
        private val all = ArrayDeque<Line>()
        private val own = ArrayDeque<Line>()

        /** Called for each new line (the live logs). */
        val listeners = java.util.concurrent.CopyOnWriteArrayList<(Line) -> Unit>()

        private var instance: LogBuffer? = null

        /** The loggers of ServerBuddy: "ServerBuddy", "serverbuddy", "ServerBuddy/<Module>", "serverbuddy-dexrewards" and the ported editor. */
        @JvmStatic
        fun isOwn(logger: String): Boolean = logger.startsWith("serverbuddy", ignoreCase = true) || logger == "pokeeditor"

        /** "ServerBuddy/Trivia" is the module "Trivia"; the general loggers ("ServerBuddy", "serverbuddy") are "core". */
        @JvmStatic
        fun moduleOf(logger: String): String {
            val slash = logger.indexOf('/')
            return if (slash >= 0 && slash < logger.length - 1) logger.substring(slash + 1) else if (logger == "pokeeditor") "PokeEditor" else "core"
        }

        @JvmStatic
        @Synchronized
        fun install() {
            if (instance != null) return
            val context = LogManager.getContext(false) as LoggerContext
            val buffer = LogBuffer()
            buffer.start()
            context.configuration.rootLogger.addAppender(buffer, null as Level?, null)
            context.updateLoggers()
            instance = buffer
        }

        /** The last [count] lines that pass [filter], oldest first. Only ServerBuddy's lines come from the small buffer. */
        fun tail(count: Int, filter: Filter = Filter()): List<Line> {
            val source = synchronized(lock) { if (filter.ownOnly || filter.module != null) own.toList() else all.toList() }
            return source.filter(filter::matches).takeLast(count.coerceIn(1, OWN_CAPACITY))
        }

        /** The same as [tail], as the JSON array of `logs.tail`. */
        fun tailJson(count: Int, minLevel: String?): JsonArray = toJson(tail(count, Filter(minLevel = minLevel)))

        /** The ServerBuddy modules that wrote something that is still in the buffer (for the module filter of the dashboard). */
        fun modules(): List<String> = synchronized(lock) { own.mapNotNull { it.module } }.distinct().sortedBy { it.lowercase() }

        fun toJson(lines: List<Line>): JsonArray = JsonArray().also { array -> lines.forEach { array.add(toJson(it)) } }

        fun toJson(line: Line): JsonObject = JsonObject().apply {
            addProperty("ts", line.ts)
            addProperty("level", line.level)
            addProperty("text", ConfigSecrets.redactEverywhere(line.text)) // a line can quote a webhook address or a login: the site never gets it
            addProperty("logger", line.logger)
            addProperty("own", line.own)
            line.module?.let { addProperty("module", it) }
        }
    }
}
