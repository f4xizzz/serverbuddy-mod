package com.f4xizzz.serverbuddy.shop

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.f4xizzz.serverbuddy.shop.commands.BaseCommand
import com.f4xizzz.serverbuddy.shop.commands.ShopCommands
import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.config.Lang
import com.f4xizzz.serverbuddy.shop.config.ShopConfig
import com.f4xizzz.serverbuddy.shop.config.ShopEntryMapAdapter
import com.f4xizzz.serverbuddy.shop.config.AliasCommand
import com.f4xizzz.serverbuddy.shop.data.TransactionType
import com.f4xizzz.serverbuddy.shop.data.click.EntryClickOption
import com.f4xizzz.serverbuddy.shop.data.entry.ShopEntry
import com.f4xizzz.serverbuddy.shop.data.entry.requirements.ComparisonType
import com.f4xizzz.serverbuddy.shop.data.entry.requirements.Requirement
import com.f4xizzz.serverbuddy.shop.data.items.actions.Action
import com.f4xizzz.serverbuddy.shop.economy.EconomyType
import com.f4xizzz.serverbuddy.shop.economy.IEconomyService
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.InventoryType
import com.f4xizzz.serverbuddy.shop.logging.LoggerManager
import com.f4xizzz.serverbuddy.shop.logging.LoggerType
import com.f4xizzz.serverbuddy.shop.placeholders.PlaceholderManager
import com.f4xizzz.serverbuddy.shop.storage.StorageType
import com.f4xizzz.serverbuddy.shop.utils.CompoundTagAdaptor
import com.f4xizzz.serverbuddy.shop.utils.ItemCompareUtils
import com.f4xizzz.serverbuddy.shop.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.SbModule
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ShopModule : SbModule {
    override val id = "shop"
    override val folder = "Shop"
    override val legacyFolders = listOf("skiesshop")

    companion object {
        /** Namespaces de placeholder: o novo e o do SkiesShop (para os textos que voce ja tem nas GUIs). */
        val PLACEHOLDER_NAMESPACES = listOf("serverbuddy_shop", "skiesshop")

        lateinit var INSTANCE: ShopModule

        var MOD_ID = "serverbuddy-shop"
        var MOD_NAME = "Shop"

        val LOGGER: Logger = LogManager.getLogger("ServerBuddy/Shop")
        val MINI_MESSAGE: MiniMessage = MiniMessage.miniMessage()

        val asyncScope = CoroutineScope(Dispatchers.IO)

        @JvmStatic
        fun asResource(path: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        }
    }

    lateinit var configDir: File

    lateinit var adventure: FabricServerAudiences
    lateinit var server: MinecraftServer
    lateinit var nbtOpts: RegistryOps<Tag>

    private var economyServices: Map<EconomyType, IEconomyService> = emptyMap()

    val asyncExecutor: ExecutorService = Executors.newFixedThreadPool(8, ThreadFactoryBuilder()
        .setNameFormat("ServerBuddy-Shop-Async-%d")
        .setDaemon(true)
        .build())

    var gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ShopEntry::class.java, ShopEntry.Adapter())
        .registerTypeAdapter(ShopConfig::class.java, ShopConfig.Deserializer())
        .registerTypeAdapter(AliasCommand::class.java, AliasCommand.Adapter())
        .registerTypeAdapter(Action::class.java, Action.Adapter())
        .registerTypeAdapter(GenericClickType::class.java, GenericClickType.Adapter())
        .registerTypeAdapter(EntryClickOption::class.java, EntryClickOption.Adapter())
        .registerTypeAdapter(LoggerType::class.java, LoggerType.Adapter())
        .registerTypeAdapter(TransactionType::class.java, TransactionType.Adapter())
        .registerTypeAdapter(StorageType::class.java, StorageType.Adapter())
        .registerTypeAdapter(InventoryType::class.java, InventoryType.Adapter())
        .registerTypeAdapter(EconomyType::class.java, EconomyType.Adapter())
        .registerTypeAdapter(Requirement::class.java, Requirement.Adapter())
        .registerTypeAdapter(ComparisonType::class.java, ComparisonType.Adapter())
        .registerTypeAdapter(ItemCompareUtils.ComparisonMode::class.java, ItemCompareUtils.ComparisonMode.Adapter())
        .registerTypeHierarchyAdapter(CompoundTag::class.java, CompoundTagAdaptor())
        .registerTypeAdapter(object : TypeToken<Map<String, ShopEntry>>() {}.type, ShopEntryMapAdapter())
        .create()
    var gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun init() {
        INSTANCE = this

        this.configDir = ModPaths.subDir(folder)
        ConfigManager.load()
        Lang.init()

        this.economyServices = IEconomyService.getLoadedEconomyServices()

        LoggerManager.load()

        registerEvents()
    }

    private fun registerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server: MinecraftServer ->
            this.adventure = FabricServerAudiences.of(
                server
            )
            this.server = server
            this.nbtOpts = server.registryAccess().createSerializationContext(NbtOps.INSTANCE)

            PlaceholderManager.init()

            if (FabricLoader.getInstance().isModLoaded("impactor")) {
                Utils.printInfo("Impactor Economy Service has been found and loaded for any Currency actions!")
            } else {
                Utils.printError("Impactor was not loaded, things wont work quite right...")
            }

            // Disabled for now
//            if (FabricLoader.getInstance().isModLoaded("plan")) {
//                Utils.printInfo("Plan has been found and loaded for any Economy actions!")
//                PlanHook.initialize()
//            }

            ShopManager.load()
        })
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping {
            try {

            } finally {
                asyncExecutor.shutdownNow()
            }
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(
                dispatcher
            )
            ShopCommands().register(dispatcher)
        }
    }

    override fun reload() {
        ConfigManager.load()

        this.economyServices = IEconomyService.getLoadedEconomyServices()
        Lang.init()

        LoggerManager.load()
        ShopManager.load()
    }

    fun getLoadedEconomyServices(): Map<EconomyType, IEconomyService> {
        return this.economyServices
    }

    fun getEconomyService(economyType: EconomyType?): IEconomyService? {
        return economyType?.let { this.economyServices[it] }
    }

    fun getEconomyServiceOrDefault(economyType: EconomyType?): IEconomyService? {
        return economyType?.let { this.economyServices[it] } ?: this.economyServices.values.firstOrNull()
    }
}
