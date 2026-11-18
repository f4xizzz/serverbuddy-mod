package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.AdminNotices
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/**
 * `/serverbuddy dashboard sync | disconnect | status`. The dashboard can run commands and edit configs, so by default only the
 * operators of the highest level (or the `serverbuddy.admin.dashboard` permission) can link it.
 */
object DashboardCommands {
    /** The invitation shown to the admins: the site and the command are clickable (the command is only typed for them, never run). */
    private fun invite(): String {
        val url = DashboardBuild.apiBase
        return "<green><bold>[ServerBuddy]</bold> The web dashboard is available at " +
            "<click:open_url:'$url'><hover:show_text:'<gray>Click to open the website'><aqua><underlined>${url.substringAfter("://")}</underlined></aqua></hover></click>. " +
            "Run <click:suggest_command:'/sb dashboard sync'><hover:show_text:'<gray>Click to type the command'><white>/sb dashboard sync</white></hover></click> to connect."
    }

    @JvmStatic
    fun registerNotices() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            if (server.isDedicatedServer && DashboardSettings.load().enabled) AdminNotices.broadcast(invite())
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { DashboardClient.disconnect(announce = false) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            if (server.isDedicatedServer && DashboardSettings.get().enabled && !DashboardClient.linked &&
                SbPermissions.check(player, "serverbuddy.admin.dashboard", 4)
            ) {
                player.sendSystemMessage(Texts.render(invite()))
            }
        }
    }

    @JvmStatic
    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("dashboard")
            .requires(SbPermissions.require("serverbuddy.admin.dashboard", 4))
            .executes { status(it) }
            .then(Commands.literal("sync").executes { sync(it) })
            .then(Commands.literal("disconnect").executes { disconnect(it) })
            .then(Commands.literal("status").executes { status(it) })

    private fun sync(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val admin = source.textName
        Texts.reply(source, "<gray>Asking the site for a pairing code…")
        DashboardClient.sync(admin) { code, error ->
            source.server.execute {
                if (code == null) {
                    Texts.fail(source, "<red>${Texts.escape(error ?: "Unknown error")}")
                } else {
                    // The code is shown only to the admin who ran the command (never broadcast). Clicking the code copies it; clicking the link opens the site.
                    val safe = Texts.escape(code)
                    val url = "${DashboardBuild.apiBase}/dashboard"
                    Texts.reply(source, "<green>Pairing code: <click:copy_to_clipboard:'$safe'><hover:show_text:'<gray>Click to copy the code'><white><bold>[ $safe ]</bold></white></hover></click> <gray>(valid for 10 minutes, one use)")
                    Texts.reply(source, "<gray>Type it at <click:open_url:'$url'><hover:show_text:'<gray>Click to open the dashboard in your browser'><aqua><underlined>$url</underlined></aqua></hover></click> <gray>and sign in with the account you will use for this dashboard.")
                }
            }
        }
        return 1
    }

    private fun disconnect(ctx: CommandContext<CommandSourceStack>): Int {
        DashboardClient.disconnect()
        Texts.reply(ctx.source, "<yellow>Dashboard disconnected.")
        return 1
    }

    private fun status(ctx: CommandContext<CommandSourceStack>): Int {
        for (line in DashboardClient.statusLines()) Texts.reply(ctx.source, "<gray>${Texts.escape(line)}")
        return 1
    }
}
