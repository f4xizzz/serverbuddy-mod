package com.f4xizzz.serverbuddy.modules.flan

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
import net.minecraft.server.level.ServerPlayer
import java.util.function.Predicate

/**
 * `/claimaccess` (e `/serverbuddy flan`): as mesmas regras do menu do Flan, para quem está dentro da claim.
 *
 *  - Quem pode entrar: sem argumento abre a tela de permissões; `tags` abre a de tags; `info` mostra tudo; `add|remove perm|tag <valor>`
 *    e `clear perm|tag` mudam por comando. Permissão: `serverbuddy.flan.edit` (padrão: todos; o Flan ainda exige que a pessoa possa editar a claim).
 *  - O que se ganha ao entrar: `grant perms|tags` abrem as telas, `grant add|remove perm|tag <valor>` e `grant clear perm|tag` mudam por comando.
 *    Permissão: `serverbuddy.flan.grants` (padrão: operadores nível 3).
 */
object FlanCommands {
    private val ADMIN: Predicate<CommandSourceStack> = SbPermissions.require("serverbuddy.flan.admin", 3)
    private val GRANTS: Predicate<CommandSourceStack> = SbPermissions.require(FlanBridge.GRANTS_PERMISSION, 3)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "claimaccess") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("flan").build(), "flan")
    }

    private fun kind(word: String, grant: Boolean): RuleKind = when {
        word == "tag" -> if (grant) RuleKind.GRANT_TAGS else RuleKind.TAGS
        else -> if (grant) RuleKind.GRANT_PERMISSIONS else RuleKind.PERMISSIONS
    }

    /** `add|remove perm|tag <valor>` (das regras de entrada ou, com [grant], do que se dá ao entrar). */
    private fun change(word: String, add: Boolean, grant: Boolean): LiteralArgumentBuilder<CommandSourceStack> {
        val root = Commands.literal(word)
        for (type in listOf("perm", "tag")) {
            root.then(Commands.literal(type).then(Commands.argument("valor", StringArgumentType.word()).executes { edit(it, kind(type, grant), StringArgumentType.getString(it, "valor"), add) }))
        }
        return root
    }

    private fun clearNode(grant: Boolean): LiteralArgumentBuilder<CommandSourceStack> {
        val clear = Commands.literal("clear")
        for (type in listOf("perm", "tag")) clear.then(Commands.literal(type).executes { clear(it, kind(type, grant)) })
        return clear
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.flan.edit", true))
            .executes { open(it, RuleKind.PERMISSIONS) }
            .then(Commands.literal("perms").executes { open(it, RuleKind.PERMISSIONS) })
            .then(Commands.literal("tags").executes { open(it, RuleKind.TAGS) })
            .then(Commands.literal("info").executes { info(it) })
            .then(change("add", true, false))
            .then(change("remove", false, false))
            .then(clearNode(false))
            .then(
                Commands.literal("grant")
                    .requires(GRANTS)
                    .executes { info(it) }
                    .then(Commands.literal("perms").executes { open(it, RuleKind.GRANT_PERMISSIONS) })
                    .then(Commands.literal("tags").executes { open(it, RuleKind.GRANT_TAGS) })
                    .then(change("add", true, true))
                    .then(change("remove", false, true))
                    .then(clearNode(true))
            )
            .then(Commands.literal("reload").requires(ADMIN).executes { reload(it) })

    private fun here(ctx: CommandContext<CommandSourceStack>): Pair<ServerPlayer, ClaimRef>? {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Players only.")
            return null
        }
        val claim = FlanBridge.claimAt(player, player.blockPosition())
        if (claim == null) {
            Texts.fail(ctx.source, FlanBridge.config.messages.noClaim)
            return null
        }
        if (!claim.canEdit(player)) {
            Texts.fail(ctx.source, FlanBridge.config.messages.notAllowed)
            return null
        }
        return player to claim
    }

    private fun open(ctx: CommandContext<CommandSourceStack>, kind: RuleKind): Int {
        val (player, claim) = here(ctx) ?: return 0
        RestrictionGui(player, claim, kind).open()
        return 1
    }

    private fun info(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        if (player == null) {
            Texts.fail(ctx.source, "<red>Players only.")
            return 0
        }
        val claim = FlanBridge.claimAt(player, player.blockPosition())
        if (claim == null) {
            Texts.fail(ctx.source, FlanBridge.config.messages.noClaim)
            return 0
        }
        val messages = FlanBridge.config.messages
        val owner = claim.owner?.let { ctx.source.server.profileCache?.get(it)?.map { p -> p.name }?.orElse(null) ?: it.toString() } ?: "Admin"
        Texts.reply(ctx.source, messages.infoHeader, mapOf("owner" to Texts.escape(owner)))
        val restriction = FlanStore.get(claim.id) ?: claim.parent?.let { FlanStore.get(it.id) }
        if (restriction == null || restriction.isEmpty) {
            Texts.reply(ctx.source, messages.infoNone)
            return 0
        }
        if (restriction.permissions.isNotEmpty()) Texts.reply(ctx.source, "<gray>Entry requires a permission (any one): <white>" + Texts.escape(restriction.permissions.joinToString(", ")))
        if (restriction.tags.isNotEmpty()) Texts.reply(ctx.source, "<gray>Entry requires a tag (any one): <white>" + Texts.escape(restriction.tags.joinToString(", ")))
        if (restriction.grantTags.isNotEmpty()) Texts.reply(ctx.source, "<gray>Tags granted on entry: <white>" + Texts.escape(restriction.grantTags.joinToString(", ")))
        if (restriction.grantPermissions.isNotEmpty()) Texts.reply(ctx.source, "<gray>Permissions granted on entry: <white>" + Texts.escape(restriction.grantPermissions.joinToString(", ")))
        return 1
    }

    private fun max(kind: RuleKind): Int {
        val config = FlanBridge.config
        return when (kind) {
            RuleKind.PERMISSIONS -> config.permissions.maxEntries
            RuleKind.TAGS -> config.tags.maxEntries
            RuleKind.GRANT_PERMISSIONS -> config.grants.maxPermissions
            RuleKind.GRANT_TAGS -> config.grants.maxTags
        }
    }

    /** O valor só pode ser um dos prontos da config? (e [entry] não é um deles) */
    private fun blockedCustom(kind: RuleKind, entry: String): Boolean {
        val config = FlanBridge.config
        val (allowCustom, presets) = when (kind) {
            RuleKind.PERMISSIONS -> config.permissions.allowCustom to config.permissions.presets
            RuleKind.TAGS -> true to emptyList()
            RuleKind.GRANT_PERMISSIONS -> config.grants.allowCustomPermissions to config.grants.permissionPresets
            RuleKind.GRANT_TAGS -> config.grants.allowCustomTags to config.grants.tagPresets
        }
        return !allowCustom && presets.none { it.equals(entry, true) }
    }

    private fun edit(ctx: CommandContext<CommandSourceStack>, kind: RuleKind, raw: String, add: Boolean): Int {
        val (_, claim) = here(ctx) ?: return 0
        val messages = FlanBridge.config.messages
        val entry = FlanAccess.clean(kind, raw)
        if (entry == null) {
            Texts.fail(ctx.source, messages.invalid)
            return 0
        }
        val list = FlanStore.edit(claim.id).list(kind)
        if (add) {
            when {
                blockedCustom(kind, entry) -> {
                    Texts.fail(ctx.source, messages.customBlocked)
                    return 0
                }
                list.any { it.equals(entry, true) } -> {
                    Texts.fail(ctx.source, messages.duplicate)
                    return 0
                }
                list.size >= max(kind) -> {
                    Texts.fail(ctx.source, messages.limit, mapOf("max" to max(kind)))
                    return 0
                }
            }
            list.add(entry)
            Texts.reply(ctx.source, messages.added, mapOf("entry" to Texts.escape(entry)))
        } else {
            if (!list.removeIf { it.equals(entry, true) }) {
                Texts.fail(ctx.source, "<red>That rule is not in the list.")
                return 0
            }
            Texts.reply(ctx.source, messages.removed, mapOf("entry" to Texts.escape(entry)))
        }
        FlanStore.save()
        return 1
    }

    private fun clear(ctx: CommandContext<CommandSourceStack>, kind: RuleKind): Int {
        val (_, claim) = here(ctx) ?: return 0
        FlanStore.edit(claim.id).list(kind).clear()
        FlanStore.save()
        Texts.reply(ctx.source, FlanBridge.config.messages.cleared)
        return 1
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        FlanBridge.load()
        Texts.reply(ctx.source, "<green>Flan configuration reloaded.")
        return 1
    }
}
