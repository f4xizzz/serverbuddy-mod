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
        DashboardProviders.register("logs.tail", ::logsTail)
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
        DashboardProviders.register("config.search") { ctx ->
            JsonObject().apply { add("hits", ConfigTools.search(ctx.string("query"))) }
        }
        DashboardProviders.register("config.history") { ctx ->
            JsonObject().apply {
                add("backups", JsonArray().also { array ->
                    for (backup in ConfigTools.history(entry(ctx))) {
                        array.add(JsonObject().apply {
                            addProperty("id", backup.id)
                            addProperty("modified", backup.modified)
                            addProperty("size", backup.size)
                        })
                    }
                })
            }
        }
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

    /**
     * `logs.tail`: the recent console lines. Parameters (all optional): `lines` (1-1000, default 200), `level` ("WARN" = WARN and ERROR),
     * `source` ("serverbuddy" = only what ServerBuddy wrote; anything else = all), `module` (one ServerBuddy module, e.g. "Trivia", or "core"
     * for the general loggers) and `query` (plain text, case-insensitive, matched in the text and in the logger name).
     * Answers `lines` ({ ts, level, text, logger, own, module? }, oldest first) and `modules` (the ServerBuddy modules present in the buffer).
     */
    private fun logsTail(ctx: Ctx): JsonObject {
        val filter = LogBuffer.Filter(
            minLevel = ctx.stringOrNull("level"),
            ownOnly = ctx.stringOrNull("source").equals("serverbuddy", ignoreCase = true),
            module = ctx.stringOrNull("module")?.takeIf { it.isNotBlank() },
            query = ctx.stringOrNull("query")?.take(100)
        )
        return JsonObject().apply {
            add("lines", LogBuffer.toJson(LogBuffer.tail(ctx.int("lines", 200), filter)))
            add("modules", JsonArray().also { array -> LogBuffer.modules().forEach(array::add) })
            addProperty("capacity", LogBuffer.ALL_CAPACITY)
        }
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
