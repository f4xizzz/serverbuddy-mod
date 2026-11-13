package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.CommandHooks
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
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.UserBanListEntry
import net.minecraft.world.BossEvent
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.level.GameType
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Cela: quem está preso, até quando e em qual cela. */
object JailService {
    private val jailed = ConcurrentHashMap<UUID, JailRecord>()
    @Volatile
    var jails: Map<String, SavedLocation> = emptyMap()
        private set

    fun load() {
        jails = EssentialsRepository.jails()
        jailed.clear()
        for (record in EssentialsRepository.jailed()) jailed[record.uuid] = record
    }

    fun reloadJails() {
        jails = EssentialsRepository.jails()
    }

    fun isJailed(uuid: UUID): Boolean = jailed.containsKey(uuid)

    /** O registro de quem está preso (para o /checkjail), ou null se não está. */
    fun recordOf(uuid: UUID): JailRecord? = jailed[uuid]

    private fun left(record: JailRecord): String = if (record.until == Long.MAX_VALUE) "forever" else "Time left: ${Durations.format(record.until - System.currentTimeMillis())}."

    /** Prende [uuid] na cela [jail]. Se a pessoa está online, vai para lá na hora. [by] = quem prendeu (aparece no /checkjail). */
    fun jail(server: MinecraftServer, uuid: UUID, jail: String, until: Long, reason: String?, by: String? = null) {
        val online = server.playerList.getPlayer(uuid)
        val previous = jailed[uuid]?.previousMode ?: online?.gameMode?.gameModeForPlayer?.getName()
        val record = JailRecord(uuid, jail, until, reason, previous)
        jailed[uuid] = record
        EssentialsRepository.saveJailed(record)
        if (by != null) EssentialsRepository.saveJailInfo(uuid, by, System.currentTimeMillis())
        online?.let { apply(it, record, announce = true) }
    }

    private fun apply(player: ServerPlayer, record: JailRecord, announce: Boolean) {
        val place = jails[record.jail] ?: return
        player.setGameMode(GameType.ADVENTURE)
        place.teleport(player)
        if (announce) Msg.send(player, "jail_you", mapOf("reason" to (record.reason?.let { ": " + Texts.escape(it) } ?: ""), "time" to left(record)))
    }

    fun release(server: MinecraftServer, uuid: UUID): Boolean {
        val record = jailed.remove(uuid) ?: return false
        EssentialsRepository.deleteJailed(uuid)
        server.playerList.getPlayer(uuid)?.let { player ->
            record.previousMode?.let { name -> GameType.byName(name, GameType.SURVIVAL).let { player.setGameMode(it) } }
            TeleportCommands.spawn()?.teleport(player)
            Msg.send(player, "unjail_you")
        }
        return true
    }

    fun onJoin(player: ServerPlayer) {
        val record = jailed[player.uuid] ?: return
        if (record.until != Long.MAX_VALUE && record.until <= System.currentTimeMillis()) {
            release(player.server, player.uuid)
        } else {
            apply(player, record, announce = true)
        }
    }

    /** A cada segundo: solta quem cumpriu a pena e traz de volta quem saiu da cela. */
    fun tick(server: MinecraftServer) {
        if (jailed.isEmpty()) return
        val radius = EssentialsModule.config.jail.radius
        for (record in jailed.values.toList()) {
            if (record.until != Long.MAX_VALUE && record.until <= System.currentTimeMillis()) {
                release(server, record.uuid)
                continue
            }
            val player = server.playerList.getPlayer(record.uuid) ?: continue
            val place = jails[record.jail] ?: continue
            if (place.distanceSquared(player) > radius.toDouble() * radius) place.teleport(player)
        }
    }

    /** Preso só usa os comandos permitidos. */
    fun blocks(source: CommandSourceStack, command: String): Boolean {
        val player = source.player ?: return false
        if (!jailed.containsKey(player.uuid)) return false
        val root = command.trim().removePrefix("/").substringBefore(' ').lowercase()
        if (root in EssentialsModule.config.jail.allowedCommands.map { it.lowercase() }) return false
        Msg.send(player, "jail_command")
        return true
    }
}

/** Reinício agendado com contagem regressiva, avisos e barra de tempo. */
object RestartService {
    private var endsAt = 0L
    private var bar: ServerBossEvent? = null
    private val warned = HashSet<Int>()

    fun scheduled(): Boolean = endsAt > 0

