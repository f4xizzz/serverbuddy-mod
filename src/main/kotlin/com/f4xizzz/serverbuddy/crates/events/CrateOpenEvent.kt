package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateOpenEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateOpenEvent> =
            EventFactory.createArrayBacked(CrateOpenEvent::class.java) { listeners ->
                CrateOpenEvent { player, crate, openData, forced ->
                    for (listener in listeners) {
                        listener.onCrateOpen(player, crate, openData, forced)
                    }
                }
            }
    }

    fun onCrateOpen(player: ServerPlayer, crate: Crate, openData: CrateOpenData, forced: Boolean)
}
