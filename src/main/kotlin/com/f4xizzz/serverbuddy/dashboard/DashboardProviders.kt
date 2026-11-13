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
        "config.save" to "plus", "config.reload" to "plus", "players.list" to "plus", "players.get" to "plus", "players.inventory" to "plus",
        "players.party" to "plus", "players.dex" to "plus", "tops.list" to "plus", "tops.get" to "plus", "audit.list" to "plus",
        "players.pc" to "pro", "pokemon.give" to "pro", "pokemon.remove" to "pro", "pokemon.move" to "pro", "console.run" to "pro",
        "luckperms.groups" to "pro", "luckperms.player" to "pro", "luckperms.set" to "pro"
    )

    /** The methods that CHANGE something on the server: they go to the audit log and to the admins' chat. */
    val MUTATIONS: Set<String> = setOf("config.save", "config.reload", "pokemon.give", "pokemon.remove", "pokemon.move", "console.run", "luckperms.set")

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
        if (method in MUTATIONS) record(by, method, summary(method, params))
        return result
    }

    /** A short, safe description for the audit log (never the whole command or config values). */
    private fun summary(method: String, params: JsonObject): String = when (method) {
        "config.save", "config.reload" -> listOfNotNull(params.get("module")?.asString, params.get("file")?.asString).joinToString("/")
        "console.run" -> params.get("command")?.asString?.substringBefore(' ')?.take(32) ?: ""
        "pokemon.give", "pokemon.remove", "pokemon.move", "luckperms.set" -> params.get("player")?.asString ?: ""
        else -> ""
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
