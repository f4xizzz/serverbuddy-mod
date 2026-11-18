package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.group.Group
import net.luckperms.api.node.Node
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.matcher.NodeMatcher
import net.luckperms.api.node.types.DisplayNameNode
import net.luckperms.api.node.types.InheritanceNode
import net.luckperms.api.node.types.PermissionNode
import net.luckperms.api.node.types.PrefixNode
import net.luckperms.api.node.types.SuffixNode
import net.luckperms.api.node.types.WeightNode
import net.luckperms.api.query.QueryOptions
import net.minecraft.server.MinecraftServer
import java.time.Duration
import java.util.Locale
import java.util.UUID

/**
 * LuckPerms for the dashboard (Pro): the groups with their weight, prefix, suffix, parents, permissions (with contexts and expiry) and members,
 * creating and deleting groups, and the groups of a player. Only loaded when LuckPerms is installed. What can do harm asks for a confirmation
 * (`confirm: true`): deleting a group and giving `*`; the `default` group cannot be deleted.
 */
object LuckPermsView {
    private val lp: LuckPerms get() = LuckPermsProvider.get()

    /** What LuckPerms accepts as a group name here: lowercase letters, digits and _ . - */
    private val NAME = Regex("^[a-z0-9_.-]{1,36}$")
    private const val PROTECTED = "default"
    private const val DEFAULT_META_PRIORITY = 100

    private fun find(name: String): Group = lp.groupManager.getGroup(name.trim().lowercase(Locale.ROOT)) ?: throw DashboardError("not_found", "group $name")

    private fun ownPrefix(group: Group): PrefixNode? = group.getNodes(NodeType.PREFIX).maxByOrNull { it.priority }

    private fun ownSuffix(group: Group): SuffixNode? = group.getNodes(NodeType.SUFFIX).maxByOrNull { it.priority }

