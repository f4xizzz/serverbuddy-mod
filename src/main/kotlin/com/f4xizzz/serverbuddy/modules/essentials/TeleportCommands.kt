package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.clickList
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.cleanName
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.playerArg
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.suggestWords
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.target
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap

/** Casas, warps, spawn, /back, /tpa, /rtp, /top e /tphere. */
object TeleportCommands {
    /** Warps e spawn ficam em memória e no banco ("__spawn" é o spawn). */
    @Volatile
    var warps: Map<String, SavedLocation> = emptyMap()
        private set

    private const val SPAWN = "__spawn"

    /** Nomes que começam com "__" são do mod (o spawn e o spawn de primeira entrada): não aparecem na lista de warps nem dá para criar um assim. */
    private fun isReserved(name: String) = name.startsWith("__")

    fun loadWarps() {
        warps = EssentialsRepository.warps()
    }

    fun spawn(): SavedLocation? = warps[SPAWN]

    fun homeLimit(player: ServerPlayer): Int {
        val homes = EssentialsModule.config.teleport.homes
        if (SbPermissions.check(player, "serverbuddy.essentials.homes.unlimited", false)) return Int.MAX_VALUE
        var max = homes.defaultLimit
        for ((key, value) in homes.limits) if (SbPermissions.check(player, "serverbuddy.essentials.homes.$key", false)) max = maxOf(max, value)
        return max
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // ---- casas
        register(dispatcher, "sethome") {
            executes { setHome(it, "home") }
            then(word("nome").executes { setHome(it, StringArgumentType.getString(it, "nome")) })
        }
        register(dispatcher, "home") {
            executes { goHome(it, null) }
            then(word("nome").suggests(HOME_SUGGESTIONS).executes { goHome(it, StringArgumentType.getString(it, "nome")) })
        }
        register(dispatcher, "delhome") {
            executes { delHome(it, "home") }
            then(word("nome").suggests(HOME_SUGGESTIONS).executes { delHome(it, StringArgumentType.getString(it, "nome")) })
        }
        register(dispatcher, "homes") { executes { listHomes(it) } }

        // ---- warps e spawn
        register(dispatcher, "warp") {
            executes { listWarps(it) }
            then(word("nome").suggests(suggestWords { warps.keys.filter { !isReserved(it) } }).executes { goWarp(it, StringArgumentType.getString(it, "nome")) })
        }
        register(dispatcher, "warps") { executes { listWarps(it) } }
        register(dispatcher, "setwarp", level = 3) { then(word("nome").executes { setWarp(it, StringArgumentType.getString(it, "nome")) }) }
        register(dispatcher, "delwarp", level = 3) { then(word("nome").suggests(suggestWords { warps.keys.filter { !isReserved(it) } }).executes { delWarp(it, StringArgumentType.getString(it, "nome")) }) }
        register(dispatcher, "spawn") { executes { goSpawn(it) } }
        register(dispatcher, "setspawn", level = 3) { executes { setSpawn(it) } }

        // ---- voltar, pedidos e utilidades
        // (sem o apelido /return: o Minecraft já tem um /return, do sistema de funções, e o apelido se misturaria com ele)
        register(dispatcher, "back") { executes { goBack(it) } }
        register(dispatcher, "tpa", aliases = listOf("tpask")) { then(playerArg().executes { tpa(it, here = false) }) }
        register(dispatcher, "tpahere") { then(playerArg().executes { tpa(it, here = true) }) }
        register(dispatcher, "tpaccept", aliases = listOf("tpyes")) {
            executes { tpaccept(it, null) }
            then(word("jogador").executes { tpaccept(it, StringArgumentType.getString(it, "jogador")) })
        }
        register(dispatcher, "tpdeny", aliases = listOf("tpno")) {
            executes { tpdeny(it) }
            then(word("jogador").executes { tpdeny(it) })
        }
        register(dispatcher, "tpcancel") { executes { tpcancel(it) } }
        register(dispatcher, "tptoggle") { executes { tptoggle(it) } }
        register(dispatcher, "rtp", aliases = listOf("randomtp", "wild")) { executes { rtp(it) } }
        register(dispatcher, "top") { executes { top(it) } }
        register(dispatcher, "tphere", level = 2) { then(playerArg().executes { tphere(it) }) }

        // ---- mais teleportes (homeother, tpaall, tppos, tpoffline, firstspawn)
        TeleportExtras.register(dispatcher)
    }

    // ---------------------------------------------------------------- casas

