package com.f4xizzz.serverbuddy.modules.commandblock

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
import net.minecraft.commands.arguments.EntityArgument
import java.util.function.Predicate

/**
 * `/commandblock` (e `/serverbuddy commandblock`), só para a equipe (`serverbuddy.commandblock.admin`, padrão nível 3):
 * `list` mostra as regras, `check <jogador> <comando>` diz se o comando está bloqueado para ele e por qual regra, `reload` relê a config.
 */
object CommandBlockCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require(CommandBlockService.ADMIN, 3)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "commandblock") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("commandblock").build(), "commandblock")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(ADMIN)
            .executes { list(it) }
            .then(Commands.literal("list").executes { list(it) })
            .then(
                Commands.literal("check").then(
                    Commands.argument("jogador", EntityArgument.player()).then(
                        Commands.argument("comando", StringArgumentType.greedyString()).executes { check(it) }
                    )
                )
            )
            .then(Commands.literal("reload").executes { reload(it) })

    /** "tudo: a e b · uma: c ou d · exceto: e ou f": quando a regra bloqueia, em poucas palavras. */
    private fun describe(rule: CommandBlockRule): String {
        val parts = ArrayList<String>()
        if (rule.conditions.isNotEmpty()) parts.add(rule.conditions.joinToString(" and "))
        if (rule.whenAny.isNotEmpty()) parts.add(rule.whenAny.joinToString(" or "))
        val text = if (parts.isEmpty()) "(no conditions: never blocks)" else parts.joinToString(" and ")
        return if (rule.except.isEmpty()) text else text + " (except " + rule.except.joinToString(" or ") + ")"
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val config = CommandBlockService.config
        val messages = config.messages
        if (config.rules.isEmpty()) {
            Texts.reply(ctx.source, messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, messages.listHeader, mapOf("count" to config.rules.size))
        for ((name, rule) in config.rules) {
            val state = if (!config.enabled || !rule.enabled) "<red>(disabled)" else "<green>(enabled)"
            Texts.reply(
                ctx.source, messages.listLine,
                mapOf(
                    "rule" to Texts.escape(name),
                    "state" to state,
                    "commands" to Texts.escape(rule.commands.joinToString(", ")),
                    "conditions" to Texts.escape(describe(rule))
                )
            )
        }
        return config.rules.size
    }

    private fun check(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val text = StringArgumentType.getString(ctx, "comando")
        val typed = CommandMatcher.words(text)
        val messages = CommandBlockService.config.messages
        val vars = mapOf("player" to Texts.escape(target.gameProfile.name), "command" to Texts.escape(typed.joinToString(" ")))
        if (typed.isEmpty()) {
            Texts.fail(ctx.source, "<red>Enter the command (without the slash).")
            return 0
        }
        if (SbPermissions.check(target, CommandBlockService.BYPASS, 4)) {
            Texts.reply(ctx.source, messages.checkBypass, vars)
            return 0
        }
        val hit = CommandBlockService.findRule(target, typed, CommandBlockService.firstNames(ctx.source.server, typed[0]))
        if (hit == null) {
            Texts.reply(ctx.source, messages.checkFree, vars)
            return 1
        }
        Texts.reply(ctx.source, messages.checkBlocked, vars + ("rule" to Texts.escape(hit.name)))
        return 0
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        CommandBlockService.load()
        Texts.reply(ctx.source, "<green>Blocking rules reloaded (<white>${CommandBlockService.config.rules.size}</white>).")
        return 1
    }
}
