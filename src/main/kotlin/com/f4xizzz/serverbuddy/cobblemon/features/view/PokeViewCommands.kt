package com.f4xizzz.serverbuddy.cobblemon.features.view

import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Targets
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import java.util.function.Predicate

/**
 * `/pokesee [jogador]` (time) e `/compsee [jogador]` (PC), mais os atalhos `/pokeseeother` e `/compseeother`.
 *
 * Permissões: `serverbuddy.pokesee.use`, `serverbuddy.pokesee.other`, `serverbuddy.compsee.use` e
 * `serverbuddy.compsee.other` (padrão: nível 2). Os grupos que já têm `cobblemonextras.command.pokesee` e parecidos
 * continuam valendo enquanto a compatibilidade com permissões antigas estiver ligada.
 */
object PokeViewCommands {
    private fun require(node: String): Predicate<CommandSourceStack> = SbPermissions.require(node, 2)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val openParty = { viewer: ServerPlayer, target: Targets.Target ->
            PartyViewGui(viewer, target, PokeViewConfigManager.config).open()
            Unit
        }
        val openPc = { viewer: ServerPlayer, target: Targets.Target ->
            PcViewGui(viewer, target, PokeViewConfigManager.config).open()
            Unit
        }
        SbCommands.register(dispatcher, "pokesee") { name -> selfNode(name, "serverbuddy.pokesee.use", "serverbuddy.pokesee.other", openParty) }
        dispatcher.register(otherNode(ModuleSettings.commandName("pokesee") + "other", "serverbuddy.pokesee.other", openParty))
        SbCommands.register(dispatcher, "compsee") { name -> selfNode(name, "serverbuddy.compsee.use", "serverbuddy.compsee.other", openPc) }
        dispatcher.register(otherNode(ModuleSettings.commandName("compsee") + "other", "serverbuddy.compsee.other", openPc))
    }

    private fun selfNode(
        name: String,
        usePerm: String,
        otherPerm: String,
        open: (ServerPlayer, Targets.Target) -> Unit
    ): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(require(usePerm))
            .executes { run(it, null, open) }
            .then(playerArgument(otherPerm, open))

    private fun otherNode(
        name: String,
        otherPerm: String,
        open: (ServerPlayer, Targets.Target) -> Unit
    ): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(require(otherPerm))
            .then(playerArgument(otherPerm, open))

    private fun playerArgument(
        otherPerm: String,
        open: (ServerPlayer, Targets.Target) -> Unit
    ): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("player", StringArgumentType.word())
            .requires(require(otherPerm))
            .suggests { ctx, builder -> Targets.suggestions(ctx.source, builder) }
            .executes { run(it, StringArgumentType.getString(it, "player"), open) }

    private fun run(
        ctx: CommandContext<CommandSourceStack>,
        targetName: String?,
        open: (ServerPlayer, Targets.Target) -> Unit
    ): Int {
        val messages = PokeViewConfigManager.config.messages
        val viewer = ctx.source.player
        if (viewer == null) {
            Texts.fail(ctx.source, messages.onlyPlayers)
            return 0
        }
        val target = if (targetName == null) {
            Targets.Target(viewer.uuid, viewer.gameProfile.name, viewer)
        } else {
            Targets.resolve(ctx.source.server, targetName) ?: run {
                Texts.fail(ctx.source, messages.notFound, mapOf("player" to Texts.escape(targetName)))
                return 0
            }
        }
        open(viewer, target)
        return 1
    }
}
