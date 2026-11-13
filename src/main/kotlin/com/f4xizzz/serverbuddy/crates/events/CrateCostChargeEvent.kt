package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.config.CostOptions
import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CrateCostChargeEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateCostChargeEvent> =
            EventFactory.createArrayBacked(CrateCostChargeEvent::class.java) { listeners ->
                CrateCostChargeEvent { player, crate, openData, cost ->
                    for (listener in listeners) {
                        listener.onCrateCostCharge(player, crate, openData, cost)
                    }
                }
            }
    }

    fun onCrateCostCharge(player: ServerPlayer, crate: Crate, openData: CrateOpenData, cost: CostOptions)
}
