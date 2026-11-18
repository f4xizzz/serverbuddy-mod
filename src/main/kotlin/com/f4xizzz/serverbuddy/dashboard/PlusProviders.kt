package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.modules.leaderboards.LeaderboardService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * The Plus dashboard: edit the configs, the players (see [PlayersView]: online and offline) and the leaderboards, plus the audit of
 * what the dashboard did.
 */
object PlusProviders {
    fun register() {
        DashboardProviders.register("config.save") { ctx ->
            val entry = BasicProviders.entry(ctx)
            val result = ConfigSchema.save(entry, ctx.params.get("values") ?: throw DashboardError("bad_params", "missing values"))
            JsonObject().apply {
                addProperty("ok", result.written)
                add("errors", problems(result.problems))
                add("warnings", JsonArray().also { array -> result.warnings.forEach(array::add) })
                add("changes", JsonArray().also { array -> result.changes.forEach(array::add) })
            }
        }
        DashboardProviders.register("config.validate") { ctx ->
            val found = ConfigTools.validate(BasicProviders.entry(ctx), ctx.params.get("values") ?: throw DashboardError("bad_params", "missing values"))
            JsonObject().apply {
                addProperty("ok", found.none { !it.warning })
                add("errors", problems(found))
            }
        }
        DashboardProviders.register("config.restore") { ctx ->
            val entry = BasicProviders.entry(ctx)
            val values = ConfigTools.restore(entry, ctx.string("backup"))
            JsonObject().apply {
                addProperty("ok", true)
                add("values", values)
            }
        }
        DashboardProviders.register("config.reload", ::reload)

        PlayersView.register()
        RedeemView.register()
        TagsView.register()

        DashboardProviders.register("tops.list") {
            JsonObject().apply {
                add("boards", JsonArray().also { array ->
                    for ((id, board) in LeaderboardService.boards) {
                        if (!board.enabled) continue
                        array.add(JsonObject().apply {
                            addProperty("id", id)
                            addProperty("title", board.title.replace(Regex("<[^>]+>"), ""))
                            addProperty("type", board.type)
                        })
                    }
                })
            }
        }
        DashboardProviders.register("tops.get") { ctx ->
            val id = ctx.string("board").lowercase(Locale.ROOT)
            val board = LeaderboardService.board(id) ?: throw DashboardError("not_found", "board $id")
            val entries = LeaderboardService.snapshot(id)?.entries.orEmpty().take(ctx.int("limit", 25).coerceIn(1, 100))
            JsonObject().apply {
                add("entries", JsonArray().also { array ->
                    entries.forEachIndexed { index, entry ->
                        array.add(JsonObject().apply {
                            addProperty("pos", index + 1)
                            addProperty("name", entry.name)
                            addProperty("uuid", entry.uuid.toString())
                            addProperty("value", LeaderboardService.formatValue(board, entry.value).replace(Regex("<[^>]+>"), ""))
                            addProperty("raw", entry.value)
                        })
                    }
                })
            }
        }
        DashboardProviders.register("audit.list") { ctx ->
            JsonObject().apply { add("entries", JsonArray().also { array -> DashboardProviders.auditEntries(ctx.int("limit", 100)).forEach(array::add) }) }
        }
    }

    private fun problems(list: List<ConfigTools.Problem>) = JsonArray().also { array -> list.forEach { array.add(it.toJson()) } }

    /**
     * `config.reload` { module?, file? } → `{ ok, results: [{ label, ok, error?, note? }], failed: [...] }`. With a file: that file is applied again (its own
     * loader, else its module). Without: everything, like `/sb reload`. Every reload says on its own whether it worked.
     */
    private fun reload(ctx: Ctx): JsonObject {
        ConfigSecrets.refresh() // reread manual edits while keeping credentials present in older log lines protected
        val results = JsonArray()
        fun report(label: String, error: String?, note: String? = null) {
            results.add(JsonObject().apply {
                addProperty("label", label)
                addProperty("ok", error == null)
                error?.let { addProperty("error", ConfigSecrets.redactEverywhere(it)) } // a loader's message can quote what it was reading
                note?.let { addProperty("note", it) }
            })
        }
        fun attempt(label: String, note: String? = null, action: () -> Unit) {
            try {
                action()
                report(label, null, note)
            } catch (e: Throwable) {
                report(label, e.message ?: e.javaClass.simpleName)
            }
        }

        if (ctx.stringOrNull("file") != null) {
            val entry = BasicProviders.entry(ctx)
            val hook = entry.reload
            when {
                hook != null -> attempt(entry.label, action = hook)
                entry.folder.isEmpty() -> attempt(entry.label) { Modules.reloadAll { throw IllegalStateException("could not reload $it") } }
                else -> {
                    var done = false
                    attempt(entry.label, note = "module") { done = Modules.reloadFolder(entry.folder) }
                    if (!done && results.last().asJsonObject.get("ok").asBoolean) {
                        results.remove(results.size() - 1)
                        report(entry.label, null, "restart")
                    }
                }
            }
        } else {
            val failed = ArrayList<String>()
            Modules.reloadAll { failed += it }
            if (failed.isEmpty()) report("modules", null) else failed.forEach { report(it, "could not reload (see the console)") }
            for (hook in com.f4xizzz.serverbuddy.core.ConfigFiles.entries().mapNotNull { it.reload }.distinct()) attempt("loaders", action = hook)
        }
        return JsonObject().apply {
            addProperty("ok", results.all { it.asJsonObject.get("ok").asBoolean })
            add("results", results)
            add("failed", JsonArray().also { array -> results.filter { !it.asJsonObject.get("ok").asBoolean }.forEach { array.add(it.asJsonObject.get("label")) } })
        }
    }

    /** The player a request named (a name or a UUID), online or offline. */
    fun who(ctx: Ctx): PlayersView.Who = PlayersView.who(ctx)

    /** The same, but the player has to be online (what changes the game needs). */
    fun online(ctx: Ctx): ServerPlayer = who(ctx).online ?: throw DashboardError("player_offline")
}
