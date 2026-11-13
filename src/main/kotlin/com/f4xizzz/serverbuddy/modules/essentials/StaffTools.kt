package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.CommandHooks
import com.f4xizzz.serverbuddy.core.Conditions
import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ModuleSettings
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.commandblock.CommandBlockService
import com.f4xizzz.serverbuddy.modules.commandblock.CommandMatcher
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Em que gesto o comando do /powertool roda. */
enum class PowerAction(val label: String) {
    USE("use"),
    ATTACK_BLOCK("attack_block"),
    ATTACK_ENTITY("attack_entity"),
    INTERACT_BLOCK("interact_block"),
    INTERACT_ENTITY("interact_entity");

    companion object {
        fun parse(text: String): PowerAction? = entries.firstOrNull { it.label.equals(text.trim(), ignoreCase = true) || it.name.equals(text.trim(), ignoreCase = true) }
    }
}

/**
 * /powertool: liga um comando a um item (por tipo de item) e a um gesto (clicar com ele, bater em bloco, bater em bicho...).
 * Os comandos ficam no banco de cada jogador e rodam como o próprio jogador. Dentro do comando: %player%, %target% (o jogador ou
 * bicho em que clicou), %x%, %y% e %z% (o bloco clicado, ou onde ele está).
 */
object PowerTools {
    private val data = ConcurrentHashMap<UUID, MutableMap<String, MutableMap<PowerAction, String>>>()
    private val off: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun itemId(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    fun onJoin(player: ServerPlayer) {
        val map = ConcurrentHashMap<String, MutableMap<PowerAction, String>>()
        for ((item, action, command) in EssentialsRepository.powertools(player.uuid)) {
            val parsed = PowerAction.parse(action) ?: continue
            map.getOrPut(item) { ConcurrentHashMap() }[parsed] = command
        }
        data[player.uuid] = map
    }

    fun onLeave(uuid: UUID) {
        data.remove(uuid)
        off.remove(uuid)
    }

    fun of(uuid: UUID): Map<String, Map<PowerAction, String>> = data[uuid].orEmpty()

    fun set(uuid: UUID, item: String, action: PowerAction, command: String) {
        data.getOrPut(uuid) { ConcurrentHashMap() }.getOrPut(item) { ConcurrentHashMap() }[action] = command
        EssentialsRepository.savePowertool(uuid, item, action.label, command)
    }

    /** Apaga o gesto [action] do item (ou todos os gestos dele). false se não tinha nada. */
    fun clear(uuid: UUID, item: String, action: PowerAction?): Boolean {
        val map = data[uuid] ?: return false
        val actions = map[item] ?: return false
        val removed = if (action == null) actions.isNotEmpty().also { map.remove(item) } else actions.remove(action) != null
        if (removed) EssentialsRepository.deletePowertool(uuid, item, action?.label)
        if (actions.isEmpty()) map.remove(item)
        return removed
    }

    /** Liga/desliga todos os powertools do jogador por um tempo (sem apagar). Devolve true se agora estão ligados. */
    fun toggle(uuid: UUID): Boolean = if (off.remove(uuid)) true else { off.add(uuid); false }

    /**
     * Roda antes dos outros ouvintes de eventos de clique: um powertool é uma escolha explícita do jogador. (O Flan atende o graveto de
     * inspeção por conta própria, antes dos eventos do Fabric: com um powertool no graveto o Flan ainda responde. Use outro item.)
     */
    private val FIRST: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "powertool")

