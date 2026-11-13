package com.f4xizzz.serverbuddy.modules.daycare

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Daycare: players leave Pokémon in a daycare where they gain levels over time (/daycare). Needs Cobblemon; without it the module
 * does nothing and creates no config files.
 */
class DaycareModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Daycare")

    override val id = "daycare"
    override val folder = "Daycare"
    override val legacyFolders = emptyList<String>()
    override val description = "A daycare where Pokémon gain levels over time (/daycare); slots and prices by permission tier (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the daycare stays off (no config files are created).")
            return
        }
        DaycareRuntime.install(folder)
    }

    override fun idleReason(): String? = if (Integrations.cobblemon()) null else "needs Cobblemon (not installed)"

    override fun reload() {
        if (Integrations.cobblemon()) DaycareRuntime.reload(folder)
    }
}
