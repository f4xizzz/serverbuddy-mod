package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import java.util.function.Predicate

/**
 * `/missions` (e `/serverbuddy missions`). Jogadores (`serverbuddy.missions.use`, liberado por padrão): sem argumento abre
 * o menu; `list` mostra em texto; `claim [missão]` resgata. Equipe (`serverbuddy.missions.admin`, nível 3):
 * `progress <jogador> <chave> [quantidade]`, `complete <jogador> <missão>`, `reset <jogador> [missão|categoria]` e `reload`.
 */
object MissionsCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.missions.admin", 3)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "missions") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("missions").build(), "missions")
    }

    private fun missionKeys(): List<String> = MissionsService.categories.values.flatMap { c -> c.missions.map { it.key } }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.missions.use", true))
            .executes { open(it) }
            .then(Commands.literal("list").executes { list(it) })
            .then(
                Commands.literal("claim")
                    .executes { claim(it, null) }
                    .then(
                        Commands.argument("missao", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(missionKeys(), builder) }
                            .executes { claim(it, StringArgumentType.getString(it, "missao")) }
                    )
            )
            .then(
                Commands.literal("progress").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player()).then(
                        Commands.argument("chave", StringArgumentType.word())
                            .executes { progress(it, 1) }
                            .then(Commands.argument("quantidade", LongArgumentType.longArg(1)).executes { progress(it, LongArgumentType.getLong(it, "quantidade")) })
                    )
                )
            )
            .then(
                Commands.literal("complete").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player()).then(
                        Commands.argument("missao", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(missionKeys(), builder) }
                            .executes { complete(it) }
                    )
                )
            )
            .then(
                Commands.literal("reset").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player())
                        .executes { reset(it, null) }
                        .then(
                            Commands.argument("alvo", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(MissionsService.categories.keys + missionKeys(), builder) }
                                .executes { reset(it, StringArgumentType.getString(it, "alvo")) }
                        )
                )
            )
            .then(Commands.literal("reload").requires(ADMIN).executes { reload(it) })
            .then(
                Commands.literal("global")
                    .executes { globalStatus(it) }
                    .then(Commands.literal("join").executes { globalJoin(it) })
                    .then(
                        Commands.literal("start").requires(ADMIN)
                            .executes { globalStart(it, null) }
                            .then(
                                Commands.argument("missao", StringArgumentType.word())
                                    .suggests { _, builder -> SharedSuggestionProvider.suggest(globalKeys(), builder) }
                                    .executes { globalStart(it, StringArgumentType.getString(it, "missao")) }
                            )
                    )
                    .then(Commands.literal("stop").requires(ADMIN).executes { globalStop(it) })
            )

    private fun globalKeys(): List<String> = MissionsService.categories.values.filter { it.def.global }.flatMap { c -> c.missions.map { it.key } }

    private fun globalStatus(ctx: CommandContext<CommandSourceStack>): Int {
        for (line in GlobalMissions.statusText(ctx.source.player, ctx.source.server)) Texts.reply(ctx.source, line)
        return 1
    }

    private fun globalJoin(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can join missions.")
            return 0
        }
        val messages = MissionsService.config.global.messages
        return when (GlobalMissions.accept(player)) {
            JoinResult.OK -> 1
            JoinResult.NONE_ACTIVE -> { Texts.fail(ctx.source, messages.noneScheduled); 0 }
            JoinResult.ALREADY -> { Texts.reply(ctx.source, messages.alreadyJoined); 0 }
            JoinResult.NOT_ELIGIBLE -> { Texts.fail(ctx.source, messages.notEligible); 0 }
            JoinResult.NOT_NEEDED -> { Texts.reply(ctx.source, messages.noJoinNeeded); 0 }
        }
    }

    private fun globalStart(ctx: CommandContext<CommandSourceStack>, key: String?): Int = when (GlobalMissions.start(ctx.source.server, key)) {
        StartResult.OK -> { Texts.reply(ctx.source, "<green>Global mission started."); 1 }
        StartResult.DISABLED -> { Texts.fail(ctx.source, "<red>Global missions are disabled: set \"global_missions\" → \"enabled\" in config.json and run /missions reload."); 0 }
        StartResult.ALREADY_ACTIVE -> { Texts.fail(ctx.source, "<red>A global mission is already active. Stop it with /missions global stop."); 0 }
        StartResult.UNKNOWN -> { Texts.fail(ctx.source, "<red>Unknown global mission '${Texts.escape(key.orEmpty())}'. Available missions: ${Texts.escape(globalKeys().joinToString(", "))}"); 0 }
        StartResult.NO_MISSIONS -> { Texts.fail(ctx.source, "<red>No global missions loaded: create a category with \"global\": true in Missions/categories."); 0 }
    }

    private fun globalStop(ctx: CommandContext<CommandSourceStack>): Int {
        if (!GlobalMissions.stop(ctx.source.server)) {
            Texts.fail(ctx.source, "<red>No global mission is active.")
            return 0
        }
        Texts.reply(ctx.source, "<green>Global mission stopped (the next starts after the configured interval).")
        return 1
    }

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can open the missions menu. Use /missions reload, progress, complete or reset.")
            return 0
        }
        MissionsGui(player).open()
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players have missions.")
            return 0
        }
        var shown = 0
        for (category in MissionsService.visibleCategories(player)) {
            Texts.reply(ctx.source, "<gold><bold>${Texts.escape(Texts.parse(category.displayName).string)}</bold>" + (MissionsService.resetIn(category)?.let { " <dark_gray>(rotates in ${Durations.format(it)})" } ?: ""))
            for (view in MissionsService.views(player, category.id)) {
                val mark = when (view.state) {
                    State.READY -> "<green>✔ ready"
                    State.ACTIVE -> "<yellow>▪"
                    State.CLAIMED -> "<dark_green>✔ completed"
                    State.COOLDOWN -> "<gray>⏳ ${Durations.format(view.cooldownLeftMs)}"
                    State.LOCKED -> "<red>✖ locked"
                    State.OFFER -> "<aqua>● join"
                }
                val progress = view.objectives.joinToString(" · ") { "${it.current}/${it.objective.amount}" }
                Texts.reply(ctx.source, " $mark <white>${Texts.escape(Texts.parse(view.mission.displayName).string)}</white> <gray>$progress <dark_gray>[${view.mission.key}]")
                shown++
            }
        }
        if (shown == 0) Texts.reply(ctx.source, "<gray>No missions available right now.")
        return shown
    }

    private fun claim(ctx: CommandContext<CommandSourceStack>, key: String?): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can claim missions.")
            return 0
        }
        val messages = MissionsService.config.messages
        if (key == null) {
            val count = MissionsService.claimAll(player)
            if (count == 0) Texts.reply(ctx.source, messages.nothingToClaim)
            return count
        }
        return when (MissionsService.claim(player, key)) {
            ClaimResult.OK -> 1
            ClaimResult.NOT_READY -> { Texts.fail(ctx.source, messages.notReady); 0 }
            ClaimResult.UNKNOWN -> { Texts.fail(ctx.source, messages.unknownMission, mapOf("mission" to Texts.escape(key))); 0 }
        }
    }

    private fun progress(ctx: CommandContext<CommandSourceStack>, amount: Long): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val key = StringArgumentType.getString(ctx, "chave")
        MissionsService.eventProgress(target, "manual", amount) { it.target.equals(key, ignoreCase = true) }
        Texts.reply(ctx.source, "<green>+$amount in <white>manual $key</white> for <white>${Texts.escape(target.gameProfile.name)}</white>.")
        return 1
    }

    private fun complete(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val key = StringArgumentType.getString(ctx, "missao")
        if (MissionsService.find(key) == null) {
            Texts.fail(ctx.source, MissionsService.config.messages.unknownMission, mapOf("mission" to Texts.escape(key)))
            return 0
        }
        if (!MissionsService.forceComplete(target, key)) {
            Texts.fail(ctx.source, "<red>This mission is not active for ${Texts.escape(target.gameProfile.name)} (already completed, locked or out of rotation).")
            return 0
        }
        Texts.reply(ctx.source, "<green>Mission <white>${Texts.escape(key)}</white> completed for <white>${Texts.escape(target.gameProfile.name)}</white>.")
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>, target: String?): Int {
        val player = EntityArgument.getPlayer(ctx, "jogador")
        val count = MissionsService.reset(player, target)
        Texts.reply(ctx.source, "<green>Progress cleared: <white>$count</white> mission(s) for <white>${Texts.escape(player.gameProfile.name)}</white>${if (target != null) " (${Texts.escape(target)})" else ""}.")
        return count
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        MissionsService.load()
        val total = MissionsService.categories.values.sumOf { it.missions.size }
        Texts.reply(ctx.source, "<green>Missions reloaded: ${MissionsService.categories.size} category/categories, $total mission(s).")
        return 1
    }
}
