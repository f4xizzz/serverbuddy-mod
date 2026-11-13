package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.clickList
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.cleanName
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.suggestWords
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * Mais teleportes do Solstice: /homeother (casas de outro jogador), /tpaall (chamar todo mundo), /tppos (coordenadas),
 * /tpoffline (onde alguém saiu) e /firstspawn, /setfirstspawn (o spawn de quem entra pela primeira vez).
 */
object TeleportExtras {
    /** O spawn de primeira entrada fica junto com os warps, com este nome (nomes que começam com "__" não aparecem na lista). */
    const val FIRST_SPAWN = "__firstspawn"

    fun firstSpawn(): SavedLocation? = TeleportCommands.warps[FIRST_SPAWN]

    private val ONLINE = suggestWords { ServerHolder.server?.playerList?.playerNamesArray?.toList() ?: emptyList() }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "homeother", level = 2) {
            then(
                word("jogador").suggests(ONLINE).executes { homeOther(it, null) }
                    .then(word("casa").executes { homeOther(it, StringArgumentType.getString(it, "casa")) })
            )
        }
        register(dispatcher, "tpaall", aliases = listOf("tpaskall"), level = 2) { executes { tpaAll(it) } }
        register(dispatcher, "tppos", level = 2) {
            then(
                Commands.argument("posicao", Vec3Argument.vec3()).executes { tpPos(it, null) }
                    .then(Commands.argument("dimensao", DimensionArgument.dimension()).executes { tpPos(it, DimensionArgument.getDimension(it, "dimensao")) })
            )
        }
        register(dispatcher, "tpoffline", aliases = listOf("tpoff"), level = 2) { then(word("jogador").suggests(ONLINE).executes { tpOffline(it) }) }
        register(dispatcher, "firstspawn") {
            executes { goFirstSpawn(it, null) }
            then(Commands.argument("jogadores", EntityArgument.players()).requires(EssentialsCommands.perm("firstspawn.others", 2)).executes { goFirstSpawn(it, EntityArgument.getPlayers(it, "jogadores")) })
        }
        register(dispatcher, "setfirstspawn", level = 3) {
            executes { setFirstSpawn(it) }
            then(Commands.literal("delete").executes { delFirstSpawn(it) })
        }
    }

    // ---------------------------------------------------------------- /homeother

    private fun homeOther(ctx: CommandContext<CommandSourceStack>, rawHome: String?): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val wanted = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
            return 0
        }
        val homes = EssentialsRepository.homes(found.first)
        if (homes.isEmpty()) {
            Msg.fail(ctx.source, "homeother_none", mapOf("player" to found.second))
            return 0
        }
        val name = if (rawHome != null) cleanName(rawHome) else when {
            homes.size == 1 -> homes.keys.first()
            "home" in homes -> "home"
            else -> {
                Msg.reply(ctx.source, "homeother_pick", mapOf("player" to found.second, "homes" to clickList(homes.keys, "/homeother ${found.second} %s")))
                return 0
            }
        }
        val location = name?.let { homes[it] }
        if (location == null) {
            Msg.fail(ctx.source, "homeother_unknown", mapOf("player" to found.second, "name" to Texts.escape(rawHome ?: ""), "homes" to clickList(homes.keys, "/homeother ${found.second} %s")))
            return 0
        }
        Msg.reply(ctx.source, "homeother_going", mapOf("player" to found.second, "name" to name))
        Teleports.request(me) { Teleports.go(me, location) }
        return 1
    }

    // ---------------------------------------------------------------- /tpaall

    private fun tpaAll(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = EssentialsCommands.player(ctx) ?: return 0
        var sent = 0
        for (target in ctx.source.server.playerList.players) {
            if (target.uuid == sender.uuid || target.uuid in Teleports.tpOff || ChatService.isIgnoring(target.uuid, sender.uuid)) continue
            Teleports.addRequest(TpaRequest(sender.uuid, sender.gameProfile.name, target.uuid, true, System.currentTimeMillis()))
            Msg.send(target, "tpa_received_here", mapOf("player" to sender.gameProfile.name))
            sent++
        }
        if (sent == 0) {
            Msg.fail(ctx.source, "tpaall_none")
            return 0
        }
        Msg.send(sender, "tpaall_sent", mapOf("count" to sent))
        return sent
    }

    // ---------------------------------------------------------------- /tppos

    private fun tpPos(ctx: CommandContext<CommandSourceStack>, level: ServerLevel?): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val pos = Vec3Argument.getVec3(ctx, "posicao")
        val world = level ?: player.serverLevel()
        if (!world.isInWorldBounds(BlockPos.containing(pos)) || !world.worldBorder.isWithinBounds(pos.x, pos.z)) {
            Msg.fail(ctx.source, "tppos_invalid")
            return 0
        }
        Teleports.remember(player)
        player.teleportTo(world, pos.x, pos.y, pos.z, player.yRot, player.xRot)
        fun fmt(d: Double) = String.format(Locale.ROOT, "%.1f", d)
        Msg.reply(ctx.source, "tppos_done", mapOf("x" to fmt(pos.x), "y" to fmt(pos.y), "z" to fmt(pos.z)))
        return 1
    }

    // ---------------------------------------------------------------- /tpoffline

    private fun tpOffline(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val wanted = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
            return 0
        }
        val online = ctx.source.server.playerList.getPlayer(found.first)
        val place = if (online != null) SavedLocation.of(online) else EssentialsRepository.logoff(found.first)
        if (place == null) {
            Msg.fail(ctx.source, "tpoffline_none", mapOf("player" to found.second))
            return 0
        }
        Msg.reply(ctx.source, "tpoffline_done", mapOf("player" to found.second))
        Teleports.request(me) { Teleports.go(me, place) }
        return 1
    }

    /** Guarda onde o jogador saiu (chamado quando ele sai do servidor). */
    fun rememberLogoff(player: ServerPlayer) {
        EssentialsRepository.saveLogoff(player.uuid, SavedLocation.of(player))
    }

    // ---------------------------------------------------------------- spawn de primeira entrada

    private fun goFirstSpawn(ctx: CommandContext<CommandSourceStack>, others: Collection<ServerPlayer>?): Int {
        val place = firstSpawn()
        if (place == null) {
            Msg.fail(ctx.source, "firstspawn_none")
            return 0
        }
        if (others == null) {
            val me = EssentialsCommands.player(ctx) ?: return 0
            Teleports.request(me) { Teleports.go(me, place) }
            return 1
        }
        for (victim in others) {
            Teleports.go(victim, place)
            Msg.reply(ctx.source, "firstspawn_sent", mapOf("player" to victim.gameProfile.name))
        }
        return others.size
    }

    private fun setFirstSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        EssentialsRepository.saveWarp(FIRST_SPAWN, SavedLocation.of(player))
        TeleportCommands.loadWarps()
        Msg.reply(ctx.source, "firstspawn_set")
        return 1
    }

    private fun delFirstSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        EssentialsRepository.deleteWarp(FIRST_SPAWN)
        TeleportCommands.loadWarps()
        Msg.reply(ctx.source, "firstspawn_deleted")
        return 1
    }
}
