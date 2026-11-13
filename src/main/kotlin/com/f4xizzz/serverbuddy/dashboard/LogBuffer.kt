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
 */
class LogBuffer private constructor() : AbstractAppender("serverbuddyLogBuffer", null, null, true, Property.EMPTY_ARRAY) {
    class Line(val ts: Long, val level: String, val text: String)

    override fun append(event: LogEvent) {
        try {
            val message = event.message.formattedMessage ?: return
            if (message.isEmpty()) return
            val line = Line(event.timeMillis, event.level.name(), message.take(2000))
            synchronized(lines) {
                lines.addLast(line)
                while (lines.size > CAPACITY) lines.removeFirst()
            }
            for (listener in listeners) listener(line)
        } catch (e: Exception) {
            // a logging problem must never reach the server
        }
    }

    companion object {
        private const val CAPACITY = 1000
        private val lines = ArrayDeque<Line>()

        /** Called for each new line (the live logs). */
        val listeners = java.util.concurrent.CopyOnWriteArrayList<(Line) -> Unit>()

        private var instance: LogBuffer? = null

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

        /** The last [count] lines at [minLevel] or above ("WARN" keeps WARN and ERROR). */
        fun tail(count: Int, minLevel: String?): JsonArray {
            val minimum = minLevel?.let { Level.toLevel(it, Level.INFO) }
            val picked = synchronized(lines) { lines.toList() }
                .filter { minimum == null || Level.toLevel(it.level).isMoreSpecificThan(minimum) }
                .takeLast(count.coerceIn(1, CAPACITY))
            return JsonArray().also { array -> picked.forEach { array.add(toJson(it)) } }
        }

        fun toJson(line: Line): JsonObject = JsonObject().apply {
            addProperty("ts", line.ts)
            addProperty("level", line.level)
            addProperty("text", line.text)
        }
    }
}
