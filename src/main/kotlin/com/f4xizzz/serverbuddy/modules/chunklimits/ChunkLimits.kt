package com.f4xizzz.serverbuddy.modules.chunklimits

import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.LoggerFactory

/**
 * The per-chunk block limiter. [allowPlace] is called by the `BlockItemMixin` right before a player places a block; the
 * groups come from `ChunkLimits/config.json` and are resolved against the block registry once the server is up (modded blocks
 * such as the Poké Snack are not registered yet when the config is first read).
 */
object ChunkLimits {
    const val BYPASS = "serverbuddy.chunklimits.bypass"
    const val INFO = "serverbuddy.chunklimits.info"

    private val log = LoggerFactory.getLogger("ServerBuddy/ChunkLimits")

    class Group(val id: String, val max: Int, val message: String, private val blocks: Set<Block>, private val tags: List<TagKey<Block>>) {
        fun matches(state: BlockState): Boolean {
            if (state.isAir) return false
            if (state.block in blocks) return true
            for (tag in tags) if (state.`is`(tag)) return true
            return false
        }

        /** True if placing [block] could change this group's count (cheap pre-check, no chunk scan). */
        fun covers(block: Block): Boolean = matches(block.defaultBlockState())
    }

    @Volatile
    private var config = ChunkLimitsConfig(groups = emptyMap())

    @Volatile
    private var groups: List<Group> = emptyList()

    val messages: ChunkLimitsMessages get() = config.messages

    /** Reads [newConfig] and resolves its block names against the registry. Call once the registries are complete. */
    fun apply(newConfig: ChunkLimitsConfig) {
        config = newConfig
        groups = if (!newConfig.enabled) emptyList() else newConfig.groups.mapNotNull { (id, group) -> resolve(id, group) }
    }

    fun activeGroups(): List<Group> = groups

    private fun resolve(id: String, group: LimitGroup): Group? {
        if (!group.enabled || group.maxPerChunk < 0) return null
        val blocks = HashSet<Block>()
        val tags = ArrayList<TagKey<Block>>()
        val unknown = ArrayList<String>()
        for (raw in group.blocks) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            if (name.startsWith("#")) {
                val tagId = ResourceLocation.tryParse(name.substring(1))
                if (tagId == null) unknown.add(name) else tags.add(TagKey.create(Registries.BLOCK, tagId))
                continue
            }
            val blockId = ResourceLocation.tryParse(name)
            val block = blockId?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
            if (block == null) unknown.add(name) else blocks.add(block)
        }
        if (unknown.isNotEmpty()) log.warn("Group '{}' has unknown block names (ignored): {}", id, unknown)
        if (blocks.isEmpty() && tags.isEmpty()) return null
        return Group(id, group.maxPerChunk, group.message, blocks, tags)
    }

    /**
     * Called before a player places [block]. Returns false (and tells the player) when a group the block belongs to is full in
     * the chunk of the placement position. Always true for non-players (dispensers...) and for players with the bypass.
     */
    @JvmStatic
    fun allowPlace(context: BlockPlaceContext, block: Block): Boolean {
        val active = groups
        if (active.isEmpty()) return true
        val player = context.player as? ServerPlayer ?: return true
        val relevant = active.filter { it.covers(block) }
        if (relevant.isEmpty()) return true
        if (SbPermissions.check(player, BYPASS, 3)) return true
        val level = player.serverLevel()
        val chunk = level.getChunkAt(context.clickedPos)
        for (group in relevant) {
            val count = count(chunk, group)
            if (count >= group.max) {
                val template = group.message.ifBlank { config.messages.limitReached }
                player.displayClientMessage(Texts.render(template, mapOf("group" to group.id, "count" to count, "limit" to group.max)), true)
                return false
            }
        }
        return true
    }

    /** How many blocks of [group] the chunk holds (palette pre-check skips sections that cannot contain any). */
    fun count(chunk: LevelChunk, group: Group): Int {
        var total = 0
        for (section in chunk.sections) {
            if (section.hasOnlyAir() || !section.maybeHas { group.matches(it) }) continue
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                if (group.matches(section.getBlockState(x, y, z))) total++
            }
        }
        return total
    }

    /** (group, count) for every active group in the chunk at [pos]. */
    fun usage(player: ServerPlayer): Pair<ChunkPos, List<Pair<Group, Int>>> {
        val chunk = player.serverLevel().getChunkAt(player.blockPosition())
        return chunk.pos to groups.map { it to count(chunk, it) }
    }
}
