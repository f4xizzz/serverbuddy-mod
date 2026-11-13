package com.f4xizzz.serverbuddy.modules.essentials

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

/** Other modules can react to a player going AFK or coming back: `AfkEvents.CHANGED.register { player, afk -> ... }`. */
object AfkEvents {
    fun interface Changed {
        fun onChanged(player: ServerPlayer, afk: Boolean)
    }

    @JvmField
    val CHANGED: Event<Changed> = EventFactory.createArrayBacked(Changed::class.java) { listeners ->
        Changed { player, afk -> for (listener in listeners) listener.onChanged(player, afk) }
    }
}
