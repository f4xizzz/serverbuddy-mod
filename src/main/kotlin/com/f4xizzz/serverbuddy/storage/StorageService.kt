package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.redeem.RedeemCode
import com.f4xizzz.serverbuddy.util.CommandRunner
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.nbt.TagParser
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Regras do /storage: guardar recompensas (itens e comandos), resgatar e avisar o jogador.
 * Outros módulos podem usar [addItem] e [addCommands] para mandar prêmios para cá.
 */
object StorageService {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Storage")
    private const val KEY_STACK = "stack"

    /** Só fica true depois que a tabela existe (o banco abre no início do servidor). */
    @Volatile
    var ready: Boolean = false

    enum class ClaimResult { SUCCESS, PARTIAL, INVENTORY_FULL, NOT_FOUND, ERROR }

    // ---------------------------------------------------------------- guardar

    @JvmStatic
    @JvmOverloads
    fun addItem(owner: UUID, stack: ItemStack, source: String? = null, notify: Boolean = true): Boolean {
        if (stack.isEmpty) return false
        val data = encodeItemData(stack) ?: return false
        val ok = StorageRepository.insert(StorageRepository.Row(newId(), owner, StorageType.ITEM.name, data, source, now()))
        if (ok && notify) notifyReceived(owner, 1)
        return ok
    }

    @JvmStatic
    @JvmOverloads
    fun addCommands(owner: UUID, spec: CommandRewardSpec, source: String? = null, notify: Boolean = true): Boolean {
        if (spec.commands.isEmpty()) return false
        val ok = StorageRepository.insert(StorageRepository.Row(newId(), owner, StorageType.COMMAND.name, SbGson.gson.toJson(spec), source, now()))
        if (ok && notify) notifyReceived(owner, 1)
        return ok
    }

    /**
     * Resgate de código (/redeem): manda os itens e os comandos do código para o storage do jogador.
     * Devolve false se o storage está desligado para o redeem (o resgate entrega tudo na hora, como antes).
     */
    @JvmStatic
    fun deliverRedeem(player: ServerPlayer, codeName: String, def: RedeemCode): Boolean {
        val cfg = StorageConfigManager.config
        val vars = mapOf("code" to codeName, "player" to player.gameProfile.name)
        if (!ready || !cfg.redeemToStorage) {
            // sem storage: os itens do código vão direto para o inventário; quem chamou roda os comandos
            for (spec in def.itens.orEmpty()) {
                val stack = spec.build(vars)
                if (!stack.isEmpty) giveDirect(player, stack)
            }
            return false
        }

        val source = "redeem:$codeName"
        var created = 0

        // o código já foi marcado como usado: se o banco falhar, a recompensa sai direto em vez de se perder
        for (spec in def.itens.orEmpty()) {
            val stack = spec.build(vars)
            if (stack.isEmpty) continue
            if (addItem(player.uuid, stack, source, notify = false)) {
                created++
            } else {
                LOGGER.warn("[Storage] Could not store an item of the code '{}' of {}: giving it directly.", codeName, player.gameProfile.name)
                giveDirect(player, stack)
            }
        }

        val commands = def.comandos.orEmpty().filter { it.isNotBlank() }
        if (commands.isNotEmpty()) {
            val show = def.exibicao
            val spec = CommandRewardSpec(
                title = Texts.fill(show?.title ?: cfg.messages.redeemTitle, vars),
                icon = show?.icon,
                lore = show?.lore ?: emptyList(),
                commands = commands
            )
            if (addCommands(player.uuid, spec, source, notify = false)) {
                created++
            } else {
                LOGGER.warn("[Storage] Could not store the commands of the code '{}' of {}: running them directly.", codeName, player.gameProfile.name)
                runDirect(player, commands)
            }
        }

        for (extra in def.entradas.orEmpty()) {
            val extraCommands = extra.commands.filter { it.isNotBlank() }
            if (extraCommands.isEmpty()) continue
            val spec = CommandRewardSpec(
                title = extra.title?.let { Texts.fill(it, vars) },
                icon = extra.icon,
                lore = extra.lore,
                commands = extraCommands
            )
            if (addCommands(player.uuid, spec, source, notify = false)) {
                created++
            } else {
                LOGGER.warn("[Storage] Could not store a reward of the code '{}' of {}: running it directly.", codeName, player.gameProfile.name)
                runDirect(player, extraCommands)
            }
        }

        if (created > 0) notifyReceived(player.uuid, created)
        return true
    }

