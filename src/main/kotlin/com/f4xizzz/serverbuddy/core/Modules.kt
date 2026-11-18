package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.core.rewards.RewardService
import org.slf4j.LoggerFactory

/**
 * Lista dos módulos embutidos e a rotina de inicialização deles. Cada módulo pode ser desligado em
 * `config/ServerBuddy/modules.conf`.
 */
object Modules {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    private const val P = "com.f4xizzz.serverbuddy."

    private val registry = listOf(
        P + "clear.ClearModule",
        P + "announcements.AnnouncementsModule",
        P + "kits.KitsModule",
        P + "shop.ShopModule",
        P + "crates.CratesModule",
        P + "guis.GuisModule",
        P + "storage.StorageModule",
        P + "shout.ShoutModule",
        P + "modules.motd.MotdModule",
        P + "modules.tags.TagsModule",
        P + "modules.essentials.EssentialsModule",
        P + "modules.commandblock.CommandBlockModule",
        P + "modules.chatguard.ChatGuardModule",
        P + "modules.flan.FlanModule",
        P + "modules.blacklist.BlacklistModule",
        P + "modules.chunklimits.ChunkLimitsModule",
        P + "modules.craftblock.CraftBlockModule",
        P + "modules.pokesnack.PokeSnackModule",
        P + "modules.trivia.TriviaModule",
        P + "modules.missions.MissionsModule",
        P + "modules.battlepass.BattlePassModule",
        P + "modules.calendar.CalendarModule",
        P + "modules.leaderboards.LeaderboardsModule",
        P + "modules.sts.StsModule",
        P + "modules.daycare.DaycareModule",
        P + "modules.wondertrade.WonderTradeModule",
        P + "modules.gts.GtsModule",
        P + "modules.portals.PortalsModule",
        P + "modules.actionitems.ActionItemsModule",
        P + "modules.astral.AstralTraderModule",
        P + "modules.worlds.WorldsModule"
    )

    private val modules: List<SbModule> by lazy {
        registry.mapNotNull { className ->
            try {
                Class.forName(className).getDeclaredConstructor().newInstance() as SbModule
            } catch (e: ClassNotFoundException) {
                LOGGER.warn("Module class {} is not in this jar.", className)
                null
            } catch (e: Throwable) {
                LOGGER.error("Could not create the module {}", className, e)
                null
            }
        }
    }

    /** Ids dos módulos que subiram (os desligados e os que falharam não entram). */
    private val running = LinkedHashSet<String>()

    @JvmStatic
    fun all(): List<SbModule> = modules

    @JvmStatic
    fun isRunning(id: String): Boolean = id in running

    /** Running AND doing something: a module that started but is idle (see [SbModule.idleReason]) is not active. */
    @JvmStatic
    fun isActive(id: String): Boolean = id in running && modules.firstOrNull { it.id == id }?.idleReason() == null

    /** Chamado no fim do onInitialize do ServerBuddy (depois de /serverbuddy ser registrado). */
    @JvmStatic
    fun initAll() {
        ServerHolder.init()
        ServerScheduler.init()
        ChatPipeline.install()
        RewardService.init()
        ModuleSettings.load()
        Webhooks.load()
        for (module in modules) {
            if (!ModuleSettings.enabled(module.id)) {
                LOGGER.info("Module '{}' disabled in modules.conf.", module.id)
                continue
            }
            try {
                LegacyImport.importModule(module)
                SbCommands.initializing = module.id
                module.init()
                running.add(module.id)
                LOGGER.info("Module '{}' started (config/ServerBuddy/{})", module.id, module.folder)
            } catch (e: Throwable) {
                // um módulo com problema não derruba o servidor nem os outros módulos
                LOGGER.error("Failed to start the module '${module.id}'", e)
            }
        }
        SbCommands.initializing = "core"
        try {
            ConfigAdapters.registerAll()
        } catch (e: Throwable) {
            LOGGER.error("Could not list the config files of the Java modules for the dashboard", e)
        }
    }

    /** Reloads the running module whose config folder is [folder] (or starts with it, like "Crates/crates"). False when there is none. */
    @JvmStatic
    fun reloadFolder(folder: String): Boolean {
        val top = folder.substringBefore('/')
        val module = modules.firstOrNull { it.folder.equals(top, ignoreCase = true) && it.id in running } ?: return false
        module.reload()
        return true
    }

    /** Recarrega as configs de todos os módulos que estão rodando (o liga/desliga só vale depois de reiniciar). */
    @JvmStatic
    fun reloadAll(onFailure: java.util.function.Consumer<String>) {
        try {
            ModuleSettings.load()
        } catch (e: Throwable) {
            LOGGER.error("Error reloading modules.conf", e)
            onFailure.accept("modules.conf")
        }
        try {
            Webhooks.load()
        } catch (e: Throwable) {
            LOGGER.error("Error reloading webhook.conf", e)
            onFailure.accept("webhook.conf")
        }
        for (module in modules) {
            if (module.id !in running) continue
            try {
                module.reload()
            } catch (e: Throwable) {
                LOGGER.error("Error reloading the module '${module.id}'", e)
                onFailure.accept("module ${module.id}")
            }
        }
    }
}
