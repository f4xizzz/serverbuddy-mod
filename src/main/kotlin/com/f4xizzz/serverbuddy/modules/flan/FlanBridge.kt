package com.f4xizzz.serverbuddy.modules.flan

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Modules
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.tags.TagsService
import com.google.gson.reflect.TypeToken
import io.github.flemmli97.flan.api.ClaimHandler
import io.github.flemmli97.flan.api.data.IPermissionContainer
import io.github.flemmli97.flan.api.fabric.PermissionCheckEvent
import io.github.flemmli97.flan.api.permission.BuiltinPermission
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Uma claim do Flan. Só a API pública está no jar de compilação, então id, dono e claim-mãe são lidos por reflexão. */
class ClaimRef(val container: IPermissionContainer) {
    val id: UUID get() = FlanBridge.call(container, "getClaimID") as UUID
    val owner: UUID? get() = FlanBridge.call(container, "getOwner") as UUID?
    val parent: ClaimRef? get() = (FlanBridge.call(container, "parentClaim") as IPermissionContainer?)?.let { ClaimRef(it) }

    /** A claim principal (a própria, se não é uma subclaim). */
    val main: ClaimRef get() = parent ?: this

    /** A pessoa pode editar as regras desta claim? (dono, ou quem o Flan deixa editar as permissões) */
    fun canEdit(player: ServerPlayer): Boolean =
        container.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition()) ||
            (parent?.container?.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition()) ?: false)
}

/** Regras de cada claim (Flan/restrictions.json) e a decisão de entrada. */
object FlanStore {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Flan")
    private val type = object : TypeToken<LinkedHashMap<String, ClaimRestriction>>() {}.type
    private val map = ConcurrentHashMap<String, ClaimRestriction>()

    private fun file() = File(ConfigFiles.dir("Flan"), "restrictions.json")

    fun load() {
        map.clear()
        val f = file()
        if (!f.exists()) return
        try {
            val loaded: Map<String, ClaimRestriction>? = f.reader(Charsets.UTF_8).use { SbGson.pretty.fromJson(it, type) }
            loaded?.forEach { (id, r) -> if (r != null && !r.isEmpty) map[id.lowercase()] = r }
        } catch (e: Exception) {
            LOGGER.error("Could not read Flan/restrictions.json (rules remain empty until the file is fixed)", e)
        }
    }

    fun get(id: UUID): ClaimRestriction? = map[id.toString().lowercase()]

    fun isEmpty(): Boolean = map.isEmpty()

    /** Alguma claim dá tag ou permissão a quem entra? (a checagem de quem entrou ou saiu só roda se sim) */
    fun anyGrants(): Boolean = map.values.any { it.hasGrants }

    /** A regra da claim para editar (cria vazia se não existe). Chame [save] depois de mudar. */
    fun edit(id: UUID): ClaimRestriction = map.computeIfAbsent(id.toString().lowercase()) { ClaimRestriction() }

    fun save() {
        map.entries.removeIf { it.value.isEmpty }
        try {
            file().parentFile.mkdirs()
            file().writeText(SbGson.pretty.toJson(LinkedHashMap(map)), Charsets.UTF_8)
        } catch (e: Exception) {
            LOGGER.error("Could not save Flan/restrictions.json", e)
        }
    }

    fun remove(id: UUID) {
        if (map.remove(id.toString().lowercase()) != null) save()
    }
}

object FlanBridge {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy/Flan")
    private const val CLAIM_CLASS = "io.github.flemmli97.flan.claim.Claim"

    /** Quem pode escolher o que uma claim dá a quem entra (padrão: operadores nível 3). */
    const val GRANTS_PERMISSION = "serverbuddy.flan.grants"

    fun canEditGrants(player: ServerPlayer): Boolean = SbPermissions.check(player, GRANTS_PERMISSION, 3)
    private val methods = ConcurrentHashMap<String, Method?>()
    private val tagCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val warnedAt = ConcurrentHashMap<UUID, Long>()

