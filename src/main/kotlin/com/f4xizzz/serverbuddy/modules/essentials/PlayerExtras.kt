package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.ServerScheduler
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.suggestWords
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.text
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/** Tempo ativo (sem estar AFK) de cada jogador: soma a cada segundo, grava a cada minuto e quando ele sai. */
object ActiveTime {
    private val seconds = ConcurrentHashMap<UUID, Long>()
    private var counter = 0

    fun onJoin(player: ServerPlayer) {
        seconds[player.uuid] = EssentialsRepository.activeSeconds(player.uuid)
    }

    fun onLeave(uuid: UUID) {
        seconds.remove(uuid)?.let { EssentialsRepository.setActiveSeconds(uuid, it) }
    }

    /** O tempo ativo de [uuid] em segundos (do cache se está online). */
    fun of(uuid: UUID): Long = seconds[uuid] ?: EssentialsRepository.activeSeconds(uuid)

    fun set(uuid: UUID, value: Long) {
        if (seconds.containsKey(uuid)) seconds[uuid] = value
        EssentialsRepository.setActiveSeconds(uuid, value)
    }

    /** A cada segundo: quem está online e não está AFK ganha um segundo. */
    fun tick(server: MinecraftServer) {
        for (player in server.playerList.players) {
            if (AfkService.isAfk(player.uuid)) continue
            seconds.merge(player.uuid, 1L, Long::plus)
        }
        if (++counter >= 60) {
            counter = 0
            flush()
        }
    }

    fun flush() {
        for ((id, value) in seconds) EssentialsRepository.setActiveSeconds(id, value)
    }
}

/**
 * Mais comandos do Solstice: /ignorelist, /plainbroadcast, /ignite, /effects, /sleep, /serverstat (/tps), /activetime, /motd e as
 * brincadeiras /kittycannon, /rocket e /nudge.
 */
object PlayerExtras {
    private val ONLINE = suggestWords { ServerHolder.server?.playerList?.playerNamesArray?.toList() ?: emptyList() }

