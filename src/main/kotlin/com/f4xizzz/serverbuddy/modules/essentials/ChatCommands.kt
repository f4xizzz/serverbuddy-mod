package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.playerArg
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.suggestWords
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.target
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.text
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Estado do chat: quem respondeu a quem, quem está no chat da equipe, quem espia e os registros dos jogadores. */
object ChatService {
    val replyTo = ConcurrentHashMap<UUID, UUID>()
    val staffChat: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val socialSpy: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val records = ConcurrentHashMap<UUID, PlayerRecord>()
    private val ignores = ConcurrentHashMap<UUID, Set<UUID>>()

    /** O registro do jogador (do cache se ele está online). Cria um vazio em memória se nunca entrou. */
    fun record(uuid: UUID, name: String = ""): PlayerRecord = records[uuid] ?: EssentialsRepository.player(uuid) ?: PlayerRecord(uuid, name, 0, 0, null, 0, null)

    /** Carrega o jogador que entrou. Devolve true se é a primeira vez dele no servidor. */
    fun onJoin(player: ServerPlayer): Boolean {
        val now = System.currentTimeMillis()
        val existing = EssentialsRepository.player(player.uuid)
        val record = existing ?: PlayerRecord(player.uuid, player.gameProfile.name, now, now, null, 0, null)
        record.name = player.gameProfile.name
        record.lastSeen = now
        EssentialsRepository.savePlayer(record)
        records[player.uuid] = record
        ignores[player.uuid] = EssentialsRepository.ignored(player.uuid)
        return existing == null
    }

    fun onLeave(player: ServerPlayer) {
        records[player.uuid]?.let {
            it.lastSeen = System.currentTimeMillis()
            EssentialsRepository.savePlayer(it)
        }
        records.remove(player.uuid)
        ignores.remove(player.uuid)
        replyTo.remove(player.uuid)
        staffChat.remove(player.uuid)
        socialSpy.remove(player.uuid)
    }

    fun save(uuid: UUID) {
        records[uuid]?.let { EssentialsRepository.savePlayer(it) }
    }

    fun isIgnoring(who: UUID, other: UUID): Boolean = ignores[who]?.contains(other) ?: false

    fun setIgnoring(who: UUID, other: UUID, value: Boolean) {
        EssentialsRepository.setIgnored(who, other, value)
        ignores[who] = EssentialsRepository.ignored(who)
    }

    /** Segundos de silêncio que faltam (0 se não está silenciado). */
    fun muteLeft(uuid: UUID): Long {
        val record = records[uuid] ?: return 0
        if (record.mutedUntil <= 0) return 0
        if (record.mutedUntil != Long.MAX_VALUE && record.mutedUntil <= System.currentTimeMillis()) {
            record.mutedUntil = 0
            record.muteReason = null
            EssentialsRepository.savePlayer(record)
            return 0
        }
        return if (record.mutedUntil == Long.MAX_VALUE) Long.MAX_VALUE else (record.mutedUntil - System.currentTimeMillis()) / 1000 + 1
    }

    fun mutedMessage(player: ServerPlayer) {
        val record = record(player.uuid)
        val left = muteLeft(player.uuid)
        val time = if (left == Long.MAX_VALUE) "forever" else "Time left: ${Durations.format(left * 1000)}."
        Msg.send(player, "muted_you", mapOf("reason" to (record.muteReason?.let { ": " + Texts.escape(it) } ?: ""), "time" to time))
    }

    /** O apelido de [uuid] (ou o nome, se não tem). */
    fun displayName(uuid: UUID, fallback: String): String = records[uuid]?.nick ?: fallback

    fun staffOnline(server: MinecraftServer, node: String): List<ServerPlayer> = server.playerList.players.filter { SbPermissions.check(it, node, 2) }

    /** Procura o jogador pelo nome (online ou já visto). */
    fun resolve(server: MinecraftServer, name: String): Pair<UUID, String>? {
        server.playerList.getPlayerByName(name)?.let { return it.uuid to it.gameProfile.name }
        return EssentialsRepository.playerByName(name)?.let { it.uuid to it.name }
    }

    fun whenText(millis: Long): String =
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
}

