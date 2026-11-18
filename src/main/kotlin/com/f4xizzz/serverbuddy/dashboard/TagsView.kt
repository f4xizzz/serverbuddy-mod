package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.tags.Outcome
import com.f4xizzz.serverbuddy.modules.tags.TagDef
import com.f4xizzz.serverbuddy.modules.tags.TagView
import com.f4xizzz.serverbuddy.modules.tags.TagsRepository
import com.f4xizzz.serverbuddy.modules.tags.TagsService
import com.f4xizzz.serverbuddy.modules.tags.UnlockType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

/**
 * The Tags section of the dashboard (Plus): the catalog (create, edit and delete tags), and what each player has: which tags they own or get
 * from a rule, which one they use, giving and taking tags (also while the player is offline). Changing the catalog keeps a backup of
 * `Tags/tags.json` first (the config editor's history). With GreatCosmetics as the source the catalog is read-only here.
 */
object TagsView {
    private val ID = Regex("^[a-z0-9_-]{1,32}$")
    private val markup = Regex("<[^>]+>")

    /** Only describes ONE tag (its form and checks); the tags themselves are the `tags` of tags.json. */
    private val tagEntry by lazy { ConfigFiles.Entry("Tags", "(tag)", TagDef::class.java, { TagDef() }) }

    fun register() {
        DashboardProviders.register("tags.list", ::list)
        DashboardProviders.register("tags.get", ::get)
        DashboardProviders.register("tags.player", ::player)
        DashboardProviders.register("tags.give", ::give)
        DashboardProviders.register("tags.remove", ::remove)
        DashboardProviders.register("tags.equip", ::equip)
        DashboardProviders.register("tags.validate", ::validate)
        DashboardProviders.register("tags.save", ::save)
        DashboardProviders.register("tags.delete", ::delete)
    }

    private fun plain(text: String) = text.replace(markup, "")

    /** The module has to be running and its tag system on. */
    private fun running() {
        if (!Modules.isRunning("tags")) throw DashboardError("module_off", "tags")
        TagsService.disabledReason()?.let { throw DashboardError("module_off", it) }
    }

    private fun editable() {
        if (TagsService.usingGreatCosmetics) throw DashboardError("tags_external", "GreatCosmetics")
    }

    private fun catalogFile(): ConfigFiles.Entry? = ConfigFiles.entries().firstOrNull { it.label == "Tags/tags.json" }

    private fun tagJson(view: TagView): JsonObject = JsonObject().apply {
        addProperty("id", view.id)
        addProperty("name", plain(view.def.name))
        addProperty("prefix", plain(view.def.prefix).trim())
        addProperty("category", view.def.category)
        addProperty("source", view.source.name.lowercase(Locale.ROOT))
        addProperty("hidden", view.def.hidden)
        addProperty("weight", view.def.weight)
        add("unlock", JsonObject().apply {
            addProperty("type", view.def.unlock.type.name.lowercase(Locale.ROOT))
            view.def.unlock.value.takeIf { it.isNotBlank() }?.let { addProperty("value", it) }
            if (view.def.unlock.price > 0) addProperty("price", view.def.unlock.price)
        })
    }

    /** `tags.list` → the catalog (as the menu shows it now), the categories and the schema of one tag. */
    private fun list(ctx: Ctx): JsonObject {
        running()
        return JsonObject().apply {
            addProperty("source", if (TagsService.usingGreatCosmetics) "greatcosmetics" else "own")
            addProperty("editable", !TagsService.usingGreatCosmetics)
            add("categories", JsonArray().also { array ->
                TagsService.catalogFile.categories.forEach { (id, button) ->
                    array.add(JsonObject().apply {
                        addProperty("id", id)
                        addProperty("name", plain(button.name))
                    })
                }
            })
            add("tags", JsonArray().also { array -> TagsService.catalog().forEach { array.add(tagJson(it)) } })
            add("schema", ConfigSchema.schema(tagEntry))
        }
    }

