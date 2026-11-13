package com.f4xizzz.serverbuddy.modules.portals

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

@Doc(
    "Portals · instant portals made of portal blocks\n" +
        "\n" +
        "/sb portal give hands out the Portal Block item. Placing it puts a portal block in the world (no obsidian frame needed). Portal\n" +
        "blocks that touch each other are ONE portal. Point at a portal and run /sb portal edit tp <x y z> to say where it leads.\n" +
        "Touching a portal teleports at once: no waiting. The portals are saved in Portals/portals.json."
)
class PortalsConfig(
    @Doc("Master switch: false makes every portal block inert (they stay in the world and in portals.json).")
    val enabled: Boolean = true,
    @Doc("The Portal Block item that /sb portal give hands out (the item that places portals; its look is up to you).")
    val item: ItemSpec = ItemSpec(
        "minecraft:purple_stained_glass_pane", name = "<light_purple><bold>Portal Block",
        lore = listOf("<gray>Place it to make a portal.", "<gray>Blocks that touch are one portal."), glint = true
    ),
    @SerializedName("cooldown_ticks")
    @Doc("How long after a teleport the same entity can use a portal again (20 ticks = 1 second). It stops a portal that leads into another portal from bouncing.")
    val cooldownTicks: Int = 20,
    @Doc("Sound played where the entity arrives (empty = none).")
    val sound: String = "minecraft:entity.enderman.teleport",
    @SerializedName("sound_volume")
    val soundVolume: Float = 0.6f,
    @Doc("Also teleport the mobs and items that touch a portal (false = players only).")
    val entities: Boolean = false,
    @SerializedName("max_blocks")
    @Doc("Most portal blocks that make up one portal (placing one more is refused).")
    val maxBlocks: Int = 256,
    @Doc("A player needs this permission to walk through portals (default: everybody).")
    val permission: String = "serverbuddy.portal.use",
    val messages: PortalMessages = PortalMessages()
)

class PortalMessages(
    @SerializedName("no_destination") val noDestination: String = "<yellow>This portal does not lead anywhere yet. <gray>(An admin sets it with /sb portal edit tp <x y z>)",
    @SerializedName("no_permission") val noPermission: String = "<red>You can't use this portal.",
    @SerializedName("too_big") val tooBig: String = "<red>A portal can have at most %max% blocks.",
    val given: String = "<green>Gave <white>%amount%</white> Portal Block(s) to <white>%player%</white>.",
    @SerializedName("only_players") val onlyPlayers: String = "<red>Only players can use this command.",
    @SerializedName("no_portal") val noPortal: String = "<red>Look at a portal block (or stand in one) first.",
    @SerializedName("destination_set") val destinationSet: String = "<green>Portal #%id% now leads to <white>%x% %y% %z%</white> in <white>%dimension%</white>.",
    val removed: String = "<green>Portal #%id% was removed (%blocks% block(s)).",
    @SerializedName("list_header") val listHeader: String = "<gold><bold>Portals</bold> <gray>(%count%)",
    @SerializedName("list_line") val listLine: String = "<gray>#%id% <white>%dimension%</white> <dark_gray>·</dark_gray> %blocks% block(s) <dark_gray>→</dark_gray> %destination%",
    @SerializedName("list_empty") val listEmpty: String = "<gray>There are no portals yet. Use <white>/sb portal give</white>."
)