/** /msg, /reply, /ignore, /mute, /nick, /broadcast, /staffchat, /helpop, /socialspy e /mail. */
object ChatCommands {
    private val settings get() = EssentialsModule.config.chat

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "msg", aliases = listOf("tell", "w", "whisper", "pm"), replaceVanilla = true) {
            then(playerArg().then(text("mensagem").executes { msg(it) }))
        }
        register(dispatcher, "reply", aliases = listOf("r")) { then(text("mensagem").executes { reply(it) }) }
        register(dispatcher, "ignore") {
            executes { listIgnored(it) }
            then(word("jogador").suggests(suggestWords { ServerHolder.server?.playerList?.playerNamesArray?.toList() ?: emptyList() }).executes { ignore(it, StringArgumentType.getString(it, "jogador"), true) })
        }
        register(dispatcher, "unignore") { then(word("jogador").executes { ignore(it, StringArgumentType.getString(it, "jogador"), false) }) }
        register(dispatcher, "mute", level = 2) {
            then(word("jogador").executes { mute(it, "") }.then(text("detalhes").executes { mute(it, StringArgumentType.getString(it, "detalhes")) }))
        }
        register(dispatcher, "unmute", level = 2) { then(word("jogador").executes { unmute(it) }) }
        register(dispatcher, "nick", aliases = listOf("nickname")) {
            executes { nick(it, "off") }
            then(text("apelido").executes { nick(it, StringArgumentType.getString(it, "apelido")) })
        }
        register(dispatcher, "realname") { then(word("apelido").executes { realName(it) }) }
        register(dispatcher, "broadcast", aliases = listOf("bc"), level = 2) { then(text("mensagem").executes { broadcast(it) }) }
        register(dispatcher, "staffchat", aliases = listOf("sc"), level = 2) {
            executes { toggleStaffChat(it) }
            then(text("mensagem").executes { staffChat(it) })
        }
        register(dispatcher, "helpop") { then(text("mensagem").executes { helpop(it) }) }
        register(dispatcher, "socialspy", level = 2) { executes { socialSpy(it) } }
        register(dispatcher, "mail") {
            then(Commands.literal("send").then(word("jogador").then(text("mensagem").executes { mailSend(it) })))
            then(Commands.literal("read").executes { mailRead(it) })
            then(Commands.literal("clear").executes { mailClear(it) })
            executes { mailRead(it) }
        }
    }

    // ---------------------------------------------------------------- mensagens privadas

    private fun send(from: ServerPlayer, to: ServerPlayer, message: String): Boolean {
        if (ChatService.muteLeft(from.uuid) > 0) {
            ChatService.mutedMessage(from)
            return false
        }
        if (to.uuid == from.uuid) {
            Msg.send(from, "msg_self")
            return false
        }
        if (ChatService.isIgnoring(to.uuid, from.uuid) && !SbPermissions.check(from, "serverbuddy.essentials.ignore.exempt", 2)) {
            Msg.send(from, "msg_ignored")
            return false
        }
        val vars = mapOf("from" to ChatService.displayName(from.uuid, from.gameProfile.name), "to" to ChatService.displayName(to.uuid, to.gameProfile.name), "message" to Texts.escape(message))
        val line = Texts.render(settings.msgFormat, vars)
        from.sendSystemMessage(line)
        to.sendSystemMessage(line)
        Notifications.notify(to)
        ChatService.replyTo[from.uuid] = to.uuid
        ChatService.replyTo[to.uuid] = from.uuid
        val spy = Texts.render(settings.socialSpyFormat, vars + ("from" to from.gameProfile.name) + ("to" to to.gameProfile.name))
        for (id in ChatService.socialSpy) {
            if (id == from.uuid || id == to.uuid) continue
            from.server.playerList.getPlayer(id)?.sendSystemMessage(spy)
        }
        return true
    }

    private fun msg(ctx: CommandContext<CommandSourceStack>): Int {
        val from = EssentialsCommands.player(ctx) ?: return 0
        return if (send(from, target(ctx), StringArgumentType.getString(ctx, "mensagem"))) 1 else 0
    }

    private fun reply(ctx: CommandContext<CommandSourceStack>): Int {
        val from = EssentialsCommands.player(ctx) ?: return 0
        val to = ChatService.replyTo[from.uuid]?.let { ctx.source.server.playerList.getPlayer(it) }
        if (to == null) {
            Msg.fail(ctx.source, "msg_nobody")
            return 0
        }
        return if (send(from, to, StringArgumentType.getString(ctx, "mensagem"))) 1 else 0
    }

    // ---------------------------------------------------------------- ignorar e silenciar

    private fun ignore(ctx: CommandContext<CommandSourceStack>, name: String, value: Boolean): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val online = ctx.source.server.playerList.getPlayer(found.first)
        if (value && online != null && SbPermissions.check(online, "serverbuddy.essentials.ignore.exempt", 2)) {
            Msg.fail(ctx.source, "ignore_staff")
            return 0
        }
        ChatService.setIgnoring(me.uuid, found.first, value)
        Msg.send(me, if (value) "ignore_added" else "ignore_removed", mapOf("player" to found.second))
        return 1
    }

    internal fun listIgnored(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val names = EssentialsRepository.ignored(me.uuid).map { ChatService.record(it).name.ifBlank { it.toString() } }
        if (names.isEmpty()) Msg.reply(ctx.source, "ignore_none") else Msg.reply(ctx.source, "ignore_list", mapOf("players" to names.joinToString(", ")))
        return names.size
    }

    private fun mute(ctx: CommandContext<CommandSourceStack>, details: String): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        // "/mute Fulano 30m spam": o primeiro pedaço é o tempo se der para entender; o resto é o motivo
        val parts = details.trim().split(" ", limit = 2)
        val duration = Durations.parseMillis(parts.firstOrNull())
        val reason = (if (duration != null) parts.getOrNull(1) else details).orEmpty().trim().ifEmpty { null }
        val record = ChatService.record(found.first, found.second)
        record.name = found.second
        record.mutedUntil = if (duration == null) Long.MAX_VALUE else System.currentTimeMillis() + duration
        record.muteReason = reason
        EssentialsRepository.savePlayer(record)
        ctx.source.server.playerList.getPlayer(found.first)?.let { ChatService.mutedMessage(it) }
        Msg.reply(ctx.source, "mute_done", mapOf("player" to found.second, "time" to if (duration == null) "forever" else "por ${Durations.format(duration)}"), broadcast = true)
        return 1
    }

    private fun unmute(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val record = ChatService.record(found.first, found.second)
        if (record.mutedUntil <= 0) {
            Msg.fail(ctx.source, "mute_not_muted", mapOf("player" to found.second))
            return 0
        }
        record.mutedUntil = 0
        record.muteReason = null
        EssentialsRepository.savePlayer(record)
        ctx.source.server.playerList.getPlayer(found.first)?.let { Msg.send(it, "unmuted_you") }
        Msg.reply(ctx.source, "unmute_done", mapOf("player" to found.second), broadcast = true)
        return 1
    }

    // ---------------------------------------------------------------- apelido

    private fun nick(ctx: CommandContext<CommandSourceStack>, wanted: String): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val record = ChatService.record(me.uuid, me.gameProfile.name)
        val text = wanted.trim()
        if (text.equals("off", ignoreCase = true) || text.isEmpty()) {
            record.nick = null
            EssentialsRepository.savePlayer(record)
            Msg.send(me, "nick_removed")
            return 1
        }
        val colors = SbPermissions.check(me, "serverbuddy.essentials.nick.colors", 2)
        val shown = if (colors) text else Texts.escape(text)
        val plain = Texts.parse(shown).string
        if (plain.length > settings.nickMaxLength) {
            Msg.fail(ctx.source, "nick_too_long", mapOf("max" to settings.nickMaxLength))
            return 0
        }
        if (!colors && text != plain) {
            Msg.fail(ctx.source, "nick_colors")
            return 0
        }
        val taken = EssentialsRepository.playerByNick(shown)
        if (taken != null && taken.uuid != me.uuid) {
            Msg.fail(ctx.source, "nick_taken")
            return 0
        }
        record.nick = shown
        EssentialsRepository.savePlayer(record)
        Msg.send(me, "nick_set", mapOf("nick" to shown))
        return 1
    }

    private fun realName(ctx: CommandContext<CommandSourceStack>): Int {
        val nick = StringArgumentType.getString(ctx, "apelido")
        val found = EssentialsRepository.playerByNick(nick) ?: ctx.source.server.playerList.players.firstOrNull { ChatService.displayName(it.uuid, "").equals(nick, ignoreCase = true) }?.let { ChatService.record(it.uuid, it.gameProfile.name) }
        if (found == null) {
            Msg.fail(ctx.source, "realname_none", mapOf("nick" to Texts.escape(nick)))
            return 0
        }
        Msg.reply(ctx.source, "realname", mapOf("nick" to Texts.escape(nick), "player" to found.name))
        return 1
    }

    // ---------------------------------------------------------------- avisos e equipe

    private fun broadcast(ctx: CommandContext<CommandSourceStack>): Int {
        val message = StringArgumentType.getString(ctx, "mensagem")
        val line = Texts.render(settings.broadcastFormat, mapOf("message" to message))
        ctx.source.server.playerList.broadcastSystemMessage(line, false)
        return 1
    }

    private fun toggleStaffChat(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (ChatService.staffChat.remove(me.uuid)) {
            Msg.send(me, "staffchat_off")
        } else {
            ChatService.staffChat.add(me.uuid)
            Msg.send(me, "staffchat_on")
        }
        return 1
    }

    private fun staffChat(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        sendStaffChat(me, StringArgumentType.getString(ctx, "mensagem"))
        return 1
    }

    /** Manda [message] para a equipe (usado pelo /staffchat e pelo modo "tudo vai para a equipe"). */
    fun sendStaffChat(from: ServerPlayer, message: String) {
        val line = Texts.render(settings.staffChatFormat, mapOf("player" to ChatService.displayName(from.uuid, from.gameProfile.name), "message" to Texts.escape(message)))
        for (staff in ChatService.staffOnline(from.server, "serverbuddy.essentials.staffchat")) staff.sendSystemMessage(line)
    }

    private fun helpop(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val line = Texts.render(settings.helpopFormat, mapOf("player" to me.gameProfile.name, "message" to Texts.escape(StringArgumentType.getString(ctx, "mensagem"))))
        val staff = ChatService.staffOnline(ctx.source.server, "serverbuddy.essentials.helpop.receive")
        for (member in staff) member.sendSystemMessage(line)
        Msg.send(me, if (staff.isEmpty()) "helpop_nobody" else "helpop_sent")
        return 1
    }

    private fun socialSpy(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (ChatService.socialSpy.remove(me.uuid)) {
            Msg.send(me, "socialspy_off")
        } else {
            ChatService.socialSpy.add(me.uuid)
            Msg.send(me, "socialspy_on")
        }
        return 1
    }

    // ---------------------------------------------------------------- correio

    private fun mailSend(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (ChatService.muteLeft(me.uuid) > 0) {
            ChatService.mutedMessage(me)
            return 0
        }
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "mail_unknown_player")
            return 0
        }
        if (ChatService.isIgnoring(found.first, me.uuid) || EssentialsRepository.ignored(found.first).contains(me.uuid)) {
            Msg.fail(ctx.source, "msg_ignored")
            return 0
        }
        EssentialsRepository.addMail(found.first, me.gameProfile.name, StringArgumentType.getString(ctx, "mensagem"))
        Msg.send(me, "mail_sent", mapOf("player" to found.second))
        ctx.source.server.playerList.getPlayer(found.first)?.let {
            Msg.send(it, "mail_new", mapOf("count" to EssentialsRepository.mail(found.first).size))
            Notifications.notify(it)
        }
        return 1
    }

    private fun mailRead(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val mail = EssentialsRepository.mail(me.uuid)
        if (mail.isEmpty()) {
            Msg.reply(ctx.source, "mail_none")
            return 0
        }
        for ((index, entry) in mail.withIndex()) {
            Msg.reply(ctx.source, "mail_line", mapOf("id" to index + 1, "from" to entry.fromName, "message" to Texts.escape(entry.message), "when" to ChatService.whenText(entry.sentAt)))
        }
        return mail.size
    }

    private fun mailClear(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        EssentialsRepository.clearMail(me.uuid)
        Msg.reply(ctx.source, "mail_cleared")
        return 1
    }

    /** Aviso ao entrar: quantas mensagens novas. */
    fun mailReminder(player: ServerPlayer) {
        val count = EssentialsRepository.mail(player.uuid).size
        if (count > 0) Msg.send(player, "mail_new", mapOf("count" to count))
    }
}
