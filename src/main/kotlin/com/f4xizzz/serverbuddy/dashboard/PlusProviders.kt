package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.modules.leaderboards.LeaderboardService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.UUID

/**
 * The Plus dashboard: edit the configs, the players (list, profile, inventory, Pokédex and party) and the leaderboards, plus the
 * audit of what the dashboard did. Only the Plus and Pro jars contain this class.
 */
object PlusProviders {
    fun register() {
        DashboardProviders.register("config.save") { ctx ->
            val entry = BasicProviders.entry(ctx)
            val warnings = ConfigSchema.save(entry, ctx.params.get("values") ?: throw DashboardError("bad_params", "missing values"))
            JsonObject().apply {
                addProperty("ok", true)
                add("warnings", JsonArray().also { array -> warnings.forEach(array::add) })
            }
        }
        DashboardProviders.register("config.reload") {
            val failed = ArrayList<String>()
            Modules.reloadAll { failed += it }
            JsonObject().apply {
                addProperty("ok", failed.isEmpty())
                add("failed", JsonArray().also { array -> failed.forEach(array::add) })
            }
        }

        DashboardProviders.register("players.list", ::list)
        DashboardProviders.register("players.get") { ctx -> profile(ctx.server, who(ctx)) }
        DashboardProviders.register("players.inventory") { ctx -> inventory(ctx) }
        DashboardProviders.register("players.dex") { ctx -> cobblemon { CobblemonView.dex(online(ctx)) } }
        DashboardProviders.register("players.party") { ctx -> cobblemon { CobblemonView.party(ctx.server, who(ctx).uuid) } }

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

    /** A player the dashboard named: online or known from the profile cache. */
    class Who(val uuid: UUID, val name: String, val online: ServerPlayer?)

    fun who(ctx: Ctx): Who {
        val name = ctx.stringOrNull("player") ?: throw DashboardError("bad_params", "missing parameter: player")
        ctx.server.playerList.getPlayerByName(name)?.let { return Who(it.uuid, it.gameProfile.name, it) }
        val profile = ctx.server.profileCache?.get(name)?.orElse(null) ?: throw DashboardError("player_not_found", name)
        return Who(profile.id, profile.name, null)
    }

    fun online(ctx: Ctx): ServerPlayer = who(ctx).online ?: throw DashboardError("player_offline")

    /** Runs Cobblemon-only code; without the mod the dashboard just says so. */
    private inline fun <T> cobblemon(block: () -> T): T {
        if (!Integrations.cobblemon()) throw DashboardError("needs_cobblemon")
        return block()
    }

    private fun list(ctx: Ctx): JsonObject {
        val server = ctx.server
        val query = ctx.stringOrNull("query")?.lowercase(Locale.ROOT).orEmpty()
        val offset = ctx.int("offset", 0).coerceAtLeast(0)
        val limit = ctx.int("limit", 50).coerceIn(1, 200)
        val seen = LinkedHashSet<UUID>()
        val players = ArrayList<JsonObject>()
        for (player in server.playerList.players) {
            if (query.isNotEmpty() && !player.gameProfile.name.lowercase(Locale.ROOT).contains(query)) continue
            seen += player.uuid
            players += JsonObject().apply {
                addProperty("name", player.gameProfile.name)
                addProperty("uuid", player.uuid.toString())
                addProperty("online", true)
                if (Integrations.cobblemon()) CobblemonView.dexPercent(player)?.let { addProperty("dexPercent", it) }
            }
        }
        for ((id, name) in knownProfiles(server)) {
            if (id in seen) continue
            if (query.isNotEmpty() && !name.lowercase(Locale.ROOT).contains(query)) continue
            seen += id
            players += JsonObject().apply {
                addProperty("name", name)
                addProperty("uuid", id.toString())
                addProperty("online", false)
            }
        }
        return JsonObject().apply {
            addProperty("total", players.size)
            add("players", JsonArray().also { array -> players.drop(offset).take(limit).forEach(array::add) })
        }
    }

    /** Players the server has seen, from usercache.json (the newest first as the server writes it). */
    private fun knownProfiles(server: MinecraftServer): List<Pair<UUID, String>> = try {
        val file = server.serverDirectory.resolve("usercache.json").toFile()
        if (!file.isFile) emptyList() else com.google.gson.JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonArray.mapNotNull { element ->
            val entry = element.asJsonObject
            val uuid = entry.get("uuid")?.asString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@mapNotNull null
            val name = entry.get("name")?.asString ?: return@mapNotNull null
            uuid to name
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun profile(server: MinecraftServer, who: Who): JsonObject = JsonObject().apply {
        addProperty("name", who.name)
        addProperty("uuid", who.uuid.toString())
        addProperty("online", who.online != null)
        val player = who.online ?: return@apply
        addProperty("health", player.health)
        addProperty("food", player.foodData.foodLevel)
        addProperty("xpLevel", player.experienceLevel)
        addProperty("gamemode", player.gameMode.gameModeForPlayer.getName())
        addProperty("dimension", player.level().dimension().location().toString())
        addProperty("x", Math.round(player.x * 10) / 10.0)
        addProperty("y", Math.round(player.y * 10) / 10.0)
        addProperty("z", Math.round(player.z * 10) / 10.0)
        addProperty("ping", player.connection.latency())
        addProperty("op", server.playerList.isOp(player.gameProfile))
    }

    private fun inventory(ctx: Ctx): JsonObject {
        val player = online(ctx)
        val container = if (ctx.stringOrNull("container") == "ender") player.enderChestInventory else player.inventory
        return JsonObject().apply {
            addProperty("container", if (ctx.stringOrNull("container") == "ender") "ender" else "inventory")
            add("items", JsonArray().also { array ->
                for (slot in 0 until container.containerSize) {
                    val stack = container.getItem(slot)
                    if (!stack.isEmpty) array.add(item(slot, stack))
                }
            })
        }
    }

    private fun item(slot: Int, stack: ItemStack): JsonObject = JsonObject().apply {
        addProperty("slot", slot)
        addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString())
        addProperty("count", stack.count)
        addProperty("name", stack.hoverName.string)
    }
}
