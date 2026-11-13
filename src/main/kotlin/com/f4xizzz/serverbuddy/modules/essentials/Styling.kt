package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Conditions
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.commandblock.CommandBlockService
import com.f4xizzz.serverbuddy.modules.tags.TagsService
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.Placeholders
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.FilteredText
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.SignText
import org.slf4j.LoggerFactory
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * O visual do Essentials (config `styling`): o formato do chat, os avisos de entrada, saída, morte, conquista e /me, a lista de
 * jogadores (Tab) e as menções. Tudo desligado por padrão, menos as menções. As placas ficam em [SignFormatting].
 *
 * O chat formatado sai como mensagem do servidor (o Minecraft não deixa trocar a linha "<Nome> mensagem" de outro jeito sem mexer em
 * pacotes), então não dá para denunciar a mensagem pelo menu do jogo. Os outros mods que ouvem o chat continuam recebendo o evento.
 */
object Styling {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Essentials")

    /** Roda depois do mute, do chat da equipe e do gerenciador de chat: só vê o que já foi aprovado. */
    private val LATE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "styling")

    private val settings get() = EssentialsModule.config.styling

    @Volatile
    private var joining: ServerPlayer? = null

    @Volatile
    private var leaving: ServerPlayer? = null

    /** Marca que nós mesmos estamos transmitindo uma mensagem, para o ouvinte não trocar a nossa de novo. */
    private val sending: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    private var tabWasOn = false
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(Event.DEFAULT_PHASE, LATE)
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(LATE, ServerMessageEvents.AllowChatMessage { message, sender, params -> onChat(message, sender, params) })
        ServerMessageEvents.ALLOW_GAME_MESSAGE.register(ServerMessageEvents.AllowGameMessage { server, message, overlay -> onGameMessage(server, message, overlay) })
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register(ServerMessageEvents.AllowCommandMessage { message, source, params -> onCommandMessage(message, source, params) })
        ServerPlayConnectionEvents.INIT.register(ServerPlayConnectionEvents.Init { handler, _ -> joining = handler.player })
        ServerPlayConnectionEvents.DISCONNECT.register(ServerPlayConnectionEvents.Disconnect { handler, _ -> leaving = handler.player })
    }

    private fun warnOnce(key: String, error: Exception) {
        if (warned.add(key)) LOGGER.warn("Style '{}' failed, using vanilla Minecraft formatting: {}", key, error.toString())
    }

    // ---------------------------------------------------------------- montar uma linha

    private fun tagPrefix(player: ServerPlayer): String =
        if (Modules.isActive("tags")) TagsService.equippedView(player)?.def?.prefix.orEmpty() else ""

    /**
     * Monta [template] (MiniMessage) para [player]: troca %player%, %name%, %world%, %tag% e %afk%, mais os valores de [extra] e os
     * componentes de [parts] (como o %message%), e resolve os placeholders do PlaceholderAPI (%luckperms:prefix%...). O texto dos
     * jogadores entra por [parts] e NÃO passa pelo PlaceholderAPI.
     */
    internal fun render(player: ServerPlayer, template: String, parts: Map<String, Component> = emptyMap(), extra: Map<String, Any?> = emptyMap()): Component {
        val vars = LinkedHashMap<String, Any?>()
        vars["player"] = Texts.escape(ChatService.displayName(player.uuid, player.gameProfile.name))
        vars["name"] = Texts.escape(player.gameProfile.name)
        vars["world"] = player.level().dimension().location().path
        vars["tag"] = tagPrefix(player)
        vars["afk"] = if (AfkService.isAfk(player.uuid)) AfkService.label() else ""
        vars.putAll(extra)
        var text = Texts.fill(template, vars)
        val all = LinkedHashMap(parts)
        val context = PlaceholderContext.of(player)
        StylingLogic.placeholderTokens(text).forEachIndexed { index, token ->
            val resolved = Placeholders.parseText(Component.literal(token), context)
            if (resolved.string != token) {
                val key = "papi$index"
                all[key] = resolved
                text = text.replace(token, "%$key%")
            }
        }
        return Texts.render(text, emptyMap(), all)
    }

    // ---------------------------------------------------------------- chat

    private fun formatMatches(player: ServerPlayer, key: String): Boolean =
        if (key.contains(':')) Conditions.matches(key, CommandBlockService.subjectOf(player)) else SbPermissions.check(player, "group.$key", false)

    /** A linha do chat para [text] escrito por [sender]. */
    internal fun chatLine(sender: ServerPlayer, text: String): Component {
        val cfg = settings.chatFormat
        val allowed = SbPermissions.check(sender, cfg.messageFormattingPermission, 3)
        val replaced = StylingLogic.applyReplacements(text, cfg.replacements)
        val body = if (allowed) Texts.parseRestricted(StylingLogic.legacyToMiniMessage(replaced)) else Component.literal(replaced)
        val template = StylingLogic.pickFormat(cfg.formats) { formatMatches(sender, it) } ?: "%player%<gray>:</gray> %message%"
        return render(sender, template, mapOf("message" to body))
    }

    private fun onChat(message: PlayerChatMessage, sender: ServerPlayer, params: ChatType.Bound): Boolean {
        if (!EssentialsModule.ready) return true
        try {
            val text = message.signedContent()
            notifyMentions(sender, text)
            val cfg = settings.chatFormat
            if (!cfg.enabled) return true
            val line = chatLine(sender, text)
            val server = sender.server
            for (receiver in server.playerList.players) {
                if (cfg.respectIgnore && receiver.uuid != sender.uuid && ChatService.isIgnoring(receiver.uuid, sender.uuid)) continue
                receiver.sendSystemMessage(line)
            }
            server.sendSystemMessage(line)
            // quem ouve o chat de fora (ponte com o Discord, por exemplo) continua recebendo a mensagem
            ServerMessageEvents.CHAT_MESSAGE.invoker().onChatMessage(message, sender, params)
            return false
        } catch (e: Exception) {
            warnOnce("chat", e)
            return true
        }
    }

    // ---------------------------------------------------------------- entrada, saída, morte, conquista e /me

    private fun broadcast(server: MinecraftServer, component: Component) {
        if (component.string.isBlank()) return
        sending.set(true)
        try {
            server.playerList.broadcastSystemMessage(component, false)
        } finally {
            sending.set(false)
        }
    }

    private fun playerOf(server: MinecraftServer, arg: Any?): ServerPlayer? {
        val name = (arg as? Component)?.string ?: arg?.toString() ?: return null
        return server.playerList.players.firstOrNull { it.displayName?.string == name || it.gameProfile.name == name }
    }

    private fun onGameMessage(server: MinecraftServer, message: Component, overlay: Boolean): Boolean {
        if (overlay || sending.get() || !EssentialsModule.ready) return true
        val events = settings.events
        if (!events.enabled) return true
        val contents = message.contents as? TranslatableContents ?: return true
        val key = contents.key
        try {
            val replacement: Component = when {
                key == "multiplayer.player.joined" || key == "multiplayer.player.joined.renamed" -> joinMessage() ?: return true
                key == "multiplayer.player.left" -> leaveMessage() ?: return true
                key.startsWith("death.") -> deathMessage(server, message, contents) ?: return true
                key.startsWith("chat.type.advancement.") -> advancementMessage(server, key, contents) ?: return true
                else -> return true
            }
            broadcast(server, replacement)
            return false
        } catch (e: Exception) {
            warnOnce(key, e)
            return true
        }
    }

    /** Quem acabou de entrar pela primeira vez (marcado no evento de entrada, que pode vir antes ou depois da mensagem do Minecraft). */
    private val firstJoiners: MutableSet<java.util.UUID> = ConcurrentHashMap.newKeySet()

    fun markFirstJoin(uuid: java.util.UUID) {
        firstJoiners.add(uuid)
    }

    fun onLeave(uuid: java.util.UUID) {
        firstJoiners.remove(uuid)
    }

    private fun joinMessage(): Component? {
        val player = joining ?: return null
        val events = settings.events
        // se o registro do jogador ainda não existe, é a primeira vez; se já existe, a marca do evento de entrada diz
        val first = firstJoiners.remove(player.uuid) || EssentialsRepository.player(player.uuid) == null
        val template = if (first && events.firstJoin.isNotBlank()) events.firstJoin else events.join
        return if (template.isBlank()) Component.empty() else render(player, template)
    }

    private fun leaveMessage(): Component? {
        val player = leaving ?: return null
        leaving = null
        val template = settings.events.leave
        return if (template.isBlank()) Component.empty() else render(player, template)
    }

    private fun deathMessage(server: MinecraftServer, message: Component, contents: TranslatableContents): Component? {
        val victim = playerOf(server, contents.args.firstOrNull()) ?: return null
        val template = settings.events.death
        return if (template.isBlank()) Component.empty() else render(victim, template, mapOf("message" to message))
    }

    private fun advancementMessage(server: MinecraftServer, key: String, contents: TranslatableContents): Component? {
        val player = playerOf(server, contents.args.getOrNull(0)) ?: return null
        val title = contents.args.getOrNull(1) as? Component ?: return null
        val events = settings.events
        val template = when (key.substringAfterLast('.')) {
            "task" -> events.advancementTask
            "goal" -> events.advancementGoal
            "challenge" -> events.advancementChallenge
            else -> return null
        }
        if (template.isBlank()) return Component.empty()
        // a descrição vem na dica (hover) do título: "Título\nDescrição"
        val description = title.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT)?.siblings?.lastOrNull() ?: Component.empty()
        return render(player, template, mapOf("title" to title, "description" to description))
    }

    private fun onCommandMessage(message: PlayerChatMessage, source: CommandSourceStack, params: ChatType.Bound): Boolean {
        if (!EssentialsModule.ready || !settings.events.enabled) return true
        if (!params.chatType().`is`(ChatType.EMOTE_COMMAND)) return true
        val player = source.player ?: return true
        try {
            val template = settings.events.emote
            if (template.isBlank()) return false
            val line = render(player, template, mapOf("message" to Component.literal(message.signedContent())))
            val server = source.server
            val respect = settings.chatFormat.respectIgnore
            for (receiver in server.playerList.players) {
                if (respect && receiver.uuid != player.uuid && ChatService.isIgnoring(receiver.uuid, player.uuid)) continue
                receiver.sendSystemMessage(line)
            }
            server.sendSystemMessage(line)
            return false
        } catch (e: Exception) {
            warnOnce("emote", e)
            return true
        }
    }

    // ---------------------------------------------------------------- lista de jogadores (Tab)

    /** O nome do jogador na lista (o mixin do `getTabListDisplayName` pergunta aqui). null = deixa o do Minecraft. */
    @JvmStatic
    fun tabName(player: ServerPlayer): Component? {
        if (!EssentialsModule.ready) return null
        val cfg = settings.tabList
        if (!cfg.enabled || cfg.playerFormat.isBlank()) return null
        return try {
            render(player, cfg.playerFormat)
        } catch (e: Exception) {
            warnOnce("tab_name", e)
            null
        }
    }

    private fun tabVars(server: MinecraftServer, player: ServerPlayer): Map<String, Any?> {
        val tps = PlayerExtras.tps(server)
        val color = if (tps >= 19.0) "<green>" else if (tps >= 15.0) "<yellow>" else "<red>"
        return mapOf(
            "online" to server.playerList.playerCount, "max" to server.maxPlayers, "ping" to player.connection.latency(),
            "tps" to color + String.format(java.util.Locale.ROOT, "%.1f", tps) + "</" + color.trim('<', '>') + ">"
        )
    }

    private fun sendHeaderFooter(server: MinecraftServer, player: ServerPlayer) {
        val cfg = settings.tabList
        val vars = tabVars(server, player)
        val header = Texts.joinLines(cfg.header.map { render(player, it, extra = vars) })
        val footer = Texts.joinLines(cfg.footer.map { render(player, it, extra = vars) })
        player.connection.send(ClientboundTabListPacket(header, footer))
    }

    /** A cada tick: de [update_ticks] em [update_ticks], atualiza cabeçalho, rodapé e nomes. */
    fun tick(server: MinecraftServer) {
        val cfg = settings.tabList
        if (!cfg.enabled) {
            if (tabWasOn) {
                tabWasOn = false
                for (player in server.playerList.players) player.connection.send(ClientboundTabListPacket(Component.empty(), Component.empty()))
                refreshNames(server)
            }
            return
        }
        tabWasOn = true
        if (server.tickCount % cfg.updateTicks.coerceAtLeast(5) != 0) return
        try {
            for (player in server.playerList.players) sendHeaderFooter(server, player)
            if (cfg.playerFormat.isNotBlank()) refreshNames(server)
        } catch (e: Exception) {
            warnOnce("tab", e)
        }
    }

    /** One player's Tab name changed (AFK on or off): send it now instead of waiting for the next cycle. */
    internal fun refreshName(player: ServerPlayer) {
        val cfg = settings.tabList
        if (!EssentialsModule.ready || !cfg.enabled || cfg.playerFormat.isBlank()) return
        player.server.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), listOf(player)))
    }

    private fun refreshNames(server: MinecraftServer) {
        val players = server.playerList.players
        if (players.isEmpty()) return
        server.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), players))
    }

    /** Quem entra já recebe o cabeçalho e o rodapé, sem esperar o próximo ciclo. */
    fun onJoin(player: ServerPlayer) {
        if (!settings.tabList.enabled) return
        try {
            sendHeaderFooter(player.server, player)
        } catch (e: Exception) {
            warnOnce("tab_join", e)
        }
    }

    // ---------------------------------------------------------------- menções e avisos sonoros

    private fun notifyMentions(sender: ServerPlayer, text: String) {
        if (!settings.mentions.enabled) return
        for (receiver in sender.server.playerList.players) {
            if (receiver.uuid == sender.uuid || ChatService.isIgnoring(receiver.uuid, sender.uuid)) continue
            val names = listOf(receiver.gameProfile.name, ChatService.displayName(receiver.uuid, receiver.gameProfile.name))
            if (names.any { StylingLogic.mentions(text, it) }) Notifications.notify(receiver, chat = true)
        }
    }
}

/**
 * Cores e formatação nas placas, para quem tem a permissão. O mixin do `SignBlockEntity.setMessages` pergunta aqui. Só as tags seguras
 * do [Texts.parseRestricted] valem (sem clique nem hover: uma placa com clique roda comandos com permissão de equipe).
 */
object SignFormatting {
    @JvmStatic
    fun format(player: Player, messages: List<FilteredText>, text: SignText): SignText? {
        if (!EssentialsModule.ready || player !is ServerPlayer) return null
        val cfg = EssentialsModule.config.styling.signs
        if (!cfg.enabled || !SbPermissions.check(player, cfg.permission, 2)) return null
        return try {
            var out = text
            for (i in messages.indices) {
                val line = if (player.isTextFilteringEnabled) messages[i].filteredOrEmpty() else messages[i].raw()
                out = out.setMessage(i, Texts.parseRestricted(StylingLogic.legacyToMiniMessage(line)))
            }
            out
        } catch (e: Exception) {
            null
        }
    }
}