    /** Sugere as casas de quem está digitando. */
    private val HOME_SUGGESTIONS = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        net.minecraft.commands.SharedSuggestionProvider.suggest(ctx.source.player?.let { EssentialsRepository.homes(it.uuid).keys } ?: emptyList(), builder)
    }

    private fun setHome(ctx: CommandContext<CommandSourceStack>, rawName: String): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val name = cleanName(rawName)
        if (name == null) {
            Msg.fail(ctx.source, "home_invalid_name")
            return 0
        }
        val homes = EssentialsRepository.homes(player.uuid)
        val limit = homeLimit(player)
        if (name !in homes && homes.size >= limit) {
            Msg.fail(ctx.source, "home_limit", mapOf("limit" to limit))
            return 0
        }
        EssentialsRepository.saveHome(player.uuid, name, SavedLocation.of(player))
        Msg.send(player, if (name in homes) "home_moved" else "home_set", mapOf("name" to name))
        return 1
    }

    private fun goHome(ctx: CommandContext<CommandSourceStack>, rawName: String?): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val homes = EssentialsRepository.homes(player.uuid)
        if (homes.isEmpty()) {
            Msg.fail(ctx.source, "home_none")
            return 0
        }
        val name = if (rawName != null) cleanName(rawName) else when {
            homes.size == 1 -> homes.keys.first()
            "home" in homes -> "home"
            else -> {
                Msg.reply(ctx.source, "home_pick", mapOf("homes" to clickList(homes.keys, "/home %s")))
                return 0
            }
        }
        val location = name?.let { homes[it] }
        if (location == null) {
            Msg.fail(ctx.source, "home_unknown", mapOf("name" to Texts.escape(rawName ?: ""), "homes" to clickList(homes.keys, "/home %s")))
            return 0
        }
        Teleports.request(player) { if (!Teleports.go(player, location)) Msg.send(player, "home_unknown", mapOf("name" to name, "homes" to "")) }
        return 1
    }

    private fun delHome(ctx: CommandContext<CommandSourceStack>, rawName: String): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val name = cleanName(rawName)
        if (name == null || !EssentialsRepository.deleteHome(player.uuid, name)) {
            Msg.fail(ctx.source, "home_unknown", mapOf("name" to Texts.escape(rawName), "homes" to clickList(EssentialsRepository.homes(player.uuid).keys, "/home %s")))
            return 0
        }
        Msg.send(player, "home_deleted", mapOf("name" to name))
        return 1
    }

    private fun listHomes(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val homes = EssentialsRepository.homes(player.uuid)
        if (homes.isEmpty()) {
            Msg.reply(ctx.source, "home_none")
            return 0
        }
        val limit = homeLimit(player)
        Msg.reply(ctx.source, "homes_list", mapOf("count" to homes.size, "limit" to if (limit == Int.MAX_VALUE) "∞" else limit, "homes" to clickList(homes.keys, "/home %s")))
        return homes.size
    }

    // ---------------------------------------------------------------- warps e spawn

    private fun listWarps(ctx: CommandContext<CommandSourceStack>): Int {
        val names = warps.keys.filter { !isReserved(it) && canUseWarp(ctx.source, it) }
        if (names.isEmpty()) {
            Msg.reply(ctx.source, "warps_none")
            return 0
        }
        Msg.reply(ctx.source, "warps_list", mapOf("count" to names.size, "warps" to clickList(names, "/warp %s")))
        return names.size
    }

    private fun canUseWarp(source: CommandSourceStack, name: String): Boolean =
        !EssentialsModule.config.teleport.warps.permissionPerWarp || SbPermissions.check(source, "serverbuddy.essentials.warp.$name", 2)

    private fun goWarp(ctx: CommandContext<CommandSourceStack>, rawName: String): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val name = cleanName(rawName)
        val location = name?.let { warps[it] }
        if (name == null || isReserved(name) || location == null) {
            Msg.fail(ctx.source, "warp_unknown", mapOf("name" to Texts.escape(rawName)))
            return 0
        }
        if (!canUseWarp(ctx.source, name)) {
            Msg.fail(ctx.source, "warp_no_permission", mapOf("name" to name))
            return 0
        }
        Teleports.request(player) { Teleports.go(player, location) }
        return 1
    }

    private fun setWarp(ctx: CommandContext<CommandSourceStack>, rawName: String): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val name = cleanName(rawName)
        if (name == null || isReserved(name)) {
            Msg.fail(ctx.source, "home_invalid_name")
            return 0
        }
        EssentialsRepository.saveWarp(name, SavedLocation.of(player))
        loadWarps()
        Msg.reply(ctx.source, "warp_set", mapOf("name" to name))
        return 1
    }

    private fun delWarp(ctx: CommandContext<CommandSourceStack>, rawName: String): Int {
        val name = cleanName(rawName)
        if (name == null || isReserved(name) || !EssentialsRepository.deleteWarp(name)) {
            Msg.fail(ctx.source, "warp_unknown", mapOf("name" to Texts.escape(rawName)))
            return 0
        }
        loadWarps()
        Msg.reply(ctx.source, "warp_deleted", mapOf("name" to name))
        return 1
    }

    private fun goSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val location = spawn()
        if (location == null) {
            Msg.fail(ctx.source, "spawn_none")
            return 0
        }
        Teleports.request(player) { Teleports.go(player, location) }
        return 1
    }

    private fun setSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        EssentialsRepository.saveWarp(SPAWN, SavedLocation.of(player))
        loadWarps()
        Msg.reply(ctx.source, "spawn_set")
        return 1
    }

    // ---------------------------------------------------------------- voltar e pedidos

    private fun goBack(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val target = Teleports.backTarget(player)
        if (target == null) {
            Msg.fail(ctx.source, "back_none")
            return 0
        }
        Teleports.request(player) { Teleports.go(player, target) }
        return 1
    }

    private fun tpa(ctx: CommandContext<CommandSourceStack>, here: Boolean): Int {
        val sender = EssentialsCommands.player(ctx) ?: return 0
        val receiver = target(ctx)
        if (receiver.uuid == sender.uuid) {
            Msg.fail(ctx.source, "tpa_self")
            return 0
        }
        if (receiver.uuid in Teleports.tpOff) {
            Msg.fail(ctx.source, "tpa_off", mapOf("player" to receiver.gameProfile.name))
            return 0
        }
        if (sender.uuid in EssentialsRepository.ignored(receiver.uuid)) {
            Msg.fail(ctx.source, "tpa_ignored")
            return 0
        }
        Teleports.addRequest(TpaRequest(sender.uuid, sender.gameProfile.name, receiver.uuid, here, System.currentTimeMillis()))
        Msg.send(sender, if (here) "tpa_sent_here" else "tpa_sent", mapOf("player" to receiver.gameProfile.name))
        Msg.send(receiver, if (here) "tpa_received_here" else "tpa_received", mapOf("player" to sender.gameProfile.name))
        Notifications.notify(receiver)
        return 1
    }

    private fun tpaccept(ctx: CommandContext<CommandSourceStack>, from: String?): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val request = Teleports.requestFor(me.uuid, from)
        if (request == null) {
            Msg.fail(ctx.source, "tpa_none")
            return 0
        }
        Teleports.removeRequest(me.uuid)
        val sender = ctx.source.server.playerList.getPlayer(request.from)
        if (sender == null) {
            Msg.fail(ctx.source, "tpa_expired")
            return 0
        }
        Msg.send(me, "tpa_accepted")
        // quem se mexe: /tpa leva o autor até quem aceitou; /tpahere leva quem aceitou até o autor
        val mover = if (request.here) me else sender
        val destination = if (request.here) sender else me
        val where = SavedLocation.of(destination)
        Teleports.request(mover) { Teleports.go(mover, where) }
        return 1
    }

    private fun tpdeny(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val request = Teleports.requestFor(me.uuid, null)
        if (request == null) {
            Msg.fail(ctx.source, "tpa_none")
            return 0
        }
        Teleports.removeRequest(me.uuid)
        ctx.source.server.playerList.getPlayer(request.from)?.let { Msg.send(it, "tpa_denied") }
        Msg.send(me, "tpa_denied")
        return 1
    }

    private fun tpcancel(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (!Teleports.cancelRequestFrom(me.uuid)) {
            Msg.fail(ctx.source, "tpa_none")
            return 0
        }
        Msg.send(me, "tpa_cancelled")
        return 1
    }

    private fun tptoggle(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (Teleports.tpOff.remove(me.uuid)) {
            Msg.send(me, "tpa_toggle_on")
        } else {
            Teleports.tpOff.add(me.uuid)
            Msg.send(me, "tpa_toggle_off")
        }
        return 1
    }

    // ---------------------------------------------------------------- /rtp, /top, /tphere

    private fun rtp(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val settings = EssentialsModule.config.teleport.rtp
        if (!settings.enabled) {
            Msg.fail(ctx.source, "rtp_disabled")
            return 0
        }
        if (player.level().dimension().location().toString() !in settings.dimensions) {
            Msg.fail(ctx.source, "rtp_world")
            return 0
        }
        Msg.send(player, "rtp_searching")
        Teleports.request(player, "rtp", settings.cooldownSeconds) {
            val level = player.serverLevel()
            val spawn = level.sharedSpawnPos
            val cx = if (settings.center.equals("player", ignoreCase = true)) player.blockX else spawn.x
            val cz = if (settings.center.equals("player", ignoreCase = true)) player.blockZ else spawn.z
            val pos = Teleports.findSafe(level, cx, cz, settings.minRadius, settings.maxRadius.coerceAtLeast(settings.minRadius + 1), 24)
            if (pos == null) {
                Msg.send(player, "rtp_failed")
            } else {
                Teleports.remember(player)
                player.teleportTo(level, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, player.yRot, player.xRot)
                Msg.send(player, "tp_done")
            }
        }
        return 1
    }

    private fun top(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val level = player.serverLevel()
        val pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.blockPosition())
        Teleports.remember(player)
        player.teleportTo(level, player.x, pos.y.toDouble(), player.z, player.yRot, player.xRot)
        Msg.send(player, "top_done")
        return 1
    }

    private fun tphere(ctx: CommandContext<CommandSourceStack>): Int {
        val staff = EssentialsCommands.player(ctx) ?: return 0
        val victim = target(ctx)
        Teleports.go(victim, SavedLocation.of(staff))
        return 1
    }
}
