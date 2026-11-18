package com.f4xizzz.serverbuddy.dashboard

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.Configurator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogBufferTest {
    private fun line(level: String, text: String, logger: String) = LogBuffer.Line(1_000L, level, text, logger)

    // ---------------------------------------------------------------- which loggers are ServerBuddy's

    @Test
    fun `the loggers of ServerBuddy are recognised and the others are not`() {
        for (name in listOf("ServerBuddy", "serverbuddy", "ServerBuddy/Trivia", "serverbuddy-dexrewards", "pokeeditor")) assertTrue(LogBuffer.isOwn(name), name)
        for (name in listOf("Minecraft", "Cobblemon", "net.minecraft.server.MinecraftServer", "LuckPerms", "NotServerBuddy", "")) assertFalse(LogBuffer.isOwn(name), name)
    }

    @Test
    fun `the module is what comes after the slash`() {
        assertEquals("Trivia", LogBuffer.moduleOf("ServerBuddy/Trivia"))
        assertEquals("BattlePass", LogBuffer.moduleOf("ServerBuddy/BattlePass"))
        assertEquals("core", LogBuffer.moduleOf("ServerBuddy"))
        assertEquals("core", LogBuffer.moduleOf("serverbuddy"))
        assertEquals("core", LogBuffer.moduleOf("serverbuddy-dexrewards"))
        assertEquals("PokeEditor", LogBuffer.moduleOf("pokeeditor"))
        assertEquals("core", LogBuffer.moduleOf("ServerBuddy/"))
        assertEquals("Trivia", line("INFO", "x", "ServerBuddy/Trivia").module)
        assertEquals(null, line("INFO", "x", "Minecraft").module)
    }

    // ---------------------------------------------------------------- the filter

    private val lines = listOf(
        line("INFO", "Server started", "Minecraft"),
        line("INFO", "Module 'trivia' started", "serverbuddy"),
        line("WARN", "Style 'chat' failed, using vanilla Minecraft formatting", "ServerBuddy/Essentials"),
        line("ERROR", "Could not save the quest", "ServerBuddy/Missions"),
        line("WARN", "Cobblemon spawn pool is empty", "Cobblemon"),
        line("DEBUG", "tick", "ServerBuddy/Essentials")
    )

    private fun pick(filter: LogBuffer.Filter) = lines.filter(filter::matches).map { it.text }

    @Test
    fun `no filter keeps every line`() {
        assertEquals(lines.size, lines.count(LogBuffer.Filter()::matches))
        assertEquals(lines.size, lines.count(LogBuffer.Filter(minLevel = "ALL")::matches))
        assertEquals(lines.size, lines.count(LogBuffer.Filter(minLevel = "")::matches))
    }

    @Test
    fun `only ServerBuddy keeps what its loggers wrote`() {
        assertEquals(
            listOf("Module 'trivia' started", "Style 'chat' failed, using vanilla Minecraft formatting", "Could not save the quest", "tick"),
            pick(LogBuffer.Filter(ownOnly = true))
        )
    }

    @Test
    fun `a module keeps only its own lines (capital letters do not matter) and the general loggers are core`() {
        assertEquals(listOf("Style 'chat' failed, using vanilla Minecraft formatting", "tick"), pick(LogBuffer.Filter(module = "essentials")))
        assertEquals(listOf("Could not save the quest"), pick(LogBuffer.Filter(module = "Missions")))
        assertEquals(listOf("Module 'trivia' started"), pick(LogBuffer.Filter(module = "core")))
        assertEquals(emptyList<String>(), pick(LogBuffer.Filter(module = "Cobblemon")))
    }

    @Test
    fun `the level keeps that level and the worse ones`() {
        assertEquals(
            listOf("Style 'chat' failed, using vanilla Minecraft formatting", "Could not save the quest", "Cobblemon spawn pool is empty"),
            pick(LogBuffer.Filter(minLevel = "WARN"))
        )
        assertEquals(listOf("Could not save the quest"), pick(LogBuffer.Filter(minLevel = "error")))
        assertEquals(5, pick(LogBuffer.Filter(minLevel = "INFO")).size)
    }

    @Test
    fun `the search looks in the text and in the logger name, ignoring capitals`() {
        assertEquals(listOf("Could not save the quest"), pick(LogBuffer.Filter(query = "QUEST")))
        assertEquals(listOf("Style 'chat' failed, using vanilla Minecraft formatting", "tick"), pick(LogBuffer.Filter(query = "essentials")))
        assertEquals(emptyList<String>(), pick(LogBuffer.Filter(query = "nothing like this")))
        assertEquals(lines.size, lines.count(LogBuffer.Filter(query = "   ")::matches))
    }

    @Test
    fun `the filters work together`() {
        assertEquals(
            listOf("Style 'chat' failed, using vanilla Minecraft formatting"),
            pick(LogBuffer.Filter(minLevel = "WARN", module = "Essentials", query = "chat"))
        )
        assertEquals(emptyList<String>(), pick(LogBuffer.Filter(minLevel = "ERROR", ownOnly = true, query = "Cobblemon")))
    }

    // ---------------------------------------------------------------- the real buffer, fed by log4j

    @Test
    fun `lines of ServerBuddy survive a flood of other lines, and the dashboard sees the module list`() {
        LogBuffer.install()
        Configurator.setRootLevel(Level.DEBUG)
        val ours = LogManager.getLogger("ServerBuddy/LogBufferTest")
        val other = LogManager.getLogger("SomeOtherMod")
        ours.warn("flood-ours-1")
        // more lines than the buffer of everything holds: the other lines push ours out of it
        repeat(LogBuffer.ALL_CAPACITY + 50) { other.info("flood $it") }
        ours.info("flood-ours-2")
        other.error("flood-other")

        val everything = LogBuffer.tail(LogBuffer.ALL_CAPACITY, LogBuffer.Filter(query = "flood-"))
        assertEquals(listOf("flood-ours-2", "flood-other"), everything.map { it.text }, "the old line fell out of the buffer of everything")

        val onlyOurs = LogBuffer.tail(100, LogBuffer.Filter(ownOnly = true, query = "flood-ours"))
        assertEquals(listOf("flood-ours-1", "flood-ours-2"), onlyOurs.map { it.text }, "but it is still in the buffer of ServerBuddy")
        assertEquals(listOf("WARN", "INFO"), onlyOurs.map { it.level })
        assertTrue(onlyOurs.all { it.own && it.module == "LogBufferTest" && it.logger == "ServerBuddy/LogBufferTest" })

        assertEquals(listOf("flood-ours-1"), LogBuffer.tail(100, LogBuffer.Filter(module = "logbuffertest", minLevel = "WARN")).map { it.text })
        assertTrue("LogBufferTest" in LogBuffer.modules())
        assertFalse(LogBuffer.modules().any { it.equals("SomeOtherMod", ignoreCase = true) })

        // the JSON the dashboard receives
        val json = LogBuffer.toJson(onlyOurs[0])
        assertEquals("ServerBuddy/LogBufferTest", json.get("logger").asString)
        assertTrue(json.get("own").asBoolean)
        assertEquals("LogBufferTest", json.get("module").asString)
        assertEquals("WARN", json.get("level").asString)
        assertFalse(LogBuffer.toJson(everything[1]).has("module"))
    }

    @Test
    fun `the exception that was thrown goes with the line and the count is limited`() {
        LogBuffer.install()
        Configurator.setRootLevel(Level.DEBUG)
        LogManager.getLogger("ServerBuddy/LogBufferTest").error("marker-thrown", IllegalStateException("boom"))
        val saved = LogBuffer.tail(5, LogBuffer.Filter(ownOnly = true, query = "marker-thrown")).single()
        assertTrue(saved.text.startsWith("marker-thrown"), saved.text)
        assertTrue(saved.text.contains("java.lang.IllegalStateException: boom"), saved.text)

        repeat(30) { LogManager.getLogger("ServerBuddy/LogBufferTest").info("marker-count $it") }
        assertEquals(10, LogBuffer.tail(10, LogBuffer.Filter(query = "marker-count")).size)
        assertEquals(1, LogBuffer.tail(0, LogBuffer.Filter(query = "marker-count")).size, "a count of zero is raised to one")
    }

    @Test
    fun `the live queue only fills while somebody watches and is sent in batches`() {
        LogBuffer.install()
        Configurator.setRootLevel(Level.DEBUG)
        val logger = LogManager.getLogger("ServerBuddy/LogBufferTest")
        LiveLogs.setActive(false)
        logger.info("live-before-watching")
        assertTrue(LiveLogs.drain().isEmpty(), "nothing is queued while nobody watches")

        LiveLogs.setActive(true)
        repeat(LiveLogs.MAX_BATCH + 5) { logger.info("live-line $it") }
        val first = LiveLogs.drain()
        assertEquals(LiveLogs.MAX_BATCH, first.size, "a batch has a limit")
        assertEquals("live-line 0", first.first().text)
        assertEquals(5, LiveLogs.drain().size)
        assertTrue(LiveLogs.drain().isEmpty())
        val payload = LiveLogs.toJson(first.take(2))
        assertEquals(2, payload.getAsJsonArray("lines").size())

        LiveLogs.setActive(false)
        logger.info("live-after-watching")
        assertTrue(LiveLogs.drain().isEmpty(), "and it stops when the last viewer leaves")
    }
}
