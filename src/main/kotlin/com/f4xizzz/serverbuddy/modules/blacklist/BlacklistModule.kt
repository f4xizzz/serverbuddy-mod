package com.f4xizzz.serverbuddy.modules.blacklist

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory

/**
 * Banned moves in Cobblemon battles, per context (always, PvP only, only with a tag or permission...). Does what the
 * Cobblemon Blacklist mod does, with more flexible conditions, configurable messages and commands to edit the list.
 *
 * Needs Cobblemon: without it the module does nothing and creates no config files or folders.
 */
class BlacklistModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Blacklist")

    override val id = "blacklist"
    override val folder = "Blacklist"
    override val legacyFolders = emptyList<String>()
    override val description = "Banned moves in Cobblemon battles, per context (always, PvP only, by tag or permission...)."

    override fun init() {
        if (!Integrations.cobblemon()) {
            log.info("Cobblemon is not installed: the move blacklist stays off (no config files are created).")
            return
        }
        BlacklistImport.importIfNeeded()
        BlacklistService.load()
        if (Integrations.isLoaded("cobblemon-blacklist")) {
            log.warn("The Cobblemon Blacklist mod is installed: the ServerBuddy blacklist stays off (remove one of the two).")
        } else {
            BlacklistService.active = true
        }
        ServerLifecycleEvents.SERVER_STARTED.register { warnUnknownMoves() }
        SbCommands.onRegister { dispatcher, _ -> BlacklistCommands.register(dispatcher) }
    }

    override fun idleReason(): String? = when {
        BlacklistService.active -> null
        !Integrations.cobblemon() -> "needs Cobblemon (not installed)"
        Integrations.isLoaded("cobblemon-blacklist") -> "the Cobblemon Blacklist mod is installed"
        else -> null
    }

    override fun reload() {
        if (!Integrations.cobblemon()) return
        BlacklistService.load()
        warnUnknownMoves()
    }

    /** A typo in the list is the most common problem: warn in the log about moves Cobblemon does not know. */
    private fun warnUnknownMoves() {
        for ((context, moves) in BlacklistService.unknownMoves()) {
            log.warn("Context '{}': Cobblemon does not know the move(s) {} (check the spelling, e.g. \"thunderbolt\").", context, moves)
        }
    }
}
