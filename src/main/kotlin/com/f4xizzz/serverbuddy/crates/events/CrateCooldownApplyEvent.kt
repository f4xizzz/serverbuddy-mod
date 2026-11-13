package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateCooldownApplyEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateCooldownApplyEvent> =
            EventFactory.createArrayBacked(CrateCooldownApplyEvent::class.java) { listeners ->
                CrateCooldownApplyEvent { player, crate, openData, appliedAt, duration ->
                    for (listener in listeners) {
                        listener.onCrateCooldownApply(player, crate, openData, appliedAt, duration)
                    }
                }
            }
    }

    fun onCrateCooldownApply(
        player: ServerPlayer,
        crate: Crate,
        openData: CrateOpenData,
        appliedAt: Long, // unix timestamp
        duration: Long // milliseconds
    )
}
