package com.f4xizzz.serverbuddy.modules.gts

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * GTS (Global Trading Station): a market where players list Pokémon and items and others buy them (/gts). Needs Cobblemon and an
 * economy mod; without Cobblemon it does nothing and creates no config files. Inspired by the GTS mod for Cobblemon by bencrow11
 * (MIT license, see README.md).
 */
class GtsModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/GTS")

    override val id = "gts"
    override val folder = "GTS"
    override val legacyFolders = emptyList<String>()
    override val description = "A global market for Pokémon and items (/gts): listings, tax, minimum prices, returned listings, history (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the GTS stays off (no config files are created).")
            return
        }
        GtsRuntime.install(folder)
    }

    override fun idleReason(): String? = when {
        !Integrations.cobblemon() -> "needs Cobblemon (not installed)"
        GtsRuntime.installed && !GtsService.economyReady() -> "no economy mod found to pay with (Impactor, CobbleDollars...)"
        else -> null
    }

    override fun reload() {
        if (Integrations.cobblemon()) GtsRuntime.reload(folder)
    }
}
