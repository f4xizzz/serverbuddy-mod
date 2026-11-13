package com.f4xizzz.serverbuddy.modules.commandblock

import com.f4xizzz.serverbuddy.core.CommandHooks
import com.f4xizzz.serverbuddy.core.Conditions
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.tags.TagsService
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** A regra que barrou o comando e a entrada da lista "commands" que casou. */
class BlockHit(val name: String, val rule: CommandBlockRule, val entry: String)

/**
 * Bloqueio de comandos por condição (tag, permissão, dimensão, horário...). Escuta todo comando digitado por jogador pelo
 * [CommandHooks] (o mesmo gancho da cela do essentials) e cancela os que caem numa regra.
 */
object CommandBlockService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/CommandBlock")
    const val BYPASS = "serverbuddy.commandblock.bypass"
    const val ADMIN = "serverbuddy.commandblock.admin"

    private val warned = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var config: CommandBlockConfig = CommandBlockConfig()
        private set

    fun load() {
        warned.clear()
        config = sanitize(ConfigFiles.load("CommandBlock", "config.json", CommandBlockConfig::class.java) { CommandBlockConfig() })
        val broken = config.rules.filter { (_, rule) -> rule.enabled && rule.commands.isNotEmpty() && rule.conditions.isEmpty() && rule.whenAny.isEmpty() }.keys
        for (name in broken) LOGGER.warn("Rule '{}' has no conditions (when / when_any): it never blocks.", name)
        LOGGER.info("Command blocking: {} rule(s).", config.rules.size)
    }

    fun register() {
        CommandHooks.register { source, command -> onCommand(source, command) }
    }

    /** Arruma o que um JSON editado à mão pode deixar nulo (`"commands": null`), para o resto do código não se preocupar. */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
    internal fun sanitize(raw: CommandBlockConfig): CommandBlockConfig {
        val rules = LinkedHashMap<String, CommandBlockRule>()
        val source: Map<String, CommandBlockRule?> = (raw.rules as Map<String, CommandBlockRule?>?) ?: emptyMap()
        for ((name, rule) in source) {
            val key = name.trim()
            if (key.isEmpty() || rule == null) continue
            rules[key] = CommandBlockRule(
                rule.enabled,
                (rule.commands as List<String?>?).orEmpty().filterNotNull().map { it.trim() }.filter { it.isNotEmpty() },
                (rule.conditions as List<String?>?).orEmpty().filterNotNull(),
                (rule.whenAny as List<String?>?).orEmpty().filterNotNull(),
                (rule.except as List<String?>?).orEmpty().filterNotNull(),
                rule.message
            )
        }
        val defaults = CommandBlockMessages()
        val m: CommandBlockMessages = (raw.messages as CommandBlockMessages?) ?: defaults
        return CommandBlockConfig(
            raw.enabled,
            rules,
            CommandBlockMessages(
                m.blocked ?: defaults.blocked, m.listHeader ?: defaults.listHeader, m.listLine ?: defaults.listLine, m.listEmpty ?: defaults.listEmpty,
                m.checkBlocked ?: defaults.checkBlocked, m.checkFree ?: defaults.checkFree, m.checkBypass ?: defaults.checkBypass
            )
        )
    }

    // ---------------------------------------------------------------- decisão

    /** O que as condições olham no jogador. */
    fun subjectOf(player: ServerPlayer): Conditions.Subject {
        val tags = Modules.isActive("tags")
        return Conditions.Subject(
            hasTag = { tag -> if (tags) TagsService.has(player, tag) else player.tags.any { it.equals(tag, ignoreCase = true) } },
            hasPermission = { node -> SbPermissions.check(player, node, false) },
            equipped = if (tags) TagsService.equippedView(player)?.id else null,
            dimension = player.level().dimension().location().toString()
        )
    }

    /** Os nomes que a primeira palavra do comando pode ter: o digitado e, se for apelido (redirect), o nome de verdade. */
    fun firstNames(server: MinecraftServer, first: String): Set<String> {
        val names = linkedSetOf(first, CommandMatcher.bare(first))
        server.commands.dispatcher.root.getChild(first)?.redirect?.name?.let {
            names += it
            names += CommandMatcher.bare(it)
        }
        return names
    }

    /** A regra que barra [typed] para [player], ou null se pode. Não olha a permissão de bypass (quem chama decide). */
    fun findRule(player: ServerPlayer, typed: List<String>, firstNames: Set<String>): BlockHit? {
        val cfg = config
        if (!cfg.enabled || typed.isEmpty()) return null
        var subject: Conditions.Subject? = null
        for ((name, rule) in cfg.rules) {
            if (!rule.enabled) continue
            val entry = rule.commands.firstOrNull { CommandMatcher.matches(it, typed, firstNames, ModuleSettings::commandName) } ?: continue
            val s = subject ?: subjectOf(player).also { subject = it }
            if (!Conditions.test(rule.conditions, rule.whenAny, s) { warnUnknown(name, it) }) continue
            if (rule.except.any { Conditions.matches(it, s) { text -> warnUnknown(name, text) } }) continue
            return BlockHit(name, rule, entry)
        }
        return null
    }

    private fun warnUnknown(rule: String, text: String) {
        if (warned.add("$rule/$text")) {
            LOGGER.warn("Rule '{}': unknown or malformed condition: '{}' (treated as false). Available conditions: {}", rule, text, Conditions.names().joinToString(", "))
        }
    }

    /** O /commandblock nunca é barrado para quem pode administrá-lo, senão uma regra com "*" trancaria quem vai corrigi-la. */
    private fun isOwnAdminCommand(player: ServerPlayer, typed: List<String>, names: Set<String>): Boolean {
        val own = ModuleSettings.commandName("commandblock")
        val viaServerBuddy = (names.contains("serverbuddy") || names.contains("sb")) && typed.getOrNull(1) == "commandblock"
        return (own in names || viaServerBuddy) && SbPermissions.check(player, ADMIN, 3)
    }

    private fun onCommand(source: CommandSourceStack, command: String): Boolean {
        val cfg = config
        if (!cfg.enabled || cfg.rules.isEmpty()) return false
        val player = source.player ?: return false
        val typed = CommandMatcher.words(command)
        if (typed.isEmpty()) return false
        if (SbPermissions.check(player, BYPASS, 4)) return false
        val names = firstNames(source.server, typed[0])
        if (isOwnAdminCommand(player, typed, names)) return false
        val hit = findRule(player, typed, names) ?: return false
        val shown = if (hit.entry.trim() == "*") typed[0] else CommandMatcher.words(hit.entry).joinToString(" ")
        Texts.send(player, hit.rule.message ?: cfg.messages.blocked, mapOf("command" to Texts.escape(shown), "rule" to Texts.escape(hit.name)))
        return true
    }
}
