package com.f4xizzz.serverbuddy.crates.events

import com.f4xizzz.serverbuddy.crates.data.Crate
import com.f4xizzz.serverbuddy.crates.data.previews.Preview
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.level.ServerPlayer

fun interface CratePreviewEvent {
    companion object {
        @JvmField
        val EVENT: Event<CratePreviewEvent> =
            EventFactory.createArrayBacked(CratePreviewEvent::class.java) { listeners ->
                CratePreviewEvent { player, crate, preview ->
                    for (listener in listeners) {
                        listener.onCratePreview(player, crate, preview)
                    }
                }
            }
    }

    fun onCratePreview(player: ServerPlayer, crate: Crate, preview: Preview)
}
