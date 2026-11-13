package com.f4xizzz.serverbuddy.core

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer

/**
 * Guarda o servidor em execução para o código que não recebe o servidor por parâmetro
 * (montagem de itens, textos, banco de dados dos módulos novos).
 */
object ServerHolder {
    @Volatile
    private var current: MinecraftServer? = null

    val server: MinecraftServer? get() = current

    @JvmStatic
    fun init() {
        ServerLifecycleEvents.SERVER_STARTING.register { current = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { current = null }
    }

    fun require(): MinecraftServer = current ?: error("The Minecraft server has not started yet.")

    /** Contexto para ler/gravar componentes de item (precisa dos registros do servidor). */
    fun nbtOps(): RegistryOps<Tag> = require().registryAccess().createSerializationContext(NbtOps.INSTANCE)
}
