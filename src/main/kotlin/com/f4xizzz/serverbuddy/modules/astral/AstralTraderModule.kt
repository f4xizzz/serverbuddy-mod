package com.f4xizzz.serverbuddy.modules.astral

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Astral Trader (Pro): trade special and shiny tokens for legendary, paradox, Ultra Beast and shiny Pokémon (`/astral`). Needs
 * Cobblemon; without it the module does nothing and creates no config files. The tokens live in the ServerBuddy database, and
 * missions, the battle pass, crates or any reward that runs a command can hand them out with `/astral admin give`.
 */
class AstralTraderModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Astral")

    override val id = "astral"
    override val folder = "AstralTrader"
    override val legacyFolders = emptyList<String>()
    override val description = "Trade special and shiny tokens for legendary, paradox, Ultra Beast and shiny Pokémon (/astral) (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the Astral Trader stays off (no config files are created).")
            return
        }
        AstralRuntime.install(folder)
    }

    override fun idleReason(): String? = if (Integrations.cobblemon()) null else "needs Cobblemon (not installed)"

    override fun reload() {
        if (Integrations.cobblemon()) AstralRuntime.reload(folder)
    }
}
