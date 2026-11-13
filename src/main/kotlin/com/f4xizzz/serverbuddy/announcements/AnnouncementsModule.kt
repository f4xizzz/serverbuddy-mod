package com.f4xizzz.serverbuddy.announcements

import com.f4xizzz.serverbuddy.announcements.commands.BaseCommands
import com.f4xizzz.serverbuddy.announcements.config.ConfigManager
import com.f4xizzz.serverbuddy.announcements.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

class AnnouncementsModule : SbModule {
    override val id = "announcements"
    override val folder = "Announcements"
    override val legacyFolders = listOf("skiesannouncements")

    companion object {
        lateinit var INSTANCE: AnnouncementsModule
        val LOGGER: Logger = LogManager.getLogger("ServerBuddy/Announcements")
    }

    private lateinit var configDir: File
    lateinit var configManager: ConfigManager

    lateinit var placeholderManager: PlaceholderManager

    var adventure: FabricServerAudiences? = null
    var server: MinecraftServer? = null

    var ticks = 0

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        this.configManager = ConfigManager(configDir)

        this.placeholderManager = PlaceholderManager()

        ServerLifecycleEvents.SERVER_STARTING.register(ServerStarting { server: MinecraftServer? ->
            this.adventure = FabricServerAudiences.of(
                server!!
            )
            this.server = server
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerStopped { _ ->
            this.adventure = null
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommands().register(
                dispatcher
            )
        }
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            if (ticks++ >= 20) {
                ticks = 0

                for ((id, group) in ConfigManager.GROUPS) {
                    if (!group.enabled) continue
                    if (--group.remainingTime <= 0) {
                        //  Mod loads or reloads set the timer to -1 initially, so as a workaround only announce if reached 0
                        if (group.remainingTime == 0) {
                            Utils.printDebug("Sending out a random automated timer Announcement for group $id!")
                            group.broadcast()
                        }

                        group.remainingTime = group.interval
                    }
                }
            }
        }
    }

    override fun reload() {
        this.configManager.reload()
        this.placeholderManager = PlaceholderManager()
    }
}