    private fun giveDirect(player: ServerPlayer, stack: ItemStack) {
        player.inventory.add(stack)
        if (!stack.isEmpty) player.drop(stack, false)
    }

    private fun runDirect(player: ServerPlayer, commands: List<String>) {
        val server = player.server ?: return
        CommandRunner.runAll(server, commands, player.gameProfile.name)
    }

    // ---------------------------------------------------------------- ler

    @JvmStatic
    fun entries(owner: UUID): List<StorageEntry> = StorageRepository.list(owner).mapNotNull { decode(it) }

    @JvmStatic
    fun entriesByType(owner: UUID): Map<StorageType, List<StorageEntry>> = entries(owner).groupBy { it.type }

    @JvmStatic
    fun total(owner: UUID): Int = StorageRepository.countByType(owner).values.sum()

    @JvmStatic
    fun totals(owner: UUID): Map<StorageType, Int> {
        val raw = StorageRepository.countByType(owner)
        return StorageType.entries.associateWith { raw[it.name] ?: 0 }
    }

    @JvmStatic
    @JvmOverloads
    fun clear(owner: UUID, type: StorageType? = null): Int = StorageRepository.deleteAll(owner, type?.name)

    // ---------------------------------------------------------------- resgatar

    @JvmStatic
    @JvmOverloads
    fun claim(player: ServerPlayer, entryId: String, silent: Boolean = false): ClaimResult {
        val entry = entries(player.uuid).firstOrNull { it.id == entryId }
        if (entry == null) {
            if (!silent) Texts.send(player, StorageConfigManager.config.messages.entryMissing)
            return ClaimResult.NOT_FOUND
        }
        return claimEntry(player, entry, silent)
    }

    /** Resgata tudo da aba, parando se o inventário encher. Devolve quantas recompensas saíram inteiras. */
    @JvmStatic
    fun claimAll(player: ServerPlayer, type: StorageType): Int {
        val cfg = StorageConfigManager.config
        val list = entries(player.uuid).filter { it.type == type }
        if (list.isEmpty()) {
            Texts.send(player, cfg.messages.claimedNone)
            return 0
        }

        var done = 0
        for (entry in list) {
            when (claimEntry(player, entry, silent = true)) {
                ClaimResult.SUCCESS -> done++
                ClaimResult.PARTIAL, ClaimResult.INVENTORY_FULL -> {
                    Texts.send(player, cfg.messages.inventoryFull)
                    break
                }
                else -> {}
            }
        }
        if (done > 0) Texts.send(player, cfg.messages.claimedAll, mapOf("amount" to done))
        return done
    }

    private fun claimEntry(player: ServerPlayer, entry: StorageEntry, silent: Boolean): ClaimResult = try {
        when (entry) {
            is StorageEntry.ItemEntry -> claimItem(player, entry, silent)
            is StorageEntry.CommandEntry -> claimCommand(player, entry, silent)
        }
    } catch (e: Exception) {
        LOGGER.error("Error claiming the reward ${entry.id} of ${player.gameProfile.name}!", e)
        if (!silent) Texts.send(player, StorageConfigManager.config.messages.error)
        ClaimResult.ERROR
    }

    private fun claimItem(player: ServerPlayer, entry: StorageEntry.ItemEntry, silent: Boolean): ClaimResult {
        val cfg = StorageConfigManager.config
        val stack = entry.stack
        val total = stack.count
        val fit = capacityFor(player, stack)
        if (fit <= 0) {
            if (!silent) Texts.send(player, cfg.messages.inventoryFull)
            return ClaimResult.INVENTORY_FULL
        }

        val give = min(fit, total)
        // o banco muda ANTES de o item sair: se algo falhar, ninguém recebe em dobro
        val stored = if (give == total) {
            StorageRepository.delete(entry.id, entry.owner)
        } else {
            val rest = stack.copy()
            rest.count = total - give
            val data = encodeItemData(rest)
            data != null && StorageRepository.updateData(entry.id, entry.owner, data)
        }
        if (!stored) {
            if (!silent) Texts.send(player, cfg.messages.entryMissing)
            return ClaimResult.NOT_FOUND
        }

        val toGive = stack.copy()
        toGive.count = give
        val name = stack.hoverName
        player.inventory.add(toGive)
        if (!toGive.isEmpty) player.drop(toGive, false)

        LOGGER.info("[Storage] {} resgatou {}x {} (origem: {}).", player.gameProfile.name, give, stack.item, entry.source ?: "-")
        if (give == total) {
            if (!silent) Texts.send(player, cfg.messages.claimedItem, mapOf("amount" to give), mapOf("item" to name))
            return ClaimResult.SUCCESS
        }
        if (!silent) Texts.send(player, cfg.messages.partialClaim, mapOf("given" to give, "total" to total))
        return ClaimResult.PARTIAL
    }

