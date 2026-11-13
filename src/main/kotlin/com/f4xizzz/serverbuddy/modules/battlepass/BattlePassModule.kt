package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

/**
 * Passe de batalha com temporadas, níveis, trilha grátis e premium (como o Great BattlePass, que é importado), XP de Pokémon, de
 * tempo jogando e de qualquer recompensa "battlepass_xp" (missões, trivia...), bônus de XP e menu de baú.
 */
class BattlePassModule : SbModule {
    override val id = "battlepass"
    override val folder = "BattlePass"
    override val legacyFolders = listOf("GreatBattlePass")
    override val description = "Battle pass with seasons, free/premium tiers and a chest menu (imports Great BattlePass)."

    override fun init() {
        BattlePassImport.convertLegacyFiles(ConfigFiles.dir(folder))
        // recompensa "battlepass_xp" para missões, trivia, caixas...
        RewardService.register(
            RewardService.Type("battlepass_xp", { reward -> reward.int("amount")?.let { "+$it battle pass XP" } }) { player, reward, _, _ ->
                BattlePassService.addXp(player, (reward.int("amount") ?: 0).toLong(), "reward")
            }
        )
        BattlePassService.load()
        if (Integrations.cobblemon()) CobblemonBattlePassEvents.register()

        ServerLifecycleEvents.SERVER_STARTING.register {
            BattlePassRepository.createTable()
            BattlePassService.ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { BattlePassService.flushAll() }
        ServerLifecycleEvents.SERVER_STOPPED.register { BattlePassService.ready = false }
        ServerTickEvents.END_SERVER_TICK.register { server -> BattlePassService.tick(server) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            BattlePassService.onJoin(player)
            ServerScheduler.later(80) { BattlePassService.onJoinReminder(player) }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> BattlePassService.onLeave(handler.player.uuid) }
        SbCommands.onRegister { dispatcher, _ -> BattlePassCommands.register(dispatcher) }

        // %serverbuddy:battlepass_level%, _xp, _xp_needed, _premium (sim/não), _season e _time_left
        SbPlaceholders.register("battlepass_level") { player, _ -> player?.let { BattlePassService.passOf(it)?.level?.toString() } ?: "0" }
        SbPlaceholders.register("battlepass_xp") { player, _ -> player?.let { BattlePassService.passOf(it)?.xp?.toString() } ?: "0" }
        SbPlaceholders.register("battlepass_xp_needed") { player, _ -> player?.let { BattlePassService.passOf(it)?.let { pass -> BattlePassService.xpNeeded(pass).toString() } } ?: "0" }
        SbPlaceholders.register("battlepass_premium") { player, _ -> if (player != null && BattlePassService.isPremium(player)) "yes" else "no" }
        SbPlaceholders.register("battlepass_season") { _, _ -> BattlePassService.season?.season?.toString() ?: "0" }
        SbPlaceholders.register("battlepass_time_left") { _, _ -> if (BattlePassService.isActive()) Durations.format(BattlePassService.timeLeftMs()) else "ended" }
    }

    override fun reload() {
        BattlePassService.load()
    }
}
