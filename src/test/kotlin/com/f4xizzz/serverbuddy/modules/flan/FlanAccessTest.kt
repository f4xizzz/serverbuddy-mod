package com.f4xizzz.serverbuddy.modules.flan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlanAccessTest {
    private fun rule(permissions: List<String> = emptyList(), tags: List<String> = emptyList()) = ClaimRestriction(permissions.toMutableList(), tags.toMutableList())

    @Test
    fun `sem regra todo mundo entra`() {
        assertTrue(FlanAccess.canEnter(null, { false }, { false }))
        assertTrue(FlanAccess.canEnter(rule(), { false }, { false }))
    }

    @Test
    fun `so permissao exige uma das permissoes`() {
        val r = rule(permissions = listOf("a.vip", "a.staff"))
        assertTrue(FlanAccess.canEnter(r, { it == "a.staff" }, { false }))
        assertFalse(FlanAccess.canEnter(r, { false }, { true })) // ter a tag não ajuda: só a regra de permissão vale
    }

    @Test
    fun `so tag exige uma das tags`() {
        val r = rule(tags = listOf("vip", "guilda"))
        assertTrue(FlanAccess.canEnter(r, { false }, { it == "guilda" }))
        assertFalse(FlanAccess.canEnter(r, { true }, { false })) // ter a permissão não ajuda
    }

    @Test
    fun `com as duas regras basta passar em uma`() {
        val r = rule(permissions = listOf("a.vip"), tags = listOf("evento"))
        assertTrue(FlanAccess.canEnter(r, { it == "a.vip" }, { false }))
        assertTrue(FlanAccess.canEnter(r, { false }, { it == "evento" }))
        assertFalse(FlanAccess.canEnter(r, { false }, { false }))
    }

    @Test
    fun `texto da regra e limpeza de nomes`() {
        assertEquals("permission a.vip or a.staff / tag evento", FlanAccess.describe(rule(listOf("a.vip", "a.staff"), listOf("evento"))))
        assertEquals("tag vip", FlanAccess.describe(rule(tags = listOf("vip"))))
        assertEquals("serverbuddy.flan.vip", FlanAccess.cleanEntry("  ServerBuddy.Flan.VIP "))
        assertNull(FlanAccess.cleanEntry("dois nomes"))
        assertNull(FlanAccess.cleanEntry("   "))
        assertNull(FlanAccess.cleanEntry("x".repeat(65)))
    }

    @Test
    fun `a tag dada ao entrar so aceita o que o comando tag do Minecraft aceita`() {
        assertEquals("em_arena", FlanAccess.clean(RuleKind.GRANT_TAGS, "  Em_Arena "))
        assertEquals("zona-pvp.1+", FlanAccess.clean(RuleKind.GRANT_TAGS, "zona-pvp.1+"))
        assertNull(FlanAccess.clean(RuleKind.GRANT_TAGS, "time:vermelho")) // dois-pontos: o /tag não consegue nem ver
        assertNull(FlanAccess.clean(RuleKind.GRANT_TAGS, "dois nomes"))
        assertNull(FlanAccess.clean(RuleKind.GRANT_TAGS, "açaí"))
        // as outras listas aceitam o que aceitavam (permissões têm pontos; ids de tag do catálogo valem como antes)
        assertEquals("serverbuddy.flan.vip", FlanAccess.clean(RuleKind.GRANT_PERMISSIONS, "ServerBuddy.Flan.VIP"))
        assertEquals("time:vermelho", FlanAccess.clean(RuleKind.TAGS, "time:vermelho"))
        assertEquals("a:b", FlanAccess.clean(RuleKind.PERMISSIONS, "a:b"))
    }

    @Test
    fun `a marca so usa caracteres que o comando tag aceita`() {
        assertTrue(Regex("[A-Za-z0-9_.+-]+").matches(FlanGrantLogic.MARK + "em_arena"))
    }

    @Test
    fun `claim que so da coisas ao entrar nao barra ninguem`() {
        val r = ClaimRestriction(grantTags = mutableListOf("em_arena"), grantPermissions = mutableListOf("a.fly"))
        assertTrue(FlanAccess.canEnter(r, { false }, { false }))
        assertFalse(r.hasEntryRules)
        assertTrue(r.hasGrants)
        assertFalse(r.isEmpty)
        assertTrue(ClaimRestriction().isEmpty)
    }

    @Test
    fun `dar coisas ao entrar nao muda a regra de entrada`() {
        val r = ClaimRestriction(mutableListOf("a.vip"), mutableListOf(), mutableListOf("a.fly"), mutableListOf("em_arena"))
        assertTrue(FlanAccess.canEnter(r, { it == "a.vip" }, { false }))
        assertFalse(FlanAccess.canEnter(r, { false }, { it == "em_arena" })) // a tag que a claim dá não abre a porta
        assertEquals("permission a.vip", FlanAccess.describe(r))
    }

    @Test
    fun `cada tipo de lista e uma lista propria`() {
        val r = ClaimRestriction()
        r.list(RuleKind.PERMISSIONS).add("p")
        r.list(RuleKind.TAGS).add("t")
        r.list(RuleKind.GRANT_PERMISSIONS).add("gp")
        r.list(RuleKind.GRANT_TAGS).add("gt")
        assertEquals(listOf("p"), r.permissions)
        assertEquals(listOf("t"), r.tags)
        assertEquals(listOf("gp"), r.grantPermissions)
        assertEquals(listOf("gt"), r.grantTags)
        assertTrue(RuleKind.GRANT_TAGS.isGrant && RuleKind.GRANT_PERMISSIONS.isGrant)
        assertFalse(RuleKind.TAGS.isGrant || RuleKind.PERMISSIONS.isGrant)
        assertTrue(RuleKind.PERMISSIONS.isPermission && RuleKind.GRANT_PERMISSIONS.isPermission)
        assertFalse(RuleKind.TAGS.isPermission || RuleKind.GRANT_TAGS.isPermission)
    }

    @Test
    fun `arquivo antigo sem as listas de dar coisas continua valendo`() {
        val old = com.f4xizzz.serverbuddy.core.SbGson.pretty.fromJson("""{ "permissions": ["a"], "tags": ["b"] }""", ClaimRestriction::class.java)
        assertEquals(listOf("a"), old.permissions)
        assertTrue(old.grantTags.isEmpty() && old.grantPermissions.isEmpty())
        val withGrants = com.f4xizzz.serverbuddy.core.SbGson.pretty.fromJson("""{ "grant_tags": ["x"], "grant_permissions": ["y"] }""", ClaimRestriction::class.java)
        assertEquals(listOf("x"), withGrants.grantTags)
        assertEquals(listOf("y"), withGrants.grantPermissions)
    }
}

