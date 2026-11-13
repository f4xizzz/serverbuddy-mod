package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import java.util.function.Predicate

/**
 * `/trivia` (e `/serverbuddy trivia`). Jogadores (`serverbuddy.trivia.use`, liberado por padrão): sem argumento, `top`,
 * `stats [jogador]`, `toggle`. Equipe (`serverbuddy.trivia.admin`, nível 3): `start [pool]`, `stop`, `info`, `pools`, `reload`.
 */
object TriviaCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.trivia.admin", 3)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "trivia") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("trivia").build(), "trivia")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.trivia.use", true))
            .executes { status(it) }
            .then(
                Commands.literal("top")
                    .executes { top(it, 10) }
                    .then(Commands.argument("quantos", IntegerArgumentType.integer(1, 50)).executes { top(it, IntegerArgumentType.getInteger(it, "quantos")) })
            )
            .then(
                Commands.literal("stats")
                    .executes { stats(it, null) }
                    .then(
                        Commands.argument("jogador", StringArgumentType.word())
                            .suggests { ctx, builder -> SharedSuggestionProvider.suggest(ctx.source.server.playerList.playerNamesArray, builder) }
                            .executes { stats(it, StringArgumentType.getString(it, "jogador")) }
                    )
            )
            .then(Commands.literal("toggle").executes { toggle(it) })
            .then(
                Commands.literal("start").requires(ADMIN)
                    .executes { start(it, null) }
                    .then(
                        Commands.argument("pool", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(TriviaService.pools.keys, builder) }
                            .executes { start(it, StringArgumentType.getString(it, "pool")) }
                    )
            )
            .then(Commands.literal("stop").requires(ADMIN).executes { stop(it) })
            .then(Commands.literal("info").requires(ADMIN).executes { info(it) })
            .then(Commands.literal("pools").requires(ADMIN).executes { pools(it) })
            .then(Commands.literal("reload").requires(ADMIN).executes { reload(it) })

    // ---------------------------------------------------------------- jogadores

    private fun status(ctx: CommandContext<CommandSourceStack>): Int {
        val config = TriviaService.config
        val round = TriviaService.round
        if (round != null) {
            Texts.reply(
                ctx.source, config.messages.runningNow,
                mapOf("question" to Texts.escape(round.question.text), "time" to AnswerMatcher.formatSeconds(TriviaService.secondsLeft()))
            )
        } else {
            Texts.reply(ctx.source, config.messages.nextIn, mapOf("time" to AnswerMatcher.formatSeconds(TriviaService.secondsUntilNext())))
        }
        ctx.source.player?.let { player ->
            val s = TriviaScores.stats(player.uuid, player.gameProfile.name)
            Texts.reply(ctx.source, "<gray>Your wins: <white>${s.wins}</white> · streak: <white>${s.streak}</white> · best: <white>${s.bestStreak}</white> <dark_gray>(/trivia top, /trivia toggle)")
        }
        return 1
    }

    private fun top(ctx: CommandContext<CommandSourceStack>, n: Int): Int {
        val list = TriviaScores.top(n)
        if (list.isEmpty()) {
            Texts.reply(ctx.source, "<gray>Nobody has won a trivia round yet.")
            return 0
        }
        Texts.reply(ctx.source, "<gradient:#a855f7:#ec4899><bold>Trivia rankings</bold></gradient>")
        list.forEachIndexed { index, s ->
            val medal = when (index) {
                0 -> "<gold>#1"
                1 -> "<white>#2"
                2 -> "<#cd7f32>#3"
                else -> "<gray>#${index + 1}"
            }
            val fastest = if (s.fastestMs > 0) " <dark_gray>· fastest: ${AnswerMatcher.formatMillis(s.fastestMs)}" else ""
            Texts.reply(ctx.source, "$medal <white>${Texts.escape(s.name)}</white> <gray>— ${s.wins} win(s)$fastest")
        }
        return list.size
    }

    private fun stats(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val target = if (name == null) {
            val player = ctx.source.player
            if (player == null) {
                Texts.fail(ctx.source, "<red>Enter a player name: /trivia stats <player>.")
                return 0
            }
            TriviaScores.stats(player.uuid, player.gameProfile.name)
        } else {
            ctx.source.server.playerList.getPlayerByName(name)?.let { TriviaScores.stats(it.uuid, it.gameProfile.name) } ?: TriviaScores.byName(name)
        }
        if (target == null) {
            Texts.fail(ctx.source, "<red>Could not find '${Texts.escape(name ?: "")}' in the trivia rankings.")
            return 0
        }
        val rank = TriviaScores.rank(target.uuid)
        Texts.reply(ctx.source, "<gradient:#a855f7:#ec4899><bold>Trivia</bold></gradient> <gray>· <white>${Texts.escape(target.name)}")
        Texts.reply(ctx.source, "<gray>Wins: <white>${target.wins}</white>${if (rank > 0) " <dark_gray>(rank ${rank})" else ""}")
        Texts.reply(ctx.source, "<gray>Streak: <white>${target.streak}</white> · best streak: <white>${target.bestStreak}</white>")
        if (target.fastestMs > 0) Texts.reply(ctx.source, "<gray>Fastest answer: <white>${AnswerMatcher.formatMillis(target.fastestMs)}")
        return 1
    }

    private fun toggle(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can mute trivia.")
            return 0
        }
        val muted = TriviaService.toggleMuted(player)
        Texts.reply(ctx.source, if (muted) TriviaService.config.messages.muted else TriviaService.config.messages.unmuted)
        return 1
    }

    // ---------------------------------------------------------------- equipe

    private fun start(ctx: CommandContext<CommandSourceStack>, pool: String?): Int {
        val problem = TriviaService.start(pool, force = true)
        if (problem != null) {
            Texts.fail(ctx.source, "<red>${Texts.escape(problem)}")
            return 0
        }
        Texts.reply(ctx.source, "<green>Question started.")
        return 1
    }

    private fun stop(ctx: CommandContext<CommandSourceStack>): Int {
        if (!TriviaService.stop()) {
            Texts.fail(ctx.source, "<red>No question is active.")
            return 0
        }
        Texts.reply(ctx.source, "<green>Question cancelled (without a winner or announcement). The next one has been rescheduled.")
        return 1
    }

    private fun info(ctx: CommandContext<CommandSourceStack>): Int {
        val round = TriviaService.round
        if (round == null) {
            Texts.reply(ctx.source, "<gray>No active question. The next one starts in <white>${AnswerMatcher.formatSeconds(TriviaService.secondsUntilNext())}</white> <dark_gray>(${TriviaService.pools.size} active pool(s))")
            return 0
        }
        Texts.reply(ctx.source, "<gold><bold>Active trivia</bold> <gray>· pool <white>${Texts.escape(round.pool.displayName)}</white> · time left <white>${AnswerMatcher.formatSeconds(TriviaService.secondsLeft())}")
        Texts.reply(ctx.source, "<gray>Question: <white>${Texts.escape(round.question.text)}")
        Texts.reply(ctx.source, "<gray>Accepted answers: <green>${Texts.escape(round.question.answers.joinToString(" / "))}")
        return 1
    }

    private fun pools(ctx: CommandContext<CommandSourceStack>): Int {
        val pools = TriviaService.pools
        if (pools.isEmpty()) {
            Texts.reply(ctx.source, "<yellow>No active pools. Create files in config/ServerBuddy/Trivia/pools/.")
            return 0
        }
        Texts.reply(ctx.source, "<gold><bold>Trivia pools</bold> <gray>(weight = relative chance)")
        for (pool in pools.values) {
            val seconds = pool.pool.answerTime ?: TriviaService.config.answerTime
            Texts.reply(ctx.source, "<dark_gray>• <white>${Texts.escape(pool.id)}</white> <gray>— weight ${pool.pool.weight}, ${pool.questions.size} question(s), ${seconds}s, ${pool.pool.rewards.size} reward(s)")
        }
        Texts.reply(ctx.source, "<gray>Available generators: <white>${Texts.escape(Questions.ids().joinToString(", "))}")
        return pools.size
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        TriviaService.load()
        Texts.reply(ctx.source, "<green>Trivia reloaded: ${TriviaService.pools.size} active pool(s).")
        return 1
    }
}
