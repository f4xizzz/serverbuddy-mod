package com.f4xizzz.serverbuddy.kits

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.f4xizzz.serverbuddy.kits.commands.BaseCommand
import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.config.actions.Action
import com.f4xizzz.serverbuddy.kits.config.actions.ActionType
import com.f4xizzz.serverbuddy.kits.config.requirements.ComparisonType
import com.f4xizzz.serverbuddy.kits.config.requirements.Requirement
import com.f4xizzz.serverbuddy.kits.config.requirements.RequirementType
import com.f4xizzz.serverbuddy.kits.economy.EconomyType
import com.f4xizzz.serverbuddy.kits.economy.IEconomyService
import com.f4xizzz.serverbuddy.kits.gui.GenericClickType
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.f4xizzz.serverbuddy.kits.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.kits.storage.IStorage
import com.f4xizzz.serverbuddy.kits.storage.StorageType
import com.f4xizzz.serverbuddy.kits.utils.Scheduler
import com.f4xizzz.serverbuddy.kits.utils.Utils
import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.Item
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException

class KitsModule : SbModule {
    override val id = "kits"
    override val folder = "Kits"
    override val legacyFolders = listOf("skieskits")

    companion object {
        /** Namespaces de placeholder: o novo e o do SkiesKits (para os textos que voce ja tem nas GUIs). */
        val PLACEHOLDER_NAMESPACES = listOf("serverbuddy_kits", "skieskits")

        lateinit var INSTANCE: KitsModule

        var MOD_ID = "serverbuddy-kits"
        var MOD_NAME = "Kits"

        val LOGGER: Logger = LogManager.getLogger("ServerBuddy/Kits")
    }

    lateinit var configDir: File
    var storage: IStorage? = null

    var economyService: IEconomyService? = null

    lateinit var adventure: FabricServerAudiences
    lateinit var server: MinecraftServer
    lateinit var nbtOpts: RegistryOps<Tag>

    var gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(Action::class.java, ActionType.Adapter())
        .registerTypeAdapter(Requirement::class.java, RequirementType.Adapter())
        .registerTypeAdapter(ComparisonType::class.java, ComparisonType.Adapter())
        .registerTypeAdapter(EconomyType::class.java, EconomyType.Adapter())
        .registerTypeAdapter(StorageType::class.java, StorageType.Adapter())
        .registerTypeAdapter(GenericClickType::class.java, GenericClickType.Adapter())
        .registerTypeAdapter(InventoryType::class.java, InventoryType.Adapter())
        .registerTypeHierarchyAdapter(Item::class.java, Utils.RegistrySerializer(BuiltInRegistries.ITEM))
        .registerTypeHierarchyAdapter(SoundEvent::class.java, Utils.RegistrySerializer(BuiltInRegistries.SOUND_EVENT))
        .registerTypeHierarchyAdapter(CompoundTag::class.java, Utils.CodecSerializer(CompoundTag.CODEC))
        .create()

    var gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        ConfigManager.load()
        try {
            this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        } catch (e: IOException) {
            Utils.printError(e.message)
            this.storage = null
        }

        this.economyService = IEconomyService.getEconomyService(ConfigManager.CONFIG.economy)


        ServerLifecycleEvents.SERVER_STARTING.register(ServerStarting { server: MinecraftServer ->
            this.adventure = FabricServerAudiences.of(
                server
            )
            this.server = server
            this.nbtOpts = server.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            Scheduler.start()
        })
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { _: MinecraftServer ->
            ConfigManager.loadKits()
            PlaceholderManager.init()
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerStopped { _: MinecraftServer ->
            this.storage?.close()
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(
                dispatcher
            )
        }

        ServerPlayConnectionEvents.JOIN.register { event, _, _ ->
            Scheduler.scheduleTask(20, Scheduler.DelayedAction({
                val player = event.player
                Utils.printDebug("Player ${player.name.string} joined the server! Checking ${ConfigManager.KITS.size} kits...")
                if (!player.hasDisconnected()) {
                    for ((id, kit) in ConfigManager.KITS) {
                        Utils.printDebug("Checking kit $id! Kit onJoin=${kit.onJoin}, Player hasPermission=${kit.hasPermission(player)}")
                        if (kit.onJoin && kit.hasPermission(player)) {
                            kit.claim(id, player, silent = true)
                        }
                    }
                }
            }))
        }
    }

    override fun reload() {
        this.storage?.close()

        ConfigManager.load()
        try {
            this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        } catch (e: IOException) {
            Utils.printError(e.message)
            this.storage = null
        }
        this.economyService = IEconomyService.getEconomyService(ConfigManager.CONFIG.economy)
    }
}
