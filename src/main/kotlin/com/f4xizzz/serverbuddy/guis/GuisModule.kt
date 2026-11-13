package com.f4xizzz.serverbuddy.guis

import com.f4xizzz.serverbuddy.guis.commands.BaseCommands
import com.f4xizzz.serverbuddy.guis.commands.GUICommands
import com.f4xizzz.serverbuddy.guis.config.ConfigManager
import com.f4xizzz.serverbuddy.guis.economy.EconomyManager
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.guis.utils.Scheduler
import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.*

class GuisModule : SbModule {
    override val id = "guis"
    override val folder = "GUIs"
    override val legacyFolders = listOf("skiesguis")

    companion object {
        lateinit var INSTANCE: GuisModule
        const val MOD_ID: String = "serverbuddy-guis"
        val LOGGER: Logger = LogManager.getLogger("ServerBuddy/GUIs")
    }

    lateinit var configDir: File
    lateinit var configManager: ConfigManager

    lateinit var placeholderManager: PlaceholderManager

    var adventure: FabricServerAudiences? = null
    lateinit var server: MinecraftServer
    lateinit var nbtOpts: RegistryOps<Tag>

    var inventoryControllers: MutableMap<UUID, ChestGUI.InventoryController> = mutableMapOf()

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        this.configManager = ConfigManager(configDir)

        this.placeholderManager = PlaceholderManager()
        EconomyManager.init()


        ServerLifecycleEvents.SERVER_STARTING.register(ServerStarting { server: MinecraftServer ->
            this.adventure = FabricServerAudiences.of(
                server
            )
            this.server = server
            this.nbtOpts = server.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            this.placeholderManager.registerServices()

            Scheduler.start()
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerStopped { server: MinecraftServer ->
            this.adventure = null
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommands().register(
                dispatcher
            )
            GUICommands().register(
                dispatcher
            )
        }
    }

    override fun reload() {
        this.configManager.reload()
    }
}
