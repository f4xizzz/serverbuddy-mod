package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPlaceholders
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory

/**
 * Chat tags with a chest menu (`/tags`): the player picks a prefix among the tags they have. Works on its own and, with
 * GreatCosmetics installed, uses its tag catalog and ownership. See Tags/config.json (`integration.source` chooses where the
 * tags come from, or turns the system off so another mod can provide `/tags`) and Tags/tags.json.
 */
class TagsModule : SbModule {
    private val log = LoggerFactory.getLogger("ServerBuddy/Tags")

    override val id = "tags"
    override val folder = "Tags"
    override val legacyFolders = emptyList<String>()
    override val description = "Chat tags with a chest menu (/tags); works with GreatCosmetics and LuckPerms."

    override fun init() {
        TagsService.load()
        if (TagsService.disabled) {
            // nothing is registered: no /tags command, no placeholders, no events (another mod can use the name /tags)
            log.info("The tag system is off ({}). No /tags command is registered.", TagsService.disabledReason())
            return
        }
        // the "tag" reward for missions, trivia, battle pass...: { "type": "tag", "tag": "champion" }
        RewardService.register(
            RewardService.Type("tag", { reward -> reward.str("tag")?.let { id -> "Tag: " + (TagsService.find(id)?.def?.name?.ifBlank { id } ?: id) } }) { player, reward, _, _ ->
                val id = reward.str("tag") ?: return@Type
                if (TagsService.grant(player.uuid, id)) Texts.send(player, "<gray>You received the tag <white>" + (TagsService.find(id)?.def?.name?.ifBlank { id } ?: id) + "</white>! <dark_gray>(/tags)")
            }
        )
        ServerLifecycleEvents.SERVER_STARTING.register {
            TagsRepository.createTable()
            TagsService.ready = true
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server -> TagEffects.cleanupTeams(server) }
        ServerLifecycleEvents.SERVER_STOPPED.register { TagsService.ready = false }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> TagsService.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> TagsService.onLeave(handler.player.uuid) }
        SbCommands.onRegister { dispatcher, _ -> TagsCommands.register(dispatcher) }

        // %serverbuddy:tag% (the prefix, for chat/list/scoreboard), %serverbuddy:tag_name% and %serverbuddy:tag_id%
        SbPlaceholders.register("tag") { player, _ -> player?.let { TagsService.equippedView(it)?.def?.prefix } ?: "" }
        SbPlaceholders.register("tag_name") { player, _ -> player?.let { TagsService.equippedView(it)?.def?.name } ?: "" }
        SbPlaceholders.register("tag_id") { player, _ -> player?.let { TagsService.equippedView(it)?.id } ?: "" }
    }

    override fun idleReason(): String? = TagsService.disabledReason()

    override fun reload() {
        TagsService.load()
    }
}