class FlanGrantLogicTest {
    @Test
    fun `quem entra recebe as tags e a marca`() {
        val plan = FlanGrantLogic.planTags(listOf("em_arena"), setOf("vip"))
        assertEquals(listOf("em_arena"), plan.add)
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `uma tag que o jogador ja tinha por fora nao e marcada nem tirada depois`() {
        val has = setOf("em_arena")
        val enter = FlanGrantLogic.planTags(listOf("em_arena"), has)
        assertTrue(enter.isEmpty) // já tem: nada a dar
        val leave = FlanGrantLogic.planTags(emptyList(), has)
        assertTrue(leave.isEmpty) // sem marca: não foi o mod que deu, então fica
    }

    @Test
    fun `ao sair o mod tira so o que deu`() {
        val tags = setOf("em_arena", FlanGrantLogic.MARK + "em_arena", "vip")
        val plan = FlanGrantLogic.planTags(emptyList(), tags)
        assertTrue(plan.add.isEmpty())
        assertEquals(listOf("em_arena"), plan.remove)
    }

    @Test
    fun `continuar dentro nao mexe em nada`() {
        val tags = setOf("em_arena", FlanGrantLogic.MARK + "em_arena")
        assertTrue(FlanGrantLogic.planTags(listOf("em_arena"), tags).isEmpty)
    }

    @Test
    fun `trocar de claim troca as tags`() {
        val tags = setOf("em_arena", FlanGrantLogic.MARK + "em_arena")
        val plan = FlanGrantLogic.planTags(listOf("zona_pvp"), tags)
        assertEquals(listOf("zona_pvp"), plan.add)
        assertEquals(listOf("em_arena"), plan.remove)
    }

    @Test
    fun `marca que sobrou depois de uma queda do servidor e limpa`() {
        // o jogador voltou para o servidor fora de qualquer claim, ainda com a tag e a marca salvas
        val tags = setOf("em_arena", FlanGrantLogic.MARK + "em_arena")
        assertEquals(listOf("em_arena"), FlanGrantLogic.planTags(emptyList(), tags).remove)
    }

    @Test
    fun `tag que foi tirada por fora enquanto ele estava dentro e dada de novo`() {
        val tags = setOf(FlanGrantLogic.MARK + "em_arena")
        val plan = FlanGrantLogic.planTags(listOf("em_arena"), tags)
        assertEquals(listOf("em_arena"), plan.add)
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `a claim e a claim mae dao tudo junto, sem repetir`() {
        val sub = ClaimRestriction(grantTags = mutableListOf("a", "b"), grantPermissions = mutableListOf("p1"))
        val main = ClaimRestriction(grantTags = mutableListOf("b", "c"), grantPermissions = mutableListOf("p2"))
        val (tags, permissions) = FlanGrantLogic.union(listOf(sub, main))
        assertEquals(listOf("a", "b", "c"), tags.toList())
        assertEquals(listOf("p1", "p2"), permissions.toList())
    }

    @Test
    fun `nomes em branco nao viram tag`() {
        val plan = FlanGrantLogic.planTags(listOf("  ", "", " x "), emptySet())
        assertEquals(listOf("x"), plan.add)
    }
}
