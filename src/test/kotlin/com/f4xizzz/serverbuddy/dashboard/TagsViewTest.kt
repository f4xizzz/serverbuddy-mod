package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What the Tags section refuses to save: a tag with no name, or a way of unlocking that is missing the thing it needs. */
class TagsViewTest {
    private fun problems(json: String) = TagsView.problems(JsonParser.parseString(json)).filter { !it.warning }

    @Test
    fun `a tag with a name and the default unlock has nothing to fix`() {
        assertTrue(problems("""{"name":"<gold>Champion","prefix":"<gold>[Champion] ","unlock":{"type":"GIVEN"}}""").isEmpty())
    }

    @Test
    fun `a tag needs a name`() {
        assertEquals(listOf("name"), problems("""{"name":"   ","unlock":{"type":"FREE"}}""").map { it.path })
        assertEquals(listOf("name"), problems("""{"unlock":{"type":"FREE"}}""").map { it.path })
    }

    @Test
    fun `unlocking by permission or by group needs the node or the group`() {
        assertEquals(listOf("unlock.value"), problems("""{"name":"VIP","unlock":{"type":"PERMISSION"}}""").map { it.path })
        assertEquals(listOf("unlock.value"), problems("""{"name":"VIP","unlock":{"type":"group","value":"  "}}""").map { it.path })
        assertTrue(problems("""{"name":"VIP","unlock":{"type":"PERMISSION","value":"vip.tag"}}""").isEmpty())
        assertTrue(problems("""{"name":"VIP","unlock":{"type":"GROUP","value":"vip"}}""").isEmpty())
    }

    @Test
    fun `a tag for sale needs a price above zero`() {
        assertEquals(listOf("unlock.price"), problems("""{"name":"Rich","unlock":{"type":"PRICE"}}""").map { it.path })
        assertEquals(listOf("unlock.price"), problems("""{"name":"Rich","unlock":{"type":"PRICE","price":0}}""").map { it.path })
        assertTrue(problems("""{"name":"Rich","unlock":{"type":"PRICE","price":2500}}""").isEmpty())
    }

    @Test
    fun `an unlock type the mod does not know is refused by the schema`() {
        assertTrue(problems("""{"name":"Odd","unlock":{"type":"MAGIC"}}""").isNotEmpty())
    }
}
