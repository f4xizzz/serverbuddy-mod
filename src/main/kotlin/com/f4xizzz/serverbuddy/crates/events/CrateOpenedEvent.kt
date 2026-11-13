package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.CrateOpenData
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

/**
 * fires after a player has opened a crate and its rewards are given.
 */
fun interface CrateOpenedEvent {
    companion object {
        @JvmField
        val EVENT: Event<CrateOpenedEvent> =
            EventFactory.createArrayBacked(CrateOpenedEvent::class.java) { listeners ->
                CrateOpenedEvent { player, crate, openData, rewards ->
                    for (listener in listeners) {
                        listener.onCrateOpened(player, crate, openData, rewards)
                    }
                }
            }
    }

    fun onCrateOpened(
        player: ServerPlayer,
        crate: Crate,
        openData: CrateOpenData,
        rewards: List<Reward>,
    )
}