    fun register() {
        UseItemCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        AttackBlockCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        AttackEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        UseBlockCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        UseEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        UseItemCallback.EVENT.register(FIRST, UseItemCallback { player, _, hand ->
            val stack = player.getItemInHand(hand)
            if (run(player, stack, PowerAction.USE, null, null)) InteractionResultHolder.success(stack) else InteractionResultHolder.pass(stack)
        })
        AttackBlockCallback.EVENT.register(FIRST, AttackBlockCallback { player, _, hand, pos, _ ->
            if (run(player, player.getItemInHand(hand), PowerAction.ATTACK_BLOCK, null, pos.center)) InteractionResult.SUCCESS else InteractionResult.PASS
        })
        AttackEntityCallback.EVENT.register(FIRST, AttackEntityCallback { player, _, hand, entity, _ ->
            if (run(player, player.getItemInHand(hand), PowerAction.ATTACK_ENTITY, entity, null)) InteractionResult.SUCCESS else InteractionResult.PASS
        })
        UseBlockCallback.EVENT.register(FIRST, UseBlockCallback { player, _, hand, hit ->
            if (run(player, player.getItemInHand(hand), PowerAction.INTERACT_BLOCK, null, hit.blockPos.center)) InteractionResult.SUCCESS else InteractionResult.PASS
        })
        UseEntityCallback.EVENT.register(FIRST, UseEntityCallback { player, _, hand, entity, _ ->
            if (run(player, player.getItemInHand(hand), PowerAction.INTERACT_ENTITY, entity, null)) InteractionResult.SUCCESS else InteractionResult.PASS
        })
    }

    /** Roda o comando ligado a este item e gesto, se houver. true = rodou (e o gesto normal é cancelado). */
    private fun run(player: Player, stack: ItemStack, action: PowerAction, entity: Entity?, at: net.minecraft.world.phys.Vec3?): Boolean {
        if (player !is ServerPlayer || stack.isEmpty || data.isEmpty() || player.uuid in off) return false
        val command = data[player.uuid]?.get(itemId(stack))?.get(action) ?: return false
        val pos = at ?: player.position()
        val filled = Texts.fill(
            command,
            mapOf(
                "player" to player.gameProfile.name,
                "target" to (entity?.let { if (it is ServerPlayer) it.gameProfile.name else it.uuid.toString() } ?: ""),
                "x" to pos.x.toInt(), "y" to pos.y.toInt(), "z" to pos.z.toInt()
            )
        ).removePrefix("/")
        player.server.commands.performPrefixedCommand(player.createCommandSourceStack(), filled)
        return true
    }
}

/**
 * Recarga de comandos: grupos de comandos (config `cooldowns`) com um tempo de espera entre um uso e outro. Vale para qualquer comando que
 * o jogador digita, não só os do ServerBuddy. A espera começa no uso, mesmo que o comando dê erro.
 */
object CommandCooldowns {
    private val until = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    fun register() {
        CommandHooks.register { source, command -> onCommand(source, command) }
    }

    fun onLeave(uuid: UUID) {
        until.remove(uuid)
    }

    fun exempt(player: ServerPlayer, key: String): Boolean = SbPermissions.check(player, "serverbuddy.essentials.cooldown.exempt.$key", 3)

