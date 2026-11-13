package com.f4xizzz.serverbuddy.modules.blacklist

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.TimeConditions
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/** O resultado de uma checagem: o golpe (já normalizado) e os contextos que o proíbem. */
class BanResult(val move: String, val contexts: List<String>)

/**
 * Regras da blacklist: quais contextos valem para um jogador e se um golpe está proibido para ele. Esta classe não
 * mexe em nada do Cobblemon (a parte de batalha fica em [BlacklistBridge]), por isso dá para testar sozinha.
 */
object BlacklistService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Blacklist")
    private const val LABEL = "Blacklist/config.json"
    private const val DEFAULT = "default"

    /** Valor de [Situation.battle] que significa "qualquer tipo de batalha" (usado para listar o que pode valer). */
    const val ANY_BATTLE = "*"

    private val warned = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var config: BlacklistConfig = BlacklistConfig()
        private set

    /** Ligado pelo módulo quando tudo está pronto; desligado, o mixin deixa todas as escolhas passarem. */
    @Volatile
    @JvmStatic
    var active: Boolean = false

    fun load() {
        warned.clear()
        config = sanitize(ConfigFiles.load("Blacklist", "config.json", BlacklistConfig::class.java) { BlacklistConfig() })
    }

    /** Arruma o que um JSON editado à mão pode deixar nulo (`"moves": null`), para o resto do código não se preocupar. */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
    internal fun sanitize(raw: BlacklistConfig): BlacklistConfig {
        val contexts = LinkedHashMap<String, BlacklistContext>()
        val source: Map<String, BlacklistContext?> = (raw.contexts as Map<String, BlacklistContext?>?) ?: emptyMap()
        for ((name, context) in source) {
            val key = name.trim()
            if (key.isEmpty()) continue
            val c = context ?: BlacklistContext()
            contexts[key] = BlacklistContext(
                (c.conditions as List<String?>?).orEmpty().filterNotNull(),
                (c.whenAny as List<String?>?).orEmpty().filterNotNull(),
                (c.moves as List<String?>?).orEmpty().filterNotNull(),
                c.message
            )
        }
        if (contexts.keys.none { it.equals(DEFAULT, ignoreCase = true) }) contexts[DEFAULT] = BlacklistContext()
        val defaults = BlacklistMessages()
        val m: BlacklistMessages = (raw.messages as BlacklistMessages?) ?: defaults
        return BlacklistConfig(
            contexts,
            BlacklistMessages(m.banned ?: defaults.banned, m.listHeader ?: defaults.listHeader, m.listLine ?: defaults.listLine, m.listEmpty ?: defaults.listEmpty)
        )
    }

    /** O nome do golpe do jeito que o Showdown escreve: minúsculo, só letras e números. */
    @JvmStatic
    fun normalize(move: String): String = move.lowercase().filter { it.isLetterOrDigit() }

    /** [entry] (da config) proíbe o golpe [move] (já normalizado)? Banir "hiddenpower" bans all of its variations. */
    private fun bans(entry: String, move: String): Boolean {
        val e = normalize(entry)
        return e.isNotEmpty() && (e == move || (e == "hiddenpower" && move.startsWith("hiddenpower")))
    }

    // ---------------------------------------------------------------- contextos

    /** O que as condições olham no jogador (separado do Minecraft para dar para testar sozinho). */
    class Situation(
        val tags: Set<String>,
        val dimension: String,
        /** "pvp", "npc", "wild", [ANY_BATTLE] (qualquer uma) ou null (fora de batalha). */
        val battle: String?,
        val hasPermission: (String) -> Boolean,
        val now: LocalDateTime = LocalDateTime.now()
    )

    fun situationOf(player: ServerPlayer, battle: String?) = Situation(
        player.tags.map { it.trim().lowercase() }.toSet(),
        player.level().dimension().location().toString(),
        battle,
        { node -> SbPermissions.check(player, node, false) }
    )

    /** O contexto [name] vale para quem está na [situation]? */
    @JvmStatic
    fun appliesTo(name: String, context: BlacklistContext, situation: Situation): Boolean {
        if (name.equals(DEFAULT, ignoreCase = true)) return true
        val all = context.conditions
        val any = context.whenAny
        // sem condição nenhuma: vale quem tem a tag com o nome do contexto (como no mod original)
        if (all.isEmpty() && any.isEmpty()) return name.trim().lowercase() in situation.tags
        return all.all { matches(it, situation) } && (any.isEmpty() || any.any { matches(it, situation) })
    }

    /** Os contextos que valem agora: o "default" e os que têm as condições verdadeiras. */
    @JvmStatic
    fun contextsFor(config: BlacklistConfig, situation: Situation): List<String> =
        config.contexts.filter { (name, context) -> appliesTo(name, context, situation) }.keys.toList()

    private fun matches(raw: String, situation: Situation): Boolean {
        var text = raw.trim()
        var negate = false
        while (text.startsWith("!")) {
            negate = !negate
            text = text.substring(1).trim()
        }
        val name = text.substringBefore(':').trim().lowercase()
        // ao listar, uma condição de batalha conta como "pode valer" (o aviso "só em PvP" mostra o resto)
        if (name == "battle" && situation.battle == ANY_BATTLE) return true
        return evaluate(name, text.substringAfter(':', "").trim(), text, situation) != negate
    }

    private fun evaluate(name: String, arg: String, text: String, situation: Situation): Boolean = when {
        name == "tag" -> arg.isNotEmpty() && arg.lowercase() in situation.tags
        name == "permission" -> arg.isNotEmpty() && situation.hasPermission(arg)
        name == "dimension" -> arg.isNotEmpty() && situation.dimension.equals(arg, ignoreCase = true)
        name == "battle" -> arg.isNotEmpty() && arg.equals(situation.battle, ignoreCase = true)
        TimeConditions.isTimeCondition(name) -> TimeConditions.evaluate(name, arg, situation.now) ?: unknown(text)
        else -> unknown(text)
    }

    private fun unknown(text: String): Boolean {
        if (warned.add(text)) LOGGER.warn("Unknown or malformed Blacklist condition: '{}' (treated as false).", text)
        return false
    }

    // ---------------------------------------------------------------- golpes

    /** O golpe [moveName] está proibido para quem está na [situation]? Devolve null se pode usar. */
    @JvmStatic
    fun check(config: BlacklistConfig, situation: Situation, moveName: String): BanResult? {
        val move = normalize(moveName)
        if (move.isEmpty()) return null
        val banning = contextsFor(config, situation).filter { name -> config.contexts.getValue(name).moves.any { bans(it, move) } }
        return if (banning.isEmpty()) null else BanResult(move, banning)
    }

    fun check(player: ServerPlayer, moveName: String, battle: String?): BanResult? =
        check(config, situationOf(player, battle), moveName)

    /** Todos os golpes proibidos nos contextos que valem para a [situation] (sem repetir, em ordem). */
    @JvmStatic
    fun bannedMoves(config: BlacklistConfig, situation: Situation): List<String> =
        contextsFor(config, situation).flatMap { name -> config.contexts.getValue(name).moves }.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()

    /** Golpes da config que o Cobblemon não conhece (provável erro de digitação), por contexto. Vazio se não dá para saber. */
    fun unknownMoves(): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        for ((name, context) in config.contexts) {
            val unknown = context.moves.filter { CobblemonMoves.exists(normalize(it)) == false }
            if (unknown.isNotEmpty()) result[name] = unknown
        }
        return result
    }

    // ---------------------------------------------------------------- edição pelos comandos

    /** Põe [move] na lista de [context] (cria o contexto se não existe). false = já estava lá. */
    fun add(context: String, move: String): Boolean {
        val normalized = normalize(move)
        if (normalized.isEmpty()) return false
        val key = config.contexts.keys.firstOrNull { it.equals(context.trim(), ignoreCase = true) } ?: context.trim()
        val current = config.contexts[key] ?: BlacklistContext()
        if (current.moves.any { normalize(it) == normalized }) return false
        replace(key, BlacklistContext(current.conditions, current.whenAny, current.moves + normalized, current.message))
        return true
    }

    /** Tira [move] da lista de [context]. false = o contexto não existe ou o golpe não estava lá. */
    fun remove(context: String, move: String): Boolean {
        val key = config.contexts.keys.firstOrNull { it.equals(context.trim(), ignoreCase = true) } ?: return false
        val current = config.contexts.getValue(key)
        val normalized = normalize(move)
        if (current.moves.none { normalize(it) == normalized }) return false
        replace(key, BlacklistContext(current.conditions, current.whenAny, current.moves.filter { normalize(it) != normalized }, current.message))
        return true
    }

    private fun replace(key: String, context: BlacklistContext) {
        val contexts = LinkedHashMap(config.contexts)
        contexts[key] = context
        config = BlacklistConfig(contexts, config.messages)
        save()
    }

    /** Grava a config atual (guarda o arquivo anterior em config.json.bak; os comentários são refeitos a partir dos @Doc). */
    private fun save() {
        val file: File = ConfigFiles.entries().firstOrNull { it.label == LABEL }?.file ?: return
        try {
            if (file.exists()) file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
            file.writeText(ConfigDocs.render(config, SbGson.defaultsWriter), Charsets.UTF_8)
        } catch (e: Exception) {
            LOGGER.error("Could not save $LABEL", e)
        }
    }
}
