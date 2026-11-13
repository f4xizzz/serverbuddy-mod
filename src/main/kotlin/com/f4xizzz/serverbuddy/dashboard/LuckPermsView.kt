package com.f4xizzz.serverbuddy.dashboard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.InheritanceNode
import java.util.UUID

/** LuckPerms groups for the dashboard. Only loaded when LuckPerms is installed. */
object LuckPermsView {
    private val lp get() = LuckPermsProvider.get()

    fun groups(): JsonObject = JsonObject().apply {
        add("groups", JsonArray().also { array ->
            lp.groupManager.loadedGroups.sortedByDescending { it.weight.orElse(0) }.forEach { group ->
                array.add(JsonObject().apply {
                    addProperty("name", group.name)
                    addProperty("weight", group.weight.orElse(0))
                    group.displayName?.let { addProperty("displayName", it) }
                })
            }
        })
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
