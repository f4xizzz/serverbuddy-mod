package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.ModuleCommands
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
import net.minecraft.commands.SharedSuggestionProvider

/**
 * `/tags` abre o menu. Admin: `/serverbuddy tags give|take|equip|unequip|list|create|delete|reload`.
 * Permissions: `serverbuddy.tags.use` (default: everybody) and `serverbuddy.tags.admin` (default: level 2).
 */
object TagsCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "tags") { name ->
            Commands.literal(name).requires(SbPermissions.require("serverbuddy.tags.use", 0)).executes { open(it) }
        }
        ModuleCommands.mount(dispatcher, adminNode().build(), "tags")
    }

    private fun admin(): java.util.function.Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.tags.admin", 2)

    private fun player(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("player", StringArgumentType.word()).suggests { ctx, builder -> Targets.suggestions(ctx.source, builder) }

    private fun tag(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("tag", StringArgumentType.word())
            .suggests { _, builder -> SharedSuggestionProvider.suggest(TagsService.catalog().map { it.id }, builder) }

    private fun adminNode(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("tags")
            .requires(SbPermissions.require("serverbuddy.tags.use", 0))
            .executes { open(it) }
            .then(Commands.literal("give").requires(admin()).then(player().then(tag().executes { give(it) })))
            .then(Commands.literal("take").requires(admin()).then(player().then(tag().executes { take(it) })))
            .then(Commands.literal("equip").requires(admin()).then(player().then(tag().executes { equip(it) })))
            .then(Commands.literal("unequip").requires(admin()).then(player().executes { unequip(it) }))
            .then(
                Commands.literal("list").requires(admin())
                    .executes { list(it, null) }
                    .then(player().executes { list(it, StringArgumentType.getString(it, "player")) })
            )
            .then(
                Commands.literal("create").requires(admin()).then(
                    Commands.argument("id", StringArgumentType.word()).then(
                        Commands.argument("prefix", StringArgumentType.greedyString()).executes { create(it) }
                    )
                )
            )
            .then(Commands.literal("delete").requires(admin()).then(tag().executes { delete(it) }))
            .then(Commands.literal("reload").requires(admin()).executes { reload(it) })

    private fun open(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, TagsService.config.messages.onlyPlayers)
            return 0
        }
        if (!TagsService.ready) {
            Texts.fail(ctx.source, "<red>The tags are not ready yet. Try again in a moment.")
            return 0
        }
        TagsGui(player, TagsService.config).open()
        return 1
    }

    private fun target(ctx: CommandContext<CommandSourceStack>): Targets.Target? {
        val name = StringArgumentType.getString(ctx, "player")
        val found = Targets.resolve(ctx.source.server, name)
        if (found == null) Texts.fail(ctx.source, "<red>Player '${Texts.escape(name)}' not found.")
        return found
    }

    private fun view(ctx: CommandContext<CommandSourceStack>): TagView? {
        val id = StringArgumentType.getString(ctx, "tag")
        val found = TagsService.find(id)
        if (found == null) Texts.fail(ctx.source, TagsService.config.messages.unknownTag, mapOf("tag" to Texts.escape(id)))
        return found
    }

    private fun give(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val view = view(ctx) ?: return 0
        if (!TagsService.grant(target.uuid, view.id)) {
            Texts.fail(ctx.source, "<yellow>${Texts.escape(target.name)} already has the tag ${view.def.name}<yellow>.")
            return 0
        }
        Texts.reply(ctx.source, "<green>Tag ${view.def.name}<green> dada a <yellow>${Texts.escape(target.name)}</yellow>.", broadcast = true)
        target.online?.let { Texts.send(it, TagsService.config.messages.received, TagsService.vars(view, it)) }
        return 1
    }

    private fun take(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val view = view(ctx) ?: return 0
        if (!TagsService.revoke(target.uuid, view.id)) {
            Texts.fail(ctx.source, "<yellow>${Texts.escape(target.name)} did not have the tag (or it comes from another source, such as a permission or group).")
            return 0
        }
        target.online?.let { online ->
            if (TagsService.equippedView(online)?.id == view.id) TagsService.unequip(online)
        }
        Texts.reply(ctx.source, "<green>Tag ${view.def.name}<green> tirada de <yellow>${Texts.escape(target.name)}</yellow>.", broadcast = true)
        return 1
    }

    private fun equip(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val online = target.online
        if (online == null) {
            Texts.fail(ctx.source, "<red>${Texts.escape(target.name)} must be online.")
            return 0
        }
        val view = view(ctx) ?: return 0
        TagsService.grant(target.uuid, view.id)
        val outcome = TagsService.equip(online, view.id)
        if (outcome == Outcome.OK || outcome == Outcome.ALREADY) {
            Texts.reply(ctx.source, "<green>${Texts.escape(target.name)} agora usa a tag ${view.def.name}<green>.", broadcast = true)
            return 1
        }
        Texts.fail(ctx.source, "<red>Could not equip the tag (${outcome.name.lowercase()}).")
        return 0
    }

    private fun unequip(ctx: CommandContext<CommandSourceStack>): Int {
        val target = target(ctx) ?: return 0
        val online = target.online
        if (online == null) {
            Texts.fail(ctx.source, "<red>${Texts.escape(target.name)} must be online.")
            return 0
        }
        TagsService.unequip(online)
        Texts.reply(ctx.source, "<green>Tag de <yellow>${Texts.escape(target.name)}</yellow> tirada.", broadcast = true)
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>, playerName: String?): Int {
        val target = if (playerName != null) Targets.resolve(ctx.source.server, playerName) else null
        if (playerName != null && target == null) {
            Texts.fail(ctx.source, "<red>Player '${Texts.escape(playerName)}' not found.")
            return 0
        }
        val owned = if (target != null && !TagsService.usingGreatCosmetics) TagsRepository.owned(target.uuid) else emptySet()
        val equipped = target?.let { if (TagsService.usingGreatCosmetics) GreatCosmeticsTags.equippedId(it.uuid) else TagsRepository.equipped(it.uuid) }
        val catalog = TagsService.catalog()
        val where = if (TagsService.usingGreatCosmetics) "GreatCosmetics" else "ServerBuddy"
        Texts.reply(ctx.source, "<gold><bold>Tags</bold> <gray>(${catalog.size}, catalog: $where)" + (target?.let { " · of <white>${Texts.escape(it.name)}" } ?: ""))
        for (view in catalog) {
            val mark = when {
                equipped.equals(view.id, ignoreCase = true) -> "<green>★"
                view.id in owned -> "<yellow>✔"
                else -> "<dark_gray>•"
            }
            val unlock = view.def.unlock
            val how = unlock.type.name.lowercase() + (unlock.value.takeIf { it.isNotBlank() && unlock.type != UnlockType.GIVEN }?.let { " $it" } ?: "")
            Texts.reply(ctx.source, "$mark <white>${Texts.escape(view.id)}</white> <gray>${view.def.name} <dark_gray>($how)")
        }
        return catalog.size
    }

    private fun create(ctx: CommandContext<CommandSourceStack>): Int {
        if (TagsService.usingGreatCosmetics) {
            Texts.fail(ctx.source, "<red>The catalog is GreatCosmetics' now: create the tags there.")
            return 0
        }
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val prefix = StringArgumentType.getString(ctx, "prefix")
        if (TagsService.find(id) != null) {
            Texts.fail(ctx.source, "<red>A tag '${Texts.escape(id)}' already exists.")
            return 0
        }
        TagsService.putTag(id, TagDef(name = prefix.trim(), prefix = if (prefix.endsWith(" ")) prefix else "$prefix "))
        Texts.reply(ctx.source, "<green>Tag <white>${Texts.escape(id)}</white> created. Edit its icon, description and how it is earned in <white>Tags/tags.json</white>.", broadcast = true)
        return 1
    }

    private fun delete(ctx: CommandContext<CommandSourceStack>): Int {
        if (TagsService.usingGreatCosmetics) {
            Texts.fail(ctx.source, "<red>The catalog is GreatCosmetics' now: delete the tag there.")
            return 0
        }
        val id = StringArgumentType.getString(ctx, "tag")
        if (!TagsService.removeTag(id)) {
            Texts.fail(ctx.source, TagsService.config.messages.unknownTag, mapOf("tag" to Texts.escape(id)))
            return 0
        }
        Texts.reply(ctx.source, "<green>Tag <white>${Texts.escape(id)}</white> apagada (e tirada de quem tinha).", broadcast = true)
        return 1
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        TagsService.load()
        Texts.reply(ctx.source, "<green>Tags reloaded: ${TagsService.catalog().size}.")
        return 1
    }
}