    /** Quanto falta (ms) para [key] de [uuid]; 0 = livre. */
    fun left(uuid: UUID, key: String): Long = ((until[uuid]?.get(key) ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0)

    fun clear(uuid: UUID, key: String): Boolean = until[uuid]?.remove(key) != null

    /** Os grupos em que [uuid] ainda espera, com o quanto falta (ms). */
    fun active(uuid: UUID): Map<String, Long> {
        val now = System.currentTimeMillis()
        return until[uuid].orEmpty().mapNotNull { (key, end) -> if (end > now) key to end - now else null }.toMap()
    }

    private fun onCommand(source: CommandSourceStack, command: String): Boolean {
        val settings = EssentialsModule.config.cooldowns
        if (!EssentialsModule.ready || !settings.enabled || settings.groups.isEmpty()) return false
        val player = source.player ?: return false
        val typed = CommandMatcher.words(command)
        if (typed.isEmpty()) return false
        val names = CommandBlockService.firstNames(source.server, typed[0])
        for ((key, group) in settings.groups) {
            if (!group.enabled || group.seconds <= 0) continue
            if (group.commands.none { CommandMatcher.matches(it, typed, names, ModuleSettings::commandName) }) continue
            if (exempt(player, key)) return false
            if ((group.conditions.isNotEmpty() || group.whenAny.isNotEmpty()) && !Conditions.test(group.conditions, group.whenAny, CommandBlockService.subjectOf(player))) continue
            val now = System.currentTimeMillis()
            val map = until.getOrPut(player.uuid) { ConcurrentHashMap() }
            val end = map[key] ?: 0L
            if (end > now) {
                val vars = mapOf("time" to Durations.format(end - now), "command" to Texts.escape(typed[0]), "group" to key)
                val template = group.message
                if (template != null) Texts.send(player, template, vars) else Msg.send(player, "cooldown_wait", vars)
                return true
            }
            map[key] = now + group.seconds * 1000L
            return false
        }
        return false
    }
}

/**
 * /timebar: uma barra de tempo (contagem regressiva ou progressiva) para todo mundo, que pode rodar um comando quando acaba.
 * No texto da barra: %total_time%, %elapsed_time% (já passou) e %remaining_time% (falta).
 */
object TimeBars {
    class Bar(
        val id: Int, val label: String, val total: Int, val countdown: Boolean, val event: ServerBossEvent,
        val command: String?, val source: CommandSourceStack, var elapsed: Int = 0
    ) {
        val remaining: Int get() = (total - elapsed).coerceAtLeast(0)
    }

    private val bars = java.util.concurrent.ConcurrentSkipListMap<Int, Bar>()
    private var nextId = 1

    fun all(): Collection<Bar> = bars.values

    fun find(id: Int): Bar? = bars[id]

    private fun text(bar: Bar): net.minecraft.network.chat.Component = Texts.render(
        bar.label,
        mapOf(
            "total_time" to Durations.format(bar.total * 1000L),
            "elapsed_time" to Durations.format(bar.elapsed * 1000L),
            "remaining_time" to Durations.format(bar.remaining * 1000L)
        )
    )

    private fun update(bar: Bar) {
        val progress = bar.elapsed.toFloat() / bar.total.toFloat()
        bar.event.progress = (if (bar.countdown) 1f - progress else progress).coerceIn(0f, 1f)
        bar.event.name = text(bar)
    }

    fun start(
        server: MinecraftServer, label: String, seconds: Int, color: BossEvent.BossBarColor, style: BossEvent.BossBarOverlay,
        countdown: Boolean, command: String?, source: CommandSourceStack
    ): Bar {
        val event = ServerBossEvent(Texts.render(label), color, style)
        val bar = Bar(nextId++, label, seconds.coerceAtLeast(1), countdown, event, command, source)
        update(bar)
        bars[bar.id] = bar
        for (player in server.playerList.players) event.addPlayer(player)
        return bar
    }

    fun cancel(id: Int): Boolean {
        val bar = bars.remove(id) ?: return false
        bar.event.removeAllPlayers()
        return true
    }

    fun cancelAll(): Int {
        val ids = bars.keys.toList()
        for (id in ids) cancel(id)
        return ids.size
    }

    /** A cada segundo: avança as barras, mostra para quem entrou e roda o comando das que acabaram. */
    fun tick(server: MinecraftServer) {
        if (bars.isEmpty()) return
        for (bar in bars.values.toList()) {
            bar.elapsed++
            update(bar)
            for (player in server.playerList.players) bar.event.addPlayer(player)
            if (bar.elapsed >= bar.total) {
                bars.remove(bar.id)
                bar.event.removeAllPlayers()
                bar.command?.let { server.commands.performPrefixedCommand(bar.source, it.removePrefix("/")) }
            }
        }
    }

    /** No fim do servidor: nada de barra pendurada. */
    fun clear() {
        cancelAll()
        nextId = 1
    }
}
