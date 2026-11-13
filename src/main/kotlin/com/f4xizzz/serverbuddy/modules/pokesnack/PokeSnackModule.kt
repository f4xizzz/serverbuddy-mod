package com.f4xizzz.serverbuddy.modules.pokesnack

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbModule
import org.slf4j.LoggerFactory

/**
 * Server-side tuning of the Cobblemon Poké Snack: configurable buffs (what each seasoning does) and Cobblemon's per-snack
 * Pokémon cap. Needs Cobblemon: without it the module does nothing and creates no config files or folders.
 */
class PokeSnackModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/PokeSnack")

    override val id = "pokesnack"
    override val folder = "PokeSnack"
    override val legacyFolders = emptyList<String>()
    override val description = "Configurable Poké Snack buffs and Pokémon cap (Cobblemon only)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the Poké Snack tuning stays off (no config files are created).")
            return
        }
        PokeSnackService.install(folder)
    }

    override fun idleReason(): String? = if (Integrations.cobblemon()) null else "needs Cobblemon (not installed)"

    override fun reload() {
        if (Integrations.cobblemon()) PokeSnackService.reload(folder)
    }
}
