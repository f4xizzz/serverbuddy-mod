package com.f4xizzz.serverbuddy.modules.chatguard

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.function.Predicate

/**
 * `/chatguard` (e `/serverbuddy chatguard`):
 *  - todo mundo: `/chatguard` ou `status` mostra os próprios avisos e bans de chat (permissão `serverbuddy.chatguard.status`, liberada);
 *  - equipe (`serverbuddy.chatguard.admin`, nível 2): `status <jogador>`, `list`, `history <jogador>`, `ban <jogador> [tempo] [motivo]`,
 *    `unban <jogador>`, `reset <jogador>` e `reload`.
 */
object ChatGuardCommands {
    private val STAFF: Predicate<CommandSourceStack> = SbPermissions.require(ChatGuardService.ADMIN, 2)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "chatguard") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("chatguard").build(), "chatguard")
    }

    private val online = com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> { ctx, builder ->
        SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray.toList(), builder)
    }

    private fun player(name: String = "player") = Commands.argument(name, StringArgumentType.word()).suggests(online)

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.chatguard.status", true))
            .executes { status(it, null) }
            .then(
                Commands.literal("status").executes { status(it, null) }
                    .then(player().requires(STAFF).executes { status(it, StringArgumentType.getString(it, "player")) })
            )
            .then(Commands.literal("list").requires(STAFF).executes { list(it) })
            .then(Commands.literal("history").requires(STAFF).then(player().executes { history(it) }))
            .then(
                Commands.literal("ban").requires(STAFF).then(
                    player().executes { ban(it, null, null) }.then(
                        Commands.argument("time", StringArgumentType.word()).executes { ban(it, StringArgumentType.getString(it, "time"), null) }.then(
                            Commands.argument("reason", StringArgumentType.greedyString()).executes { ban(it, StringArgumentType.getString(it, "time"), StringArgumentType.getString(it, "reason")) }
                        )
                    )
                )
            )
            .then(Commands.literal("unban").requires(STAFF).then(player().executes { unban(it) }))
            .then(Commands.literal("reset").requires(STAFF).then(player().executes { reset(it) }))
            .then(Commands.literal("reload").requires(STAFF).executes { reload(it) })

    /** O jogador pelo nome: online, ou (offline) o que o gerenciador ou o cache de perfis do servidor conhecem. */
    private fun resolve(ctx: CommandContext<CommandSourceStack>, name: String): Pair<UUID, String>? {
        val server = ctx.source.server
        server.playerList.getPlayerByName(name)?.let { return it.uuid to it.gameProfile.name }
        ChatGuardRepository.findByName(name)?.let { return it }
        return server.profileCache?.get(name)?.orElse(null)?.let { it.id to it.name }
    }

    private fun unknown(ctx: CommandContext<CommandSourceStack>, name: String): Int {
        Texts.fail(ctx.source, ChatGuardService.config.messages.playerUnknown, mapOf("player" to Texts.escape(name)))
        return 0
    }

    private fun when_(millis: Long): String = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

    private fun status(ctx: CommandContext<CommandSourceStack>, wanted: String?): Int {
        val cfg = ChatGuardService.config
        val (uuid, name) = if (wanted == null) {
            val me = ctx.source.player
            if (me == null) {
                Texts.fail(ctx.source, "<red>Name a player: /chatguard status <player>")
                return 0
            }
            me.uuid to me.gameProfile.name
        } else {
            resolve(ctx, wanted) ?: return unknown(ctx, wanted)
        }
        val state = ChatGuardService.peek(uuid)
        val now = System.currentTimeMillis()
        val ban = if (state.bannedAt(now)) Texts.fill(cfg.messages.statusBanned, mapOf("time" to Durations.format(state.banUntil - now))) else ""
        val vars = mapOf("player" to Texts.escape(name), "warnings" to state.warnings, "max" to cfg.warnings.maxWarnings, "bans" to state.bans, "total" to state.totalWarnings, "ban" to ban)
        Texts.reply(ctx.source, if (wanted == null) cfg.messages.statusSelf else cfg.messages.statusOther, vars)
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val cfg = ChatGuardService.config
        val now = System.currentTimeMillis()
        val banned = ChatGuardRepository.banned(now)
        if (banned.isEmpty()) {
            Texts.reply(ctx.source, cfg.messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, cfg.messages.listHeader, mapOf("count" to banned.size))
        for (entry in banned) Texts.reply(ctx.source, cfg.messages.listLine, mapOf("player" to Texts.escape(entry.name), "time" to Durations.format(entry.until - now)))
        return banned.size
    }

    private fun history(ctx: CommandContext<CommandSourceStack>): Int {
        val cfg = ChatGuardService.config
        val wanted = StringArgumentType.getString(ctx, "player")
        val (uuid, name) = resolve(ctx, wanted) ?: return unknown(ctx, wanted)
        val entries = ChatGuardRepository.history(uuid, 15)
        if (entries.isEmpty()) {
            Texts.reply(ctx.source, cfg.messages.historyEmpty, mapOf("player" to Texts.escape(name)))
            return 0
        }
        Texts.reply(ctx.source, cfg.messages.historyHeader, mapOf("player" to Texts.escape(name)))
        for (entry in entries) {
            val time = if (entry.durationMs > 0) " (" + Durations.format(entry.durationMs) + ")" else ""
            Texts.reply(ctx.source, cfg.messages.historyLine, mapOf("when" to when_(entry.at), "kind" to entry.kind, "detail" to Texts.escape(entry.detail), "time" to Texts.escape(time)))
        }
        return entries.size
    }

    private fun ban(ctx: CommandContext<CommandSourceStack>, time: String?, reason: String?): Int {
        val cfg = ChatGuardService.config
        val wanted = StringArgumentType.getString(ctx, "player")
        val (uuid, name) = resolve(ctx, wanted) ?: return unknown(ctx, wanted)
        val millis = if (time == null) null else Durations.parseMillis(time)?.takeIf { it > 0 }
        if (time != null && millis == null) {
            Texts.fail(ctx.source, cfg.messages.badTime)
            return 0
        }
        val applied = ChatGuardService.manualBan(ctx.source.server, uuid, name, millis, reason ?: "staff decision", ctx.source.textName)
        Texts.reply(ctx.source, cfg.messages.banDone, mapOf("player" to Texts.escape(name), "time" to Durations.format(applied)))
        return 1
    }

    private fun unban(ctx: CommandContext<CommandSourceStack>): Int {
        val cfg = ChatGuardService.config
        val wanted = StringArgumentType.getString(ctx, "player")
        val (uuid, name) = resolve(ctx, wanted) ?: return unknown(ctx, wanted)
        if (!ChatGuardService.unban(ctx.source.server, uuid, name, ctx.source.textName)) {
            Texts.fail(ctx.source, cfg.messages.unbanNotBanned, mapOf("player" to Texts.escape(name)))
            return 0
        }
        Texts.reply(ctx.source, cfg.messages.unbanDone, mapOf("player" to Texts.escape(name)))
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>): Int {
        val wanted = StringArgumentType.getString(ctx, "player")
        val (uuid, name) = resolve(ctx, wanted) ?: return unknown(ctx, wanted)
        ChatGuardService.reset(uuid, name, ctx.source.textName)
        Texts.reply(ctx.source, ChatGuardService.config.messages.resetDone, mapOf("player" to Texts.escape(name)))
        return 1
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        ChatGuardService.load()
        Texts.reply(ctx.source, "<green>Chat guard reloaded.")
        return 1
    }
}
