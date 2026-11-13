package com.f4xizzz.serverbuddy.modules.flan

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.Texts
import io.github.flemmli97.flan.api.data.IPermissionContainer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import org.slf4j.LoggerFactory

/**
 * Liga os quatro botões ao menu da claim do Flan. O mixin (em Java) chama [fill] depois que o Flan monta o menu e [click] antes
 * de ele tratar um clique. Tudo do Flan que não está na API é lido por reflexão; se o Flan mudar, os botões somem mas o resto funciona.
 */
object FlanMenuBridge {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Flan")

    @Volatile
    private var warned = false

    private fun claimOf(menu: Any): ClaimRef? {
        var type: Class<*>? = menu.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == "data" }
            if (field != null) {
                field.isAccessible = true
                return (field.get(menu) as? IPermissionContainer)?.let { ClaimRef(it) }
            }
            type = type.superclass
        }
        return null
    }

    private fun fail(e: Throwable) {
        if (!warned) {
            warned = true
            LOGGER.error("Could not modify the Flan claim menu (entry buttons are unavailable)", e)
        }
    }

    private fun entriesText(list: List<String>, none: String): String =
        if (list.isEmpty()) none else list.joinToString("\n") { "<gray>• <white>" + Texts.escape(it) }

    /** Põe os quatro botões (entrada por permissão e por tag, dar permissão e dar tag ao entrar) nos espaços livres do menu. */
    @JvmStatic
    fun fill(menu: Any) {
        try {
            if (!FlanModule.active) return
            val claim = claimOf(menu) ?: return
            val config = FlanBridge.config.menu
            val messages = FlanBridge.config.messages
            val restriction = FlanStore.get(claim.id)
            val slots = (menu as AbstractContainerMenu).slots
            fun button(slot: Int, kind: RuleKind, spec: ItemSpec) {
                val list = restriction?.list(kind).orEmpty()
                val none = if (kind.isGrant) messages.grantNone else messages.none
                slots.getOrNull(slot)?.set(spec.build(mapOf("count" to list.size, "entries" to entriesText(list, none))))
            }
            button(config.permissionSlot, RuleKind.PERMISSIONS, config.permissionButton)
            button(config.tagSlot, RuleKind.TAGS, config.tagButton)
            button(config.grantPermissionSlot, RuleKind.GRANT_PERMISSIONS, config.grantPermissionButton)
            button(config.grantTagSlot, RuleKind.GRANT_TAGS, config.grantTagButton)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    /** Trata o clique nos botões. Devolve true se o clique era de um deles (o Flan então não faz mais nada). */
    @JvmStatic
    fun click(menu: Any, player: ServerPlayer, index: Int): Boolean {
        try {
            if (!FlanModule.active) return false
            val config = FlanBridge.config.menu
            val kind = when (index) {
                config.permissionSlot -> RuleKind.PERMISSIONS
                config.tagSlot -> RuleKind.TAGS
                config.grantPermissionSlot -> RuleKind.GRANT_PERMISSIONS
                config.grantTagSlot -> RuleKind.GRANT_TAGS
                else -> return false
            }
            val claim = claimOf(menu) ?: return false
            if (kind.isGrant && !FlanBridge.canEditGrants(player)) {
                Texts.send(player, FlanBridge.config.messages.grantsNotAllowed)
                return true
            }
            if (!claim.canEdit(player)) {
                Texts.send(player, FlanBridge.config.messages.notAllowed)
                return true
            }
            player.closeContainer()
            player.server.execute { RestrictionGui(player, claim, kind).open() }
            return true
        } catch (e: Throwable) {
            fail(e)
            return false
        }
    }

    /** Volta ao menu da claim do Flan (botão "voltar" das nossas telas). */
    fun reopenClaimMenu(player: ServerPlayer, claim: ClaimRef) {
        try {
            val handler = Class.forName("io.github.flemmli97.flan.gui.ClaimMenuScreenHandler")
            val claimClass = Class.forName("io.github.flemmli97.flan.claim.Claim")
            handler.getMethod("openClaimMenu", ServerPlayer::class.java, claimClass).invoke(null, player, claim.container)
        } catch (e: Throwable) {
            fail(e)
        }
    }
}