    /** Os jogadores que estão dormindo por causa do /sleep (o Minecraft normal acorda quem não está numa cama). */
    private val sleepers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun registerPlayer(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "ignite", level = 2) {
            executes { ignite(it, null, null) }
            then(
                Commands.argument("jogadores", EntityArgument.players()).executes { ignite(it, EntityArgument.getPlayers(it, "jogadores"), null) }
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(0)).executes { ignite(it, EntityArgument.getPlayers(it, "jogadores"), IntegerArgumentType.getInteger(it, "ticks")) })
            )
        }
        register(dispatcher, "effects") {
            executes { effects(it, null) }
            then(Commands.argument("jogador", EntityArgument.player()).requires(EssentialsCommands.perm("effects.others", 2)).executes { effects(it, EntityArgument.getPlayer(it, "jogador")) })
        }
        // deitar sem cama ajuda a passar a noite: por padrão é da equipe (dê serverbuddy.essentials.sleep a quem você quiser pelo LuckPerms)
        register(dispatcher, "sleep", level = 2) {
            executes { sleep(it, null) }
            then(Commands.argument("jogadores", EntityArgument.players()).requires(EssentialsCommands.perm("sleep.others", 2)).executes { sleep(it, EntityArgument.getPlayers(it, "jogadores")) })
        }
        register(dispatcher, "serverstat", aliases = listOf("tps"), level = 3) { executes { serverStat(it) } }
        register(dispatcher, "activetime") {
            executes { activeTime(it, null) }
            then(word("jogador").suggests(ONLINE).executes { activeTime(it, StringArgumentType.getString(it, "jogador")) })
            then(Commands.literal("top").executes { top(it, 10) }.then(Commands.argument("quantos", IntegerArgumentType.integer(1, 50)).executes { top(it, IntegerArgumentType.getInteger(it, "quantos")) }))
            then(
                Commands.literal("set").requires(EssentialsCommands.perm("activetime.set", 3))
                    .then(word("jogador").suggests(ONLINE).then(word("tempo").executes { setActive(it) }))
            )
        }
        register(dispatcher, "kittycannon", level = 2) { executes { kittyCannon(it) } }
        register(dispatcher, "rocket", level = 2) {
            then(Commands.argument("alvos", EntityArgument.players()).executes { rocket(it, "") }.then(text("opcoes").executes { rocket(it, StringArgumentType.getString(it, "opcoes")) }))
        }
        register(dispatcher, "nudge", level = 2) {
            then(
                Commands.argument("alvos", EntityArgument.entities()).executes { nudge(it, 1f, false) }
                    .then(
                        Commands.argument("forca", FloatArgumentType.floatArg(0f, 32f)).executes { nudge(it, FloatArgumentType.getFloat(it, "forca"), false) }
                            .then(Commands.argument("silencioso", BoolArgumentType.bool()).executes { nudge(it, FloatArgumentType.getFloat(it, "forca"), BoolArgumentType.getBool(it, "silencioso")) })
                    )
            )
        }
    }

    fun registerChat(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "ignorelist") { executes { ChatCommands.listIgnored(it) } }
        register(dispatcher, "plainbroadcast", aliases = listOf("pbc"), level = 2) { then(text("mensagem").executes { plainBroadcast(it) }) }
    }

    fun registerInfo(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, "motd") { executes { motd(it) } }
    }

    // ---------------------------------------------------------------- chat e informações

    private fun plainBroadcast(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.server.playerList.broadcastSystemMessage(Texts.render(StringArgumentType.getString(ctx, "mensagem")), false)
        return 1
    }

    private fun motd(ctx: CommandContext<CommandSourceStack>): Int {
        val lines = EssentialsModule.config.info.motd
        for (line in lines) ctx.source.sendSuccess({ Texts.render(line) }, false)
        return lines.size
    }

    /** Mostra o /motd a quem acabou de entrar (se "motd_on_join" estiver ligado). */
    fun motdOnJoin(player: ServerPlayer) {
        val info = EssentialsModule.config.info
        if (!info.motdOnJoin) return
        ServerScheduler.later(40) { for (line in info.motd) player.sendSystemMessage(Texts.render(line)) }
    }

    // ---------------------------------------------------------------- jogador

    private fun ignite(ctx: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>?, ticks: Int?): Int {
        val targets = players ?: listOfNotNull(EssentialsCommands.player(ctx))
        for (player in targets) player.remainingFireTicks = ticks ?: 200
        for (player in targets) Msg.reply(ctx.source, "ignite_done", mapOf("player" to player.gameProfile.name, "seconds" to ((ticks ?: 200) / 20.0).let { String.format(java.util.Locale.ROOT, "%.1f", it) }), broadcast = true)
        return targets.size
    }

    private fun effects(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val target = who ?: EssentialsCommands.player(ctx) ?: return 0
        val active = target.activeEffectsMap
        if (active.isEmpty()) {
            Msg.reply(ctx.source, "effects_none", mapOf("player" to target.gameProfile.name))
            return 0
        }
        Msg.reply(ctx.source, "effects_header", mapOf("player" to target.gameProfile.name, "count" to active.size))
        for ((holder, instance) in active) {
            val time = if (instance.isInfiniteDuration) "∞" else Durations.format(instance.duration * 50L)
            Msg.reply(ctx.source, "effects_line", mapOf("effect" to Texts.lang(holder.value().descriptionId), "level" to (instance.amplifier + 1), "time" to time))
        }
        return active.size
    }

    private fun sleep(ctx: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>?): Int {
        val targets = players ?: listOfNotNull(EssentialsCommands.player(ctx))
        for (player in targets) {
            sleepers.add(player.uuid)
            player.startSleeping(player.blockPosition())
            player.serverLevel().updateSleepingPlayerList()
        }
        return targets.size
    }

    /** O mixin [com.f4xizzz.serverbuddy.mixin.LivingEntitySleepMixin] pergunta aqui se a pessoa dorme pelo /sleep (então "a cama existe"). */
    @JvmStatic
    fun isCommandSleeping(uuid: UUID): Boolean = sleepers.contains(uuid)

    /** Ao acordar, a pessoa deixa de ser "dorminhoca do /sleep"; e dormir assim só passa a noite se for noite mesmo. */
    fun registerEvents() {
        EntitySleepEvents.STOP_SLEEPING.register(EntitySleepEvents.StopSleeping { entity, _ -> sleepers.remove(entity.uuid) })
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(EntitySleepEvents.AllowResettingTime { player -> player.uuid !in sleepers || !player.level().isDay })
    }

    /** Tira da lista quem saiu do servidor ou já levantou. */
    fun tickSleepers(server: MinecraftServer) {
        if (sleepers.isEmpty()) return
        for (id in sleepers.toList()) {
            val player = server.playerList.getPlayer(id)
            if (player == null || !player.isSleeping) sleepers.remove(id)
        }
    }

    fun onLeave(uuid: UUID) {
        sleepers.remove(uuid)
    }

    /** Milissegundos que um tick leva, em média. */
    fun mspt(server: MinecraftServer): Double = server.averageTickTimeNanos / 1_000_000.0

    /** TPS estimado a partir do tempo médio de um tick (20 é o máximo). */
    fun tps(server: MinecraftServer): Double = minOf(20.0, 1000.0 / maxOf(mspt(server), 50.0))

    private fun serverStat(ctx: CommandContext<CommandSourceStack>): Int {
        val server = ctx.source.server
        val mspt = mspt(server)
        val tps = tps(server)
        val runtime = Runtime.getRuntime()
        val mb = 1024L * 1024L
        val used = (runtime.totalMemory() - runtime.freeMemory()) / mb
        val entities = server.allLevels.sumOf { level -> level.allEntities.count() }
        val chunks = server.allLevels.sumOf { level -> level.chunkSource.loadedChunksCount }
        fun f(d: Double) = String.format(java.util.Locale.ROOT, "%.1f", d)
        val color = if (tps >= 19.0) "<green>" else if (tps >= 15.0) "<yellow>" else "<red>"
        Msg.reply(ctx.source, "stat_title")
        Msg.reply(ctx.source, "stat_tps", mapOf("tps" to color + f(tps), "mspt" to f(mspt)))
        Msg.reply(ctx.source, "stat_uptime", mapOf("time" to Durations.format(ManagementFactory.getRuntimeMXBean().uptime)))
        Msg.reply(ctx.source, "stat_memory", mapOf("used" to used, "allocated" to runtime.totalMemory() / mb, "max" to runtime.maxMemory() / mb))
        Msg.reply(ctx.source, "stat_world", mapOf("players" to server.playerList.playerCount, "max" to server.maxPlayers, "entities" to entities, "chunks" to chunks))
        return 1
    }

    private fun activeTime(ctx: CommandContext<CommandSourceStack>, wanted: String?): Int {
        if (wanted == null) {
            val me = EssentialsCommands.player(ctx) ?: return 0
            Msg.reply(ctx.source, "activetime_self", mapOf("time" to Durations.format(ActiveTime.of(me.uuid) * 1000)))
            return 1
        }
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
            return 0
        }
        val value = ActiveTime.of(found.first)
        if (value <= 0) {
            Msg.fail(ctx.source, "activetime_never", mapOf("player" to found.second))
            return 0
        }
        Msg.reply(ctx.source, "activetime_other", mapOf("player" to found.second, "time" to Durations.format(value * 1000)))
        return 1
    }

    private fun top(ctx: CommandContext<CommandSourceStack>, count: Int): Int {
        ActiveTime.flush()
        val ranking = EssentialsRepository.topActive(count)
        if (ranking.isEmpty()) {
            Msg.reply(ctx.source, "activetime_top_empty")
            return 0
        }
        Msg.reply(ctx.source, "activetime_top_header", mapOf("count" to ranking.size))
        ranking.forEachIndexed { index, (name, value) ->
            Msg.reply(ctx.source, "activetime_top_line", mapOf("index" to index + 1, "player" to Texts.escape(name), "time" to Durations.format(value * 1000)))
        }
        return ranking.size
    }

    private fun setActive(ctx: CommandContext<CommandSourceStack>): Int {
        val wanted = StringArgumentType.getString(ctx, "jogador")
        val found = ChatService.resolve(ctx.source.server, wanted)
        if (found == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(wanted)))
            return 0
        }
        val millis = Durations.parseMillis(StringArgumentType.getString(ctx, "tempo"))
        if (millis == null) {
            Msg.fail(ctx.source, "bad_time")
            return 0
        }
        ActiveTime.set(found.first, millis / 1000)
        Msg.reply(ctx.source, "activetime_set", mapOf("player" to found.second, "time" to Durations.format(millis)), broadcast = true)
        return 1
    }

    // ---------------------------------------------------------------- brincadeiras

    /** Só o brilho e o barulho de uma explosão, sem destruir nada (como o Solstice faz). */
    private fun fakeExplosion(level: ServerLevel, pos: Vec3, big: Boolean) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, (1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f) * 0.7f)
        level.sendParticles(if (big) ParticleTypes.EXPLOSION_EMITTER else ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 1.0)
    }

    private fun push(entity: Entity, velocity: Vec3) {
        entity.deltaMovement = velocity
        entity.hurtMarked = true
    }

    private fun kittyCannon(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val level = player.serverLevel()
        val cat = EntityType.CAT.create(level) ?: return 0
        val look = player.lookAngle
        val start = player.eyePosition.add(look.scale(1.5))
        cat.moveTo(start.x, start.y, start.z, player.yRot, player.xRot)
        cat.deltaMovement = look.scale(1.6)
        level.addFreshEntity(cat)
        ServerScheduler.later(20) {
            fakeExplosion(level, cat.position(), false)
            cat.discard()
        }
        return 1
    }

    private fun rocket(ctx: CommandContext<CommandSourceStack>, options: String): Int {
        val targets = EntityArgument.getPlayers(ctx, "alvos")
        val words = options.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var explode = false
        var power = 2.0f
        var i = 0
        while (i < words.size) {
            when (words[i].lowercase()) {
                "-e", "--explode" -> explode = true
                "-p", "--power" -> {
                    power = words.getOrNull(i + 1)?.toFloatOrNull()?.coerceIn(0f, 16f) ?: power
                    i++
                }
            }
            i++
        }
        for (player in targets) {
            if (explode) fakeExplosion(player.serverLevel(), player.position(), power >= 2f)
            push(player, Vec3(0.0, power.toDouble(), 0.0))
        }
        return targets.size
    }

    private fun nudge(ctx: CommandContext<CommandSourceStack>, power: Float, quiet: Boolean): Int {
        val targets = EntityArgument.getEntities(ctx, "alvos")
        val random = ctx.source.level.random
        for (entity in targets) {
            if (entity !is LivingEntity) continue
            val angle = random.nextDouble() * Math.PI * 2
            push(entity, Vec3(sin(angle), 0.0, cos(angle)).scale(power.toDouble()))
            if (!quiet && entity is ServerPlayer) {
                val pitch = (sin(random.nextDouble() * Math.PI * 2) / 10.0 + 1.0).toFloat()
                entity.serverLevel().playSound(null, entity.x, entity.y, entity.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, pitch)
            }
        }
        return targets.size
    }
}
