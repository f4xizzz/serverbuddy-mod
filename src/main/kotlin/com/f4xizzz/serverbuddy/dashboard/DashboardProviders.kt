package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.AdminNotices
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/** An error the dashboard shows to the person: [code] is translated by the site (`dashboard.errors.<code>`). */
class DashboardError(val code: String, message: String? = null) : RuntimeException(message ?: code)

/** One request from the dashboard: the server, the parameters and the site user who asked. */
class Ctx(val server: MinecraftServer, val params: JsonObject, val by: String) {
    private fun raw(name: String): JsonElement? = params.get(name)?.takeIf { !it.isJsonNull }

    fun string(name: String): String = raw(name)?.asString ?: throw DashboardError("bad_params", "missing parameter: $name")
    fun stringOrNull(name: String): String? = raw(name)?.asString
    fun int(name: String, default: Int): Int = raw(name)?.asInt ?: default
    fun obj(name: String): JsonObject = raw(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: throw DashboardError("bad_params", "missing object: $name")
}

/**
 * The functions the dashboard can call (contract in `docs/LICENCAS-E-DASHBOARD.md` of the site). Every function has a minimum
 * PLAN, checked here again even though the site already filtered: the mod never trusts the website alone. A lower-tier jar does
 * not even contain the code of the higher tiers (the Plus and Pro providers are separate classes that the lower jars leave out).
 */
object DashboardProviders {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    /** Method → minimum plan (the same table as DASHBOARD_METHODS in the site's shared/constants.js). */
    private val REQUIRED = mapOf(
        "overview.get" to "basic", "logs.tail" to "basic", "config.list" to "basic", "config.get" to "basic", "config.schema" to "basic",
        "config.search" to "basic", "config.history" to "basic", "config.save" to "plus", "config.reload" to "plus", "config.validate" to "plus", "config.restore" to "plus",
        "redeem.list" to "plus", "redeem.get" to "plus", "redeem.validate" to "plus", "redeem.save" to "plus", "redeem.delete" to "plus", "redeem.reset" to "plus",
        "tags.list" to "plus", "tags.get" to "plus", "tags.player" to "plus", "tags.give" to "plus", "tags.remove" to "plus", "tags.equip" to "plus",
        "tags.validate" to "plus", "tags.save" to "plus", "tags.delete" to "plus", "players.list" to "plus", "players.get" to "plus", "players.inventory" to "plus",
        "players.party" to "plus", "players.dex" to "plus", "tops.list" to "plus", "tops.get" to "plus", "audit.list" to "plus",
        "players.enderchest" to "plus", "players.pc" to "plus", "pokemon.give" to "pro", "pokemon.remove" to "pro", "pokemon.move" to "pro", "console.run" to "pro",
        "luckperms.groups" to "pro", "luckperms.player" to "pro", "luckperms.set" to "pro", "luckperms.group" to "pro", "luckperms.groupCreate" to "pro",
        "luckperms.groupDelete" to "pro", "luckperms.groupUpdate" to "pro", "luckperms.groupParent" to "pro", "luckperms.groupNode" to "pro"
    )

    /** The methods that CHANGE something on the server: they go to the audit log and to the admins' chat. */
    val MUTATIONS: Set<String> = setOf(
        "config.save", "config.reload", "config.restore", "redeem.save", "redeem.delete", "redeem.reset", "tags.give", "tags.remove", "tags.equip", "tags.save",
        "tags.delete", "pokemon.give", "pokemon.remove", "pokemon.move", "console.run", "luckperms.set", "luckperms.groupCreate", "luckperms.groupDelete",
        "luckperms.groupUpdate", "luckperms.groupParent", "luckperms.groupNode"
    )

    private val handlers = LinkedHashMap<String, (Ctx) -> JsonElement>()

    private class AuditEntry(val ts: Long, val by: String, val method: String, val summary: String)

    private val audit = ArrayDeque<AuditEntry>()

    /** Registers one function. Called by the provider objects. */
    fun register(method: String, handler: (Ctx) -> JsonElement) {
        handlers[method] = handler
    }

    /**
     * Loads the providers of this jar by name: a jar of a lower plan simply does not have the classes of the higher plans, and
     * the functions they would register stay unknown (the site then answers `unknown_method`).
     */
    @JvmStatic
    fun init() {
        for (name in listOf("BasicProviders", "PlusProviders", "ProProviders")) {
            try {
                val instance = Class.forName("com.f4xizzz.serverbuddy.dashboard.$name").getField("INSTANCE").get(null)
                instance.javaClass.getMethod("register").invoke(instance)
            } catch (e: ClassNotFoundException) {
                // this jar's plan does not include these providers
            } catch (e: Throwable) {
                LOGGER.error("Could not load the dashboard providers '{}'", name, e)
            }
        }
    }