    /** `tags.get` `{ id }` → the tag and (for the tags of the own catalog) its definition for the form. */
    private fun get(ctx: Ctx): JsonObject {
        running()
        val id = ctx.string("id")
        val view = TagsService.find(id) ?: throw DashboardError("not_found", id)
        return JsonObject().apply {
            add("tag", tagJson(view))
            if (view.source == com.f4xizzz.serverbuddy.modules.tags.TagSource.OWN) add("definition", SbGson.pretty.toJsonTree(view.def))
        }
    }

    /** `tags.player` `{ player }` → every tag with its state for that player (owned, used, buyable, locked) and which can be taken away. */
    private fun player(ctx: Ctx): JsonObject {
        running()
        val who = PlusProviders.who(ctx)
        val online = who.online
        val owned = if (TagsService.usingGreatCosmetics) emptySet() else TagsRepository.owned(who.uuid)
        val equippedId = online?.let { TagsService.equippedView(it)?.id } ?: TagsRepository.equipped(who.uuid)
        val snapshot = online?.let { TagsService.snapshot(it) }
        return JsonObject().apply {
            addProperty("online", online != null)
            addProperty("name", who.name)
            equippedId?.let { addProperty("equipped", it) }
            add("tags", JsonArray().also { array ->
                for (view in TagsService.catalog()) {
                    array.add(JsonObject().apply {
                        addProperty("id", view.id)
                        addProperty("name", plain(view.def.name))
                        val state = when {
                            online != null && snapshot != null -> TagsService.stateOf(online, view, snapshot).name.lowercase(Locale.ROOT)
                            view.id == equippedId -> "equipped"
                            view.id in owned -> "owned"
                            // offline: a free tag is everybody's; a permission or a group cannot be checked without the player in the game
                            view.def.unlock.type == UnlockType.FREE -> "owned"
                            view.def.unlock.type == UnlockType.PERMISSION || view.def.unlock.type == UnlockType.GROUP -> "unknown"
                            else -> "locked"
                        }
                        addProperty("state", state)
                        addProperty("given", view.id in owned)
                    })
                }
            })
        }
    }

    /** `tags.give` `{ player, tag }`: gives the tag (the player is told, if they are online). */
    private fun give(ctx: Ctx): JsonObject {
        running()
        val who = PlusProviders.who(ctx)
        val view = TagsService.find(ctx.string("tag")) ?: throw DashboardError("not_found", ctx.string("tag"))
        if (!TagsService.grant(who.uuid, view.id)) throw DashboardError("already_has", view.id)
        who.online?.let { Texts.send(it, TagsService.config.messages.received, TagsService.vars(view, it)) }
        return JsonObject().apply { addProperty("ok", true) }
    }

    /** `tags.remove` `{ player, tag }`: takes a given tag away (one that comes from a permission or a group cannot be taken here). */
    private fun remove(ctx: Ctx): JsonObject {
        running()
        val who = PlusProviders.who(ctx)
        val view = TagsService.find(ctx.string("tag")) ?: throw DashboardError("not_found", ctx.string("tag"))
        if (!TagsService.revoke(who.uuid, view.id)) throw DashboardError("not_owned", view.id)
        who.online?.let { online -> if (TagsService.equippedView(online)?.id == view.id) TagsService.unequip(online) }
        return JsonObject().apply { addProperty("ok", true) }
    }

    /** `tags.equip` `{ player, tag? }`: the player (online) uses the tag, or takes theirs off when there is no `tag`. */
    private fun equip(ctx: Ctx): JsonObject {
        running()
        val online = PlusProviders.online(ctx)
        val tag = ctx.stringOrNull("tag")?.takeIf { it.isNotBlank() }
        val outcome = if (tag == null) TagsService.unequip(online) else TagsService.equip(online, tag)
        when (outcome) {
            Outcome.OK -> Unit
            Outcome.UNKNOWN -> throw DashboardError("not_found", tag ?: "")
            Outcome.NOT_OWNED -> throw DashboardError("not_owned", tag ?: "")
            Outcome.ALREADY -> throw DashboardError("already_has", tag ?: "")
            else -> throw DashboardError("error", outcome.name)
        }
        return JsonObject().apply { addProperty("ok", true) }
    }

