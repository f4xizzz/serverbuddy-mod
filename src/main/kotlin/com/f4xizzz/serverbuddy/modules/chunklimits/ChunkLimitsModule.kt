package com.f4xizzz.serverbuddy.modules.chunklimits

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer

/**
 * Per-chunk block limits (redstone parts, hoppers, Poké Snacks...). The check itself lives in `BlockItemMixin` →
 * [ChunkLimits.allowPlace]; this class loads the config and registers `/chunklimits`.
 */
class ChunkLimitsModule : SbModule {
    override val id = "chunklimits"
    override val folder = "ChunkLimits"
    override val legacyFolders = emptyList<String>()
    override val description = "Caps redstone parts, hoppers, Poké Snacks... per chunk (configurable groups)."

    override fun init() {
        // the registries are complete by SERVER_STARTED (modded blocks included)
        ServerLifecycleEvents.SERVER_STARTED.register { load() }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    override fun reload() {
        if (ServerHolder.server != null) load()
    }

    private fun load() {
        ChunkLimits.apply(ConfigFiles.load(folder, "config.json", ChunkLimitsConfig::class.java) { ChunkLimitsConfig() })
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "chunklimits") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("chunklimits").build(), "chunklimits")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(SbPermissions.require(ChunkLimits.INFO, 2)).executes { ctx ->
            val player = ctx.source.entity as? ServerPlayer
            if (player == null) {
                Texts.fail(ctx.source, "<red>Only players can use this command.")
                return@executes 0
            }
            val (chunk, usage) = ChunkLimits.usage(player)
            val messages = ChunkLimits.messages
            if (usage.isEmpty()) {
                Texts.reply(ctx.source, messages.infoNone)
                return@executes 0
            }
            Texts.reply(ctx.source, messages.infoHeader, mapOf("x" to chunk.x, "z" to chunk.z))
            for ((group, count) in usage) {
                Texts.reply(ctx.source, messages.infoLine, mapOf("group" to group.id, "count" to count, "limit" to group.max))
            }
            usage.size
        }
}
