package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.LegacyText
import com.f4xizzz.serverbuddy.core.Texts
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.PermissionNode
import net.luckperms.api.node.types.PrefixNode
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * What happens when a tag of ServerBuddy's catalog is equipped or taken off: prefix and permissions in LuckPerms,
 * scoreboard team (optional) and vanilla tag. All defensive: LuckPerms is optional.
 */
object TagEffects {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Tags")
    private const val TEAM_PREFIX = "sbt_"

    private fun luckPerms(): Boolean = TagsService.config.apply.luckPerms && Integrations.isLoaded("luckperms")

    fun apply(player: ServerPlayer, view: TagView) {
        val cfg = TagsService.config.apply
        val def = view.def
        if (luckPerms()) {
            try {
                val prefix = prefixText(def.prefix)
                LuckPermsTags.setPrefix(player, prefix, cfg.luckPermsWeight, cfg.luckPermsPersistent)
                LuckPermsTags.addPermissions(player, def.permissions, cfg.luckPermsPersistent)
            } catch (e: Throwable) {
                LOGGER.warn("Could not apply the tag '{}' in LuckPerms: {}", view.id, e.toString())
            }
        }
        if (def.minecraftTag.isNotBlank()) player.addTag(def.minecraftTag.trim())
        if (cfg.teamPrefix) setTeamPrefix(player, def.prefix)
    }

    fun clear(player: ServerPlayer, view: TagView) {
        val cfg = TagsService.config.apply
        val def = view.def
        if (luckPerms()) {
            try {
                LuckPermsTags.clearPrefix(player, cfg.luckPermsWeight, cfg.luckPermsPersistent)
                LuckPermsTags.removePermissions(player, def.permissions, cfg.luckPermsPersistent)
            } catch (e: Throwable) {
                LOGGER.warn("Could not take the tag '{}' off in LuckPerms: {}", view.id, e.toString())
            }
        }
        if (def.minecraftTag.isNotBlank()) player.removeTag(def.minecraftTag.trim())
        if (cfg.teamPrefix) clearTeamPrefix(player)
    }

    /** The prefix in the format LuckPerms receives: MiniMessage as written, or `§` if the config asks for it. */
    private fun prefixText(prefix: String): String =
        if (TagsService.config.apply.luckPermsFormat == TagsApplyConfig.Format.LEGACY) LegacyText.of(Texts.parse(prefix)) else prefix

    // ---------------------------------------------------------------- equipe do scoreboard

    private fun teamName(player: ServerPlayer): String = TEAM_PREFIX + player.uuid.toString().replace("-", "").take(12)

    private fun setTeamPrefix(player: ServerPlayer, prefix: String) {
        val scoreboard = player.server?.scoreboard ?: return
        val name = teamName(player)
        val team = scoreboard.getPlayerTeam(name) ?: scoreboard.addPlayerTeam(name)
        team.playerPrefix = Texts.parse(prefix)
        scoreboard.addPlayerToTeam(player.scoreboardName, team)
    }

    private fun clearTeamPrefix(player: ServerPlayer) {
        val scoreboard = player.server?.scoreboard ?: return
        val team = scoreboard.getPlayerTeam(teamName(player)) ?: return
        scoreboard.removePlayerFromTeam(player.scoreboardName, team)
        scoreboard.removePlayerTeam(team)
    }

    /** When the server starts: teams left over from a previous run. */
    fun cleanupTeams(server: net.minecraft.server.MinecraftServer) {
        val scoreboard = server.scoreboard
        scoreboard.playerTeams.filter { it.name.startsWith(TEAM_PREFIX) }.forEach { scoreboard.removePlayerTeam(it) }
    }
}

/** The part that talks to the LuckPerms API: only loaded when it is installed. */
internal object LuckPermsTags {
    private fun user(player: ServerPlayer): User? = LuckPermsProvider.get().userManager.getUser(player.uuid)

    private fun data(user: User, persistent: Boolean) = if (persistent) user.data() else user.transientData()

    private fun save(user: User, persistent: Boolean) {
        if (persistent) LuckPermsProvider.get().userManager.saveUser(user)
    }

    fun setPrefix(player: ServerPlayer, prefix: String, weight: Int, persistent: Boolean) {
        val user = user(player) ?: return
        val nodes = data(user, persistent)
        nodes.clear(NodeType.PREFIX.predicate { it.priority == weight })
        if (prefix.isNotEmpty()) nodes.add(PrefixNode.builder(prefix, weight).build())
        save(user, persistent)
    }

    fun clearPrefix(player: ServerPlayer, weight: Int, persistent: Boolean) {
        val user = user(player) ?: return
        data(user, persistent).clear(NodeType.PREFIX.predicate { it.priority == weight })
        save(user, persistent)
    }

    fun addPermissions(player: ServerPlayer, permissions: List<String>, persistent: Boolean) {
        if (permissions.isEmpty()) return
        val user = user(player) ?: return
        val nodes = data(user, persistent)
        for (raw in permissions) parse(raw)?.let { (node, value) -> nodes.add(PermissionNode.builder(node).value(value).build()) }
        save(user, persistent)
    }

    fun removePermissions(player: ServerPlayer, permissions: List<String>, persistent: Boolean) {
        if (permissions.isEmpty()) return
        val user = user(player) ?: return
        val nodes = data(user, persistent)
        for (raw in permissions) parse(raw)?.let { (node, value) -> nodes.remove(PermissionNode.builder(node).value(value).build()) }
        save(user, persistent)
    }

    /** `"node"`, `"-node"` (negates) or `"node false"`. */
    private fun parse(raw: String): Pair<String, Boolean>? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        var value = true
        if (text.endsWith(" false", ignoreCase = true)) {
            value = false
            text = text.dropLast(6).trim()
        } else if (text.endsWith(" true", ignoreCase = true)) {
            text = text.dropLast(5).trim()
        }
        if (text.startsWith("-")) {
            value = false
            text = text.substring(1).trim()
        }
        return if (text.isEmpty()) null else text to value
    }

    fun isInGroup(player: ServerPlayer, group: String): Boolean {
        val user = user(player) ?: return false
        return user.getInheritedGroups(user.queryOptions).any { it.name.equals(group, ignoreCase = true) }
    }

    class GroupInfo(val name: String, val displayName: String, val weight: Int, val prefix: String)

    fun groups(): List<GroupInfo> = LuckPermsProvider.get().groupManager.loadedGroups.map { group ->
        GroupInfo(
            group.name,
            group.displayName ?: group.name,
            group.weight.orElse(0),
            group.cachedData.metaData.prefix ?: ""
        )
    }
}