    // ------------------------------------------------------------------ the catalog

    internal fun problems(definition: JsonElement): List<ConfigTools.Problem> {
        val found = ArrayList(ConfigTools.validate(tagEntry, definition))
        if (definition.isJsonObject) {
            val obj = definition.asJsonObject
            if (obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.isNullOrBlank()) found += ConfigTools.Problem("name", "required", "The tag needs a name.")
            val unlock = obj.get("unlock")?.takeIf { it.isJsonObject }?.asJsonObject
            val type = unlock?.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase(Locale.ROOT)
            val value = unlock?.get("value")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if ((type == "PERMISSION" || type == "GROUP") && value.isBlank()) {
                found += ConfigTools.Problem("unlock.value", "required", "This way of unlocking needs a value: a permission node, or the name of a LuckPerms group.")
            }
            if (type == "PRICE" && (unlock.get("price")?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0) <= 0.0) {
                found += ConfigTools.Problem("unlock.price", "range", "A tag for sale needs a price above 0.")
            }
        }
        return found
    }

    private fun problemsJson(found: List<ConfigTools.Problem>) = JsonArray().also { array -> found.forEach { array.add(it.toJson()) } }

    /** `tags.validate` `{ definition }` → `{ ok, errors }`. */
    private fun validate(ctx: Ctx): JsonObject {
        running()
        val found = problems(ctx.params.get("definition") ?: throw DashboardError("bad_params", "missing definition"))
        return JsonObject().apply {
            addProperty("ok", found.none { !it.warning })
            add("errors", problemsJson(found))
        }
    }

    private fun ownKey(id: String): String? = TagsService.catalogFile.tags.keys.firstOrNull { it.equals(id, ignoreCase = true) }

    /** `tags.save` `{ id, definition, create? }`: creates (`create: true`, the id must be free) or edits a tag of the catalog. A refusal comes back as `ok: false`. */
    private fun save(ctx: Ctx): JsonObject {
        running()
        editable()
        val id = ctx.string("id").trim().lowercase(Locale.ROOT)
        if (!ID.matches(id)) throw DashboardError("bad_params", "The tag id has 1 to 32 lowercase letters, digits, _ or -")
        val definition = ctx.params.get("definition") ?: throw DashboardError("bad_params", "missing definition")
        val creating = ctx.params.get("create")?.let { it.isJsonPrimitive && it.asBoolean } == true
        val current = ownKey(id)
        if (creating && current != null) throw DashboardError("already_exists", id)
        if (!creating && current == null) throw DashboardError("not_found", id)
        val found = problems(definition)
        if (found.any { !it.warning }) {
            return JsonObject().apply {
                addProperty("ok", false)
                add("errors", problemsJson(found))
            }
        }
        val parsed = SbGson.gson.fromJson(definition, TagDef::class.java) ?: throw DashboardError("bad_config", "empty tag")
        val before = current?.let { SbGson.pretty.toJsonTree(TagsService.catalogFile.tags[it]) }
        catalogFile()?.let { ConfigTools.backup(it) }
        TagsService.putTag(id, parsed)
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("created", current == null)
            addProperty("id", id)
            add("errors", problemsJson(found))
            add("changes", JsonArray().also { array -> ConfigTools.changes(before, SbGson.pretty.toJsonTree(parsed)).forEach(array::add) })
        }
    }

    /** `tags.delete` `{ id }`: takes the tag out of the catalog; everybody who had it loses it. */
    private fun delete(ctx: Ctx): JsonObject {
        running()
        editable()
        val id = ctx.string("id").trim()
        val key = ownKey(id) ?: throw DashboardError("not_found", id)
        catalogFile()?.let { ConfigTools.backup(it) }
        TagsService.removeTag(key)
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("id", key)
        }
    }
}
