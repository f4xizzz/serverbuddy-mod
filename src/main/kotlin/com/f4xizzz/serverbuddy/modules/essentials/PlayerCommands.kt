package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.playerArg
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.target
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.commands.Commands
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.stats.Stats
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Controle de AFK: quem está parado há tempo demais (ou usou /afk). */
object AfkService {
    private class Spot(var x: Double, var y: Double, var z: Double, var yaw: Float, var pitch: Float, var lastActive: Long)

    private val spots = ConcurrentHashMap<UUID, Spot>()
    val afk: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isAfk(uuid: UUID): Boolean = uuid in afk

    /** Is [command] (without the slash) the /afk command, under whatever name or alias it has? */
    fun isAfkCommand(command: String): Boolean {
        val root = command.trim().substringBefore(' ').lowercase()
        val entry = com.f4xizzz.serverbuddy.core.SbCommands.find("afk") ?: return root == "afk"
        return root == entry.name.lowercase() || entry.aliases.any { it.equals(root, true) }
    }

    /** What %afk% turns into for someone who is AFK (see `afk.label`). */
    fun label(): String = EssentialsModule.config.afk.label

    fun set(player: ServerPlayer, value: Boolean) {
        val changed = if (value) afk.add(player.uuid) else afk.remove(player.uuid)
        if (!changed) return
        val settings = EssentialsModule.config.afk
        val tag = settings.entityTag.trim()
        if (tag.isNotEmpty()) {
            if (value) player.addTag(tag) else player.removeTag(tag)
        }
        if (settings.announce) {
            val key = if (value) "afk_on" else "afk_off"
            player.server.playerList.broadcastSystemMessage(Texts.render(Msg.text(key, mapOf("player" to ChatService.displayName(player.uuid, player.gameProfile.name)))), false)
        }
        Styling.refreshName(player)
        AfkEvents.CHANGED.invoker().onChanged(player, value)
        if (!value) spots[player.uuid]?.lastActive = System.currentTimeMillis()
    }

    /** The tag is saved with the player, so it must not outlive the AFK state: take it off when they leave and when they join. */
    fun cleanTag(player: ServerPlayer) {
        val tag = EssentialsModule.config.afk.entityTag.trim()
        if (tag.isNotEmpty()) player.removeTag(tag)
    }

    /** O jogador fez alguma coisa (falou, usou comando): deixa de estar AFK. */
    fun activity(player: ServerPlayer) {
        spots[player.uuid]?.lastActive = System.currentTimeMillis()
        if (player.uuid in afk) set(player, false)
    }

    fun onLeave(player: ServerPlayer) {
        cleanTag(player)
        spots.remove(player.uuid)
        afk.remove(player.uuid)
    }

    /** A cada segundo: mexeu? Se não, há quanto tempo? */
    fun tick(server: MinecraftServer) {
        val settings = EssentialsModule.config.afk
        if (!settings.enabled) return
        val now = System.currentTimeMillis()
        for (player in server.playerList.players) {
            val spot = spots.getOrPut(player.uuid) { Spot(player.x, player.y, player.z, player.yRot, player.xRot, now) }
            val moved = Math.abs(player.x - spot.x) + Math.abs(player.y - spot.y) + Math.abs(player.z - spot.z) > 0.05 || player.yRot != spot.yaw || player.xRot != spot.pitch
            if (moved) {
                spot.x = player.x
                spot.y = player.y
                spot.z = player.z
                spot.yaw = player.yRot
                spot.pitch = player.xRot
                spot.lastActive = now
                if (player.uuid in afk) set(player, false)
                continue
            }
            val idle = now - spot.lastActive
            if (settings.afterMinutes > 0 && idle >= settings.afterMinutes * 60_000L && player.uuid !in afk) set(player, true)
            if (settings.kickAfterMinutes > 0 && idle >= settings.kickAfterMinutes * 60_000L && !SbPermissions.check(player, "serverbuddy.essentials.afk.bypass", 2)) {
                player.connection.disconnect(Texts.render(Msg.text("afk_kick")))
            }
        }
    }
}

