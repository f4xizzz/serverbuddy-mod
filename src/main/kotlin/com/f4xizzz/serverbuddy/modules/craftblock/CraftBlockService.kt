package com.f4xizzz.serverbuddy.modules.craftblock

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import org.slf4j.LoggerFactory

/**
 * Takes the blocked recipes out of the recipe manager. The unfiltered recipes of the last datapack load are kept so a config
 * reload can filter them again without a full /reload.
 */
object CraftBlockService {
    private val log = LoggerFactory.getLogger("ServerBuddy/CraftBlock")

    @Volatile
    var config = CraftBlockConfig()
        private set

    /** The recipes exactly as the datapacks loaded them (never filtered). */
    private var originals: List<RecipeHolder<*>> = emptyList()

    class Report(
        /** rule → ids of the recipes it removed */
        val removed: Map<String, List<ResourceLocation>>,
        /** rules that removed nothing */
        val unmatched: List<String>,
        /** (item, recipe type, recipe id) still craftable through a recipe type that is not blocked */
        val leaks: List<Triple<String, String, ResourceLocation>>
    ) {
        val total: Int get() = removed.values.sumOf { it.size }
    }

    @Volatile
    var report = Report(emptyMap(), emptyList(), emptyList())
        private set

    /** One line of "blocked"/"allowed" turned into something that can be tested against a recipe. */
    private class Rule(val text: String) {
        private val recipeNamespace: String?
        private val recipeId: ResourceLocation?
        private val namespace: String?
        private val itemId: ResourceLocation?
        private val tag: TagKey<Item>?
        val valid: Boolean

        init {
            val raw = text.trim()
            var rNs: String? = null
            var rId: ResourceLocation? = null
            var ns: String? = null
            var item: ResourceLocation? = null
            var tagKey: TagKey<Item>? = null
            var ok = true
            when {
                raw.startsWith("recipe:") -> {
                    val body = raw.removePrefix("recipe:")
                    if (body.endsWith(":*")) rNs = body.removeSuffix(":*") else rId = ResourceLocation.tryParse(body)
                    ok = rNs != null || rId != null
                }
                raw.startsWith("#") -> {
                    val id = ResourceLocation.tryParse(raw.substring(1))
                    if (id == null) ok = false else tagKey = TagKey.create(Registries.ITEM, id)
                }
                raw.endsWith(":*") -> ns = raw.removeSuffix(":*")
                else -> {
                    item = ResourceLocation.tryParse(raw)
                    ok = item != null
                }
            }
            recipeNamespace = rNs; recipeId = rId; namespace = ns; itemId = item; tag = tagKey; valid = ok && raw.isNotEmpty()
        }

        fun matches(id: ResourceLocation, result: ItemStack): Boolean {
            if (!valid) return false
            if (recipeId != null) return id == recipeId
            if (recipeNamespace != null) return id.namespace == recipeNamespace
            if (result.isEmpty) return false
            if (itemId != null) return BuiltInRegistries.ITEM.getKey(result.item) == itemId
            if (namespace != null) return BuiltInRegistries.ITEM.getKey(result.item).namespace == namespace
            return tag != null && result.`is`(tag)
        }
    }

    fun load(newConfig: CraftBlockConfig) {
        config = newConfig
    }

    /** Remember what the datapacks loaded. Call right after a data load, BEFORE [apply]. */
    fun capture(server: MinecraftServer) {
        originals = ArrayList(server.recipeManager.recipes)
    }

    private fun typeAffected(type: String): Boolean {
        val types = config.recipeTypes
        return types.isEmpty() || types.any { it == "*" || it.equals(type, ignoreCase = true) }
    }

    private fun resultOf(server: MinecraftServer, holder: RecipeHolder<*>): ItemStack = try {
        holder.value().getResultItem(server.registryAccess())
    } catch (e: Exception) {
        ItemStack.EMPTY
    }

    /** Filters [originals] with the current config and installs the result. [resync] re-sends the recipes to online players. */
    fun apply(server: MinecraftServer, resync: Boolean) {
        if (originals.isEmpty()) capture(server)
        val blocked = if (config.enabled) config.blocked.filter { it.isNotBlank() }.map { Rule(it) } else emptyList()
        val allowed = config.allowed.filter { it.isNotBlank() }.map { Rule(it) }
        val removed = LinkedHashMap<String, MutableList<ResourceLocation>>()
        for (rule in blocked) removed[rule.text] = ArrayList()
        val kept = ArrayList<RecipeHolder<*>>(originals.size)
        val leaks = ArrayList<Triple<String, String, ResourceLocation>>()
        val matched = HashSet<String>()
        for (holder in originals) {
            val type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().type)?.toString() ?: ""
            val result = resultOf(server, holder)
            val hits = if (blocked.isEmpty()) emptyList() else blocked.filter { it.matches(holder.id(), result) }
            val rule = hits.firstOrNull()
            for (hit in hits) matched.add(hit.text)
            if (rule != null && allowed.none { it.matches(holder.id(), result) }) {
                if (typeAffected(type)) {
                    removed.getValue(rule.text).add(holder.id())
                    continue
                }
                // matches a rule but its type is not blocked: the item can still be made this way
                if (!result.isEmpty) leaks.add(Triple(BuiltInRegistries.ITEM.getKey(result.item).toString(), type, holder.id()))
            }
            kept.add(holder)
        }
        server.recipeManager.replaceRecipes(kept)
        val unmatched = blocked.filter { it.text !in matched }.map { it.text }.distinct()
        report = Report(removed.filterValues { it.isNotEmpty() }, unmatched, leaks)
        if (report.total > 0) log.info("Craft block: {} recipes removed by {} rule(s).", report.total, report.removed.size)
        if (resync) server.playerList.reloadResources()
    }
}