    fun schedule(server: MinecraftServer, millis: Long) {
        cancel()
        endsAt = System.currentTimeMillis() + millis
        warned.clear()
        bar = ServerBossEvent(Component.literal("Restart"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS).also { b -> server.playerList.players.forEach { b.addPlayer(it) } }
        total = millis
        server.playerList.broadcastSystemMessage(Texts.render(Msg.text("restart_started", mapOf("time" to Durations.format(millis)))), false)
    }

    private var total = 1L

    fun cancel(): Boolean {
        val was = endsAt > 0
        endsAt = 0
        bar?.removeAllPlayers()
        bar = null
        return was
    }

    fun onJoin(player: ServerPlayer) {
        bar?.addPlayer(player)
    }

    fun tick(server: MinecraftServer) {
        if (endsAt <= 0) return
        val left = endsAt - System.currentTimeMillis()
        val seconds = ((left + 999) / 1000).toInt()
        bar?.let {
            it.progress = (left.toFloat() / total).coerceIn(0f, 1f)
            it.name = Component.literal("Restart in ${Durations.format(left)}")
        }
        if (left <= 0) {
            val message = Texts.render(EssentialsModule.config.restart.kickMessage)
            endsAt = 0
            bar?.removeAllPlayers()
            for (player in server.playerList.players.toList()) player.connection.disconnect(message)
            server.halt(false)
            return
        }
        val marks = EssentialsModule.config.restart.warnings
        if (seconds in marks && warned.add(seconds)) {
            server.playerList.broadcastSystemMessage(Texts.render(Msg.text("restart_warning", mapOf("time" to Durations.format(left)))), false)
        }
    }
}

/** Mostra e deixa editar o inventário de outro jogador (o que a pessoa vê é o que o alvo tem, ao vivo). */
class InvseeGui(viewer: ServerPlayer, private val victim: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x5, viewer, false) {
    init {
        title = Component.literal("Inventory of ${victim.gameProfile.name}")
        // linha de cima: capacete, peitoral, calça, bota e a mão secundária
        listOf(39, 38, 37, 36, 40).forEachIndexed { i, index -> setSlotRedirect(i, Slot(victim.inventory, index, 0, 0)) }
        // linhas 2 a 4: o inventário; última linha: a barra de itens
        for (i in 9..35) setSlotRedirect(i, Slot(victim.inventory, i, 0, 0))
        for (i in 0..8) setSlotRedirect(36 + i, Slot(victim.inventory, i, 0, 0))
    }
}

/** /invsee, /enderchest, /tempban, cela, anotações, /sudo, /restart e /commandspy. */
object StaffCommands {
    private val spies: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun hook() {
        CommandHooks.register { source, command ->
            val player = source.player
            if (player != null) {
                // quem fala comando conta como ativo (AFK) e a equipe que espia vê o comando
                // (but /afk itself is not activity: it would take the player out of AFK first and then put them right back in)
                if (!AfkService.isAfkCommand(command)) AfkService.activity(player)
                for (spyId in spies) {
                    if (spyId == player.uuid) continue
                    source.server.playerList.getPlayer(spyId)?.sendSystemMessage(Texts.render(Msg.text("commandspy_line", mapOf("player" to player.gameProfile.name, "command" to Texts.escape(command)))))
                }
            }
            JailService.blocks(source, command)
        }
    }

    fun onLeave(uuid: UUID) {
        spies.remove(uuid)
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "invsee", level = 3) { then(playerArg().executes { invsee(it) }) }
        register(dispatcher, "enderchest", aliases = listOf("ec"), level = 3) {
            executes { enderChest(it, null) }
            then(playerArg().executes { enderChest(it, target(it)) })
        }
        register(dispatcher, "tempban", level = 3) {
            then(word("jogador").then(word("tempo").executes { tempBan(it, "") }.then(text("motivo").executes { tempBan(it, StringArgumentType.getString(it, "motivo")) })))
        }
        register(dispatcher, "tempbanip", level = 3) {
            then(word("alvo").suggests(suggestWords { ServerHolder.server?.playerList?.playerNamesArray?.toList() ?: emptyList() }).then(word("tempo").executes { tempBanIp(it, "") }.then(text("motivo").executes { tempBanIp(it, StringArgumentType.getString(it, "motivo")) })))
        }
        register(dispatcher, "setjail", level = 3) { then(word("nome").executes { setJail(it) }) }
        register(dispatcher, "deljail", level = 3) { then(word("nome").suggests(suggestWords { JailService.jails.keys }).executes { delJail(it) }) }
        register(dispatcher, "jails", level = 3) { executes { listJails(it) } }
        register(dispatcher, "jail", level = 3) {
            then(word("jogador").executes { jail(it, "") }.then(text("detalhes").executes { jail(it, StringArgumentType.getString(it, "detalhes")) }))
        }
        register(dispatcher, "unjail", level = 3) { then(word("jogador").executes { unjail(it) }) }
        register(dispatcher, "notes", level = 3) {
            then(
                word("jogador").executes { notes(it) }
                    .then(Commands.literal("add").then(text("texto").executes { addNote(it) }))
                    .then(Commands.literal("clear").executes { clearNotes(it) })
            )
        }
        register(dispatcher, "sudo", level = 3) { then(playerArg().then(text("comando").executes { sudo(it) })) }
        register(dispatcher, "restart", level = 3) {
            executes { restart(it, "60s") }
            then(Commands.literal("cancel").executes { restartCancel(it) })
            then(word("tempo").executes { restart(it, StringArgumentType.getString(it, "tempo")) })
        }
        register(dispatcher, "commandspy", aliases = listOf("cspy"), level = 3) { executes { commandSpy(it) } }
    }

