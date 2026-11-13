package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.modules.essentials.StylingLogic
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TagSource { OWN, GROUP, GREATCOSMETICS }

/** A tag ready for the menu: where it came from and its data. [raw] is the original object (GreatCosmetics only). */
class TagView(val id: String, val def: TagDef, val source: TagSource, val raw: Any? = null)

enum class TagState { EQUIPPED, OWNED, PURCHASABLE, LOCKED }

enum class Outcome { OK, UNKNOWN, NOT_OWNED, NO_ECONOMY, NOT_ENOUGH, ALREADY, NOT_FOR_SALE, ERROR }

/** What the player has right now: read once per menu refresh. */
class TagSnapshot(val owned: Set<String>, val equipped: String?)

/**
 * Tag rules: catalog (ServerBuddy's or GreatCosmetics'), ownership, equip, take off and buy.
 * The prefix reaches the player through [TagEffects]; with GreatCosmetics, it is GreatCosmetics that applies it ([GreatCosmeticsTags]).
 */
object TagsService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Tags")

    @Volatile
    var config: TagsConfig = TagsConfig()
        private set

    @Volatile
    var catalogFile: TagsCatalog = TagsCatalog()
        private set

    @Volatile
    var ready: Boolean = false

    private val equippedCache = ConcurrentHashMap<UUID, String>()
    private var ownViews: List<TagView> = emptyList()

    private val source: TagsIntegrationConfig.Source get() = config.integration.source

    /** The whole tag system is off: `source = DISABLED`, or `GREATCOSMETICS` without GreatCosmetics installed. */
    val disabled: Boolean
        get() = source == TagsIntegrationConfig.Source.DISABLED ||
            (source == TagsIntegrationConfig.Source.GREATCOSMETICS && !GreatCosmeticsTags.available())

    /** Why the tag system is off, for `/serverbuddy modules`; null when it is on. */
    fun disabledReason(): String? = when {
        source == TagsIntegrationConfig.Source.DISABLED -> "turned off in Tags/config.json (integration.source = DISABLED)"
        disabled -> "integration.source is GREATCOSMETICS but GreatCosmetics is not installed"
        else -> null
    }

    /** The catalog and ownership come from GreatCosmetics (`source` is AUTO or GREATCOSMETICS, and it is installed). */
    val usingGreatCosmetics: Boolean
        get() = (source == TagsIntegrationConfig.Source.AUTO || source == TagsIntegrationConfig.Source.GREATCOSMETICS) && GreatCosmeticsTags.available()

    // ---------------------------------------------------------------- loading

    fun load() {
        config = ConfigFiles.load("Tags", "config.json", TagsConfig::class.java) { TagsConfig() }
        catalogFile = ConfigFiles.load("Tags", "tags.json", TagsCatalog::class.java) { TagsCatalog() }
        ownViews = catalogFile.tags.map { (id, def) -> TagView(id.lowercase(), def, TagSource.OWN) }
        if (usingGreatCosmetics) LOGGER.info("[Tags] GreatCosmetics found: the menu uses its tag catalog and ownership.")
    }

    /** Saves ServerBuddy's catalog (the `create` and `delete` commands); the config comments are rewritten. */
    fun saveCatalog() {
        val file = ConfigFiles.entries().firstOrNull { it.label == "Tags/tags.json" }?.file ?: return
        file.writeText(ConfigDocs.render(catalogFile, SbGson.defaultsWriter), Charsets.UTF_8)
        load()
    }

    fun putTag(id: String, def: TagDef) {
        val tags = LinkedHashMap(catalogFile.tags)
        tags[id.lowercase()] = def
        catalogFile = TagsCatalog(catalogFile.categories, tags)
        saveCatalog()
    }

    fun removeTag(id: String): Boolean {
        val key = catalogFile.tags.keys.firstOrNull { it.equals(id, ignoreCase = true) } ?: return false
        val tags = LinkedHashMap(catalogFile.tags)
        tags.remove(key)
        catalogFile = TagsCatalog(catalogFile.categories, tags)
        saveCatalog()
        TagsRepository.deleteTag(key.lowercase())
        return true
    }

    // ---------------------------------------------------------------- catalog

    /** The catalog that applies now, in order (weight, then name). */
    fun catalog(): List<TagView> {
        if (disabled) return emptyList()
        val base = if (usingGreatCosmetics) GreatCosmeticsTags.catalog() else ownViews + groupViews()
        return base.sortedWith(compareBy({ it.def.weight }, { it.id }))
    }

    /** Finds a tag by id. The old Portuguese id prefix "grupo_" of group tags still works. */
    fun find(id: String?): TagView? {
        if (id == null) return null
        val wanted = if (id.startsWith("grupo_", ignoreCase = true)) "group_" + id.substring(6) else id
        return catalog().firstOrNull { it.id.equals(wanted, ignoreCase = true) }
    }

    private fun groupViews(): List<TagView> {
        val cfg = config.integration.groupsAsTags
        if (!cfg.enabled || !Integrations.isLoaded("luckperms")) return emptyList()
        return try {
            val blocked = cfg.blacklist.map { it.lowercase() }.toSet()
            LuckPermsTags.groups().filter { it.name.lowercase() !in blocked }.map { group ->
                // the group's prefix usually has legacy color codes (&c): turn them into MiniMessage
                val prefix = StylingLogic.legacyToMiniMessage(group.prefix)
                TagView(
                    "group_" + group.name.lowercase(),
                    TagDef(
                        // in the menu a group tag shows the group's prefix (what players see in chat), not the group's name
                        name = if (cfg.showPrefix && prefix.isNotBlank()) prefix.trim() else group.displayName,
                        prefix = prefix,
                        category = cfg.category,
                        unlock = TagUnlock(type = UnlockType.GROUP, value = group.name),
                        weight = group.weight
                    ),
                    TagSource.GROUP
                )
            }
        } catch (e: Throwable) {
            LOGGER.warn("[Tags] Could not read the LuckPerms groups: {}", e.toString())
            emptyList()
        }
    }

    // ---------------------------------------------------------------- posse e estado

    fun snapshot(player: ServerPlayer): TagSnapshot =
        if (usingGreatCosmetics) TagSnapshot(emptySet(), GreatCosmeticsTags.equippedId(player.uuid)) else TagSnapshot(TagsRepository.owned(player.uuid), equippedOf(player.uuid))

    private fun equippedOf(uuid: UUID): String? = equippedCache[uuid] ?: TagsRepository.equipped(uuid)?.also { equippedCache[uuid] = it }

    /** The tag the player is using right now (null = none). */
    fun equippedView(player: ServerPlayer): TagView? =
        find(if (usingGreatCosmetics) GreatCosmeticsTags.equippedId(player.uuid) else equippedCache[player.uuid])

    fun owns(player: ServerPlayer, view: TagView, snapshot: TagSnapshot = snapshot(player)): Boolean {
        if (view.source == TagSource.GREATCOSMETICS) return GreatCosmeticsTags.owns(player, view)
        val unlock = view.def.unlock
        return when (unlock.type) {
            UnlockType.FREE -> true
            UnlockType.GIVEN, UnlockType.PRICE -> view.id in snapshot.owned
            UnlockType.PERMISSION -> unlock.value.isNotBlank() && Permissions.check(player, unlock.value, false)
            UnlockType.GROUP -> unlock.value.isNotBlank() && Integrations.isLoaded("luckperms") && try {
                LuckPermsTags.isInGroup(player, unlock.value)
            } catch (e: Throwable) {
                false
            }
        }
    }

    /**
     * Does the player "have" the tag [tag]? A scoreboard tag (given with /tag) counts if [scoreboard] is true, and so does a catalog tag they own
     * (free tags, which everybody owns, also count: to know what they are USING, compare with [equippedView]).
     */
    fun has(player: ServerPlayer, tag: String, scoreboard: Boolean = true): Boolean {
        if (scoreboard && player.tags.any { it.equals(tag, ignoreCase = true) }) return true
        val view = find(tag) ?: return false
        return owns(player, view)
    }

    fun stateOf(player: ServerPlayer, view: TagView, snapshot: TagSnapshot): TagState {
        val owns = owns(player, view, snapshot)
        return when {
            owns && snapshot.equipped.equals(view.id, ignoreCase = true) -> TagState.EQUIPPED
            owns -> TagState.OWNED
            view.def.unlock.type == UnlockType.PRICE && view.source != TagSource.GREATCOSMETICS -> TagState.PURCHASABLE
            else -> TagState.LOCKED
        }
    }

    // ---------------------------------------------------------------- actions

    fun equip(player: ServerPlayer, id: String): Outcome {
        val view = find(id) ?: return Outcome.UNKNOWN
        val snapshot = snapshot(player)
        if (!owns(player, view, snapshot)) return Outcome.NOT_OWNED
        if (snapshot.equipped.equals(view.id, ignoreCase = true)) return Outcome.ALREADY
        val previous = find(snapshot.equipped)
        if (view.source == TagSource.GREATCOSMETICS) {
            GreatCosmeticsTags.equip(player, view, previous)
        } else {
            if (previous != null && previous.source != TagSource.GREATCOSMETICS) TagEffects.clear(player, previous)
            TagsRepository.setEquipped(player.uuid, view.id)
            equippedCache[player.uuid] = view.id
            TagEffects.apply(player, view)
        }
        return Outcome.OK
    }

    fun unequip(player: ServerPlayer): Outcome {
        val current = equippedView(player) ?: return Outcome.ALREADY
        if (current.source == TagSource.GREATCOSMETICS) {
            GreatCosmeticsTags.unequip(player, current)
        } else {
            TagEffects.clear(player, current)
            TagsRepository.setEquipped(player.uuid, null)
            equippedCache.remove(player.uuid)
        }
        return Outcome.OK
    }

    fun buy(player: ServerPlayer, id: String): Outcome {
        val view = find(id) ?: return Outcome.UNKNOWN
        val unlock = view.def.unlock
        if (view.source == TagSource.GREATCOSMETICS || unlock.type != UnlockType.PRICE) return Outcome.NOT_FOR_SALE
        if (owns(player, view)) return Outcome.ALREADY
        if (!Economy.available(unlock.currency)) return Outcome.NO_ECONOMY
        if (!Economy.withdraw(player, unlock.price, unlock.currency)) return Outcome.NOT_ENOUGH
        if (!TagsRepository.grant(player.uuid, view.id)) return Outcome.ERROR
        return Outcome.OK
    }

    /** Gives the tag (by command, mission, battle pass...). false if they already had it or the tag does not exist. */
    fun grant(uuid: UUID, id: String): Boolean {
        val view = find(id) ?: return false
        return if (view.source == TagSource.GREATCOSMETICS) GreatCosmeticsTags.grant(uuid, view.id) else TagsRepository.grant(uuid, view.id)
    }

    fun revoke(uuid: UUID, id: String): Boolean {
        val view = find(id) ?: return false
        return if (view.source == TagSource.GREATCOSMETICS) GreatCosmeticsTags.revoke(uuid, view.id) else TagsRepository.revoke(uuid, view.id)
    }

    // ---------------------------------------------------------------- join and leave

    fun onJoin(player: ServerPlayer) {
        if (!ready || usingGreatCosmetics) return
        val id = TagsRepository.equipped(player.uuid) ?: return
        equippedCache[player.uuid] = id
        reapply(player)
        // LuckPerms is sometimes still loading the player's data when they join: do it again 3 seconds later
        ServerScheduler.later(60) {
            if (player.server?.playerList?.getPlayer(player.uuid) != null) reapply(player)
        }
    }

    fun onLeave(uuid: UUID) {
        equippedCache.remove(uuid)
    }

    private fun reapply(player: ServerPlayer) {
        val id = equippedCache[player.uuid] ?: return
        val view = find(id)
        if (view == null) {
            TagsRepository.setEquipped(player.uuid, null)
            equippedCache.remove(player.uuid)
            return
        }
        if (!owns(player, view)) {
            if (config.apply.removeOnLose) {
                unequip(player)
                Texts.send(player, config.messages.lost, mapOf("tag" to view.def.name, "player" to player.gameProfile.name))
            }
            return
        }
        TagEffects.apply(player, view)
    }

    // ---------------------------------------------------------------- textos

    /** Values shared by the messages and the menu. */
    fun vars(view: TagView?, player: ServerPlayer? = null): Map<String, Any?> = mapOf(
        "tag" to (view?.def?.name?.ifBlank { view.id } ?: ""),
        "tag_id" to (view?.id ?: ""),
        "player" to (player?.gameProfile?.name ?: ""),
        "price" to (view?.def?.unlock?.price?.let { Economy.format(it) } ?: "")
    )
}
