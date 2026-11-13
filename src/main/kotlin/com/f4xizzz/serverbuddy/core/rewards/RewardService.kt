package com.f4xizzz.serverbuddy.core.rewards

import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.Integrations
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SoundSpec
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.storage.StorageService
import com.f4xizzz.serverbuddy.util.CommandRunner
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Entrega de recompensas para todos os módulos (trivia, missões, passe de batalha...). Cada tipo (`item`, `money`,
 * `command`...) é registrado aqui; os módulos podem acrescentar os seus com [register] (o passe de batalha cria o
 * `battlepass_xp`, as tags criam o `tag`). Os campos de cada tipo estão em [RewardDocs].
 */
object RewardService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Rewards")

    /**
     * Um tipo de recompensa. [give] entrega ao jogador (os [vars] já trazem `player` e `uuid`); [describe] diz como ela
     * aparece nos menus e nas mensagens (null = não aparece, como uma mensagem ou um som).
     */
    class Type(
        val id: String,
        /** O texto (MiniMessage) que descreve a recompensa nos menus, ou null se não há o que mostrar. */
        val describe: (Reward) -> String? = { null },
        val give: (player: ServerPlayer, reward: Reward, vars: Map<String, Any?>, source: String?) -> Unit
    )

    private val types = ConcurrentHashMap<String, Type>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun register(type: Type) {
        types[type.id.lowercase()] = type
    }

    fun isKnown(type: String): Boolean = types.containsKey(type.lowercase())

    /** Registra os tipos que vêm de fábrica. Chamado uma vez, no início. */
    fun init() {
        register(Type("item", ::describeItem) { player, reward, vars, source ->
            val stack = itemSpec(reward).build(vars)
            if (stack.isEmpty) LOGGER.warn("Item reward with an invalid id: {}", reward) else giveItem(player, stack, source)
        })
        register(Type("command") { player, reward, vars, _ ->
            val commands = reward.strings("command").map { Texts.fill(it, vars) }
            val server = player.server ?: return@Type
            if (reward.str("as")?.equals("player", ignoreCase = true) == true) {
                for (command in commands) server.commands.performPrefixedCommand(player.createCommandSourceStack(), command.removePrefix("/"))
            } else {
                CommandRunner.runAll(server, commands, player.gameProfile.name)
            }
        })
        register(Type("money", ::describeMoney) { player, reward, vars, _ ->
            val amount = reward.double("amount") ?: 0.0
            val economy = reward.str("economy")
            if (amount <= 0) return@Type
            if (!Economy.deposit(player, amount, economy)) {
                if (warned.add("money")) LOGGER.warn("Money reward but no economy mod installed (Impactor, CobbleDollars, BEconomy or Pebbles): nobody received it.")
            }
        })
        register(Type("xp", ::describeXp) { player, reward, _, _ ->
            val amount = reward.int("amount") ?: 0
            if (amount <= 0) return@Type
            if (reward.bool("levels") != false) player.giveExperienceLevels(amount) else player.giveExperiencePoints(amount)
        })
        register(Type("message") { player, reward, vars, _ -> reward.str("text")?.let { Texts.send(player, it, vars) } })
        register(Type("broadcast") { player, reward, vars, _ ->
            val text = reward.str("text") ?: return@Type
            player.server?.playerList?.broadcastSystemMessage(Texts.render(text, vars), false)
        })
        register(Type("title") { player, reward, vars, _ ->
            val title = reward.str("title")
            val subtitle = reward.str("subtitle")
            player.connection.send(ClientboundSetTitlesAnimationPacket(reward.int("fade_in") ?: 10, reward.int("stay") ?: 60, reward.int("fade_out") ?: 20))
            if (subtitle != null) player.connection.send(ClientboundSetSubtitleTextPacket(Texts.render(subtitle, vars)))
            if (title != null) player.connection.send(ClientboundSetTitleTextPacket(Texts.render(title, vars)))
        })
        register(Type("sound") { player, reward, _, _ ->
            val sound = reward.str("sound") ?: return@Type
            SoundSpec(sound, (reward.double("volume") ?: 1.0).toFloat(), (reward.double("pitch") ?: 1.0).toFloat()).play(player)
        })
        register(Type("pokemon", ::describePokemon) { player, reward, _, _ ->
            val spec = reward.str("spec") ?: return@Type
            if (!Integrations.cobblemon()) {
                if (warned.add("pokemon")) LOGGER.warn("Pokémon reward, but Cobblemon is not installed: nobody received it.")
                return@Type
            }
            CobblemonRewards.give(player, spec)
        })
    }

    // ---------------------------------------------------------------- entrega

    /**
     * Entrega [rewards] a [player]. Cada recompensa respeita o próprio `chance`. [vars] troca os %valores% dos textos e
     * comandos; [source] é só para o registro (ex.: `trivia:geral`). Devolve as que saíram.
     */
    @JvmStatic
    @JvmOverloads
    fun give(player: ServerPlayer, rewards: List<Reward>, vars: Map<String, Any?> = emptyMap(), source: String? = null): List<Reward> {
        val all = mapOf("player" to player.gameProfile.name, "uuid" to player.stringUUID) + vars
        val given = ArrayList<Reward>()
        for (reward in rewards) {
            if (reward.chance < 100.0 && Random.nextDouble() * 100.0 >= reward.chance) continue
            val type = types[reward.type]
            if (type == null) {
                if (warned.add("type:" + reward.type)) LOGGER.warn("Unknown reward type: '{}' (see the list at the top of the config). Reward: {}", reward.type, reward)
                continue
            }
            try {
                type.give(player, reward, all, source)
                given.add(reward)
            } catch (e: Exception) {
                LOGGER.error("Error delivering the reward $reward to ${player.gameProfile.name}", e)
            }
        }
        return given
    }

    /** Sorteia uma recompensa da lista, respeitando o `weight` de cada uma. null se a lista está vazia. */
    @JvmStatic
    fun pick(rewards: List<Reward>, random: Random = Random.Default): Reward? {
        val candidates = rewards.filter { it.weight > 0 }
        if (candidates.isEmpty()) return null
        var roll = random.nextInt(candidates.sumOf { it.weight })
        for (reward in candidates) {
            roll -= reward.weight
            if (roll < 0) return reward
        }
        return candidates.last()
    }

    /**
     * Põe o item no inventário. O que não couber vai para o /storage do jogador (se o módulo estiver ligado) ou cai no
     * chão aos pés dele. Assim nenhuma recompensa se perde por inventário cheio.
     */
    @JvmStatic
    @JvmOverloads
    fun giveItem(player: ServerPlayer, stack: ItemStack, source: String? = null) {
        val rest = stack.copy()
        val fit = StorageService.capacityFor(player, rest).coerceAtMost(rest.count)
        if (fit > 0) {
            val part = rest.copy()
            part.count = fit
            player.inventory.add(part)
            if (!part.isEmpty) player.drop(part, false)
            rest.shrink(fit)
        }
        if (rest.isEmpty) return
        if (StorageService.ready && StorageService.addItem(player.uuid, rest.copy(), source)) return
        player.drop(rest, false)
    }

    // ---------------------------------------------------------------- descrição

    /** Como a recompensa aparece para o jogador, em MiniMessage (null para as que não têm o que mostrar, como mensagem ou som). */
    @JvmStatic
    fun describeText(reward: Reward): String? = reward.label ?: types[reward.type]?.describe?.invoke(reward)

    /** Como a recompensa aparece para o jogador (null para as que não têm o que mostrar). */
    @JvmStatic
    fun describe(reward: Reward): Component? = describeText(reward)?.let { Texts.parse(it) }

    /** Um item que representa as recompensas nos menus: o primeiro item da lista ou, na falta dele, um ícone do tipo da primeira. */
    @JvmStatic
    fun icon(rewards: List<Reward>): ItemStack {
        for (reward in rewards) {
            if (reward.type != "item") continue
            val stack = itemSpec(reward).build()
            if (!stack.isEmpty) return stack
        }
        val id = when (rewards.firstOrNull()?.type) {
            "money" -> "minecraft:gold_ingot"
            "xp" -> "minecraft:experience_bottle"
            "pokemon" -> if (Integrations.cobblemon()) "cobblemon:poke_ball" else "minecraft:egg"
            "battlepass_xp" -> "minecraft:nether_star"
            "tag" -> "minecraft:name_tag"
            "command" -> "minecraft:command_block"
            else -> "minecraft:chest"
        }
        return ItemSpec(item = id).build()
    }

    /** As descrições juntas, em MiniMessage, separadas por vírgula ("3x Diamante, $500"). Vazio se nenhuma tem o que mostrar. */
    @JvmStatic
    fun describeAllText(rewards: List<Reward>): String = rewards.mapNotNull { describeText(it) }.joinToString(", ")

    /** As descrições juntas, separadas por vírgula ("3x Diamante, $500"). */
    @JvmStatic
    fun describeAll(rewards: List<Reward>): Component = Texts.parse(describeAllText(rewards))

    /** Avisa no log sobre recompensas mal escritas (tipo desconhecido, falta o campo principal). [where] diz de onde vieram. */
    @JvmStatic
    fun check(rewards: List<Reward>, where: String) {
        for (reward in rewards) {
            val problem = when {
                reward.type.isEmpty() -> "the \"type\" is missing"
                !isKnown(reward.type) && reward.type != "battlepass_xp" && reward.type != "tag" -> "unknown type '${reward.type}'"
                reward.type == "item" && reward.str("item") == null -> "the item needs the field \"item\""
                reward.type == "command" && reward.strings("command").isEmpty() -> "the command needs the field \"command\""
                reward.type == "money" && (reward.double("amount") ?: 0.0) <= 0 -> "money needs an \"amount\" greater than 0"
                reward.type == "pokemon" && reward.str("spec") == null -> "the Pokémon needs the field \"spec\""
                else -> null
            }
            if (problem != null) LOGGER.warn("{}: reward with a problem ({}): {}", where, problem, reward)
        }
    }

    // ---------------------------------------------------------------- descrições dos tipos de fábrica

    private fun itemSpec(reward: Reward): ItemSpec = SbGson.gson.fromJson(reward.json, ItemSpec::class.java)

    private fun describeItem(reward: Reward): String? {
        val spec = itemSpec(reward)
        val stack = spec.build()
        if (stack.isEmpty) return null
        // o nome que o administrador deu ao item, ou o nome do item no idioma de quem está vendo
        return "${stack.count}x ${spec.name ?: Texts.lang(stack.item.descriptionId)}"
    }

    private fun describeMoney(reward: Reward): String? {
        val amount = reward.double("amount") ?: return null
        return "$" + Economy.format(amount)
    }

    private fun describeXp(reward: Reward): String? {
        val amount = reward.int("amount") ?: return null
        return if (reward.bool("levels") != false) "$amount XP levels" else "$amount XP points"
    }

    private fun describePokemon(reward: Reward): String? {
        val spec = reward.str("spec") ?: return null
        val species = spec.trim().split(' ').firstOrNull { !it.contains('=') }?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: return null
        return "Pokémon: ${Texts.escape(species)}"
    }
}
