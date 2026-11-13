package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import java.util.function.Predicate

/**
 * Peças comuns dos comandos do módulo de essentials: nome e permissão (`serverbuddy.essentials.<comando>`), apelidos de comando
 * e atalhos para pedir um jogador ou um texto.
 */
internal object EssentialsCommands {
    /** Permissão do comando: [level] 0 = todo mundo, 2 = ajudante, 3 = administrador (se o LuckPerms não disser o contrário). */
    fun perm(node: String, level: Int = 0): Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.essentials.$node", level)

    /**
     * Registra `/nome` (que pode ser renomeado ou trocado em modules.conf) e os [aliases], que apontam para ele.
     * [replaceVanilla] = tira antes o comando de mesmo nome do Minecraft (como o /msg), senão os dois ficariam misturados.
     */
    fun register(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        name: String,
        aliases: List<String> = emptyList(),
        level: Int = 0,
        replaceVanilla: Boolean = false,
        build: LiteralArgumentBuilder<CommandSourceStack>.() -> Unit
    ) {
        if (replaceVanilla) for (root in listOf(name) + aliases) SbCommands.removeRoot(dispatcher, root)
        val requires = perm(name, level)
        val node = SbCommands.register(dispatcher, name) { effective -> Commands.literal(effective).requires(requires).also(build) }
        for (alias in aliases) SbCommands.alias(dispatcher, alias, node, requires)
    }

    fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.player
        if (player == null) Msg.fail(ctx.source, "players_only")
        return player
    }

    fun target(ctx: CommandContext<CommandSourceStack>, arg: String = "jogador"): ServerPlayer = EntityArgument.getPlayer(ctx, arg)

    fun playerArg(name: String = "jogador"): RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector> =
        Commands.argument(name, EntityArgument.player())

    fun text(name: String = "texto"): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(name, StringArgumentType.greedyString())

    fun word(name: String): RequiredArgumentBuilder<CommandSourceStack, String> = Commands.argument(name, StringArgumentType.word())

    fun suggestWords(words: () -> Collection<String>): com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> =
        com.mojang.brigadier.suggestion.SuggestionProvider { _, builder -> SharedSuggestionProvider.suggest(words(), builder) }

    /** O nome em letras minúsculas, números e _ (até 24), ou null se não serve. */
    fun cleanName(text: String): String? {
        val name = text.trim().lowercase()
        return if (Regex("[a-z0-9_]{1,24}").matches(name)) name else null
    }

    /** Lista de nomes para mostrar num texto, com clique para rodar [command] (ex.: "/home %s"). */
    fun clickList(names: Collection<String>, command: String): String =
        names.joinToString("<gray>, ") { "<click:run_command:'${command.replace("%s", it)}'><hover:show_text:'Click to teleport'><white>$it</white></hover></click>" }
}