    private fun invsee(ctx: CommandContext<CommandSourceStack>): Int {
        val staff = EssentialsCommands.player(ctx) ?: return 0
        val victim = target(ctx)
        if (victim.uuid == staff.uuid) {
            Msg.fail(ctx.source, "invsee_self")
            return 0
        }
        InvseeGui(staff, victim).open()
        return 1
    }

    private fun enderChest(ctx: CommandContext<CommandSourceStack>, other: ServerPlayer?): Int {
        val staff = EssentialsCommands.player(ctx) ?: return 0
        val victim = other ?: staff
        staff.openMenu(SimpleMenuProvider({ id, inventory, _ -> ChestMenu.threeRows(id, inventory, victim.enderChestInventory) }, Component.literal("Ender Chest of ${victim.gameProfile.name}")))
        return 1
    }

    private fun tempBan(ctx: CommandContext<CommandSourceStack>, reason: String): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val duration = Durations.parseMillis(StringArgumentType.getString(ctx, "tempo"))
        if (duration == null || duration <= 0) {
            Msg.fail(ctx.source, "no_number")
            return 0
        }
        val server = ctx.source.server
        val profile = server.profileCache?.get(name)?.orElse(null) ?: server.playerList.getPlayerByName(name)?.gameProfile
        if (profile == null) {
            Msg.fail(ctx.source, "tempban_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val why = reason.trim().ifEmpty { "No reason" }
        val entry = UserBanListEntry(profile, Date(), ctx.source.textName, Date(System.currentTimeMillis() + duration), why)
        server.playerList.bans.add(entry)
        val time = Durations.format(duration)
        server.playerList.getPlayer(profile.id)?.connection?.disconnect(Texts.render(Msg.text("tempban_screen", mapOf("reason" to Texts.escape(why), "time" to time))))
        Msg.reply(ctx.source, "tempban_done", mapOf("player" to profile.name, "time" to time, "reason" to Texts.escape(why)), broadcast = true)
        return 1
    }

    private val IP_PATTERN = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+:[0-9a-fA-F:]*$")

    /** /tempbanip <jogador online|endereço IP> <tempo> [motivo]: bane o endereço por um tempo e expulsa quem está entrando por ele. */
    private fun tempBanIp(ctx: CommandContext<CommandSourceStack>, reason: String): Int {
        val raw = StringArgumentType.getString(ctx, "alvo")
        val duration = Durations.parseMillis(StringArgumentType.getString(ctx, "tempo"))
        if (duration == null || duration <= 0) {
            Msg.fail(ctx.source, "no_number")
            return 0
        }
        val server = ctx.source.server
        val ip = if (IP_PATTERN.matches(raw)) raw else server.playerList.getPlayerByName(raw)?.ipAddress
        if (ip.isNullOrBlank()) {
            Msg.fail(ctx.source, "tempbanip_unknown", mapOf("target" to Texts.escape(raw)))
            return 0
        }
        val why = reason.trim().ifEmpty { "No reason" }
        val entry = net.minecraft.server.players.IpBanListEntry(ip, Date(), ctx.source.textName, Date(System.currentTimeMillis() + duration), why)
        server.playerList.ipBans.add(entry)
        val time = Durations.format(duration)
        for (victim in server.playerList.getPlayersWithAddress(ip).toList()) {
            victim.connection.disconnect(Texts.render(Msg.text("tempban_screen", mapOf("reason" to Texts.escape(why), "time" to time))))
        }
        Msg.reply(ctx.source, "tempbanip_done", mapOf("ip" to ip, "time" to time, "reason" to Texts.escape(why)), broadcast = true)
        return 1
    }

    // ---------------------------------------------------------------- cela

    private fun setJail(ctx: CommandContext<CommandSourceStack>): Int {
        val staff = EssentialsCommands.player(ctx) ?: return 0
        val name = EssentialsCommands.cleanName(StringArgumentType.getString(ctx, "nome"))
        if (name == null) {
            Msg.fail(ctx.source, "home_invalid_name")
            return 0
        }
        EssentialsRepository.saveJail(name, SavedLocation.of(staff))
        JailService.reloadJails()
        Msg.reply(ctx.source, "jail_set", mapOf("name" to name))
        return 1
    }

    private fun delJail(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "nome").lowercase()
        if (!EssentialsRepository.deleteJail(name)) {
            Msg.fail(ctx.source, "jail_unknown", mapOf("name" to Texts.escape(name)))
            return 0
        }
        JailService.reloadJails()
        Msg.reply(ctx.source, "jail_deleted", mapOf("name" to name))
        return 1
    }

