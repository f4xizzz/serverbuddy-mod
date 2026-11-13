package com.f4xizzz.serverbuddy.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Garante que os nós de permissão novos continuam equivalentes aos dos mods que o ServerBuddy substituiu. */
class SbPermissionsTest {
    @Test
    fun `modulos dos Skies mapeiam para os nos antigos`() {
        assertEquals("skieskits.kit.diario", SbPermissions.legacyNode("serverbuddy.kits.kit.diario"))
        assertEquals("skieskits.command.reload", SbPermissions.legacyNode("serverbuddy.kits.command.reload"))
        assertEquals("skiesguis.open.warp", SbPermissions.legacyNode("serverbuddy.guis.open.warp"))
        assertEquals("skiesshop.command.open", SbPermissions.legacyNode("serverbuddy.shop.command.open"))
        assertEquals("skiescrates.command.base", SbPermissions.legacyNode("serverbuddy.crates.command.base"))
        assertEquals("skiesannouncements.command.send", SbPermissions.legacyNode("serverbuddy.announcements.command.send"))
        assertEquals("skiesclear.command.force", SbPermissions.legacyNode("serverbuddy.clear.command.force"))
    }

    @Test
    fun `comandos que vieram do CobblemonExtras mapeiam para os nos cobblemonextras command`() {
        assertEquals("cobblemonextras.command.pokesee", SbPermissions.legacyNode("serverbuddy.pokesee.use"))
        assertEquals("cobblemonextras.command.pokeseeother", SbPermissions.legacyNode("serverbuddy.pokesee.other"))
        assertEquals("cobblemonextras.command.compsee", SbPermissions.legacyNode("serverbuddy.compsee.use"))
        assertEquals("cobblemonextras.command.compseeother", SbPermissions.legacyNode("serverbuddy.compsee.other"))
        assertEquals("cobblemonextras.command.pokeshout", SbPermissions.legacyNode("serverbuddy.pokeshout.use"))
        assertEquals("cobblemonextras.command.pokeshoutall", SbPermissions.legacyNode("serverbuddy.pokeshout.all"))
        assertEquals("cobblemonextras.command.itemshout", SbPermissions.legacyNode("serverbuddy.itemshout.use"))
        assertEquals("cobblemonextras.command.pokeodds", SbPermissions.legacyNode("serverbuddy.pokeodds.use"))
        assertEquals("cobblemonextras.command.pokeodds", SbPermissions.legacyNode("serverbuddy.pokeodds.set"))
    }

    @Test
    fun `editor e builder mapeiam para os nos do GreatEditor e do GreatBuilder`() {
        assertEquals("greateditor.devmode", SbPermissions.legacyNode("serverbuddy.editor.devmode"))
        assertEquals("greateditor.perm.gui", SbPermissions.legacyNode("serverbuddy.editor.gui"))
        assertEquals("greateditor.perm.admin.aspects", SbPermissions.legacyNode("serverbuddy.editor.admin.aspects"))
        assertEquals("greatbuilder.perm.gui", SbPermissions.legacyNode("serverbuddy.builder.gui"))
    }

    @Test
    fun `o resto do ServerBuddy mapeia para o GreatServerUtils`() {
        assertEquals("greatserverutils.hunt", SbPermissions.legacyNode("serverbuddy.hunt"))
        assertEquals("greatserverutils.hunt.admin.reload", SbPermissions.legacyNode("serverbuddy.hunt.admin.reload"))
        assertEquals("greatserverutils.catcharea.bypass", SbPermissions.legacyNode("serverbuddy.catcharea.bypass"))
        assertEquals("greatserverutils.pokeheal", SbPermissions.legacyNode("serverbuddy.pokeheal"))
    }

    @Test
    fun `nos que nao sao do ServerBuddy passam direto`() {
        // permissoes escritas nos arquivos de config (ex.: permission de um kit) nao sao traduzidas
        assertNull(SbPermissions.legacyNode("skieskits.kit.diario"))
        assertNull(SbPermissions.legacyNode("luckperms.user.info"))
        assertNull(SbPermissions.legacyNode("minecraft.command.gamemode"))
    }
}
