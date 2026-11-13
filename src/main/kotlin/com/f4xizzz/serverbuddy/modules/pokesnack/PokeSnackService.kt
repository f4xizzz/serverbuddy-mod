package com.f4xizzz.serverbuddy.modules.pokesnack

import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Locale

/** Loads `PokeSnack/config.json`, applies it when Cobblemon's data is ready and registers `/pokesnack`. Cobblemon only. */
object PokeSnackService {
    const val ADMIN = "serverbuddy.pokesnack.admin"

    @Volatile
    private var config = PokeSnackConfig()

    fun install(folder: String) {
        config = load(folder)
        // Cobblemon's data (seasonings, bait effects) is complete once the server is up, and again after every /reload
        ServerLifecycleEvents.SERVER_STARTED.register { SnackBuffs.apply(config) }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, success -> if (success) SnackBuffs.apply(config) }
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        config = load(folder)
        if (ServerHolder.server != null) SnackBuffs.apply(config)
    }

    private fun load(folder: String): PokeSnackConfig = ConfigFiles.load(folder, "config.json", PokeSnackConfig::class.java) { PokeSnackConfig() }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        SbCommands.register(dispatcher, "pokesnack") { name -> node(name) }
        ModuleCommands.mount(dispatcher, node("pokesnack").build(), "pokesnack")
    }

    private fun node(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires(SbPermissions.require(ADMIN, 2))
            .then(
                Commands.literal("buffs")
                    .executes { ctx ->
                        Texts.reply(ctx.source, "<gold>Poké Snack buffs: <white>${SnackBuffs.summary}</white>")
                        Texts.reply(ctx.source, "<gray>Use <white>/pokesnack buffs <item></white> to see what one ingredient gives right now.")
                        1
                    }
                    .then(
                        Commands.argument("item", ResourceLocationArgument.id())
                            .suggests { _, builder -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder) }
                            .executes { ctx ->
                            val id = ResourceLocationArgument.getId(ctx, "item")
                            val raw = id.toString()
                            val effects = SnackBuffs.describe(id)
                            if (effects == null) {
                                Texts.fail(ctx.source, "<red>Unknown item: <white>${Texts.escape(raw)}")
                                return@executes 0
                            }
                            if (effects.isEmpty()) {
                                Texts.reply(ctx.source, "<gray><white>${Texts.escape(raw)}</white> gives no snack buffs.")
                                return@executes 0
                            }
                            Texts.reply(ctx.source, "<gold>Effects of <white>${Texts.escape(raw)}</white> (after your config):")
                            for (fx in effects) {
                                val sub = fx.subcategory?.let { "@$it" } ?: ""
                                Texts.reply(ctx.source, " <gray>- <white>${Texts.escape(fx.type + sub)}</white> chance <yellow>${percent(fx.chance)}</yellow>, value <yellow>${number(fx.value)}</yellow>")
                            }
                            effects.size
                        }
                    )
            )

    private fun percent(chance: Double): String = number(chance * 100) + "%"

    private fun number(value: Double): String = if (value == Math.floor(value) && Math.abs(value) < 1e9) value.toLong().toString() else String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
}
