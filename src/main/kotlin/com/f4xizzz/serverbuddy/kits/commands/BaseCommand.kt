package com.f4xizzz.serverbuddy.kits.commands

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import com.f4xizzz.serverbuddy.kits.commands.subcommands.*
import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.gui.KitsMenuGui
import com.f4xizzz.serverbuddy.kits.utils.Utils
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

class BaseCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            ReloadCommand().build(),
            DebugCommand().build(),
            ClaimCommand().build(),
            GiveCommand().build(),
            ResetUsageCommand().build(),
            ResetCooldownCommand().build(),
            CreateCommand().build(),
            PreviewCommand().build(),
            MenuCommand().build(),
        )

        // /serverbuddy kits ... (e /sb kits ...)
        val moduleRoot = buildRoot("kits")
        subCommands.forEach { sub -> moduleRoot.addChild(sub) }
        ModuleCommands.mountModule(dispatcher, moduleRoot)

        // comandos raiz definidos na server configuration (ex.: /kit, /kits): sempre registrados
        ConfigManager.CONFIG.commands.forEach { name ->
            val root = buildRoot(name)
            subCommands.forEach { sub -> root.addChild(sub) }
            ModuleCommands.mountRoot(dispatcher, root)
        }
    }

    private fun buildRoot(name: String): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal(name)
            .requires(Permissions.require("serverbuddy.kits.command.base", 2))
            .then(Commands.argument("kit", StringArgumentType.word())
                .requires(Permissions.require("serverbuddy.kits.command.claim", 2))
                .suggests { ctx, builder ->
                    SharedSuggestionProvider.suggest(ConfigManager.KITS.filter { entry ->
                        return@filter !(ctx.source.isPlayer && !entry.value.hasPermission(ctx.source.player!!))
                    }.keys, builder)
                }
                .requires { obj: CommandSourceStack -> obj.isPlayer }
                .executes(ClaimCommand::claim)
            )
            .executes(Companion::execute)
            .build()
    }

    companion object {
        fun execute(ctx: CommandContext<CommandSourceStack>): Int {
            val player = ctx.source.player
            if (player == null) {
                ctx.source.sendMessage(Utils.deserializeText("<red>You must be a player to run this command!"))
                return 0
            }

            val menuConfig = ConfigManager.MENUS[ConfigManager.CONFIG.defaultMenu] ?: run {
                ctx.source.sendMessage(Utils.deserializeText(
                    Utils.parsePlaceholders(
                        player,
                        ConfigManager.CONFIG.messages.kitMenuError
                    )
                ))
                Utils.printError("Default menu '${ConfigManager.CONFIG.defaultMenu}' not found as a valid configuration menu.")
                return 0
            }

            try {
                KitsMenuGui(player, menuConfig).open()
            } catch (e: Exception) {
                Utils.printError("An error occurred while opening the Kits menu for player ${player.name.string}: ${e.message}")
                ctx.source.sendMessage(Utils.deserializeText(
                    Utils.parsePlaceholders(
                        player,
                        ConfigManager.CONFIG.messages.kitMenuError
                    )
                ))
                return 0
            }
            return 1
        }
    }
}
