package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.Integrations
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.util.Locale

/**
 * The Pro dashboard: the PC and editing Pokémon (give, remove, move), the console and the LuckPerms groups. Only the Pro jar
 * contains this class. Every call here is a mutation or a sensitive read; the audit and the admin chat notice come from
 * [DashboardProviders.handle].
 */
object ProProviders {
    fun register() {
        DashboardProviders.register("players.pc") { ctx -> cobblemon { CobblemonView.pc(ctx.server, PlusProviders.who(ctx).uuid) } }
        DashboardProviders.register("pokemon.give") { ctx -> cobblemon { CobblemonView.give(PlusProviders.online(ctx), ctx.string("properties")) } }
        DashboardProviders.register("pokemon.remove") { ctx ->
            cobblemon { CobblemonView.remove(PlusProviders.online(ctx), ctx.string("where"), ctx.int("box", 0), ctx.int("slot", 0)) }
        }
        DashboardProviders.register("pokemon.move") { ctx ->
            cobblemon {
                val from = ctx.obj("from")
                val to = ctx.obj("to")
                CobblemonView.move(
                    PlusProviders.online(ctx),
                    from.get("where")?.asString ?: throw DashboardError("bad_params", "from.where"),
                    from.get("box")?.asInt ?: 0,
                    from.get("slot")?.asInt ?: 0,
                    to.get("where")?.asString ?: throw DashboardError("bad_params", "to.where")
                )
            }
        }
        DashboardProviders.register("console.run", ::console)
        DashboardProviders.register("luckperms.groups") { luckperms { LuckPermsView.groups() } }
        DashboardProviders.register("luckperms.player") { ctx -> luckperms { LuckPermsView.player(PlusProviders.who(ctx).uuid) } }
        DashboardProviders.register("luckperms.set") { ctx ->
            luckperms { LuckPermsView.set(PlusProviders.who(ctx).uuid, ctx.string("group"), ctx.string("action")) }
        }
    }

    private inline fun <T> cobblemon(block: () -> T): T {
        if (!Integrations.cobblemon()) throw DashboardError("needs_cobblemon")
        return block()
    }

    private inline fun <T> luckperms(block: () -> T): T {
        if (!Integrations.isLoaded("luckperms")) throw DashboardError("needs_luckperms")
        return block()
    }

    /** Runs a command as the server console would, and sends back what it printed. */
    private fun console(ctx: Ctx): JsonObject {
        val settings = DashboardSettings.get()
        if (!settings.consoleEnabled) throw DashboardError("console_disabled")
        val command = ctx.string("command").trim().removePrefix("/")
        if (command.isEmpty()) throw DashboardError("bad_params", "empty command")
        val first = command.substringBefore(' ').lowercase(Locale.ROOT).removePrefix("minecraft:")
        if (settings.blockedCommands.any { it.lowercase(Locale.ROOT).removePrefix("/") == first }) {
            throw DashboardError("command_blocked", first)
        }

        val output = ArrayList<String>()
        val sink = object : CommandSource {
            override fun sendSystemMessage(message: Component) {
                if (output.size < settings.maxOutputLines) output += message.string
            }

            override fun acceptsSuccess() = true
            override fun acceptsFailure() = true
            override fun shouldInformAdmins() = false
        }
        val server = ctx.server
        val source = CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, server.overworld(), 4, "Dashboard (${ctx.by})", Component.literal("Dashboard"), server, null)
        server.commands.performPrefixedCommand(source, command)
        return JsonObject().apply { add("output", JsonArray().also { array -> output.forEach(array::add) }) }
    }
}
