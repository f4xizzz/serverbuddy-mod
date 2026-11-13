package com.f4xizzz.serverbuddy.modules.battlepass

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattlePassLogicTest {
    private val formula = BpCurve(CurveMode.FORMULA, 1000, 1.1, emptyMap())

    @Test
    fun `curva em formula cresce 10 por cento por nivel`() {
        assertEquals(1000L, BpMath.xpForNext(1, formula))
        assertEquals(1100L, BpMath.xpForNext(2, formula))
        assertEquals(1210L, BpMath.xpForNext(3, formula))
        assertEquals(500L, BpMath.xpForNext(7, BpCurve(CurveMode.FORMULA, 500, 1.0, emptyMap())))
    }

    @Test
    fun `curva manual usa o valor do nivel de destino`() {
        val manual = BpCurve(CurveMode.MANUAL, 777, 1.1, mapOf("2" to 300, "3" to 450, "5" to 900))
        assertEquals(300L, BpMath.xpForNext(1, manual)) // para chegar no 2
        assertEquals(450L, BpMath.xpForNext(2, manual))
        // nível 4 não está na tabela: vale o último definido antes dele (o do 3)
        assertEquals(450L, BpMath.xpForNext(3, manual))
        assertEquals(900L, BpMath.xpForNext(4, manual))
        // sem tabela nenhuma, cai no xp_per_level
        assertEquals(777L, BpMath.xpForNext(1, BpCurve(CurveMode.MANUAL, 777, 1.1, emptyMap())))
    }

    @Test
    fun `ganhar xp sobe varios niveis e para no maximo`() {
        // 1000 + 1100 = 2100: com 2500 de XP sobe 2 níveis e sobram 400
        val r = BpMath.add(1, 0, 2500, 30, formula)
        assertEquals(3, r.level)
        assertEquals(400L, r.xp)
        assertEquals(2, r.levelsGained)
        // XP que não completa o nível só acumula
        val small = BpMath.add(3, 400, 100, 30, formula)
        assertEquals(3, small.level)
        assertEquals(500L, small.xp)
        assertEquals(0, small.levelsGained)
        // no nível máximo a barra fica cheia e o XP não acumula
        val max = BpMath.add(29, 0, 10_000_000, 30, formula)
        assertEquals(30, max.level)
        assertEquals(0L, max.xp)
        assertEquals(1, max.levelsGained)
        assertEquals(0, BpMath.add(30, 0, 500, 30, formula).levelsGained)
        // XP negativo não tira nada
        assertEquals(2, BpMath.add(2, 10, -50, 30, formula).level)
    }

    @Test
    fun `bonus vale o maior que casa e nunca baixa o xp`() {
        val boosts = listOf(BpBoost(multiplier = 1.5), BpBoost(multiplier = 2.0), BpBoost(multiplier = 0.5))
        assertEquals(2.0, BpMath.multiplier(boosts) { true })
        assertEquals(1.5, BpMath.multiplier(boosts) { it.multiplier == 1.5 })
        assertEquals(1.0, BpMath.multiplier(boosts) { false })
        assertEquals(1.0, BpMath.multiplier(emptyList()) { true })
        assertEquals(1.0, BpMath.multiplier(listOf(BpBoost(multiplier = 0.5))) { true }) // abaixo de 1 vira 1
    }

    @Test
    fun `lista de niveis resgatados em texto`() {
        assertEquals("1,3,10", BattlePassRepository.encode(linkedSetOf(10, 1, 3)))
        assertEquals(linkedSetOf(1, 3, 10), BattlePassRepository.decode("1, 3,10, x"))
        assertTrue(BattlePassRepository.decode("").isEmpty())
        assertTrue(BattlePassRepository.decode(null).isEmpty())
    }

    @Test
    fun `converte a config do Great BattlePass`() {
        val old = JsonParser.parseString(
            """{ "maxLevel": 50, "catchXP": 120, "defeatXP": 60, "evolveXP": 80, "hatchXP": 40, "tradeXP": 10, "premiumCost": 2500, "seasonDurationDays": 30,
                 "xpProgression": { "mode": "manual", "xpPerLevel": 900, "xpMultiplier": 1.2, "manualXpValues": { "2": 500, "3": 700 } } }"""
        ).asJsonObject
        val out = BattlePassImport.convertConfig(old)!!
        assertEquals(50, out.get("levels").asInt)
        assertEquals(120, out.getAsJsonObject("xp").get("catch").asInt)
        assertEquals(10, out.getAsJsonObject("xp").get("trade").asInt)
        val curve = out.getAsJsonObject("xp").getAsJsonObject("curve")
        assertEquals("MANUAL", curve.get("mode").asString)
        assertEquals(900, curve.get("xp_per_level").asInt)
        assertEquals(700, curve.getAsJsonObject("manual").get("3").asInt)
        assertEquals(30, out.getAsJsonObject("season").get("days").asInt)
        assertEquals(2500, out.getAsJsonObject("premium").get("price").asInt)
        assertNull(BattlePassImport.convertConfig(JsonParser.parseString("""{ "levels": 30, "xp": {} }""").asJsonObject))
    }

    @Test
    fun `converte os premios do Great BattlePass`() {
        val old = JsonParser.parseString(
            """{
              "templates": {
                "pokeballs_10": { "type": "ITEM", "data": { "id": "cobblemon:poke_ball", "Count": 10 } },
                "shiny_eevee": { "type": "POKEMON", "data": { "species": "eevee", "shiny": true, "level": 5 } },
                "hello": { "type": "COMMAND", "data": { "command": "say oi %player%", "display_name": "Saudação", "id": "minecraft:paper" } }
              },
              "tiers": [
                { "level": 1, "freeReward": { "${'$'}template": "pokeballs_10" }, "premiumReward": { "${'$'}template": "shiny_eevee" } },
                { "level": 2, "freeReward": { "type": "ITEM", "data": { "id": "minecraft:diamond", "Count": 3 } } },
                { "level": 3, "premiumReward": { "${'$'}template": "hello" } }
              ]
            }"""
        ).asJsonObject
        val out = BattlePassImport.convertTiers(old)!!
        val tiers = out.getAsJsonArray("tiers")
        assertEquals(3, tiers.size())
        val first = tiers[0].asJsonObject
        assertEquals("cobblemon:poke_ball", first.getAsJsonArray("free")[0].asJsonObject.get("item").asString)
        assertEquals(10, first.getAsJsonArray("free")[0].asJsonObject.get("amount").asInt)
        assertEquals("eevee shiny=yes level=5", first.getAsJsonArray("premium")[0].asJsonObject.get("spec").asString)
        val second = tiers[1].asJsonObject
        assertEquals(3, second.getAsJsonArray("free")[0].asJsonObject.get("amount").asInt)
        assertEquals(0, second.getAsJsonArray("premium").size())
        val third = tiers[2].asJsonObject.getAsJsonArray("premium")[0].asJsonObject
        assertEquals("command", third.get("type").asString)
        assertEquals("say oi %player%", third.get("command").asString)
        assertEquals("Saudação", third.get("label").asString)
        // um tiers.json que já está no formato daqui não é convertido de novo
        assertNull(BattlePassImport.convertTiers(JsonParser.parseString("""{ "tiers": [ { "level": 1, "free": [], "premium": [] } ] }""").asJsonObject))
    }
}
