package com.f4xizzz.serverbuddy.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The chat stages run in a fixed order whichever module registered first. A bug here is "answering the trivia in chat does nothing":
 * if the chat guard or the chat format runs before the trivia and blocks the message, the trivia never sees it.
 */
class ChatPipelineTest {
    // The same four stages as ChatPipeline.Stage, in the same order, so the test does not need a Minecraft server.
    private enum class Stage { GATE, ANSWER, GUARD, FORMAT }

    private class Chat(val text: String) {
        val calls = ArrayList<Stage>()
        var answered = false
    }

    /** Registers every handler in [order] (the order the modules start in) and returns the chain. */
    private fun chain(order: List<Stage>, block: (Stage, Chat) -> Boolean = { _, _ -> true }): StageChain<Stage, Chat> {
        val chain = StageChain<Stage, Chat>()
        for (stage in order) chain.register(stage) { chat -> chat.calls += stage; block(stage, chat) }
        return chain
    }

    private fun permutations(items: List<Stage>): List<List<Stage>> =
        if (items.size <= 1) listOf(items) else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }

    @Test
    fun `the stages run in the same order whichever module registers first`() {
        val orders = permutations(Stage.entries)
        assertEquals(24, orders.size)
        for (order in orders) {
            val chat = Chat("4")
            assertTrue(chain(order).run(chat), "registered as $order")
            assertEquals(listOf(Stage.GATE, Stage.ANSWER, Stage.GUARD, Stage.FORMAT), chat.calls, "registered as $order")
        }
    }

    @Test
    fun `the trivia sees the message before the guard and the format, in both registration orders`() {
        for (order in listOf(listOf(Stage.GUARD, Stage.FORMAT, Stage.ANSWER), listOf(Stage.ANSWER, Stage.GUARD, Stage.FORMAT))) {
            // the guard blocks anything that is not an answer (the "repeated message" case) and the format would cancel the vanilla line
            val chain = chain(order) { stage, chat ->
                when (stage) {
                    Stage.ANSWER -> { chat.answered = chat.text == "pikachu"; true }
                    Stage.GUARD -> chat.answered
                    else -> true
                }
            }
            val answer = Chat("pikachu")
            assertTrue(chain.run(answer), "the answer goes through the guard (registered as $order)")
            assertEquals(listOf(Stage.ANSWER, Stage.GUARD, Stage.FORMAT), answer.calls)

            val repeated = Chat("pikachu again")
            assertFalse(chain.run(repeated), "a repeated chat line is still blocked (registered as $order)")
            assertEquals(listOf(Stage.ANSWER, Stage.GUARD), repeated.calls, "and the format never sees it")
        }
    }

    @Test
    fun `the first handler that blocks stops the rest`() {
        val chat = Chat("x")
        assertFalse(chain(Stage.entries) { stage, _ -> stage != Stage.ANSWER }.run(chat))
        assertEquals(listOf(Stage.GATE, Stage.ANSWER), chat.calls)
    }

    @Test
    fun `the two Fabric phases split the stages the way the pipeline promises`() {
        val before = Chat("a")
        val chain = chain(Stage.entries)
        assertTrue(chain.run(before) { it != Stage.FORMAT })
        assertEquals(listOf(Stage.GATE, Stage.ANSWER, Stage.GUARD), before.calls)
        val after = Chat("a")
        assertTrue(chain.run(after) { it == Stage.FORMAT })
        assertEquals(listOf(Stage.FORMAT), after.calls)
    }

    @Test
    fun `handlers of the same stage keep their registration order`() {
        val seen = ArrayList<String>()
        val chain = StageChain<Stage, Chat>()
        chain.register(Stage.GUARD) { seen += "guard-1"; true }
        chain.register(Stage.GATE) { seen += "gate"; true }
        chain.register(Stage.GUARD) { seen += "guard-2"; true }
        chain.run(Chat("x"))
        assertEquals(listOf("gate", "guard-1", "guard-2"), seen)
    }

    @Test
    fun `a handler that throws does not silence the chat`() {
        val failures = ArrayList<Stage>()
        val chain = StageChain<Stage, Chat>({ stage, _ -> failures += stage })
        chain.register(Stage.ANSWER) { throw IllegalStateException("bug in a module") }
        val reached = ArrayList<Stage>()
        chain.register(Stage.GUARD) { reached += Stage.GUARD; true }
        assertTrue(chain.run(Chat("x")))
        assertEquals(listOf(Stage.ANSWER), failures)
        assertEquals(listOf(Stage.GUARD), reached)
    }
}
