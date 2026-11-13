package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.LegacyText
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.google.gson.Gson
import com.mojang.authlib.GameProfile
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.status.ServerStatus
import net.minecraft.server.players.PlayerList
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/** O resultado de escolher o perfil e montar tudo que vai na lista de servidores. */
class MotdResolved(
    val profileName: String,
    val description: Component,
    val online: Int,
    val max: Int,
    val hideCount: Boolean,
    val hover: List<String>?,
    val favicon: ServerStatus.Favicon?
)

/**
 * Escolhe o perfil de MOTD que vale agora e monta o [ServerStatus] da lista de servidores.
 *
 * Atenção: [status] roda na thread de rede (a do ping), não na do servidor; por isso só lê estado simples e usa o
 * status que o próprio servidor já tem pronto (jogadores online, máximo, lista de jogadores).
 */
object MotdService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Motd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val STATE_GSON = Gson()

    class State(val maintenance: Boolean = false)

    @Volatile
    var config: MotdConfig = MotdConfig()
        private set

    /** Ligado = o módulo subiu e o arquivo diz `enabled: true`. */
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var maintenance: Boolean = false
        private set

    @Volatile
    private var icons: Map<String, ServerStatus.Favicon> = emptyMap()
    private val counter = AtomicInteger()

    val folder: File get() = ConfigFiles.dir("Motd")
    private val iconsFolder: File get() = File(folder, "icons")
    private val stateFile: File get() = File(folder, "state.json")

    // ---------------------------------------------------------------- carregar

    fun load() {
        config = ConfigFiles.load("Motd", "config.json", MotdConfig::class.java) { MotdConfig() }
        active = config.enabled
        loadIcons()
        loadState()
    }

    private fun loadIcons() {
        iconsFolder.mkdirs()
        val found = LinkedHashMap<String, ServerStatus.Favicon>()
        iconsFolder.listFiles { f -> f.isFile && f.name.endsWith(".png", ignoreCase = true) }?.sortedBy { it.name }?.forEach { file ->
            try {
                val image = ImageIO.read(file)
                if (image == null || image.width != 64 || image.height != 64) {
                    LOGGER.warn("The icon {} must be a 64x64 PNG (it is {}x{}); it was ignored.", file.name, image?.width, image?.height)
                } else {
                    found[file.name.removeSuffix(".png").removeSuffix(".PNG")] = ServerStatus.Favicon(Files.readAllBytes(file.toPath()))
                }
            } catch (e: Exception) {
                LOGGER.warn("Could not read the icon {}: {}", file.name, e.toString())
            }
        }
        icons = found
    }

    private fun loadState() {
        maintenance = try {
            if (stateFile.exists()) STATE_GSON.fromJson(stateFile.readText(Charsets.UTF_8), State::class.java)?.maintenance ?: false else false
        } catch (e: Exception) {
            false
        }
    }

    private fun saveState() {
        try {
            stateFile.writeText(STATE_GSON.toJson(State(maintenance)), Charsets.UTF_8)
        } catch (e: Exception) {
            LOGGER.warn("Could not save the maintenance state: {}", e.toString())
        }
    }

    // ---------------------------------------------------------------- manutenção

    /** Liga ou desliga a manutenção; ao ligar, expulsa quem não pode ficar (se a config pedir). Devolve quantos foram expulsos. */
    fun setMaintenance(on: Boolean): Int {
        maintenance = on
        saveState()
        var kicked = 0
        val server = ServerHolder.server
        if (on && config.maintenance.kickOnlineWhenEnabled && server != null) {
            val message = Texts.parse(config.maintenance.kickMessage)
            for (player in server.playerList.players.toList()) {
                if (!canStayDuringMaintenance(server.playerList, player.gameProfile)) {
                    player.connection.disconnect(message)
                    kicked++
                }
            }
        }
        return kicked
    }

    /** Operadores e a lista `maintenance.allowed` (nome ou UUID) entram durante a manutenção. */
    fun canStayDuringMaintenance(playerList: PlayerList, profile: GameProfile): Boolean {
        if (playerList.isOp(profile)) return true
        return config.maintenance.allowed.any { it.equals(profile.name, ignoreCase = true) || it.equals(profile.id.toString(), ignoreCase = true) }
    }

    // ---------------------------------------------------------------- escolher e montar

    /** Os perfis em ordem e o primeiro que casa com [ctx] (ou o pedido em [forced]). */
    fun pickProfile(ctx: MotdContext, forced: String? = null): Pair<String, MotdProfile>? {
        if (forced != null) {
            val key = config.profiles.keys.firstOrNull { it.equals(forced, ignoreCase = true) } ?: return null
            return key to config.profiles.getValue(key)
        }
        for ((name, profile) in config.profiles) {
            if (MotdConditions.profileMatches(profile.conditions, profile.whenAny, ctx)) return name to profile
        }
        return null
    }

    fun context(online: Int, max: Int): MotdContext {
        val whitelist = ServerHolder.server?.playerList?.isUsingWhitelist ?: false
        return MotdContext(online, max, whitelist, maintenance, LocalDateTime.now())
    }

    /** Monta o resultado para o ping (ou para o `preview`, com [forced] = nome de um perfil). */
    fun resolve(vanilla: ServerStatus, forced: String? = null): MotdResolved? {
        val vanillaPlayers = vanilla.players().orElse(null)
        val realOnline = vanillaPlayers?.online() ?: 0
        val realMax = vanillaPlayers?.max() ?: 0
        val ctx = context(realOnline, realMax)
        val (name, profile) = pickProfile(ctx, forced) ?: return null
        if (profile.motds.isEmpty()) return null

        val entry = when (profile.mode) {
            Rotation.RANDOM -> profile.motds[ThreadLocalRandom.current().nextInt(profile.motds.size)]
            Rotation.SEQUENTIAL -> profile.motds[Math.floorMod(counter.getAndIncrement(), profile.motds.size)]
        }

        // contagem de jogadores
        val cfg = profile.players
        var online = realOnline
        var max = realMax
        cfg.fake?.let { text ->
            val modifier = PlayerCountModifier.parse(text)
            if (modifier != null) online = modifier(online) else LOGGER.warn("The \"fake\" value '{}' of profile '{}' makes no sense.", text, name)
        }
        cfg.max?.let { max = it }
        cfg.justXMore?.let { max = online + it }
        if (!cfg.allowExceedingMax) online = minOf(online, max)

        val text = fill(entry.line1, online, max) + "\n" + fill(entry.line2, online, max)
        val hover = if (cfg.hoverLines.isNotEmpty()) cfg.hoverLines.map { LegacyText.of(Texts.parse(fill(it, online, max))) } else null

        return MotdResolved(
            profileName = name,
            description = Texts.parse(text),
            online = online,
            max = max,
            hideCount = cfg.hideCount,
            hover = if (cfg.hideHover) emptyList() else hover,
            favicon = pickIcon(entry.icon)
        )
    }

    /** O ServerStatus para o ping. Se nada casa (ou o módulo está desligado) devolve o do próprio servidor. */
    fun status(vanilla: ServerStatus): ServerStatus {
        if (!active) return vanilla
        val resolved = resolve(vanilla) ?: return vanilla
        val vanillaPlayers = vanilla.players().orElse(null)
        val players: Optional<ServerStatus.Players> = if (resolved.hideCount) {
            Optional.empty()
        } else {
            val sample: List<GameProfile> = resolved.hover?.map { GameProfile(UUID.nameUUIDFromBytes(it.toByteArray()), it) }
                ?: vanillaPlayers?.sample()
                ?: emptyList()
            Optional.of(ServerStatus.Players(resolved.max, resolved.online, sample))
        }
        return ServerStatus(
            resolved.description,
            players,
            vanilla.version(),
            resolved.favicon?.let { Optional.of(it) } ?: vanilla.favicon(),
            vanilla.enforcesSecureChat()
        )
    }

    private fun pickIcon(name: String): ServerStatus.Favicon? {
        val key = name.trim()
        return when {
            key.isEmpty() || key.equals("server", ignoreCase = true) || key.equals("default", ignoreCase = true) -> null
            key.equals("random", ignoreCase = true) -> icons.values.toList().let { if (it.isEmpty()) null else it[ThreadLocalRandom.current().nextInt(it.size)] }
            else -> icons[key] ?: icons.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        }
    }

    /** Troca os valores do texto. `<online_players>` e `<max_players>` são os do MiniMOTD. */
    fun fill(text: String, online: Int, max: Int): String {
        val now = LocalDateTime.now()
        return text
            .replace("%online%", online.toString()).replace("<online_players>", online.toString())
            .replace("%max%", max.toString()).replace("<max_players>", max.toString())
            .replace("%time%", now.format(TIME))
            .replace("%date%", now.format(DATE))
    }
}
