package com.f4xizzz.serverbuddy.modules.portals

import com.f4xizzz.serverbuddy.core.SbModule

/**
 * Portals (Plus): portal blocks placed with an item, no obsidian frame, no waiting. Portal blocks that touch are one portal, and
 * each portal leads to the coordinates set with `/sb portal edit tp <x y z>`. The portal blocks themselves are vanilla; a mixin keeps
 * them from breaking and sends the entity through at once.
 */
class PortalsModule : SbModule {
    override val id = "portals"
    override val folder = "Portals"
    override val legacyFolders = emptyList<String>()
    override val description = "Instant portals made of portal blocks (/sb portal give, /sb portal edit tp <x y z>)."

    override fun init() {
        PortalsRuntime.install(folder)
    }

    override fun reload() {
        PortalsRuntime.reload(folder)
    }
}
