package com.f4xizzz.serverbuddy.modules.tags

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.ItemSpec
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bridge to GreatCosmetics (another mod by the same author). Uses its catalog (`TagsConfig.tagsMap`) and its ownership database
 * (`DatabaseManager`) and calls the same equip/take-off routines, so both mods always show the same tag.
 *
 * Everything is done by reflection, looking the methods up by name: ServerBuddy does not need its jar to compile and nothing here
 * breaks if GreatCosmetics changes version (when something is missing, the integration warns once and turns itself off).
 */
object GreatCosmeticsTags {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Tags")
    private const val PKG = "com.f4xizzz.greatcosmetics."

    @Volatile
    private var broken = false

    private val tagsConfig: Class<*> by lazy { Class.forName(PKG + "config.TagsConfig") }
    private val database: Class<*> by lazy { Class.forName(PKG + "database.DatabaseManager") }
    private val luckPermsManager: Class<*> by lazy { Class.forName(PKG + "util.LuckPermsTagManager") }
    private val main: Class<*> by lazy { Class.forName(PKG + "GreatCosmetics") }

    /** GreatCosmetics is installed and the tag classes exist. */
    fun available(): Boolean {
        if (broken || !Integrations.isLoaded("greatcosmetics")) return false
        return try {
            tagsConfig
            database
            main
            true
        } catch (e: Throwable) {
            fail("the tag classes were not found", e)
            false
        }
    }

    private fun fail(what: String, e: Throwable) {
        if (!broken) {
            broken = true
            LOGGER.warn("GreatCosmetics integration turned off: {} ({}). Using ServerBuddy's catalog.", what, e.toString())
        }
    }

    private fun method(owner: Class<*>, name: String, params: Int): java.lang.reflect.Method =
        owner.methods.first { it.name == name && it.parameterCount == params }

    /** The tags of GreatCosmetics' catalog, already in ServerBuddy's format. */
    @Suppress("UNCHECKED_CAST")
    fun catalog(): List<TagView> = try {
        val map = tagsConfig.getField("tagsMap").get(null) as Map<String, Any>
        map.map { (id, data) -> view(id, data) }
    } catch (e: Throwable) {
        fail("could not read the catalog", e)
        emptyList()
    }

    private fun view(id: String, data: Any): TagView {
        fun text(field: String): String = data.javaClass.getField(field).get(data) as? String ?: ""
        val isGroup = data.javaClass.getField("isGroupTag").getBoolean(data)
        @Suppress("UNCHECKED_CAST")
        val permissions = data.javaClass.getField("permissions").get(data) as? List<String> ?: emptyList()
        val description = text("description").takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
        val def = TagDef(
            name = text("displayName").ifBlank { id },
            description = description,
            prefix = text("tag"),
            icon = ItemSpec(item = "minecraft:name_tag"),
            category = if (isGroup) "cargos" else "geral",
            unlock = TagUnlock(type = if (isGroup) UnlockType.GROUP else UnlockType.GIVEN, value = id),
            permissions = permissions,
            minecraftTag = text("minecraftTag"),
            weight = data.javaClass.getField("weight").getInt(data)
        )
        return TagView(id, def, TagSource.GREATCOSMETICS, data)
    }

    fun owns(player: ServerPlayer, view: TagView): Boolean = try {
        if (view.def.unlock.type == UnlockType.GROUP) {
            method(luckPermsManager, "isInGroup", 2).invoke(null, player, view.id) as Boolean
        } else {
            method(database, "hasTag", 2).invoke(null, player.uuid, view.id) as Boolean
        }
    } catch (e: Throwable) {
        fail("could not check tag ownership", e)
        false
    }

    fun equippedId(uuid: UUID): String? = try {
        method(database, "getEquippedTagId", 1).invoke(null, uuid) as String?
    } catch (e: Throwable) {
        fail("error reading the equipped tag", e)
        null
    }

    /** Equipa [view] (tirando a que estava): as mesmas chamadas do toque no menu do GreatCosmetics. */
    fun equip(player: ServerPlayer, view: TagView, previous: TagView?) {
        try {
            if (previous != null && previous.id != view.id) method(luckPermsManager, "removeTag", 2).invoke(null, player, previous.raw)
            method(database, "equipTag", 2).invoke(null, player.uuid, view.id)
            method(luckPermsManager, "applyTag", 2).invoke(null, player, view.raw)
            sync(player)
        } catch (e: Throwable) {
            fail("error equipping the tag", e)
        }
    }

    /** Takes off the tag [current]; GreatCosmetics gives back the player's rank tag, if they have one. */
    fun unequip(player: ServerPlayer, current: TagView?) {
        try {
            if (current != null) method(luckPermsManager, "removeTag", 2).invoke(null, player, current.raw)
            method(database, "unequipAllForPlayer", 1).invoke(null, player.uuid)
            method(main, "autoEquipCurrentGroupTag", 1).invoke(null, player)
            sync(player)
        } catch (e: Throwable) {
            fail("could not remove the tag", e)
        }
    }

    fun grant(player: UUID, id: String): Boolean = try {
        method(database, "unlockTag", 2).invoke(null, player, id) as Boolean
    } catch (e: Throwable) {
        fail("could not grant the tag", e)
        false
    }

    fun revoke(player: UUID, id: String): Boolean = try {
        method(database, "removeTagOwnership", 2).invoke(null, player, id) as Boolean
    } catch (e: Throwable) {
        fail("could not revoke tag ownership", e)
        false
    }

    private fun sync(player: ServerPlayer) {
        method(main, "syncPlayerTags", 1).invoke(null, player)
        method(main, "pushNameTag", 1).invoke(null, player)
    }
}
