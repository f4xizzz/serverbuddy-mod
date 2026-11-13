package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

/** Módulo Storage: caixa de recompensas do jogador (`/storage`), usada também pelo /redeem. */
class StorageModule : SbModule {
    override val id = "storage"
    override val folder = "Storage"
    override val legacyFolders = emptyList<String>()
    override val description = "Each player's reward chest (/storage); /redeem delivers here."

    override fun init() {
        StorageConfigManager.load()
        // roda depois do callback do ServerBuddy que abre o banco de dados
        ServerLifecycleEvents.SERVER_STARTING.register {
            StorageRepository.createTable()
            StorageService.ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { StorageService.ready = false }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> StorageService.onJoin(handler.player) }
        SbCommands.onRegister { dispatcher, registryAccess -> StorageCommands.register(dispatcher, registryAccess) }
    }

    override fun reload() {
        StorageConfigManager.load()
    }
}
