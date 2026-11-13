package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import java.util.function.Predicate

/**
 * `/battlepass` (atalho `/bp`, e `/serverbuddy battlepass`). Jogadores (`serverbuddy.battlepass.use`, liberado): menu, `claim`, `info`
 * e `premium` (comprar). Equipe (`serverbuddy.battlepass.admin`, nível 3): `addxp`, `addlevels`, `removelevels`, `reset`,
 * `resetrewards`, `premium give|remove <jogador>`, `season start|end|info`, `import` e `reload`.
 */
object BattlePassCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.battlepass.admin", 3)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val node = SbCommands.register(dispatcher, "battlepass") { name -> node(name) }
        SbCommands.alias(dispatcher, "bp", node, SbPermissions.require("serverbuddy.battlepass.use", true))
        ModuleCommands.mount(dispatcher, node("battlepass").build(), "battlepass")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.battlepass.use", true))
            .executes { open(it) }
            .then(Commands.literal("claim").executes { claim(it) })
            .then(Commands.literal("info").executes { info(it) })
            .then(
                Commands.literal("premium")
                    .executes { buy(it) }
                    .then(Commands.literal("give").requires(ADMIN).then(Commands.argument("jogador", EntityArgument.player()).executes { premium(it, true) }))
                    .then(Commands.literal("remove").requires(ADMIN).then(Commands.argument("jogador", EntityArgument.player()).executes { premium(it, false) }))
            )
            .then(
                Commands.literal("addxp").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("xp", IntegerArgumentType.integer(1)).executes { addXp(it) })
                )
            )
            .then(
                Commands.literal("addlevels").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("niveis", IntegerArgumentType.integer(1)).executes { addLevels(it) })
                )
            )
            .then(
                Commands.literal("removelevels").requires(ADMIN).then(
                    Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("niveis", IntegerArgumentType.integer(1)).executes { removeLevels(it) })
                )
            )
            .then(Commands.literal("reset").requires(ADMIN).then(Commands.argument("jogador", EntityArgument.player()).executes { reset(it, false) }))
            .then(Commands.literal("resetrewards").requires(ADMIN).then(Commands.argument("jogador", EntityArgument.player()).executes { reset(it, true) }))
            .then(
                Commands.literal("season").requires(ADMIN)
                    .executes { seasonInfo(it) }
                    .then(Commands.literal("info").executes { seasonInfo(it) })
                    .then(
                        Commands.literal("start")
                            .executes { seasonStart(it, null) }
                            .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650)).executes { seasonStart(it, IntegerArgumentType.getInteger(it, "dias")) })
                    )
                    .then(Commands.literal("end").executes { seasonEnd(it) })
            )
            .then(Commands.literal("import").requires(ADMIN).executes { import(it) })
            .then(Commands.literal("reload").requires(ADMIN).executes { reload(it) })

    private fun playerOrFail(ctx: CommandContext<CommandSourceStack>): net.minecraft.server.level.ServerPlayer? {
        val player = ctx.source.player
        if (player == null) Texts.fail(ctx.source, "<red>Only players have a battle pass.")
        return player
    }

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrFail(ctx) ?: return 0
        if (BattlePassService.passOf(player) == null) {
            Texts.reply(ctx.source, BattlePassService.config.messages.seasonInactive)
            return 0
        }
        BattlePassGui(player).open()
        return 1
    }

    private fun claim(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrFail(ctx) ?: return 0
        if (BattlePassService.passOf(player) == null) {
            Texts.reply(ctx.source, BattlePassService.config.messages.seasonInactive)
            return 0
        }
        val count = BattlePassService.claimAll(player)
        if (count == 0) Texts.reply(ctx.source, BattlePassService.config.messages.nothingToClaim)
        return count
    }

    private fun info(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrFail(ctx) ?: return 0
        val pass = BattlePassService.passOf(player)
        val season = BattlePassService.season
        if (pass == null || season == null) {
            Texts.reply(ctx.source, BattlePassService.config.messages.seasonInactive)
            return 0
        }
        val config = BattlePassService.config
        val needed = BattlePassService.xpNeeded(pass)
        val time = if (BattlePassService.isActive()) "ends in ${Durations.format(BattlePassService.timeLeftMs())}" else "ended"
        Texts.reply(ctx.source, "<gradient:#a855f7:#ec4899><bold>Battle Pass</bold></gradient> <gray>· season <white>${season.season}</white> ($time)")
        Texts.reply(ctx.source, "<gray>Level <white>${pass.level}</white>/<white>${config.levels}</white>" + if (needed > 0) " · XP <white>${pass.xp}</white>/<white>$needed</white>" else " <green>(max level)")
        val mult = BattlePassService.multiplierFor(player)
        if (mult > 1.0) Texts.reply(ctx.source, "<gold>XP bonus active: <white>x$mult")
        Texts.reply(ctx.source, "<gray>Premium pass: " + if (BattlePassService.isPremium(player)) "<green>active" else "<red>inactive")
        val claimable = BattlePassService.claimableCount(player)
        if (claimable > 0) Texts.reply(ctx.source, "<green>$claimable</green> <gray>reward(s) to claim: <white>/battlepass claim")
        return 1
    }

    private fun buy(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrFail(ctx) ?: return 0
        val config = BattlePassService.config
        val messages = config.messages
        if (BattlePassService.passOf(player) == null) {
            Texts.reply(ctx.source, messages.seasonInactive)
            return 0
        }
        if (BattlePassService.isPremium(player)) {
            Texts.reply(ctx.source, messages.premiumAlready)
            return 0
        }
        if (!config.premium.enabled || config.premium.price <= 0) {
            Texts.reply(ctx.source, messages.premiumNotForSale)
            return 0
        }
        val price = Economy.format(config.premium.price)
        return when (BattlePassService.buyPremium(player)) {
            BuyOutcome.OK -> { Texts.reply(ctx.source, messages.premiumBought); 1 }
            BuyOutcome.ALREADY -> { Texts.reply(ctx.source, messages.premiumAlready); 0 }
            BuyOutcome.NOT_FOR_SALE -> { Texts.reply(ctx.source, messages.premiumNotForSale); 0 }
            BuyOutcome.NO_ECONOMY -> { Texts.reply(ctx.source, messages.noEconomy); 0 }
            BuyOutcome.NOT_ENOUGH -> { Texts.reply(ctx.source, messages.notEnoughMoney, mapOf("price" to price)); 0 }
        }
    }

    private fun premium(ctx: CommandContext<CommandSourceStack>, value: Boolean): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        if (!BattlePassService.setPremium(target, value)) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        Texts.reply(ctx.source, if (value) "<green>Passe premium dado a <white>${Texts.escape(target.gameProfile.name)}</white>." else "<green>Passe premium tirado de <white>${Texts.escape(target.gameProfile.name)}</white>.")
        return 1
    }

    private fun addXp(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val xp = IntegerArgumentType.getInteger(ctx, "xp")
        if (!BattlePassService.addXp(target, xp.toLong(), "comando")) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        val pass = BattlePassService.passOf(target)
        Texts.reply(ctx.source, "<green>+$xp XP for <white>${Texts.escape(target.gameProfile.name)}</white> <gray>(level ${pass?.level}, ${pass?.xp} XP)")
        return 1
    }

    private fun addLevels(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val amount = IntegerArgumentType.getInteger(ctx, "niveis")
        if (!BattlePassService.addLevels(target, amount)) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        Texts.reply(ctx.source, "<green><white>${Texts.escape(target.gameProfile.name)}</white> is now at level <white>${BattlePassService.passOf(target)?.level}</white>.")
        return 1
    }

    private fun removeLevels(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val amount = IntegerArgumentType.getInteger(ctx, "niveis")
        if (!BattlePassService.removeLevels(target, amount)) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        Texts.reply(ctx.source, "<green><white>${Texts.escape(target.gameProfile.name)}</white> is now at level <white>${BattlePassService.passOf(target)?.level}</white>.")
        return 1
    }

    private fun reset(ctx: CommandContext<CommandSourceStack>, rewardsOnly: Boolean): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val ok = if (rewardsOnly) BattlePassService.resetRewards(target) else BattlePassService.reset(target)
        if (!ok) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        Texts.reply(ctx.source, if (rewardsOnly) "<green>Claims of <white>${Texts.escape(target.gameProfile.name)}</white> reset (levels are kept)." else "<green>Pass of <white>${Texts.escape(target.gameProfile.name)}</white> reset.")
        return 1
    }

    private fun seasonInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val season = BattlePassService.season
        if (season == null) {
            Texts.reply(ctx.source, "<gray>No season. Start one with <white>/battlepass season start [days]</white>.")
            return 0
        }
        val state = if (BattlePassService.isActive()) "<green>in progress <gray>(ends in <white>${Durations.format(BattlePassService.timeLeftMs())}</white>)" else "<red>ended"
        Texts.reply(ctx.source, "<gold><bold>Season ${season.season}</bold> <gray>· $state <gray>· ${BattlePassRepository.countPlayers(season.season)} player(s) with progress")
        return 1
    }

    private fun seasonStart(ctx: CommandContext<CommandSourceStack>, days: Int?): Int {
        val state = BattlePassService.startSeason(days)
        Texts.reply(ctx.source, "<green>Season <white>${state.season}</white> started: <white>${Durations.format(state.end - state.start)}</white>. Everyone restarts at level 1.", broadcast = true)
        return 1
    }

    private fun seasonEnd(ctx: CommandContext<CommandSourceStack>): Int {
        if (!BattlePassService.endSeason()) {
            Texts.fail(ctx.source, "<red>There is no season in progress.")
            return 0
        }
        Texts.reply(ctx.source, "<green>Season ended. Nobody earns more XP; unlocked rewards can still be claimed.", broadcast = true)
        return 1
    }

    private fun import(ctx: CommandContext<CommandSourceStack>): Int {
        val season = BattlePassService.season
        if (season == null) {
            Texts.fail(ctx.source, "<red>Start a season before importing (/battlepass season start).")
            return 0
        }
        val count = BattlePassImport.importPlayers(season.season)
        if (count == null) {
            Texts.fail(ctx.source, "<red>Could not find the Great BattlePass database (config/GreatBattlePass/greatbattlepass.db) or it did not open. Only SQLite is imported.")
            return 0
        }
        // quem está online já tinha um passe em memória: recarrega do banco
        for (player in ctx.source.server.playerList.players) BattlePassService.onJoin(player)
        Texts.reply(ctx.source, "<green>Imported <white>$count</white> player(s) from Great BattlePass into season <white>${season.season}</white>.")
        return count
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        BattlePassService.load()
        Texts.reply(ctx.source, "<green>Battle pass reloaded: ${BattlePassService.tiers.size} tier(s) with a reward.")
        return 1
    }
}
