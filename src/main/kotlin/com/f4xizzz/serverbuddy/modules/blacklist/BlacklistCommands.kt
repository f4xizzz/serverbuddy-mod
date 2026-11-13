package com.f4xizzz.serverbuddy.modules.blacklist

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import java.util.function.Predicate

/**
 * `/blacklist` (e `/serverbuddy blacklist`):
 *  - `/blacklist` ou `/blacklist list`: os golpes proibidos para quem digitou (qualquer jogador; permissão `serverbuddy.blacklist.list`)
 *  - `/blacklist contexts`, `list <contexto>`, `check <jogador> [golpe]`, `add`, `remove`, `reload`: só para a equipe
 *    (permissão `serverbuddy.blacklist.admin`, padrão nível 3).
 */
object BlacklistCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.blacklist.admin", 3)

    private val BATTLES = listOf("pvp" to "PvP", "npc" to "NPC", "wild" to "wild")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "blacklist") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("blacklist").build(), "blacklist")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.blacklist.list", true))
            .executes { listMine(it) }
            .then(
                Commands.literal("list")
                    .executes { listMine(it) }
                    .then(
                        Commands.argument("contexto", StringArgumentType.word())
                            .requires(ADMIN)
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(BlacklistService.config.contexts.keys, builder) }
                            .executes { listContext(it, StringArgumentType.getString(it, "contexto")) }
                    )
            )
            .then(Commands.literal("contexts").requires(ADMIN).executes { contexts(it) })
            .then(
                Commands.literal("check").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player())
                        .executes { check(it, null) }
                        .then(
                            Commands.argument("golpe", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(CobblemonMoves.names(), builder) }
                                .executes { check(it, StringArgumentType.getString(it, "golpe")) }
                        )
                )
            )
            .then(
                Commands.literal("add").requires(ADMIN).then(
                    Commands.argument("contexto", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(BlacklistService.config.contexts.keys, builder) }
                        .then(
                            Commands.argument("golpe", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(CobblemonMoves.names(), builder) }
                                .executes { add(it) }
                        )
                )
            )
            .then(
                Commands.literal("remove").requires(ADMIN).then(
                    Commands.argument("contexto", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(BlacklistService.config.contexts.keys, builder) }
                        .then(
                            Commands.argument("golpe", StringArgumentType.word())
                                .suggests { ctx, builder ->
                                    val context = StringArgumentType.getString(ctx, "contexto")
                                    val banned = BlacklistService.config.contexts.entries.firstOrNull { it.key.equals(context, ignoreCase = true) }?.value?.moves.orEmpty()
                                    SharedSuggestionProvider.suggest(banned, builder)
                                }
                                .executes { remove(it) }
                        )
                )
            )
            .then(Commands.literal("reload").requires(ADMIN).executes { reload(it) })

    // ---------------------------------------------------------------- textos

    /** Os nomes dos golpes como o jogador vê no Cobblemon (no idioma dele); o que a config tem de errado fica vermelho. */
    private fun movesComponent(moves: List<String>): Component {
        val result = Component.empty()
        moves.forEachIndexed { index, move ->
            if (index > 0) result.append(Component.literal(", "))
            val id = BlacklistService.normalize(move)
            result.append(
                when (CobblemonMoves.exists(id)) {
                    true -> Component.translatable("cobblemon.move.$id")
                    false -> Component.literal(move).withStyle(ChatFormatting.RED)
                    null -> Component.literal(move)
                }
            )
        }
        return result
    }

    /** " (only against players)": avisa quando o contexto só vale em certos tipos de batalha (o "default" vale sempre). */
    private fun note(name: String, context: BlacklistContext): String {
        if (name.equals("default", ignoreCase = true)) return ""
        val kinds = (context.conditions + context.whenAny).map { it.trim().lowercase() }.filter { it.startsWith("battle:") }
            .mapNotNull {
                when (it.removePrefix("battle:").trim()) {
                    "pvp" -> "only against players"
                    "npc" -> "only against NPCs"
                    "wild" -> "only against wild Pokémon"
                    else -> null
                }
            }.distinct()
        return if (kinds.isEmpty()) "" else " (${kinds.joinToString(" or ")})"
    }

    private fun conditionsText(context: BlacklistContext, name: String): String {
        if (name.equals("default", ignoreCase = true)) return "always"
        if (context.conditions.isEmpty() && context.whenAny.isEmpty()) return "tag ${name.lowercase()}"
        val all = context.conditions.joinToString(" and ")
        val any = context.whenAny.takeIf { it.isNotEmpty() }?.joinToString(" or ", prefix = "(", postfix = ")")
        return listOfNotNull(all.takeIf { it.isNotEmpty() }, any).joinToString(" and ")
    }

    // ---------------------------------------------------------------- comandos

    private fun listMine(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) return contexts(ctx)
        val config = BlacklistService.config
        val situation = BlacklistService.situationOf(player, BlacklistService.ANY_BATTLE)
        val lines = BlacklistService.contextsFor(config, situation)
            .map { it to config.contexts.getValue(it) }
            .filter { (_, context) -> context.moves.isNotEmpty() }
        if (lines.isEmpty()) {
            Texts.reply(ctx.source, config.messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, config.messages.listHeader)
        for ((name, context) in lines) {
            val vars = mapOf("context" to Texts.escape(name), "note" to note(name, context))
            Texts.reply(ctx.source, config.messages.listLine, vars, mapOf("moves" to movesComponent(context.moves)))
        }
        return lines.size
    }

    private fun contexts(ctx: CommandContext<CommandSourceStack>): Int {
        val config = BlacklistService.config
        val state = if (BlacklistService.active) "<green>on" else "<red>off <gray>(without Cobblemon or with the original mod installed)"
        Texts.reply(ctx.source, "<gold><bold>Blacklist</bold> <gray>· $state<gray> · ${config.contexts.size} context(s)")
        for ((name, context) in config.contexts) {
            Texts.reply(
                ctx.source,
                "<dark_gray>• <white>${Texts.escape(name)}</white> <gray>— ${Texts.escape(conditionsText(context, name))} <dark_gray>(${context.moves.size} move(s))"
            )
        }
        Texts.reply(ctx.source, "<gray>View a context's moves with <white>/blacklist list <context></white>.")
        return config.contexts.size
    }

    private fun listContext(ctx: CommandContext<CommandSourceStack>, name: String): Int {
        val entry = BlacklistService.config.contexts.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        if (entry == null) {
            Texts.fail(ctx.source, "<red>There is no context '${Texts.escape(name)}'. Use <white>/blacklist contexts</white> to see the ones that exist.")
            return 0
        }
        Texts.reply(ctx.source, "<gold><bold>${Texts.escape(entry.key)}</bold> <gray>· applies: ${Texts.escape(conditionsText(entry.value, entry.key))}")
        if (entry.value.moves.isEmpty()) {
            Texts.reply(ctx.source, "<gray>No banned moves in this context. Use <white>/blacklist add ${Texts.escape(entry.key)} <move></white>.")
            return 0
        }
        Texts.reply(ctx.source, "<white>%moves%", parts = mapOf("moves" to movesComponent(entry.value.moves)))
        return entry.value.moves.size
    }

    private fun check(ctx: CommandContext<CommandSourceStack>, move: String?): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val config = BlacklistService.config
        val who = Texts.escape(target.gameProfile.name)
        if (move == null) {
            Texts.reply(ctx.source, "<gold><bold>$who</bold> <gray>· active contexts by battle type:")
            for ((kind, label) in BATTLES) {
                val names = BlacklistService.contextsFor(config, BlacklistService.situationOf(target, kind))
                val banned = BlacklistService.bannedMoves(config, BlacklistService.situationOf(target, kind)).size
                Texts.reply(ctx.source, "<dark_gray>• <white>$label<gray>: ${Texts.escape(names.joinToString(", "))} <dark_gray>($banned banned move(s))")
            }
            return 1
        }
        val id = BlacklistService.normalize(move)
        Texts.reply(ctx.source, "<gold><bold>$who</bold> <gray>· move <white>${Texts.escape(id)}</white>:")
        var banned = 0
        for ((kind, label) in BATTLES) {
            val ban = BlacklistService.check(config, BlacklistService.situationOf(target, kind), id)
            if (ban != null) {
                banned++
                Texts.reply(ctx.source, "<dark_gray>• <white>$label<gray>: <red>banned <gray>(${Texts.escape(ban.contexts.joinToString(", "))})")
            } else {
                Texts.reply(ctx.source, "<dark_gray>• <white>$label<gray>: <green>allowed")
            }
        }
        if (CobblemonMoves.exists(id) == false) Texts.reply(ctx.source, "<yellow>Warning: Cobblemon does not know the move '${Texts.escape(id)}'.")
        return banned
    }

    private fun add(ctx: CommandContext<CommandSourceStack>): Int {
        val context = StringArgumentType.getString(ctx, "contexto")
        val move = StringArgumentType.getString(ctx, "golpe")
        val id = BlacklistService.normalize(move)
        val existed = BlacklistService.config.contexts.keys.any { it.equals(context, ignoreCase = true) }
        if (!BlacklistService.add(context, move)) {
            Texts.fail(ctx.source, "<red>'${Texts.escape(id)}' is already banned in '${Texts.escape(context)}' (or the move name is invalid).")
            return 0
        }
        Texts.reply(ctx.source, "<green>Move <white>${Texts.escape(id)}</white> banned in <white>${Texts.escape(context)}</white>.", broadcast = true)
        if (!existed) {
            Texts.reply(ctx.source, "<yellow>The context '${Texts.escape(context)}' is new: it applies to anyone with the tag <white>${Texts.escape(context.lowercase())}</white>. For other conditions, edit its \"when\" in Blacklist/config.json.")
        }
        if (CobblemonMoves.exists(id) == false) Texts.reply(ctx.source, "<yellow>Warning: Cobblemon does not know the move '${Texts.escape(id)}'. Check the spelling.")
        return 1
    }

    private fun remove(ctx: CommandContext<CommandSourceStack>): Int {
        val context = StringArgumentType.getString(ctx, "contexto")
        val move = StringArgumentType.getString(ctx, "golpe")
        if (!BlacklistService.remove(context, move)) {
            Texts.fail(ctx.source, "<red>'${Texts.escape(BlacklistService.normalize(move))}' was not in the list of '${Texts.escape(context)}'.")
            return 0
        }
        Texts.reply(ctx.source, "<green>Move <white>${Texts.escape(BlacklistService.normalize(move))}</white> allowed in <white>${Texts.escape(context)}</white>.", broadcast = true)
        return 1
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        BlacklistService.load()
        val config = BlacklistService.config
        Texts.reply(ctx.source, "<green>Blacklist reloaded: ${config.contexts.size} context(s), ${config.contexts.values.sumOf { it.moves.size }} banned move(s).")
        for ((name, moves) in BlacklistService.unknownMoves()) {
            Texts.reply(ctx.source, "<yellow>Context '${Texts.escape(name)}': Cobblemon does not know ${Texts.escape(moves.joinToString(", "))}.")
        }
        return 1
    }
}