    @Volatile
    var config: FlanConfig = FlanConfig()
        private set

    fun load() {
        config = ConfigFiles.load("Flan", "config.json", FlanConfig::class.java) { FlanConfig() }
        FlanStore.load()
    }

    fun call(target: Any, name: String): Any? {
        val method = methods.computeIfAbsent(target.javaClass.name + "#" + name) {
            try {
                target.javaClass.getMethod(name).also { it.isAccessible = true }
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("Flan changed: could not find {}() in {}.", name, target.javaClass.name)
                null
            }
        } ?: return null
        return method.invoke(target)
    }

    /** A claim (ou subclaim) na posição, ou null se ali não há claim. */
    fun claimAt(player: ServerPlayer, pos: BlockPos): ClaimRef? {
        val container = ClaimHandler.getPermissionStorage(player.serverLevel()).getForPermissionCheck(pos)
        if (container.javaClass.name != CLAIM_CLASS) return null
        val sub = try {
            container.javaClass.getMethod("getSubClaim", BlockPos::class.java).invoke(container, pos) as IPermissionContainer?
        } catch (e: ReflectiveOperationException) {
            null
        }
        return ClaimRef(sub ?: container)
    }

    /** A tag [tag] vale para o jogador? (tag do /tags ou do scoreboard) Guarda o resultado por alguns segundos: esta checagem roda todo tick. */
    fun hasTag(player: ServerPlayer, tag: String): Boolean {
        if (config.tags.scoreboardTags && player.tags.any { it.equals(tag, ignoreCase = true) }) return true
        if (!Modules.isActive("tags")) return false
        val key = player.stringUUID + "/" + tag
        val now = System.currentTimeMillis()
        tagCache[key]?.let { if (now - it.second < 3000) return it.first }
        val view = TagsService.find(tag)
        val result = view != null && TagsService.owns(player, view)
        tagCache[key] = result to now
        return result
    }

    private fun bypasses(player: ServerPlayer, claim: ClaimRef): Boolean =
        player.uuid == claim.owner || SbPermissions.check(player, "serverbuddy.flan.bypass", 3) || claim.canEdit(player)

    /** A regra de entrada que barra o jogador nesta claim (ou na claim-mãe), ou null se ele pode ficar. */
    fun deniedBy(player: ServerPlayer, ref: ClaimRef): ClaimRestriction? {
        for (claim in listOfNotNull(ref, ref.parent)) {
            val restriction = FlanStore.get(claim.id) ?: continue
            if (!restriction.hasEntryRules || bypasses(player, claim)) continue
            if (!FlanAccess.canEnter(restriction, { SbPermissions.check(player, it, false) }, { hasTag(player, it) })) return restriction
        }
        return null
    }

    /** Resposta do Flan para "pode ficar aqui?": FAIL se a claim tem regras e o jogador não passa; PASS nos outros casos. */
    fun checkStay(player: ServerPlayer, pos: BlockPos): InteractionResult {
        if (FlanStore.isEmpty() || player.javaClass != ServerPlayer::class.java) return InteractionResult.PASS
        val ref = claimAt(player, pos) ?: return InteractionResult.PASS
        val denied = deniedBy(player, ref) ?: return InteractionResult.PASS
        notifyDenied(player, denied)
        return InteractionResult.FAIL
    }

    private fun notifyDenied(player: ServerPlayer, restriction: ClaimRestriction) {
        val now = System.currentTimeMillis()
        if (now - (warnedAt[player.uuid] ?: 0) < 3000) return
        warnedAt[player.uuid] = now
        player.displayClientMessage(Texts.render(config.messages.denied, mapOf("rule" to Texts.escape(FlanAccess.describe(restriction)))), true)
    }

    fun register() {
        PermissionCheckEvent.CHECK.register { player, permission, pos ->
            if (player == null || permission != BuiltinPermission.CANSTAY) InteractionResult.PASS else checkStay(player, pos)
        }
    }
}
