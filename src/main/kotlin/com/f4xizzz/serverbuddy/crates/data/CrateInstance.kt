package com.f4xizzz.serverbuddy.crates.data

import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.block.HologramOptions
import com.f4xizzz.serverbuddy.crates.config.block.ModelOptions
import com.f4xizzz.serverbuddy.crates.data.particles.ParticleAnimation
import com.f4xizzz.serverbuddy.crates.data.particles.ParticleAnimationOptions
import com.f4xizzz.serverbuddy.crates.integrations.ModIntegration
import com.f4xizzz.serverbuddy.crates.integrations.bil.BILCrateData
import com.f4xizzz.serverbuddy.crates.managers.HologramsManager
import com.f4xizzz.serverbuddy.crates.utils.Utils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.chunk.LevelChunk

class CrateInstance(
    val crate: Crate,
    val level: ServerLevel,
    val pos: BlockPos,
    val dimPos: DimensionalBlockPos,
    val model: ModelOptions? = null,
    val hologram: HologramOptions? = null,
    val particles: ParticleAnimationOptions? = null,
) {
    private var particleAnimation: ParticleAnimation? = null
    var bilData: BILCrateData? = null

    private var nearPlayers = mutableSetOf<ServerPlayer>()
    private var ticks = 0

    init {
        particleAnimation = particles?.generateAnimation(this)

        model?.let { modelOptions ->
            if (!ModIntegration.BIL.isModLoaded()) {
                Utils.printError("The crate '${crate.id}' is using a crate model but the Blockbench Import Library mod is not installed!")
                return@let
            }

            var chunk: LevelChunk? = null
            if (level.isLoaded(pos)) {
                try {
                    chunk = level.getChunkAt(pos)
                } catch (e: Exception) {
                    Utils.printError("Error while attaching BIL Crate at chunk ${pos}!")
                    e.printStackTrace()
                }
            }

            bilData = BILCrateData.create(this, chunk, modelOptions)
        }
    }

    fun destroy() {
        bilData?.holder?.destroy()

        if (FabricLoader.getInstance().isModLoaded("holodisplays")) {
            HologramsManager.unloadCrateHologram(this)
        }
    }

    fun tick() {
        particleAnimation?.let { particle ->
            CratesModule.INSTANCE.runOnParticleThread {
                if (ticks++ >= 20) {
                    ticks = 0
                    nearPlayers = level.players().filter { p ->
                        p.distanceToSqr(pos.bottomCenter) < particle.getDistance()
                    }.toMutableSet()
                }

                particle.tick(nearPlayers.toList())
            }
        }
    }
}