package com.f4xizzz.serverbuddy.core

import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.world.entity.Entity
import java.util.function.Predicate

/**
 * Fachada da fabric-permissions-api usada por todos os módulos do ServerBuddy.
 *
 * Aceita o nó novo (`serverbuddy.<módulo>.…`) **ou** o nó do mod que o ServerBuddy substituiu
 * (`skieskits.…`, `greatserverutils.…`...), para que os grupos do LuckPerms que já existem continuem
 * funcionando depois da troca dos JARs. Nós que não começam por `serverbuddy.` (por exemplo, permissões
 * escritas dentro dos arquivos de config) passam direto, sem tradução.
 */
object SbPermissions {
    // Do mais específico para o mais geral: o primeiro prefixo que casar é o usado.
    private val LEGACY_PREFIXES: List<Pair<String, String>> = listOf(
        "serverbuddy.guis." to "skiesguis.",
        "serverbuddy.kits." to "skieskits.",
        "serverbuddy.shop." to "skiesshop.",
        "serverbuddy.crates." to "skiescrates.",
        "serverbuddy.announcements." to "skiesannouncements.",
        "serverbuddy.clear." to "skiesclear.",
        "serverbuddy.trivia." to "skiestrivia.",
        "serverbuddy.editor." to "greateditor.perm.",
        "serverbuddy.builder." to "greatbuilder.perm.",
        "serverbuddy." to "greatserverutils."
    )

    // Nós exatos dos comandos que vieram do CobblemonExtras: quem já tinha `cobblemonextras.command.pokesee` no
    // LuckPerms continua com acesso ao /pokesee do ServerBuddy.
    private val EXACT_LEGACY: Map<String, String> = mapOf(
        "serverbuddy.pokesee.use" to "cobblemonextras.command.pokesee",
        "serverbuddy.pokesee.other" to "cobblemonextras.command.pokeseeother",
        "serverbuddy.compsee.use" to "cobblemonextras.command.compsee",
        "serverbuddy.compsee.other" to "cobblemonextras.command.compseeother",
        "serverbuddy.pokeshout.use" to "cobblemonextras.command.pokeshout",
        "serverbuddy.pokeshout.all" to "cobblemonextras.command.pokeshoutall",
        "serverbuddy.itemshout.use" to "cobblemonextras.command.itemshout",
        "serverbuddy.pokeodds.use" to "cobblemonextras.command.pokeodds",
        "serverbuddy.pokeodds.set" to "cobblemonextras.command.pokeodds"
    )

    /** O nó antigo equivalente a [node], ou null se [node] não é do ServerBuddy. */
    @JvmStatic
    fun legacyNode(node: String): String? {
        EXACT_LEGACY[node]?.let { return it }
        if (node == "serverbuddy.editor.devmode") return "greateditor.devmode"
        for ((prefix, legacy) in LEGACY_PREFIXES) {
            if (node.startsWith(prefix)) return legacy + node.removePrefix(prefix)
        }
        return null
    }

    private fun value(source: SharedSuggestionProvider, node: String): TriState {
        val primary = Permissions.getPermissionValue(source, node)
        if (primary != TriState.DEFAULT || !LegacyCompat.permissions) return primary
        val legacy = legacyNode(node) ?: return primary
        return Permissions.getPermissionValue(source, legacy)
    }

    private fun value(entity: Entity, node: String): TriState {
        val primary = Permissions.getPermissionValue(entity, node)
        if (primary != TriState.DEFAULT || !LegacyCompat.permissions) return primary
        val legacy = legacyNode(node) ?: return primary
        return Permissions.getPermissionValue(entity, legacy)
    }

    private fun resolve(value: TriState, fallback: () -> Boolean): Boolean = when (value) {
        TriState.TRUE -> true
        TriState.FALSE -> false
        else -> fallback()
    }

    // ---- CommandSourceStack (e qualquer SharedSuggestionProvider) ----

    @JvmStatic
    fun check(source: SharedSuggestionProvider, node: String, level: Int): Boolean =
        resolve(value(source, node)) { source.hasPermission(level) }

    @JvmStatic
    fun check(source: SharedSuggestionProvider, node: String, default: Boolean): Boolean =
        resolve(value(source, node)) { default }

    @JvmStatic
    fun check(source: SharedSuggestionProvider, node: String): Boolean = check(source, node, false)

    // ---- Entidades (jogadores) ----

    @JvmStatic
    fun check(entity: Entity, node: String, level: Int): Boolean =
        resolve(value(entity, node)) { entity.createCommandSourceStack().hasPermission(level) }

    @JvmStatic
    fun check(entity: Entity, node: String, default: Boolean): Boolean =
        resolve(value(entity, node)) { default }

    @JvmStatic
    fun check(entity: Entity, node: String): Boolean = check(entity, node, false)

    // ---- Predicados para .requires(...) do Brigadier ----

    @JvmStatic
    fun <S : SharedSuggestionProvider> require(node: String, level: Int): Predicate<S> =
        Predicate { source -> check(source, node, level) }

    @JvmStatic
    fun <S : SharedSuggestionProvider> require(node: String, default: Boolean): Predicate<S> =
        Predicate { source -> check(source, node, default) }

    @JvmStatic
    fun <S : SharedSuggestionProvider> require(node: String): Predicate<S> = require(node, false)
}
