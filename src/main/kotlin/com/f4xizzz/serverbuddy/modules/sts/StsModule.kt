package com.f4xizzz.serverbuddy.modules.sts

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Sell To Server: players sell Pokémon from the party or PC to the server for money (/sts). Needs Cobblemon and an economy mod;
 * without Cobblemon it does nothing and creates no config files.
 */
class StsModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/STS")

    override val id = "sts"
    override val folder = "STS"
    override val legacyFolders = emptyList<String>()
    override val description = "Sell Pokémon to the server for money (/sts); price by level, rarity, shininess and IVs (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the STS stays off (no config files are created).")
            return
        }
        StsRuntime.install(folder)
    }

    override fun idleReason(): String? = when {
        !Integrations.cobblemon() -> "needs Cobblemon (not installed)"
        !StsRuntime.installed -> null
        !StsService.economyReady() -> "no economy mod found to pay with (Impactor, CobbleDollars...)"
        else -> null
    }

    override fun reload() {
        if (Integrations.cobblemon()) StsRuntime.reload(folder)
    }
}
