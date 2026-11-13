package com.f4xizzz.serverbuddy.clear

import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.clear.commands.BaseCommand
import com.f4xizzz.serverbuddy.clear.config.ConfigManager
import com.f4xizzz.serverbuddy.clear.utils.CompoundTagAdaptor
import com.f4xizzz.serverbuddy.clear.utils.Utils
import com.f4xizzz.serverbuddy.core.SbModule
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import org.apache.logging.log4j.LogManager
import java.io.File

/**
 * Módulo Clear (limpeza periódica de entidades, itens e Pokémon) — portado do SkiesClear (PokeSkies, Apache-2.0).
 * Config em `config/ServerBuddy/Clear`; comando em `/serverbuddy clear` (alias `/sb clear`).
 */
class ClearModule : SbModule {
    companion object {
        lateinit var INSTANCE: ClearModule

        const val MOD_ID = "serverbuddy-clear"
        const val MOD_NAME = "Clear"

        val LOGGER = LogManager.getLogger("ServerBuddy/Clear")
    }

    override val id = "clear"
    override val folder = "Clear"
    override val legacyFolders = listOf("skiesclear")

    lateinit var configDir: File

    var adventure: FabricServerAudiences? = null
    var server: MinecraftServer? = null

    lateinit var clearManager: ClearManager

    var gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ResourceLocation::class.java, Utils.ResourceLocationSerializer())
        .registerTypeHierarchyAdapter(SoundEvent::class.java, Utils.RegistrySerializer(BuiltInRegistries.SOUND_EVENT))
        .registerTypeAdapter(CompoundTag::class.java, CompoundTagAdaptor())
        .create()

    var gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        ConfigManager.load()

        this.clearManager = ClearManager()

        ServerLifecycleEvents.SERVER_STARTING.register(ServerStarting { server ->
            this.adventure = FabricServerAudiences.of(
                server
            )
            this.server = server
        })
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { _ ->

        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerStopped { _ ->
            this.adventure = null
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(
                dispatcher
            )
        }
    }

    override fun reload() {
        ConfigManager.load()
        this.clearManager.reload()
    }
}
