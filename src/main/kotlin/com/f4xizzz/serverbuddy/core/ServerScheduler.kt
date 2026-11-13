package com.f4xizzz.serverbuddy.core

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Agenda tarefas para daqui a alguns ticks **na thread do servidor** (a segura para mexer em jogadores e mundo).
 * Um tick são 50 ms; 20 ticks = 1 segundo.
 */
object ServerScheduler {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    private class Task(var remaining: Int, val action: () -> Unit)

    private val tasks = ConcurrentLinkedQueue<Task>()

    @Volatile
    private var started = false

    fun init() {
        if (started) return
        started = true
        ServerTickEvents.END_SERVER_TICK.register { tick() }
    }

    /** Roda [action] daqui a [ticks] ticks (no mínimo 1). */
    @JvmStatic
    fun later(ticks: Int, action: () -> Unit) {
        tasks.add(Task(ticks.coerceAtLeast(1), action))
    }

    private fun tick() {
        if (tasks.isEmpty()) return
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            task.remaining--
            if (task.remaining <= 0) {
                iterator.remove()
                try {
                    task.action()
                } catch (e: Throwable) {
                    LOGGER.error("Error in a scheduled task", e)
                }
            }
        }
    }
}