    private fun claimCommand(player: ServerPlayer, entry: StorageEntry.CommandEntry, silent: Boolean): ClaimResult {
        val cfg = StorageConfigManager.config
        val server = player.server ?: return ClaimResult.ERROR
        // apaga primeiro: o comando só roda se a recompensa ainda existia
        if (!StorageRepository.delete(entry.id, entry.owner)) {
            if (!silent) Texts.send(player, cfg.messages.entryMissing)
            return ClaimResult.NOT_FOUND
        }
        CommandRunner.runAll(server, entry.spec.commands, player.gameProfile.name)
        LOGGER.info("[Storage] {} claimed a command reward (source: {}).", player.gameProfile.name, entry.source ?: "-")
        if (!silent) Texts.send(player, cfg.messages.claimedCommand, mapOf("title" to (entry.spec.title ?: cfg.commandEntry.defaultTitle)))
        return ClaimResult.SUCCESS
    }

    /** Quanto desse item cabe no inventário do jogador (soma os espaços vazios e as pilhas que ainda aceitam mais). */
    @JvmStatic
    fun capacityFor(player: ServerPlayer, stack: ItemStack): Int {
        var capacity = 0
        val max = stack.maxStackSize
        for (slot in player.inventory.items) {
            capacity += when {
                slot.isEmpty -> max
                ItemStack.isSameItemSameComponents(slot, stack) -> (slot.maxStackSize - slot.count).coerceAtLeast(0)
                else -> 0
            }
        }
        return capacity
    }

    // ---------------------------------------------------------------- avisos

    @JvmStatic
    fun onJoin(player: ServerPlayer) {
        val cfg = StorageConfigManager.config
        if (!ready || !cfg.notifyOnJoin) return
        val amount = total(player.uuid)
        if (amount > 0) Texts.send(player, cfg.messages.pending, mapOf("amount" to amount))
    }

    private fun notifyReceived(owner: UUID, amount: Int) {
        val cfg = StorageConfigManager.config
        if (!cfg.notifyOnReceive) return
        val player = ServerHolder.server?.playerList?.getPlayer(owner) ?: return
        Texts.send(player, cfg.messages.received, mapOf("amount" to amount))
    }

    // ---------------------------------------------------------------- (de)serialização

    private fun newId(): String = UUID.randomUUID().toString()

    private val lastStamp = AtomicLong(0)

    /** Carimbo de criação em milissegundos e sempre crescente, para as recompensas aparecerem na ordem em que chegaram. */
    private fun now(): Long = lastStamp.updateAndGet { previous -> max(previous + 1, System.currentTimeMillis()) }

    private fun encodeItemData(stack: ItemStack): String? {
        val tag = ItemStack.CODEC.encodeStart(ServerHolder.nbtOps(), stack).result().orElse(null)
        if (tag == null) {
            LOGGER.error("Could not save the item {} in the storage.", stack)
            return null
        }
        val obj = JsonObject()
        obj.addProperty(KEY_STACK, tag.toString())
        return obj.toString()
    }

    private fun decodeItemData(data: String): ItemStack? = try {
        val snbt = JsonParser.parseString(data).asJsonObject.get(KEY_STACK).asString
        ItemStack.CODEC.parse(ServerHolder.nbtOps(), TagParser.parseTag(snbt)).result().orElse(null)
    } catch (e: Exception) {
        LOGGER.error("Storage item with invalid data: {}", data, e)
        null
    }

    private fun decode(row: StorageRepository.Row): StorageEntry? = when (row.type) {
        StorageType.ITEM.name -> decodeItemData(row.data)?.takeIf { !it.isEmpty }
            ?.let { StorageEntry.ItemEntry(row.id, row.owner, row.createdAt, row.source, it) }
        StorageType.COMMAND.name -> runCatching { SbGson.gson.fromJson(row.data, CommandRewardSpec::class.java) }.getOrNull()
            ?.let { StorageEntry.CommandEntry(row.id, row.owner, row.createdAt, row.source, it) }
        else -> null
    }
}
