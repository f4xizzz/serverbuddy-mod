package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The live logs (`logs.live`). The site tells the mod when somebody is watching ([setActive]); only then are the console lines queued, and
 * [DashboardClient] sends them in small batches (every half second, at most [MAX_BATCH] lines), so a server that logs a lot does not
 * flood the connection. When nobody watches, nothing is queued.
 */
object LiveLogs {
    const val TOPIC = "logs.live"
    const val MAX_BATCH = 200
    private const val MAX_PENDING = 2000

    private val pending = ConcurrentLinkedQueue<LogBuffer.Line>()
    private val size = AtomicInteger()
    private val listener: (LogBuffer.Line) -> Unit = { line ->
        if (size.get() < MAX_PENDING) {
            pending.add(line)
            size.incrementAndGet()
        }
    }

    @Volatile
    var active = false
        private set

    @JvmStatic
    @Synchronized
    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        if (on) {
            LogBuffer.listeners.add(listener)
        } else {
            LogBuffer.listeners.remove(listener)
            pending.clear()
            size.set(0)
        }
    }

    /** The next batch of lines to send (empty when there is nothing new). */
    fun drain(): List<LogBuffer.Line> {
        val batch = ArrayList<LogBuffer.Line>()
        while (batch.size < MAX_BATCH) {
            val line = pending.poll() ?: break
            size.decrementAndGet()
            batch += line
        }
        return batch
    }

    /** The `event` payload of a batch. */
    fun toJson(batch: List<LogBuffer.Line>): JsonObject = JsonObject().apply { add("lines", LogBuffer.toJson(batch)) }
}
