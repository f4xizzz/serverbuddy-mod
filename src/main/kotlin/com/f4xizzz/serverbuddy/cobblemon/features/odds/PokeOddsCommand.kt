package com.f4xizzz.serverbuddy.cobblemon.features.odds

import com.cobblemon.mod.common.Cobblemon
import com.f4xizzz.serverbuddy.ServerBuddy
import com.f4xizzz.serverbuddy.cobblemon.events.ShinyRateController
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import java.nio.file.Files

/**
 * `/pokeodds` mostra a taxa de Shiny (1 em N) e `/pokeodds setRate <N>` muda e grava na config do Cobblemon.
 * Permissões: `serverbuddy.pokeodds.use` e `serverbuddy.pokeodds.set` (padrão: nível 2).
 */
object PokeOddsCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "pokeodds") { name ->
            Commands.literal(name)
                .requires(SbPermissions.require("serverbuddy.pokeodds.use", 2))
                .executes { show(it) }
                .then(
                    Commands.literal("setRate")
                        .requires(SbPermissions.require("serverbuddy.pokeodds.set", 2))
                        .then(Commands.argument("rate", FloatArgumentType.floatArg(1f)).executes { set(it, FloatArgumentType.getFloat(it, "rate")) })
                )
        }
    }

    private fun show(ctx: CommandContext<CommandSourceStack>): Int {
        val rate = Cobblemon.config.shinyRate
        Texts.reply(ctx.source, "<gray>Taxa de Shiny atual: <yellow>1/${format(rate)}</yellow>")
        return 1
    }

    private fun set(ctx: CommandContext<CommandSourceStack>, rate: Float): Int {
        val eventActive = ShinyRateController.setBaseRate(rate)
        val saved = persist(rate)
        Texts.reply(ctx.source, "<green>Default Shiny rate set to <yellow>1/${format(rate)}</yellow>.", broadcast = true)
        if (eventActive) {
            Texts.reply(ctx.source, "<yellow>A Shiny event is active: the new rate applies when it ends.")
        }
        if (!saved) {
            Texts.reply(ctx.source, "<yellow>Could not write to the Cobblemon config; the rate goes back to normal on the next restart.")
        }
        ServerBuddy.LOGGER.info("[PokeOdds] {} set the default Shiny rate to 1/{} (saved to the config: {}).", ctx.source.textName, rate, saved)
        return 1
    }

    private fun format(rate: Float): String = if (rate == rate.toLong().toFloat()) rate.toLong().toString() else rate.toString()

    /** Grava `shinyRate` em `config/cobblemon/main.json` para a mudança sobreviver ao reinício. */
    private fun persist(rate: Float): Boolean = try {
        val path = FabricLoader.getInstance().configDir.resolve("cobblemon").resolve("main.json")
        if (!Files.exists(path)) {
            false
        } else {
            val json = JsonParser.parseString(Files.readString(path)).asJsonObject
            json.addProperty("shinyRate", rate)
            Files.writeString(path, GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json))
            true
        }
    } catch (e: Exception) {
        ServerBuddy.LOGGER.error("[PokeOdds] Could not save the Shiny rate in the Cobblemon configuration!", e)
        false
    }
}
