package com.f4xizzz.serverbuddy.announcements.commands.subcommands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.f4xizzz.serverbuddy.announcements.config.ConfigManager
import com.f4xizzz.serverbuddy.announcements.utils.SubCommand
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ListCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("list")
            .requires(Permissions.require("serverbuddy.announcements.command.list", 4))
            .executes(Companion::list)
            .build()
    }

    companion object {
        fun list(ctx: CommandContext<CommandSourceStack>): Int {
            if (ConfigManager.GROUPS.isEmpty()) {
                ctx.source.sendMessage(Utils.deserializeText("<red>There are no Announcement Groups!"))
                return 1
            }

            ctx.source.sendMessage(Utils.deserializeText("<aqua><b>Loaded Announcement Groups:"))

            // "<blue>$id - [$enabled] [$details]"
            for ((id, group) in ConfigManager.GROUPS) {
                val message = Component.text()

                message.append(Utils.deserializeText("<white>$id <gray>- "))
                message.append(Utils.deserializeText(
                    if (group.enabled) "<green>[Enabled] " else "<red>[Disabled] "
                ))
                message.append(
                    Utils.deserializeText("<gold>[Details]")
                        .hoverEvent(HoverEvent.showText(
                            Component.text()
                                .append(Utils.deserializeText("<gray>${group.announcements.size} Announcement(s)")).appendNewline()
                                .append(Utils.deserializeText("<gray>${group.interval}s Interval")).appendNewline()
                                .append(Utils.deserializeText("<gray>${group.order} Order")).appendNewline()
                                .append(Utils.deserializeText("<gray>${group.requirements.size} Requirement(s)"))
                        ))
                )

                ctx.source.sendMessage(message.build())
            }

            return 1
        }
    }
}
