package com.f4xizzz.serverbuddy.modules.wondertrade

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Wonder Trade: a blind Pokémon swap with other players (/wondertrade, /wt). Needs Cobblemon; without it the module does nothing
 * and creates no config files. Inspired by the Cobblemon Wonder Trade mechanic (a shared pool, cooldown, level/rarity matching).
 */
class WonderTradeModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/WonderTrade")

    override val id = "wondertrade"
    override val folder = "WonderTrade"
    override val legacyFolders = emptyList<String>()
    override val description = "Blind Pokémon swaps with other players through a shared pool (/wondertrade, /wt) (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: Wonder Trade stays off (no config files are created).")
            return
        }
        WonderTradeRuntime.install(folder)
    }

    override fun idleReason(): String? = if (Integrations.cobblemon()) null else "needs Cobblemon (not installed)"

    override fun reload() {
        if (Integrations.cobblemon()) WonderTradeRuntime.reload(folder)
    }
}
