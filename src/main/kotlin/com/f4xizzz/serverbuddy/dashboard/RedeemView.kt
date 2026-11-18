package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.redeem.RedeemCode
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager
import com.f4xizzz.serverbuddy.redeem.RedeemManager
import com.f4xizzz.serverbuddy.redeem.RedeemRepository
import com.f4xizzz.serverbuddy.redeem.RedeemRules
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.TreeSet

/**
 * The Redeem section of the dashboard (Plus): every code with its state (active, exhausted, expired), who redeemed it and when, resetting the
 * uses of one player or of everybody, and creating, editing and deleting codes. The codes live inside `Redeem/redeem.conf`; a change keeps a
 * backup of that file first (the same history as the config editor) and reads the file again before it writes, so a hand edit is never lost.
 */
object RedeemView {
    private val NAME = Regex("^[A-Za-z0-9_.-]{1,32}$")

    /** Only describes ONE code (its form and checks); the codes themselves are the `codes` of redeem.conf. */
    private val codeEntry by lazy { ConfigFiles.Entry("Redeem", "(code)", RedeemCode::class.java, { RedeemCode() }) }

    fun register() {
        DashboardProviders.register("redeem.list", ::list)
        DashboardProviders.register("redeem.get", ::get)
        DashboardProviders.register("redeem.validate", ::validate)
        DashboardProviders.register("redeem.save", ::save)
        DashboardProviders.register("redeem.delete", ::delete)
        DashboardProviders.register("redeem.reset", ::reset)
    }

    private fun existing(typed: String): String? = RedeemRules.canonicalName(RedeemConfigManager.getConfig().codes.keys, typed.trim())

    private fun redeemFile(): ConfigFiles.Entry? = ConfigFiles.entries().firstOrNull { it.label == "Redeem/redeem.conf" }

    /** What the list says about a code: `invalid` (its date cannot be read, so nobody can redeem it), `expired`, `exhausted` (every use taken) or `active`. */
    internal fun stateOf(def: RedeemCode, uses: Int, now: Long): String {
        val expiry = RedeemRules.expiry(def.expiresAt)
        return when {
            expiry is RedeemRules.Expiry.Invalid -> "invalid"
            RedeemRules.isExpired(expiry, now) -> "expired"
            def.maxUses > 0 && uses >= def.maxUses -> "exhausted"
            else -> "active"
        }
    }

    /** One line of the list: the code, its state and the numbers the admin looks at. */
    private fun describe(name: String, def: RedeemCode, stats: LongArray?, now: Long): JsonObject {
        val uses = RedeemManager.getUses(name)
        val expiry = RedeemConfigManager.expiryOf(def)
        return JsonObject().apply {
            addProperty("code", name)
            addProperty("state", stateOf(def, uses, now))
            addProperty("uses", uses)
            addProperty("maxUses", def.maxUses)
            def.permission?.takeIf { it.isNotBlank() }?.let { addProperty("permission", it) }
            def.expiresAt?.takeIf { it.isNotBlank() }?.let { addProperty("expiresAt", it) }
            addProperty("expiresLabel", RedeemRules.describe(expiry))
            (expiry as? RedeemRules.Expiry.At)?.let { addProperty("expiresMillis", it.millis) }
            addProperty("commands", def.commands.size + def.entries.size)
            addProperty("items", def.items.size)
            if (stats != null && stats[0] > 0) addProperty("lastRedeemedAt", stats[1] * 1000)
        }
    }

    /** Permission nodes to suggest while the admin types one: the groups, what LuckPerms already knows and what the other codes use. */
    private fun permissionSuggestions(): JsonArray {
        val nodes = TreeSet<String>()
        RedeemConfigManager.getConfig().codes.values.forEach { code -> code.permission?.takeIf { it.isNotBlank() }?.let(nodes::add) }
        if (Integrations.isLoaded("luckperms")) {
            try {
                nodes += LuckPermsView.knownPermissions()
            } catch (e: Throwable) {
                // LuckPerms is installed but not ready: the other suggestions still work
            }
        }
        return JsonArray().also { array -> nodes.take(300).forEach(array::add) }
    }

    /** `redeem.list` → `{ codes: [...], schema, suggestions: { permissions } }`. */
    private fun list(ctx: Ctx): JsonObject {
        val summary = RedeemRepository.summary()
        val now = System.currentTimeMillis()
        return JsonObject().apply {
            add("codes", JsonArray().also { array ->
                for ((name, def) in RedeemConfigManager.getConfig().codes) array.add(describe(name, def, summary[name], now))
            })
            add("schema", ConfigSchema.schema(codeEntry))
            add("suggestions", JsonObject().apply { add("permissions", permissionSuggestions()) })
        }
    }