    private fun saved(group: Group): JsonObject {
        lp.groupManager.saveGroup(group).join()
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("name", group.name)
        }
    }

    /** A node as the dashboard shows it: the key, whether it is a yes or a no, when it ends and in which contexts it counts. */
    private fun nodeJson(node: Node): JsonObject = JsonObject().apply {
        addProperty("key", node.key)
        addProperty("value", node.value)
        node.expiry?.let { addProperty("expiresAt", it.toEpochMilli()) }
        if (!node.contexts.isEmpty()) {
            add("contexts", JsonArray().also { array ->
                node.contexts.forEach { context -> array.add(JsonObject().apply { addProperty("key", context.key); addProperty("value", context.value) }) }
            })
        }
    }

    /** `luckperms.groups` → every group, heaviest first, with what the list shows. */
    fun groups(): JsonObject = JsonObject().apply {
        add("groups", JsonArray().also { array ->
            lp.groupManager.loadedGroups.sortedWith(compareByDescending<Group> { it.weight.orElse(0) }.thenBy { it.name }).forEach { group ->
                array.add(JsonObject().apply {
                    addProperty("name", group.name)
                    addProperty("weight", group.weight.orElse(0))
                    group.displayName?.let { addProperty("displayName", it) }
                    ownPrefix(group)?.let { addProperty("prefix", it.metaValue) }
                    ownSuffix(group)?.let { addProperty("suffix", it.metaValue) }
                    add("parents", JsonArray().also { parents -> group.getNodes(NodeType.INHERITANCE).forEach { parents.add(it.groupName) } })
                    addProperty("permissions", group.getNodes(NodeType.PERMISSION).size)
                    addProperty("protected", group.name == PROTECTED)
                })
            }
        })
    }

    /** `luckperms.group` `{ name }` → one group in full, and who is in it. */
    fun group(server: MinecraftServer, name: String): JsonObject {
        val group = find(name)
        val members = lp.userManager.searchAll(NodeMatcher.key(InheritanceNode.builder(group.name).build())).join()
        return JsonObject().apply {
            addProperty("name", group.name)
            addProperty("weight", group.weight.orElse(0))
            group.displayName?.let { addProperty("displayName", it) }
            ownPrefix(group)?.let {
                addProperty("prefix", it.metaValue)
                addProperty("prefixPriority", it.priority)
            }
            ownSuffix(group)?.let {
                addProperty("suffix", it.metaValue)
                addProperty("suffixPriority", it.priority)
            }
            addProperty("protected", group.name == PROTECTED)
            add("parents", JsonArray().also { array -> group.getNodes(NodeType.INHERITANCE).sortedBy { it.groupName }.forEach { array.add(nodeJson(it).also { json -> json.addProperty("group", it.groupName) }) } })
            add("permissions", JsonArray().also { array -> group.getNodes(NodeType.PERMISSION).sortedBy { it.key }.take(500).forEach { array.add(nodeJson(it)) } })
            addProperty("membersTotal", members.size)
            add("members", JsonArray().also { array ->
                members.keys.map { it to PlayersView.nameFor(server, it) }.sortedBy { it.second.lowercase(Locale.ROOT) }.take(200).forEach { (uuid, playerName) ->
                    array.add(JsonObject().apply {
                        addProperty("uuid", uuid.toString())
                        addProperty("name", playerName)
                    })
                }
            })
        }
    }

    /** `luckperms.groupCreate` `{ name, weight? }`. */
    fun create(name: String, weight: Int?): JsonObject {
        val lower = name.trim().lowercase(Locale.ROOT)
        if (!NAME.matches(lower)) throw DashboardError("bad_params", "A group name has 1 to 36 lowercase letters, digits, _ . or -")
        if (lp.groupManager.getGroup(lower) != null) throw DashboardError("already_exists", lower)
        val group = lp.groupManager.createAndLoadGroup(lower).join()
        if (weight != null) group.data().add(WeightNode.builder(weight).build())
        return saved(group)
    }

    /** `luckperms.groupDelete` `{ name, confirm: true }`. The default group stays; everybody in the deleted group loses it. */
    fun delete(name: String, confirmed: Boolean): JsonObject {
        val group = find(name)
        if (group.name == PROTECTED) throw DashboardError("protected", PROTECTED)
        if (!confirmed) throw DashboardError("confirm_required", "delete the group ${group.name}")
        lp.groupManager.deleteGroup(group).join()
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("name", group.name)
        }
    }

    /**
     * `luckperms.groupUpdate` `{ name, weight?, displayName?, prefix?, suffix?, prefixPriority?, suffixPriority? }`: only what is there changes; an
     * empty prefix, suffix or display name takes it away.
     */
    fun update(name: String, params: JsonObject): JsonObject {
        val group = find(name)
        fun text(key: String): String? = params.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        fun number(key: String): Int? = params.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()

        number("weight")?.let {
            group.data().clear(NodeType.WEIGHT::matches)
            group.data().add(WeightNode.builder(it).build())
        }
        text("displayName")?.let { value ->
            group.data().clear(NodeType.DISPLAY_NAME::matches)
            if (value.isNotBlank()) group.data().add(DisplayNameNode.builder(value).build())
        }
        text("prefix")?.let { value ->
            val priority = number("prefixPriority") ?: ownPrefix(group)?.priority ?: DEFAULT_META_PRIORITY
            group.data().clear(NodeType.PREFIX::matches)
            if (value.isNotBlank()) group.data().add(PrefixNode.builder(value, priority).build())
        }
        text("suffix")?.let { value ->
            val priority = number("suffixPriority") ?: ownSuffix(group)?.priority ?: DEFAULT_META_PRIORITY
            group.data().clear(NodeType.SUFFIX::matches)
            if (value.isNotBlank()) group.data().add(SuffixNode.builder(value, priority).build())
        }
        return saved(group)
    }

    /** `luckperms.groupParent` `{ name, parent, action: "add" | "remove" }`: the group inherits (or stops inheriting) from another. */
    fun parent(name: String, parent: String, action: String): JsonObject {
        val group = find(name)
        val target = find(parent)
        if (group.name == target.name) throw DashboardError("bad_params", "A group cannot inherit from itself")
        val node = InheritanceNode.builder(target.name).build()
        when (action) {
            "add" -> {
                if (target.getInheritedGroups(QueryOptions.nonContextual()).any { it.name == group.name }) {
                    throw DashboardError("bad_params", "${target.name} already inherits from ${group.name}: that would go in circles")
                }
                group.data().add(node)
            }
            "remove" -> group.data().clear { it.type == NodeType.INHERITANCE && (it as InheritanceNode).groupName == target.name }
            else -> throw DashboardError("bad_params", "action must be add or remove")
        }
        return saved(group)
    }

    /**
     * `luckperms.groupNode` `{ name, key, value?: true, action: "set" | "unset", expiresInSeconds?, server?, world?, confirm? }`: gives, denies
     * (`value: false`) or takes away one permission, with an end time and contexts if asked. `*` asks for `confirm: true`.
     */
    fun node(name: String, key: String, value: Boolean, action: String, expiresInSeconds: Long?, server: String?, world: String?, confirmed: Boolean): JsonObject {
        val group = find(name)
        val cleaned = key.trim()
        if (cleaned.isEmpty() || cleaned.length > 200 || cleaned.any { it.isWhitespace() }) throw DashboardError("bad_params", "The permission has no spaces and up to 200 characters")
        if (action == "set" && value && cleaned == "*" && !confirmed) throw DashboardError("confirm_required", "give * to ${group.name}")
        val builder = PermissionNode.builder(cleaned).value(value)
        expiresInSeconds?.takeIf { it > 0 }?.let { builder.expiry(Duration.ofSeconds(it)) }
        server?.takeIf { it.isNotBlank() }?.let { builder.withContext("server", it.trim()) }
        world?.takeIf { it.isNotBlank() }?.let { builder.withContext("world", it.trim()) }
        val node = builder.build()
        // the same permission in the same contexts is replaced, never doubled
        group.data().clear { it.type == NodeType.PERMISSION && it.key.equals(cleaned, ignoreCase = true) && it.contexts == node.contexts }
        when (action) {
            "set" -> group.data().add(node)
            "unset" -> Unit
            else -> throw DashboardError("bad_params", "action must be set or unset")
        }
        return saved(group)
    }

    /** Permission nodes worth suggesting: `group.<name>` for every group and what the groups already have. */
    fun knownPermissions(): List<String> {
        val out = LinkedHashSet<String>()
        for (group in lp.groupManager.loadedGroups.sortedBy { it.name }) {
            out += "group.${group.name}"
            group.getNodes(NodeType.PERMISSION).forEach { out += it.key }
        }
        return out.toList()
    }

    fun player(uuid: UUID): JsonObject {
        val user = lp.userManager.loadUser(uuid).join()
        return JsonObject().apply {
            addProperty("primary", user.primaryGroup)
            add("groups", JsonArray().also { array -> user.getNodes(NodeType.INHERITANCE).forEach { array.add(it.groupName) } })
        }
    }

    /** `add` / `remove` a group, or `set` it as the only one. */
    fun set(uuid: UUID, group: String, action: String): JsonObject {
        val target = lp.groupManager.getGroup(group) ?: throw DashboardError("not_found", "group $group")
        val user = lp.userManager.loadUser(uuid).join()
        val node = InheritanceNode.builder(target.name).build()
        when (action) {
            "add" -> user.data().add(node)
            "remove" -> user.data().remove(node)
            "set" -> {
                user.data().clear(NodeType.INHERITANCE::matches)
                user.data().add(node)
            }
            else -> throw DashboardError("bad_params", "action must be add, remove or set")
        }
        lp.userManager.saveUser(user).join()
        return JsonObject().apply { addProperty("ok", true) }
    }
}
