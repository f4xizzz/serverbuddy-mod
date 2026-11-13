package com.f4xizzz.serverbuddy.crates

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.f4xizzz.serverbuddy.crates.commands.BaseCommand
import com.f4xizzz.serverbuddy.crates.commands.KeysCommand
import com.f4xizzz.serverbuddy.crates.config.ConfigManager
import com.f4xizzz.serverbuddy.crates.config.GenericClickType
import com.f4xizzz.serverbuddy.crates.config.Lang
import com.f4xizzz.serverbuddy.crates.config.SoundOption
import com.f4xizzz.serverbuddy.crates.data.actions.Action
import com.f4xizzz.serverbuddy.crates.data.opening.world.WorldOpeningAnimation
import com.f4xizzz.serverbuddy.crates.data.particles.effects.ParticleEffect
import com.f4xizzz.serverbuddy.crates.data.rewards.Reward
import com.f4xizzz.serverbuddy.crates.data.rewards.options.bool.BooleanOption
import com.f4xizzz.serverbuddy.crates.data.rewards.options.int.IntOption
import com.f4xizzz.serverbuddy.crates.economy.EconomyManager
import com.f4xizzz.serverbuddy.crates.gui.InventoryType
import com.f4xizzz.serverbuddy.crates.integrations.ModIntegration
import com.f4xizzz.serverbuddy.crates.managers.CratesManager
import com.f4xizzz.serverbuddy.crates.managers.CratesManager.tick
import com.f4xizzz.serverbuddy.crates.managers.HologramsManager
import com.f4xizzz.serverbuddy.crates.managers.KeyManager
import com.f4xizzz.serverbuddy.crates.managers.OpeningManager
import com.f4xizzz.serverbuddy.crates.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.crates.storage.IStorage
import com.f4xizzz.serverbuddy.crates.storage.StorageType
import com.f4xizzz.serverbuddy.crates.utils.CompoundTagAdaptor
import com.f4xizzz.serverbuddy.crates.utils.Utils
import com.f4xizzz.serverbuddy.crates.utils.WebhookUtils
import kotlinx.coroutines.*
import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.Item
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CratesModule : SbModule {
    override val id = "crates"
    override val folder = "Crates"
    override val legacyFolders = listOf("skiescrates")

    companion object {
        lateinit var INSTANCE: CratesModule

        /** Namespace dos dados gravados nos itens (skiescrates:crate, skiescrates:key): NAO mudar, senao as chaves ja entregues deixam de valer. */
        const val MOD_ID = "skiescrates"

        /** Namespaces de placeholder: o novo e o do SkiesCrates. */
        val PLACEHOLDER_NAMESPACES = listOf("serverbuddy_crates", "skiescrates")
        const val MOD_NAME = "Crates"

        val LOGGER: Logger = LogManager.getLogger("ServerBuddy/Crates")
        val MINI_MESSAGE: MiniMessage = MiniMessage.miniMessage()

        val asyncScope = CoroutineScope(Dispatchers.IO)

        @JvmStatic
        fun asResource(path: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        }
    }

    lateinit var configDir: File
    lateinit var storage: IStorage

    lateinit var adventure: FabricServerAudiences
    lateinit var server: MinecraftServer
    lateinit var nbtOpts: RegistryOps<Tag>

    val asyncExecutor: ExecutorService = Executors.newFixedThreadPool(8, ThreadFactoryBuilder()
        .setNameFormat("ServerBuddy-Crates-Async-%d")
        .setDaemon(true)
        .build())

    @OptIn(DelicateCoroutinesApi::class)
    private val particleThreadPool = newFixedThreadPoolContext(1, "ServerBuddy-Crates-ParticleThread")
    private val particleScope = CoroutineScope(particleThreadPool + SupervisorJob())
    fun runOnParticleThread(block: suspend () -> Unit) {
        particleScope.launch {
            block()
            delay(1)
        }
    }

    var gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(Reward::class.java, Reward.Adapter())
        .registerTypeAdapter(Action::class.java, Action.Adapter())
        .registerTypeAdapter(StorageType::class.java, StorageType.Adapter())
        .registerTypeAdapter(GenericClickType::class.java, GenericClickType.Adapter())
        .registerTypeAdapter(ParticleEffect::class.java, ParticleEffect.Adapter())
        .registerTypeAdapter(WorldOpeningAnimation::class.java, WorldOpeningAnimation.Adapter())
        .registerTypeAdapter(IntOption::class.java, IntOption.Adapter())
        .registerTypeAdapter(BooleanOption::class.java, BooleanOption.Adapter())
        .registerTypeHierarchyAdapter(Item::class.java, Utils.RegistrySerializer(BuiltInRegistries.ITEM))
        .registerTypeHierarchyAdapter(SoundEvent::class.java, Utils.RegistrySerializer(BuiltInRegistries.SOUND_EVENT))
        .registerTypeHierarchyAdapter(ParticleOptions::class.java, Utils.CodecSerializer(ParticleTypes.CODEC))
        .registerTypeAdapter(InventoryType::class.java, InventoryType.Deserializer())
        .registerTypeAdapter(SoundOption::class.java, SoundOption.Adaptor())
        .registerTypeAdapter(CompoundTag::class.java, CompoundTagAdaptor())
        .create()

    var gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        ConfigManager.load()
        this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        Lang.init()

        EconomyManager.init()

        ModIntegration.onInit()

        registerEvents()
    }

    private fun registerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server: MinecraftServer ->
            this.adventure = FabricServerAudiences.of(server)
            this.server = server
            this.nbtOpts = server.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            ModIntegration.onServerStarting()
        })
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { _: MinecraftServer ->
            OpeningManager.load()
            CratesManager.load()
            PlaceholderManager.init()
            ModIntegration.onServerStarted()
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(dispatcher)
            KeysCommand().register(dispatcher)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping { _: MinecraftServer ->
            this.storage.close()
            ModIntegration.onServerShutdown()
        })
        WebhookUtils.registerEvents()
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            tick()
            OpeningManager.tick()

            if (server.tickCount % 6000 == 0) {
                KeyManager.cleanCache()
            }
        })
    }

    override fun reload() {
        this.storage.close()

        ConfigManager.load()
        this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        Lang.init()

        OpeningManager.load()
        CratesManager.load()

        if (FabricLoader.getInstance().isModLoaded("holodisplays")) HologramsManager.load()
    }
}