    private fun listJails(ctx: CommandContext<CommandSourceStack>): Int {
        val names = JailService.jails.keys
        if (names.isEmpty()) {
            Msg.reply(ctx.source, "jail_none")
            return 0
        }
        Msg.reply(ctx.source, "jails_list", mapOf("count" to names.size, "jails" to names.joinToString(", ")))
        return names.size
    }

    private fun jail(ctx: CommandContext<CommandSourceStack>, details: String): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val cell = JailService.jails.keys.firstOrNull()
        if (cell == null) {
            Msg.fail(ctx.source, "jail_none")
            return 0
        }
        val parts = details.trim().split(" ", limit = 2)
        val duration = Durations.parseMillis(parts.firstOrNull())
        val reason = (if (duration != null) parts.getOrNull(1) else details).orEmpty().trim().ifEmpty { null }
        JailService.jail(ctx.source.server, found.first, cell, if (duration == null) Long.MAX_VALUE else System.currentTimeMillis() + duration, reason, ctx.source.textName)
        Msg.reply(ctx.source, "jail_done", mapOf("player" to found.second, "jail" to cell, "time" to if (duration == null) "forever" else "por ${Durations.format(duration)}"), broadcast = true)
        return 1
    }

    private fun unjail(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null || !JailService.release(ctx.source.server, found.first)) {
            Msg.fail(ctx.source, "jail_not_jailed", mapOf("player" to Texts.escape(name)))
            return 0
        }
        Msg.reply(ctx.source, "unjail_done", mapOf("player" to found.second), broadcast = true)
        return 1
    }

    // ---------------------------------------------------------------- anotações, sudo, reinício, espião

    private fun notes(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val list = EssentialsRepository.notes(found.first)
        if (list.isEmpty()) {
            Msg.reply(ctx.source, "notes_none", mapOf("player" to found.second))
            return 0
        }
        list.forEachIndexed { i, note -> Msg.reply(ctx.source, "notes_line", mapOf("id" to i + 1, "when" to ChatService.whenText(note.createdAt), "author" to note.author, "text" to Texts.escape(note.text))) }
        return list.size
    }

    private fun addNote(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        EssentialsRepository.addNote(found.first, ctx.source.textName, StringArgumentType.getString(ctx, "texto"))
        Msg.reply(ctx.source, "notes_added", mapOf("player" to found.second))
        return 1
    }

    private fun clearNotes(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name)))
            return 0
        }
        EssentialsRepository.clearNotes(found.first)
        Msg.reply(ctx.source, "notes_cleared", mapOf("player" to found.second))
        return 1
    }

    private fun sudo(ctx: CommandContext<CommandSourceStack>): Int {
        val victim = target(ctx)
        val command = StringArgumentType.getString(ctx, "comando").removePrefix("/")
        ctx.source.server.commands.performPrefixedCommand(victim.createCommandSourceStack(), command)
        Msg.reply(ctx.source, "sudo_done", mapOf("player" to victim.gameProfile.name))
        return 1
    }

    private fun restart(ctx: CommandContext<CommandSourceStack>, time: String): Int {
        val millis = Durations.parseMillis(time)
        if (millis == null || millis < 1000) {
            Msg.fail(ctx.source, "no_number")
            return 0
        }
        RestartService.schedule(ctx.source.server, millis)
        return 1
    }

    private fun restartCancel(ctx: CommandContext<CommandSourceStack>): Int {
        if (!RestartService.cancel()) {
            Msg.fail(ctx.source, "restart_none")
            return 0
        }
        Msg.reply(ctx.source, "restart_cancelled", broadcast = true)
        return 1
    }

    private fun commandSpy(ctx: CommandContext<CommandSourceStack>): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        if (spies.remove(me.uuid)) {
            Msg.send(me, "commandspy_off")
        } else {
            spies.add(me.uuid)
            Msg.send(me, "commandspy_on")
        }
        return 1
    }
}
