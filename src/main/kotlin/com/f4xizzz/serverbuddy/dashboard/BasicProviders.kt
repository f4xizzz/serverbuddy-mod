package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Modules
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.lang.management.ManagementFactory

/**
 * What every plan's dashboard gets (Basic and up): the overview, the recent logs and the configs to read. Edits to the configs
 * are in [PlusProviders].
 */
object BasicProviders {
    fun register() {
        DashboardProviders.register("overview.get", ::overview)
        DashboardProviders.register("logs.tail") { ctx ->
            JsonObject().apply { add("lines", LogBuffer.tail(ctx.int("lines", 200), ctx.stringOrNull("level"))) }
        }
        DashboardProviders.register("config.list") {
            JsonObject().apply {
                add("files", JsonArray().also { files ->
                    for (entry in ConfigFiles.entries()) {
                        files.add(JsonObject().apply {
                            addProperty("module", entry.folder)
                            addProperty("file", entry.fileName)
                            addProperty("label", entry.label)
                        })
                    }
                })
            }
        }
        DashboardProviders.register("config.schema") { ctx -> ConfigSchema.schema(entry(ctx)) }
        DashboardProviders.register("config.get") { ctx ->
            val entry = entry(ctx)
            JsonObject().apply {
                add("values", ConfigSchema.values(entry))
                add("schema", ConfigSchema.schema(entry))
            }
        }
    }

    /** The config file named by `module` (the folder) and `file`. */
    fun entry(ctx: Ctx): ConfigFiles.Entry {
        val module = ctx.string("module")
        val file = ctx.string("file")
        return ConfigFiles.entries().firstOrNull { it.folder == module && it.fileName == file } ?: throw DashboardError("not_found", "config $module/$file")
    }

    private fun overview(ctx: Ctx): JsonObject {
        val server = ctx.server
        val runtime = Runtime.getRuntime()
        val mspt = server.averageTickTimeNanos / 1_000_000.0
        return JsonObject().apply {
            add("server", JsonObject().apply {
                addProperty("name", server.motd)
                addProperty("version", net.minecraft.SharedConstants.getCurrentVersion().name)
                addProperty("motd", server.motd)
                addProperty("tps", Math.round(minOf(20.0, 1000.0 / maxOf(mspt, 50.0)) * 10) / 10.0)
                addProperty("mspt", Math.round(mspt * 10) / 10.0)
                addProperty("uptimeSec", ManagementFactory.getRuntimeMXBean().uptime / 1000)
            })
            add("players", JsonObject().apply {
                addProperty("online", server.playerCount)
                addProperty("max", server.maxPlayers)
                add("list", JsonArray().also { list ->
                    for (player in server.playerList.players) {
                        list.add(JsonObject().apply {
                            addProperty("name", player.gameProfile.name)
                            addProperty("uuid", player.uuid.toString())
                        })
                    }
                })
            })
            add("memory", JsonObject().apply {
                addProperty("usedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024)
                addProperty("maxMb", runtime.maxMemory() / 1024 / 1024)
            })
            add("modules", JsonArray().also { modules ->
                for (module in Modules.all()) {
                    modules.add(JsonObject().apply {
                        addProperty("id", module.id)
                        addProperty("enabled", Modules.isActive(module.id))
                        addProperty("description", module.description)
                    })
                }
            })
            addProperty("tier", DashboardClient.plan)
        }
    }
}
