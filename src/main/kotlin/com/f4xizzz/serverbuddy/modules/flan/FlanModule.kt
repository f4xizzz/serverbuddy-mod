package com.f4xizzz.serverbuddy.modules.flan

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Claims do Flan: quem pode entrar (por permissão e por tag, dois botões separados no menu da claim) e o que se ganha ao entrar (uma
 * tag e/ou permissões, outros dois botões), mais o comando /claimaccess. O Flan já expulsa quem não pode ficar ("can_stay"); aqui só se
 * acrescenta a regra de quem pode.
 */
class FlanModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Flan")

    override val id = "flan"
    override val folder = "Flan"
    override val legacyFolders = emptyList<String>()
    override val description = "Flan claims: entry requirements and grants (permissions and tags), managed through claim menu buttons."

    companion object {
        /** Ligado quando o Flan está instalado e o módulo subiu (o mixin do menu só age com isto ligado). */
        @Volatile
        var active: Boolean = false
    }

    override fun init() {
        if (!Integrations.isLoaded("flan")) {
            log.info("Flan is not installed: the claim entry module is inactive.")
            return
        }
        FlanBridge.load()
        FlanBridge.register()
        FlanGrants.register()
        active = true
        SbCommands.onRegister { dispatcher, _ -> FlanCommands.register(dispatcher) }
    }

    override fun idleReason(): String? = if (active) null else "Flan is not installed"

    override fun reload() {
        if (active) FlanBridge.load()
    }
}
