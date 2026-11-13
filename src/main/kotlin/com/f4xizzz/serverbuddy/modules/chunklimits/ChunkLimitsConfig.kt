package com.f4xizzz.serverbuddy.modules.chunklimits

import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/ChunkLimits/config.json` */
@Doc(
    "Chunk limits · caps how many blocks of a kind can exist in one chunk (16x16 columns), so redstone farms, hopper walls or\n" +
        "stacks of Poké Snacks cannot lag the server.\n" +
        "\n" +
        "HOW IT WORKS\n" +
        "  - Each entry in \"groups\" is a set of blocks that count TOGETHER (all redstone parts share one budget, for example).\n" +
        "  - When a player tries to place a block of a group and the chunk already holds \"max_per_chunk\" of them, the placement is\n" +
        "    refused and the player sees \"messages.limit_reached\" in the action bar.\n" +
        "  - Only blocks placed BY PLAYERS are checked (hand placement). Existing blocks are never removed.\n" +
        "  - Players with the permission serverbuddy.chunklimits.bypass (default: operators level 3) ignore every limit.\n" +
        "  - \"blocks\" accepts block ids (\"minecraft:hopper\") and block tags (\"#minecraft:buttons\"). Unknown names are reported in the log.\n" +
        "  - max_per_chunk: 0 forbids the block entirely; a negative number means no limit.\n" +
        "  - /chunklimits shows how full the chunk you are standing in is (permission serverbuddy.chunklimits.info, default level 2)."
)
class ChunkLimitsConfig(
    @Doc("false = no limits are enforced.")
    val enabled: Boolean = true,
    @Doc("The limit groups. The key is a free name (shown in the message and in /chunklimits).")
    val groups: Map<String, LimitGroup> = defaultGroups(),
    val messages: ChunkLimitsMessages = ChunkLimitsMessages()
) {
    companion object {
        fun defaultGroups(): Map<String, LimitGroup> {
            val groups = LinkedHashMap<String, LimitGroup>()
            groups["redstone"] = LimitGroup(
                blocks = listOf(
                    "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator", "minecraft:observer",
                    "minecraft:piston", "minecraft:sticky_piston", "minecraft:dispenser", "minecraft:dropper",
                    "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:daylight_detector",
                    "minecraft:lever", "minecraft:tripwire_hook", "minecraft:target", "minecraft:note_block",
                    "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor", "minecraft:powered_rail",
                    "minecraft:detector_rail", "#minecraft:buttons", "#minecraft:pressure_plates"
                ),
                maxPerChunk = 256
            )
            groups["hoppers"] = LimitGroup(enabled = false, blocks = listOf("minecraft:hopper"), maxPerChunk = 32)
            // the Poké Snack only exists with Cobblemon: the example is only written when it is installed
            if (Integrations.cobblemon()) {
                groups["poke_snack"] = LimitGroup(blocks = listOf("cobblemon:poke_snack"), maxPerChunk = 3, message = "<red>This chunk already has %count% Poké Snacks (limit %limit%).")
            }
            return groups
        }
    }
}

class LimitGroup(
    @Doc("false = this group is ignored.")
    val enabled: Boolean = true,
    @Doc("Block ids or #block tags that count together.")
    val blocks: List<String> = emptyList(),
    @SerializedName("max_per_chunk")
    @Doc("How many of these blocks one chunk may hold. 0 = forbidden, negative = no limit.")
    val maxPerChunk: Int = 256,
    @Doc("Optional message just for this group (empty = messages.limit_reached). Placeholders: %group% %count% %limit%.")
    val message: String = ""
)

class ChunkLimitsMessages(
    @SerializedName("limit_reached")
    @Doc("Action bar text when a placement is refused (MiniMessage). Placeholders: %group% %count% %limit%.")
    val limitReached: String = "<red>This chunk already has %count%/%limit% blocks of the group <white>%group%</white>.",
    @SerializedName("info_header")
    @Doc("First line of /chunklimits. Placeholders: %x% %z% (chunk coordinates).")
    val infoHeader: String = "<gold>Chunk limits for chunk <white>%x%, %z%</white>:",
    @SerializedName("info_line")
    @Doc("One line per group in /chunklimits. Placeholders: %group% %count% %limit%.")
    val infoLine: String = " <gray>- <white>%group%</white>: <yellow>%count%</yellow>/%limit%",
    @SerializedName("info_none")
    @Doc("Shown when no group is active.")
    val infoNone: String = "<gray>There are no active chunk limits.",
    @SerializedName("unlimited")
    @Doc("What %limit% shows for a group without a limit (negative max_per_chunk).")
    val unlimited: String = "unlimited"
)