    /** `redeem.get` `{ code }` → the code, its full definition (for the form) and who redeemed it, newest first. */
    private fun get(ctx: Ctx): JsonObject {
        val typed = ctx.string("code")
        val name = existing(typed) ?: throw DashboardError("not_found", typed)
        val def = RedeemConfigManager.getConfig().codes[name] ?: throw DashboardError("not_found", typed)
        val claims = RedeemRepository.claimsOf(name)
        return JsonObject().apply {
            add("code", describe(name, def, RedeemRepository.summary()[name], System.currentTimeMillis()))
            add("definition", SbGson.pretty.toJsonTree(def))
            addProperty("claimsTotal", claims.size)
            add("claims", JsonArray().also { array ->
                for (claim in claims.take(200)) {
                    array.add(JsonObject().apply {
                        addProperty("uuid", claim.player().toString())
                        addProperty("name", PlayersView.nameFor(ctx.server, claim.player()))
                        addProperty("at", claim.at() * 1000)
                    })
                }
            })
        }
    }

    /** What the schema check and the mod's own rules find wrong in a code. */
    internal fun problems(definition: JsonElement): List<ConfigTools.Problem> {
        val found = ArrayList(ConfigTools.validate(codeEntry, definition))
        if (definition.isJsonObject) {
            val obj = definition.asJsonObject
            val expires = obj.get("expires_at")?.takeIf { it.isJsonPrimitive }?.asString
            if (RedeemRules.expiry(expires) is RedeemRules.Expiry.Invalid) {
                found += ConfigTools.Problem("expires_at", "expiry", "Cannot read this date. Use 2026-12-31, 2026-12-31 18:30 or 2026-12-31T18:30:00Z.")
            }
            val max = obj.get("max_uses")?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()
            if (max != null && max < 0) found += ConfigTools.Problem("max_uses", "range", "Cannot be negative (0 = no limit).")
        }
        return found
    }

    private fun problemsJson(found: List<ConfigTools.Problem>) = JsonArray().also { array -> found.forEach { array.add(it.toJson()) } }

    /** `redeem.validate` `{ definition }` → `{ ok, errors }`. */
    private fun validate(ctx: Ctx): JsonObject {
        val found = problems(ctx.params.get("definition") ?: throw DashboardError("bad_params", "missing definition"))
        return JsonObject().apply {
            addProperty("ok", found.none { !it.warning })
            add("errors", problemsJson(found))
        }
    }

    /**
     * `redeem.save` `{ code, definition, create? }`: creates (`create: true`, the name must be free) or edits a code. A refusal comes back as
     * `ok: false` with the `errors` and nothing is written. The name of an existing code cannot change (its redemptions are kept under it).
     */
    private fun save(ctx: Ctx): JsonObject {
        val typed = ctx.string("code").trim()
        if (!NAME.matches(typed)) throw DashboardError("bad_params", "The code name has 1 to 32 letters, digits, _ . or -")
        val definition = ctx.params.get("definition") ?: throw DashboardError("bad_params", "missing definition")
        val creating = ctx.params.get("create")?.let { it.isJsonPrimitive && it.asBoolean } == true
        RedeemConfigManager.loadConfig() // what the file says now: a hand edit made after the last reload is not thrown away
        val codes = RedeemConfigManager.getConfig().codes
        val current = existing(typed)
        if (creating && current != null) throw DashboardError("already_exists", typed)
        if (!creating && current == null) throw DashboardError("not_found", typed)
        val found = problems(definition)
        if (found.any { !it.warning }) {
            return JsonObject().apply {
                addProperty("ok", false)
                add("errors", problemsJson(found))
            }
        }
        val name = current ?: typed
        val before = current?.let { SbGson.pretty.toJsonTree(codes[it]) }
        val parsed = SbGson.gson.fromJson(definition, RedeemCode::class.java) ?: throw DashboardError("bad_config", "empty code")
        redeemFile()?.let { ConfigTools.backup(it) }
        codes[name] = parsed
        RedeemConfigManager.saveConfig()
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("created", current == null)
            addProperty("code", name)
            add("errors", problemsJson(found))
            add("changes", JsonArray().also { array -> ConfigTools.changes(before, SbGson.pretty.toJsonTree(parsed)).forEach(array::add) })
        }
    }

    /** `redeem.delete` `{ code }`: removes the code and forgets who redeemed it (a new code with the same name starts from zero). */
    private fun delete(ctx: Ctx): JsonObject {
        val typed = ctx.string("code")
        RedeemConfigManager.loadConfig()
        val name = existing(typed) ?: throw DashboardError("not_found", typed)
        redeemFile()?.let { ConfigTools.backup(it) }
        RedeemConfigManager.getConfig().codes.remove(name)
        RedeemConfigManager.saveConfig()
        val players = RedeemManager.resetAll(name)
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("code", name)
            addProperty("cleared", players)
        }
    }

    /** `redeem.reset` `{ code, player }` (one player, name or UUID) or `{ code, all: true }`: they can redeem it again. */
    private fun reset(ctx: Ctx): JsonObject {
        val typed = ctx.string("code")
        val name = existing(typed) ?: throw DashboardError("not_found", typed)
        val everybody = ctx.params.get("all")?.let { it.isJsonPrimitive && it.asBoolean } == true
        val reset = when {
            everybody -> RedeemManager.resetAll(name)
            ctx.stringOrNull("player") != null -> if (RedeemManager.resetForPlayer(PlayersView.who(ctx).uuid, name)) 1 else 0
            else -> throw DashboardError("bad_params", "give a player, or all: true")
        }
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("code", name)
            addProperty("reset", reset)
        }
    }
}
