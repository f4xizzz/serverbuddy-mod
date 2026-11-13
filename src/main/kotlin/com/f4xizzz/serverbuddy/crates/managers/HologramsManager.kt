package com.f4xizzz.serverbuddy.crates.managers

import net.minecraft.resources.ResourceLocation
import com.f4xizzz.serverbuddy.crates.data.CrateInstance
import com.f4xizzz.serverbuddy.crates.integrations.holodisplays.CrateHologramData
import com.f4xizzz.serverbuddy.crates.mixins.ViewerHandlerAccessor
import com.f4xizzz.serverbuddy.crates.utils.Utils
import dev.furq.holodisplays.api.HoloDisplaysAPI
import dev.furq.holodisplays.api.HoloDisplaysAPI.HologramBuilder
import dev.furq.holodisplays.handlers.ViewerHandler
import net.minecraft.server.level.ServerPlayer

object HologramsManager {
    // Namespace próprio do ServerBuddy (e não "skiescrates"): com o SkiesCrates antigo instalado junto, os dois criariam
    // hologramas com o MESMO id e o HoloDisplays recusa (derrubando o servidor) um id já registrado.
    private const val NAMESPACE = "serverbuddy"
    private var hologramsAPI: HoloDisplaysAPI = HoloDisplaysAPI.get(NAMESPACE)

    private fun idFor(instance: CrateInstance): String =
        ResourceLocation.fromNamespaceAndPath(NAMESPACE, instance.dimPos.hashCode().toString()).toString()
    private val holograms = mutableMapOf<String, CrateHologramData>()

    fun load() {
        unload()
        CratesManager.getAllInstances().forEach { instance ->
            loadCrateHologram(instance)
        }
    }

    fun loadCrateHologram(instance: CrateInstance) {
        val hologramConfig = instance.hologram ?: return
        val id = idFor(instance)

        hologramsAPI.createTextDisplay(
            id
        ) { builder ->
            builder.text(*hologramConfig.text.map {
                instance.crate.parsePlaceholders(it)
            }.toTypedArray())
            builder.scale(hologramConfig.scale.x, hologramConfig.scale.y, hologramConfig.scale.z)
            builder.rotation(hologramConfig.rotation.x, hologramConfig.rotation.y, hologramConfig.rotation.z)
            builder.billboardMode(hologramConfig.billboard.name)
            builder.shadow(hologramConfig.shadow)
            hologramConfig.background?.let {
                builder.backgroundColor(it.color, it.opacity)
            }
            builder.opacity(hologramConfig.opacity)
        }

        val builder: HologramBuilder = hologramsAPI.createHologramBuilder()
            // Position is modified by 0.5f to center the hologram on the block
            .position(
                instance.dimPos.x + 0.5f + hologramConfig.offset.x,
                instance.dimPos.y + 0.5f + hologramConfig.offset.y,
                instance.dimPos.z + 0.5f + hologramConfig.offset.z
            )
            .world(instance.dimPos.dimension)
            .addDisplay(id)
            .updateRate(hologramConfig.updateRate)
            .viewRange(hologramConfig.viewDistance)

        if (!hologramsAPI.registerHologram(id, builder.build())) {
            Utils.printError("Failed to register hologram with ID: $id")
            return
        }
        holograms[id] = CrateHologramData(instance)
    }

    fun unload() {
        holograms.forEach { (id, _) ->
            hologramsAPI.unregisterDisplay(id)
            hologramsAPI.unregisterHologram(id)
        }
    }

    fun unloadCrateHologram(instance: CrateInstance) {
        val id = idFor(instance)
        hologramsAPI.unregisterDisplay(id)
        hologramsAPI.unregisterHologram(id)
        holograms.remove(id)
    }

    fun getHologramData(id: String): CrateHologramData? {
        return holograms[id]
    }

    fun hideHologramForPlayer(player: ServerPlayer, crateInstance: CrateInstance) {
        val id = idFor(crateInstance)
        val crateHologram = holograms[id] ?: return

        crateHologram.hiddenPlayers.add(player.uuid)
        (ViewerHandler as ViewerHandlerAccessor).invokeRemoveViewer(player, id)
    }

    fun showHologramForPlayer(player: ServerPlayer, crateInstance: CrateInstance) {
        val id = idFor(crateInstance)
        val crateHologram = holograms[id] ?: return

        crateHologram.hiddenPlayers.remove(player.uuid)
    }
}
