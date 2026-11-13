package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Targets
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.item.ItemArgument
import org.slf4j.LoggerFactory

/**
 * `/storage` (jogadores) e `/serverbuddy storage ...` (admin).
 *
 * Permissões: `serverbuddy.storage.use` (padrão: todos), `serverbuddy.storage.admin` (padrão: nível 2) e
 * `serverbuddy.storage.admin.command` (padrão: nível 4; só quem tem pode guardar um comando, que roda como console).
 */
object StorageCommands {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Storage")

    /** O item guardado é uma pilha só, e o Minecraft não grava pilha com mais de 99. Para mais, use várias recompensas. */
    private const val MAX_AMOUNT = 99

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, registryAccess: CommandBuildContext) {
        // o nome pode mudar (modules.conf) ou ceder a vez a outro mod que também tem /storage
        SbCommands.register(dispatcher, "storage") { name ->
            Commands.literal(name)
                .requires(SbPermissions.require("serverbuddy.storage.use", 0))
                .executes { open(it) }
        }
        ModuleCommands.mount(dispatcher, moduleNode(registryAccess).build(), "storage")
    }

    private fun admin(): java.util.function.Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.storage.admin", 2)

    private fun player(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("player", StringArgumentType.word()).suggests { ctx, builder -> Targets.suggestions(ctx.source, builder) }

    private fun moduleNode(registryAccess: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("storage")
            .requires(SbPermissions.require("serverbuddy.storage.use", 0))
            .executes { open(it) }
            .then(Commands.literal("open").executes { open(it) })
            .then(
                Commands.literal("add").requires(admin())
                    .then(
                        player()
                            .then(
                                Commands.literal("hand")
                                    .executes { addHand(it, 0) }
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT)).executes { addHand(it, IntegerArgumentType.getInteger(it, "amount")) })
                            )
                            .then(
                                Commands.literal("item").then(
                                    Commands.argument("item", ItemArgument.item(registryAccess))
                                        .executes { addItem(it, 1) }
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT)).executes { addItem(it, IntegerArgumentType.getInteger(it, "amount")) })
                                )
                            )
                            .then(
                                // o comando roda como console quando o jogador resgata: por isso só o nível 4 (ou a permissão abaixo)
                                Commands.literal("command").requires(SbPermissions.require("serverbuddy.storage.admin.command", 4)).then(
                                    Commands.argument("command", StringArgumentType.greedyString()).executes { addCommand(it) }
                                )
                            )
                    )
            )
            .then(Commands.literal("list").requires(admin()).then(player().executes { list(it) }))
            .then(
                Commands.literal("clear").requires(admin()).then(
                    player()
                        .executes { clear(it, null) }
                        .then(Commands.literal("items").executes { clear(it, StorageType.ITEM) })
                        .then(Commands.literal("commands").executes { clear(it, StorageType.COMMAND) })
                )
            )
            .then(Commands.literal("reload").requires(admin()).executes { reload(it) })

    // ---------------------------------------------------------------- jogador

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only a player can use this command.")
            return 0
        }
        if (!StorageService.ready) {
            Texts.fail(ctx.source, "<red>The storage is not ready yet. Try again in a moment.")
            return 0
        }
        StorageGui(player, StorageConfigManager.config).open()
        return 1
    }

    // ---------------------------------------------------------------- admin

    private fun target(ctx: CommandContext<CommandSourceStack>): Targets.Target? {
        val name = StringArgumentType.getString(ctx, "player")
        val found = Targets.resolve(ctx.source.server, name)
        if (found == null) Texts.fail(ctx.source, "<red>Player '$name' not found (use the name of someone who has joined the server, or the UUID).")
        return found
    }

    private fun addHand(ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val target = target(ctx) ?: return 0
        val holder = ctx.source.player
        if (holder == null) {
            Texts.fail(ctx.source, "<red>Only a player can use 'hand' (the item comes from their hand).")
            return 0
        }
        val stack = holder.mainHandItem.copy()
        if (stack.isEmpty) {
            Texts.fail(ctx.source, "<red>You need to hold an item in your hand.")
            return 0
        }
        if (amount > 0) stack.count = amount
        return report(ctx, target, StorageService.addItem(target.uuid, stack, "admin:${ctx.source.textName}"))
    }

    private fun addItem(ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val target = target(ctx) ?: return 0
        val stack = ItemArgument.getItem(ctx, "item").createItemStack(amount, false)
        return report(ctx, target, StorageService.addItem(target.uuid, stack, "admin:${ctx.source.textName}"))
    }

    private fun addCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val command = StringArgumentType.getString(ctx, "command")
        val spec = CommandRewardSpec(title = "<gray>Admin command", lore = listOf("<dark_gray>${Texts.escape(command)}"), commands = listOf(command))
        return report(ctx, target, StorageService.addCommands(target.uuid, spec, "admin:${ctx.source.textName}"))
    }

    private fun report(ctx: CommandContext<CommandSourceStack>, target: Targets.Target, ok: Boolean): Int {
        if (ok) {
            Texts.reply(ctx.source, "<green>Reward sent to the storage of <yellow>${target.name}</yellow>.", broadcast = true)
            LOGGER.info("[Storage] {} sent a reward to the storage of {}.", ctx.source.textName, target.name)
            return 1
        }
        Texts.fail(ctx.source, "<red>The reward could not be saved (see the console).")
        return 0
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val totals = StorageService.totals(target.uuid)
        Texts.reply(ctx.source, "<gold>Storage of <yellow>${target.name}</yellow>: <white>${totals.values.sum()}</white> reward(s)")
        for ((type, amount) in totals) Texts.reply(ctx.source, "<gray> - ${type.key}: <white>$amount")
        val entries = StorageService.entries(target.uuid)
        for (entry in entries.take(15)) {
            val what = when (entry) {
                is StorageEntry.ItemEntry -> "${entry.stack.count}x ${entry.stack.item}"
                is StorageEntry.CommandEntry -> "${entry.spec.commands.size} command(s)"
            }
            Texts.reply(ctx.source, "<dark_gray>[${entry.type.key}] <gray>${Texts.escape(what)} <dark_gray>(${entry.source ?: "-"}, ${entry.id.take(8)})")
        }
        if (entries.size > 15) Texts.reply(ctx.source, "<dark_gray>... and ${entries.size - 15} more.")
        return totals.values.sum()
    }

    private fun clear(ctx: CommandContext<CommandSourceStack>, type: StorageType?): Int {
        val target = target(ctx) ?: return 0
        val removed = StorageService.clear(target.uuid, type)
        Texts.reply(ctx.source, "<green>$removed reward(s) removed from the storage of <yellow>${target.name}</yellow>.", broadcast = true)
        LOGGER.info("[Storage] {} cleared {} reward(s) from the storage of {}.", ctx.source.textName, removed, target.name)
        return removed
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        StorageConfigManager.load()
        Texts.reply(ctx.source, "<green>Storage config reloaded.")
        return 1
    }
}
