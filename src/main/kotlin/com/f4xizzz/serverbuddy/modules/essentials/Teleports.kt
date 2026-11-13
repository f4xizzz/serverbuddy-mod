package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Cooldowns
import com.f4xizzz.serverbuddy.core.SbPermissions
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Pedido de /tpa: [here] = true quando o autor quer que o alvo venha até ele (/tpahere). */
class TpaRequest(val from: UUID, val fromName: String, val to: UUID, val here: Boolean, val createdAt: Long)

/**
 * Tudo que move jogadores: espera e recarga dos teleportes, o lugar de onde o jogador veio (/back), os pedidos de /tpa e a busca
 * de lugar seguro do /rtp.
 */
object Teleports {
    private class Pending(val x: Double, val y: Double, val z: Double, var secondsLeft: Int, val action: () -> Unit)

    private val pending = ConcurrentHashMap<UUID, Pending>()
    private val back = ConcurrentHashMap<UUID, SavedLocation>()
    private val deathBack = ConcurrentHashMap<UUID, SavedLocation>()
    private val requests = ConcurrentHashMap<UUID, TpaRequest>() // chave = quem recebe
    val tpOff: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val config get() = EssentialsModule.config.teleport

    // ---------------------------------------------------------------- espera, recarga e /back

    /**
     * Teleporta [player] com a espera e a recarga da config. [action] faz o teleporte de verdade e só roda quando a espera acaba
     * (ou na hora, se não há espera). [cooldownKey] separa recargas diferentes (o /rtp tem a dele).
     */
    fun request(player: ServerPlayer, cooldownKey: String = "tp", cooldownSeconds: Int = config.cooldownSeconds, action: () -> Unit) {
        if (pending.containsKey(player.uuid)) {
            Msg.send(player, "tp_pending")
            return
        }
        val bypassCooldown = SbPermissions.check(player, "serverbuddy.essentials.bypass.cooldown", 2)
        if (!bypassCooldown) {
            val left = Cooldowns.use("essentials:$cooldownKey", player.uuid, cooldownSeconds)
            if (left > 0) {
                Msg.send(player, "tp_cooldown", mapOf("seconds" to left))
                return
            }
        }
        val warmup = config.warmupSeconds
        if (warmup <= 0 || SbPermissions.check(player, "serverbuddy.essentials.bypass.warmup", 2)) {
            action()
            return
        }
        pending[player.uuid] = Pending(player.x, player.y, player.z, warmup, action)
        Msg.send(player, "tp_warmup", mapOf("seconds" to warmup))
    }

    /** A cada segundo: conta a espera dos teleportes e cancela quem se mexeu. */
    fun tick(players: List<ServerPlayer>) {
        if (pending.isEmpty()) return
        for (player in players) {
            val wait = pending[player.uuid] ?: continue
            if (config.cancelOnMove) {
                val dx = player.x - wait.x
                val dy = player.y - wait.y
                val dz = player.z - wait.z
                if (dx * dx + dy * dy + dz * dz > 0.5) {
                    pending.remove(player.uuid)
                    Msg.send(player, "tp_cancelled_move")
                    continue
                }
            }
            wait.secondsLeft--
            if (wait.secondsLeft <= 0) {
                pending.remove(player.uuid)
                wait.action()
            }
        }
        // quem saiu do servidor no meio da espera
        pending.keys.removeIf { id -> players.none { it.uuid == id } }
    }

    fun cancel(uuid: UUID) {
        pending.remove(uuid)
    }

    /** Guarda de onde o jogador vai sair (para o /back). Chame antes de teleportar. */
    fun remember(player: ServerPlayer) {
        back[player.uuid] = SavedLocation.of(player)
    }

    fun rememberDeath(player: ServerPlayer) {
        deathBack[player.uuid] = SavedLocation.of(player)
    }

    /** Para onde o /back leva: o lugar da morte (se a config e a permissão deixam e ela é mais nova) ou o último lugar de teleporte. */
    fun backTarget(player: ServerPlayer): SavedLocation? {
        val death = deathBack[player.uuid]
        if (death != null && config.backOnDeath && SbPermissions.check(player, "serverbuddy.essentials.back.death", true)) {
            deathBack.remove(player.uuid)
            return death
        }
        return back[player.uuid]
    }

    /** Teleporta guardando o lugar de origem. */
    fun go(player: ServerPlayer, target: SavedLocation): Boolean {
        remember(player)
        val ok = target.teleport(player)
        if (ok) Msg.send(player, "tp_done")
        return ok
    }

    // ---------------------------------------------------------------- /tpa

    fun addRequest(request: TpaRequest) {
        requests[request.to] = request
    }

    /** O pedido que [to] recebeu e ainda vale (apaga os vencidos). [fromName] filtra por quem pediu. */
    fun requestFor(to: UUID, fromName: String?): TpaRequest? {
        val request = requests[to] ?: return null
        if (System.currentTimeMillis() - request.createdAt > config.tpaTimeoutSeconds * 1000L) {
            requests.remove(to)
            return null
        }
        if (fromName != null && !request.fromName.equals(fromName, ignoreCase = true)) return null
        return request
    }

    fun removeRequest(to: UUID) {
        requests.remove(to)
    }

    /** Apaga o pedido que [from] mandou (para /tpcancel). */
    fun cancelRequestFrom(from: UUID): Boolean {
        val entry = requests.entries.firstOrNull { it.value.from == from } ?: return false
        requests.remove(entry.key)
        return true
    }

    fun onLeave(uuid: UUID) {
        pending.remove(uuid)
        requests.remove(uuid)
        requests.entries.removeIf { it.value.from == uuid }
        back.remove(uuid)
        deathBack.remove(uuid)
    }

    // ---------------------------------------------------------------- /rtp

    /** Procura um lugar seguro a uma distância entre [minRadius] e [maxRadius] de ([cx], [cz]). */
    fun findSafe(level: ServerLevel, cx: Int, cz: Int, minRadius: Int, maxRadius: Int, attempts: Int): BlockPos? {
        repeat(attempts) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val distance = Random.nextDouble(minRadius.toDouble(), maxRadius.toDouble().coerceAtLeast(minRadius + 1.0))
            val x = cx + (Math.cos(angle) * distance).toInt()
            val z = cz + (Math.sin(angle) * distance).toInt()
            if (!level.worldBorder.isWithinBounds(x.toDouble(), z.toDouble())) return@repeat
            level.getChunk(x shr 4, z shr 4) // carrega o pedaço para saber a altura
            val top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos(x, 0, z))
            if (isSafe(level, top)) return top
        }
        return null
    }

    private fun isSafe(level: ServerLevel, feet: BlockPos): Boolean {
        if (feet.y <= level.minBuildHeight + 1 || feet.y >= level.maxBuildHeight - 2) return false
        val ground = level.getBlockState(feet.below())
        if (ground.isAir || ground.fluidState.`is`(FluidTags.WATER) || ground.fluidState.`is`(FluidTags.LAVA)) return false
        if (ground.`is`(Blocks.CACTUS) || ground.`is`(Blocks.MAGMA_BLOCK) || ground.`is`(Blocks.FIRE) || ground.`is`(Blocks.CAMPFIRE) || ground.`is`(Blocks.POWDER_SNOW)) return false
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty) return false
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty) return false
        return true
    }

    fun levelOf(player: ServerPlayer, dimension: String): ServerLevel? = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)))
}
