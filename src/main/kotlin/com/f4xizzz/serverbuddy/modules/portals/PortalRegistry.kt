package com.f4xizzz.serverbuddy.modules.portals

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.SbGson
import com.google.gson.annotations.SerializedName
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.slf4j.LoggerFactory
import java.io.File

/** Where a portal leads. */
class PortalDestination(
    val dimension: String = "minecraft:overworld",
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f
)

/** One portal: every portal block that touches belongs to it. [blocks] are `[x, y, z]`. */
class PortalGroup(
    val id: Int,
    val dimension: String,
    val blocks: MutableList<IntArray> = ArrayList(),
    var destination: PortalDestination? = null
)

private class PortalFile(@SerializedName("portals") val portals: MutableList<PortalGroup> = ArrayList())

/**
 * Every portal, saved in `Portals/portals.json`. Blocks are indexed by dimension and position so the hooks (which run every tick
 * for entities inside a portal) are a map lookup. Everything here runs on the server thread.
 */
object PortalRegistry {
    private val log = LoggerFactory.getLogger("ServerBuddy/Portals")
    private val groups = LinkedHashMap<Int, PortalGroup>()
    private val index = HashMap<String, HashMap<Long, PortalGroup>>()
    private var file: File? = null

    fun load(folder: String) {
        val target = File(ConfigFiles.dir(folder), "portals.json")
        file = target
        groups.clear()
        index.clear()
        if (!target.exists()) return
        try {
            val data = target.reader(Charsets.UTF_8).use { SbGson.pretty.fromJson(it, PortalFile::class.java) } ?: return
            for (group in data.portals) {
                groups[group.id] = group
                for (block in group.blocks) if (block.size == 3) index.getOrPut(group.dimension) { HashMap() }[BlockPos.asLong(block[0], block[1], block[2])] = group
            }
        } catch (e: Exception) {
            log.error("Could not read portals.json (the portals are off until it is fixed)", e)
        }
    }

    private fun save() {
        val target = file ?: return
        try {
            target.writeText(SbGson.pretty.toJson(PortalFile(ArrayList(groups.values))), Charsets.UTF_8)
        } catch (e: Exception) {
            log.error("Could not save portals.json", e)
        }
    }

    fun all(): List<PortalGroup> = groups.values.toList()

    fun at(dimension: String, pos: BlockPos): PortalGroup? = index[dimension]?.get(pos.asLong())

    fun at(dimension: String, packed: Long): PortalGroup? = index[dimension]?.get(packed)

    /** The groups that touch [pos] through a face (up to six). */
    private fun neighbors(dimension: String, pos: BlockPos): List<PortalGroup> =
        Direction.entries.mapNotNull { at(dimension, pos.relative(it)) }.distinct()

    /** How many blocks the portal would have if a block went at [pos] (merging every portal it touches). */
    fun sizeIfAdded(dimension: String, pos: BlockPos): Int = neighbors(dimension, pos).sumOf { it.blocks.size } + 1

    /** Adds a portal block at [pos]; portals it touches merge into one. Returns the portal it belongs to now. */
    fun add(dimension: String, pos: BlockPos): PortalGroup {
        val touching = neighbors(dimension, pos)
        val keep = touching.minByOrNull { it.id } ?: PortalGroup((groups.keys.maxOrNull() ?: 0) + 1, dimension).also { groups[it.id] = it }
        for (other in touching) {
            if (other === keep) continue
            for (block in other.blocks) {
                keep.blocks.add(block)
                index.getOrPut(dimension) { HashMap() }[BlockPos.asLong(block[0], block[1], block[2])] = keep
            }
            if (keep.destination == null) keep.destination = other.destination
            groups.remove(other.id)
        }
        keep.blocks.add(intArrayOf(pos.x, pos.y, pos.z))
        index.getOrPut(dimension) { HashMap() }[pos.asLong()] = keep
        save()
        return keep
    }

    /** Drops a whole portal (the caller takes the blocks out of the world). */
    fun remove(group: PortalGroup) {
        groups.remove(group.id)
        index[group.dimension]?.let { map -> for (block in group.blocks) map.remove(BlockPos.asLong(block[0], block[1], block[2])) }
        save()
    }

    /** Takes one block out of its portal; the rest may split into several portals, which keep the destination. */
    fun removeBlock(dimension: String, pos: BlockPos) {
        val group = at(dimension, pos) ?: return
        index[dimension]?.remove(pos.asLong())
        group.blocks.removeIf { it[0] == pos.x && it[1] == pos.y && it[2] == pos.z }
        groups.remove(group.id)
        val remaining = HashSet(group.blocks.map { BlockPos.asLong(it[0], it[1], it[2]) })
        var first = true
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            val part = ArrayList<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(start)
            remaining.remove(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                part.add(current)
                val here = BlockPos.of(current)
                for (direction in Direction.entries) {
                    val next = here.relative(direction).asLong()
                    if (remaining.remove(next)) queue.add(next)
                }
            }
            val target = if (first) group else PortalGroup((groups.keys.maxOrNull() ?: group.id) + 1, dimension, destination = group.destination)
            first = false
            target.blocks.clear()
            for (packed in part) {
                val p = BlockPos.of(packed)
                target.blocks.add(intArrayOf(p.x, p.y, p.z))
                index.getOrPut(dimension) { HashMap() }[packed] = target
            }
            groups[target.id] = target
        }
        save()
    }

    fun setDestination(group: PortalGroup, destination: PortalDestination) {
        group.destination = destination
        save()
    }
}
