package com.f4xizzz.serverbuddy.modules.motd

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

/**
 * `/serverbuddy motd ...` (e o atalho `/listmotd ...`, que pode mudar de nome em modules.conf):
 * `reload`, `preview [perfil]`, `profiles` e `maintenance [on|off]`. Permissão: `serverbuddy.motd.admin` (padrão: nível 3).
 */
object MotdCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "listmotd") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("motd").build(), "motd")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name)
            .requires(SbPermissions.require("serverbuddy.motd.admin", 3))
            .then(Commands.literal("reload").executes { reload(it) })
            .then(Commands.literal("profiles").executes { profiles(it) })
            .then(
                Commands.literal("preview")
                    .executes { preview(it, null) }
                    .then(
                        Commands.argument("perfil", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(MotdService.config.profiles.keys, builder) }
                            .executes { preview(it, StringArgumentType.getString(it, "perfil")) }
                    )
            )
            .then(
                Commands.literal("maintenance")
                    .executes { maintenanceStatus(it) }
                    .then(Commands.literal("on").executes { setMaintenance(it, true) })
                    .then(Commands.literal("off").executes { setMaintenance(it, false) })
            )

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        MotdService.load()
        Texts.reply(ctx.source, "<green>MOTD reloaded: ${MotdService.config.profiles.size} profile(s).")
        return 1
    }

    private fun profiles(ctx: CommandContext<CommandSourceStack>): Int {
        val server = ServerHolder.server
        val status = server?.status
        val now = if (status != null) MotdService.context(status.players().map { it.online() }.orElse(0), status.players().map { it.max() }.orElse(0)) else null
        Texts.reply(ctx.source, "<gold><bold>MOTD</bold> · profiles <gray>(the first one that matches wins)")
        var winner = true
        for ((name, profile) in MotdService.config.profiles) {
            val matches = now != null && MotdConditions.profileMatches(profile.conditions, profile.whenAny, now)
            val mark = when {
                matches && winner -> "<green>▶"
                matches -> "<yellow>•"
                else -> "<dark_gray>•"
            }
            if (matches) winner = false
            val conditions = (profile.conditions + profile.whenAny.map { "or $it" }).joinToString(", ").ifEmpty { "always" }
            Texts.reply(ctx.source, "$mark <white>${Texts.escape(name)}</white> <gray>— ${Texts.escape(conditions)} <dark_gray>(${profile.motds.size} motd)")
        }
        return MotdService.config.profiles.size
    }

    private fun preview(ctx: CommandContext<CommandSourceStack>, forced: String?): Int {
        val status = ServerHolder.server?.status
        if (status == null) {
            Texts.fail(ctx.source, "<red>The server has not built the server list yet.")
            return 0
        }
        val resolved = MotdService.resolve(status, forced)
        if (resolved == null) {
            Texts.fail(ctx.source, if (forced != null) "<red>There is no profile '${Texts.escape(forced)}'." else "<red>No profile matches right now (the motd in server.properties applies).")
            return 0
        }
        val how = if (forced != null) "requested" else "the first one whose conditions match now"
        Texts.reply(ctx.source, "<gold><bold>MOTD preview</bold> <gray>· profile <white>${Texts.escape(resolved.profileName)}</white> <dark_gray>($how)")
        ctx.source.sendSuccess({ resolved.description }, false)
        val count = if (resolved.hideCount) "hidden" else "${resolved.online}/${resolved.max}"
        Texts.reply(ctx.source, "<gray>Players: <white>$count</white> · icon: <white>${if (resolved.favicon != null) "from the Motd/icons folder" else "the server's own"}</white>")
        resolved.hover?.takeIf { it.isNotEmpty() }?.let { lines -> Texts.reply(ctx.source, "<gray>On hover: <white>${Texts.escape(lines.joinToString(" | ") { it.replace(Regex("§."), "") })}") }
        return 1
    }

    private fun maintenanceStatus(ctx: CommandContext<CommandSourceStack>): Int {
        Texts.reply(ctx.source, "<gray>Maintenance: ${if (MotdService.maintenance) "<red><bold>ON" else "<green>off"}<gray>. Use <white>/serverbuddy motd maintenance on|off</white>.")
        return if (MotdService.maintenance) 1 else 0
    }

    private fun setMaintenance(ctx: CommandContext<CommandSourceStack>, on: Boolean): Int {
        val kicked = MotdService.setMaintenance(on)
        if (on) {
            Texts.reply(ctx.source, "<red><bold>Maintenance on.</bold> <gray>Only operators and the \"allowed\" list can join." + if (kicked > 0) " <white>$kicked</white> player(s) kicked." else "", broadcast = true)
        } else {
            Texts.reply(ctx.source, "<green><bold>Maintenance off.</bold> <gray>Everyone can join again.", broadcast = true)
        }
        return 1
    }
}
