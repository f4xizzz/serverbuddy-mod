package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import java.io.File

/**
 * Perguntas de trivia no chat (como o SkiesTrivia), com perguntas de Minecraft prontas, dicas, ranking, sequência de
 * vitórias, bônus de velocidade e a opção de silenciar só para si. As pools do SkiesTrivia são importadas e convertidas.
 */
class TriviaModule : SbModule {
    override val id = "trivia"
    override val folder = "Trivia"
    override val legacyFolders = listOf("skiestrivia")
    override val description = "Chat trivia with pools, hints, rankings and rewards (imports SkiesTrivia pools)."

    override fun init() {
        Questions.registerBuiltIn()
        if (Integrations.cobblemon()) CobblemonQuestions.register()
        TriviaImport.convertLegacyPools(File(ConfigFiles.dir(folder), "pools"))
        TriviaService.load()

        ServerLifecycleEvents.SERVER_STARTING.register {
            TriviaScores.createTable()
            TriviaService.ready = true
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { TriviaService.ready = false }
        ServerTickEvents.END_SERVER_TICK.register { TriviaService.tick() }
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, _ -> TriviaService.onChat(sender, message.signedContent()) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> TriviaService.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> TriviaService.onLeave(handler.player) }
        SbCommands.onRegister { dispatcher, _ -> TriviaCommands.register(dispatcher) }

        // %serverbuddy:trivia_wins%, trivia_streak, trivia_best_streak, trivia_rank, trivia_next, trivia_top/1 (nome) e trivia_top_wins/1
        SbPlaceholders.register("trivia_wins") { player, _ -> player?.let { TriviaScores.stats(it.uuid).wins.toString() } ?: "0" }
        SbPlaceholders.register("trivia_streak") { player, _ -> player?.let { TriviaScores.stats(it.uuid).streak.toString() } ?: "0" }
        SbPlaceholders.register("trivia_best_streak") { player, _ -> player?.let { TriviaScores.stats(it.uuid).bestStreak.toString() } ?: "0" }
        SbPlaceholders.register("trivia_rank") { player, _ -> player?.let { TriviaScores.rank(it.uuid).takeIf { r -> r > 0 }?.toString() } ?: "-" }
        SbPlaceholders.register("trivia_next") { _, _ ->
            if (TriviaService.round != null) "agora" else AnswerMatcher.formatSeconds(TriviaService.secondsUntilNext())
        }
        SbPlaceholders.register("trivia_top") { _, arg -> TriviaScores.top(100).getOrNull((arg?.toIntOrNull() ?: 1) - 1)?.name ?: "-" }
        SbPlaceholders.register("trivia_top_wins") { _, arg -> TriviaScores.top(100).getOrNull((arg?.toIntOrNull() ?: 1) - 1)?.wins?.toString() ?: "0" }
    }

    override fun reload() {
        TriviaService.load()
    }
}