    /** Runs [method] (on the server thread). Throws [DashboardError] for anything the person should see. */
    fun handle(server: MinecraftServer, method: String, params: JsonObject, by: String): JsonElement {
        val required = REQUIRED[method] ?: throw DashboardError("unknown_method")
        if (!DashboardClient.linked) throw DashboardError("link_ended")
        if ((DashboardBuild.rank[DashboardClient.plan] ?: -1) < (DashboardBuild.rank[required] ?: Int.MAX_VALUE)) {
            throw DashboardError("plan_required", required)
        }
        val handler = handlers[method] ?: throw DashboardError("not_implemented")
        val ctx = Ctx(server, params, by)
        val result = handler(ctx)
        // a save the mod refused (the answer says ok = false) changed nothing: it is not an action to record
        val refused = result is JsonObject && result.get("ok")?.let { it.isJsonPrimitive && !it.asBoolean } == true
        if (method in MUTATIONS && !refused) record(by, method, summary(method, params, result))
        return result
    }

    /** A short, safe description for the audit log (never the whole command or config values). */
    private fun summary(method: String, params: JsonObject, result: JsonElement): String = when (method) {
        "config.save" -> listOfNotNull(params.get("module")?.asString, params.get("file")?.asString).joinToString("/") + changedPaths(result)
        "config.restore" -> listOfNotNull(params.get("module")?.asString, params.get("file")?.asString).joinToString("/") + " <- " + (params.get("backup")?.asString ?: "")
        "config.reload" -> listOfNotNull(params.get("module")?.asString, params.get("file")?.asString).joinToString("/")
        "console.run" -> params.get("command")?.asString?.substringBefore(' ')?.take(32) ?: ""
        "redeem.save" -> (params.get("code")?.asString ?: "") + changedPaths(result)
        "redeem.delete" -> params.get("code")?.asString ?: ""
        "redeem.reset" -> (params.get("code")?.asString ?: "") + " <- " + (params.get("player")?.asString ?: "everybody")
        "tags.give", "tags.remove", "tags.equip" -> (params.get("player")?.asString ?: "") + " " + (params.get("tag")?.asString ?: "(none)")
        "tags.save" -> (params.get("id")?.asString ?: "") + changedPaths(result)
        "tags.delete" -> params.get("id")?.asString ?: ""
        "luckperms.groupCreate", "luckperms.groupDelete" -> params.get("name")?.asString ?: ""
        "luckperms.groupUpdate" -> (params.get("name")?.asString ?: "") + " [" + listOf("weight", "displayName", "prefix", "suffix").filter { params.has(it) }.joinToString(", ") + "]"
        "luckperms.groupParent" -> (params.get("name")?.asString ?: "") + " " + (params.get("action")?.asString ?: "") + " " + (params.get("parent")?.asString ?: "")
        "luckperms.groupNode" -> (params.get("name")?.asString ?: "") + ": " + (params.get("action")?.asString ?: "") + " " + (params.get("key")?.asString ?: "")
        "pokemon.give", "pokemon.remove", "pokemon.move", "luckperms.set" -> params.get("player")?.asString ?: ""
        else -> ""
    }

    /** " [a.b, c.d +3]": the options a config save changed (names only, never the values). */
    private fun changedPaths(result: JsonElement): String {
        val changes = (result as? JsonObject)?.getAsJsonArray("changes") ?: return ""
        if (changes.size() == 0) return " [no changes]"
        val paths = changes.take(5).map { it.asJsonObject.get("path").asString }
        return " [" + paths.joinToString(", ") + (if (changes.size() > 5) " +" + (changes.size() - 5) else "") + "]"
    }

    private fun record(by: String, method: String, summary: String) {
        val entry = AuditEntry(System.currentTimeMillis(), by, method, summary)
        synchronized(audit) {
            audit.addFirst(entry)
            while (audit.size > 500) audit.removeLast()
        }
        try {
            File(ModPaths.subDir(), "dashboard-audit.log").appendText("${Instant.ofEpochMilli(entry.ts)} | $by | $method | $summary\n")
        } catch (e: Exception) {
            LOGGER.warn("Could not write dashboard-audit.log: {}", e.toString())
        }
        AdminNotices.broadcast("<gray>[Dashboard] <white>$by</white> ran <white>$method</white>" + if (summary.isNotBlank()) " <dark_gray>($summary)" else "")
    }

    /** The most recent actions that came from the dashboard, newest first. */
    fun auditEntries(limit: Int): List<JsonObject> = synchronized(audit) {
        audit.take(limit.coerceIn(1, 500)).map {
            JsonObject().apply {
                addProperty("ts", it.ts)
                addProperty("by", it.by)
                addProperty("method", it.method)
                addProperty("summary", it.summary)
            }
        }
    }
}