/** /god, /fly, /heal, /feed, /speed, /hat, /near, /seen, /playtime, /afk, /suicide, /ping, /smite e /extinguish. */
object PlayerCommands {
    private val SPEED_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "essentials_speed")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "god", level = 2) { executes { god(it) } }
        register(dispatcher, "fly", level = 2) { executes { fly(it) } }
        register(dispatcher, "heal", level = 2) {
            executes { heal(it, null) }
            then(playerArg().executes { heal(it, target(it)) })
        }
        register(dispatcher, "feed", level = 2) {
            executes { feed(it, null) }
            then(playerArg().executes { feed(it, target(it)) })
        }
        register(dispatcher, "speed", level = 2) { then(Commands.argument("velocidade", DoubleArgumentType.doubleArg(0.0, 10.0)).executes { speed(it) }) }
        register(dispatcher, "hat") { executes { hat(it) } }
        register(dispatcher, "near", aliases = listOf("nearby")) {
            executes { near(it, 200) }
            then(Commands.argument("raio", IntegerArgumentType.integer(1, 2000)).executes { near(it, IntegerArgumentType.getInteger(it, "raio")) })
        }
        register(dispatcher, "seen") { then(word("jogador").executes { seen(it) }) }
        register(dispatcher, "playtime", aliases = listOf("pt")) {
            executes { playtime(it, null) }
            then(playerArg().executes { playtime(it, target(it)) })
        }
        register(dispatcher, "afk") { executes { afk(it) } }
        register(dispatcher, "suicide") { executes { suicide(it) } }
        register(dispatcher, "ping") {
            executes { ping(it, null) }
            then(playerArg().executes { ping(it, target(it)) })
        }
        register(dispatcher, "smite", level = 2) { then(playerArg().executes { smite(it) }) }
        register(dispatcher, "extinguish", aliases = listOf("ext"), level = 2) { executes { extinguish(it) } }
    }

    private fun god(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        player.abilities.invulnerable = !player.abilities.invulnerable
        player.onUpdateAbilities()
        Msg.send(player, if (player.abilities.invulnerable) "god_on" else "god_off")
        return 1
    }

    private fun fly(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val on = !player.abilities.mayfly
        player.abilities.mayfly = on
        if (!on) player.abilities.flying = false
        player.onUpdateAbilities()
        Msg.send(player, if (on) "fly_on" else "fly_off")
        return 1
    }

    private fun others(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): ServerPlayer? {
        if (who != null) {
            if (!SbPermissions.check(ctx.source, "serverbuddy.essentials.others", 3)) {
                Msg.fail(ctx.source, "player_unknown", mapOf("player" to who.gameProfile.name))
                return null
            }
            return who
        }
        return EssentialsCommands.player(ctx)
    }

    private fun heal(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val player = others(ctx, who) ?: return 0
        player.heal(player.maxHealth - player.health)
        player.foodData.setFoodLevel(20)
        player.clearFire()
        Msg.send(player, "healed")
        return 1
    }

    private fun feed(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val player = others(ctx, who) ?: return 0
        player.foodData.setFoodLevel(20)
        player.foodData.setSaturation(20f)
        Msg.send(player, "fed")
        return 1
    }

    private fun speed(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val value = DoubleArgumentType.getDouble(ctx, "velocidade")
        if (player.abilities.flying) {
            player.abilities.setFlyingSpeed((0.05 * value).toFloat())
            player.onUpdateAbilities()
            Msg.send(player, "speed_set", mapOf("kind" to "voo", "speed" to value))
        } else {
            val attribute = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return 0
            if (value == 1.0) attribute.removeModifier(SPEED_ID) else attribute.addOrReplacePermanentModifier(AttributeModifier(SPEED_ID, value - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))
            Msg.send(player, "speed_set", mapOf("kind" to "caminhada", "speed" to value))
        }
        return 1
    }

    private fun hat(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val held = player.mainHandItem
        if (held.isEmpty) {
            Msg.fail(ctx.source, "hat_empty")
            return 0
        }
        val old = player.getItemBySlot(EquipmentSlot.HEAD).copy()
        player.setItemSlot(EquipmentSlot.HEAD, held.split(1))
        if (!old.isEmpty) {
            player.inventory.add(old)
            if (!old.isEmpty) player.drop(old, false)
        }
        Msg.send(player, "hat_done")
        return 1
    }

    private fun near(ctx: CommandContext<CommandSourceStack>, radius: Int): Int {
        val me = EssentialsCommands.player(ctx) ?: return 0
        val close = ctx.source.server.playerList.players
            .filter { it.uuid != me.uuid && it.level() == me.level() && it.distanceTo(me) <= radius }
            .sortedBy { it.distanceTo(me) }
        if (close.isEmpty()) {
            Msg.reply(ctx.source, "near_none", mapOf("radius" to radius))
            return 0
        }
        Msg.reply(ctx.source, "near_line", mapOf("radius" to radius, "players" to close.joinToString("<gray>, ") { "<white>${it.gameProfile.name}</white> <dark_gray>(${it.distanceTo(me).toInt()}m)" }))
        return close.size
    }

    private fun seen(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, name)
        val record = found?.let { ChatService.record(it.first, it.second) }
        if (found == null || record == null || record.lastSeen <= 0) {
            Msg.fail(ctx.source, "seen_never", mapOf("player" to Texts.escape(name)))
            return 0
        }
        val online = ctx.source.server.playerList.getPlayer(found.first) != null
        val ago = Durations.format(System.currentTimeMillis() - record.lastSeen)
        Msg.reply(ctx.source, if (online) "seen_online" else "seen_offline", mapOf("player" to found.second, "time" to ago))
        return 1
    }

    private fun playtime(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val player = who ?: EssentialsCommands.player(ctx) ?: return 0
        val id = BuiltInRegistries.CUSTOM_STAT.get(ResourceLocation.withDefaultNamespace("play_time")) ?: return 0
        val ticks = player.stats.getValue(Stats.CUSTOM.get(id)).toLong()
        Msg.reply(ctx.source, "playtime", mapOf("player" to player.gameProfile.name, "time" to Durations.format(ticks * 50)))
        return 1
    }

    private fun afk(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        AfkService.set(player, !AfkService.isAfk(player.uuid))
        return 1
    }

    private fun suicide(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE)
        return 1
    }

    private fun ping(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val player = who ?: EssentialsCommands.player(ctx) ?: return 0
        Msg.reply(ctx.source, "ping", mapOf("player" to player.gameProfile.name, "ping" to player.connection.latency()))
        return 1
    }

    private fun smite(ctx: CommandContext<CommandSourceStack>): Int {
        val victim = target(ctx)
        val level = victim.serverLevel()
        val bolt = EntityType.LIGHTNING_BOLT.create(level) ?: return 0
        bolt.moveTo(Vec3.atBottomCenterOf(victim.blockPosition()))
        level.addFreshEntity(bolt)
        Msg.reply(ctx.source, "smite_done", mapOf("player" to victim.gameProfile.name))
        return 1
    }

    private fun extinguish(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        player.clearFire()
        Msg.send(player, "extinguished")
        return 1
    }
}
