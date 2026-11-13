package com.f4xizzz.serverbuddy.storage

import com.f4xizzz.serverbuddy.kits.utils.FlexibleListAdaptorFactory
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Categorias do /storage (cada uma vira uma aba no menu). */
enum class StorageType(val key: String) {
    ITEM("items"),
    COMMAND("commands");

    companion object {
        fun byKey(key: String?): StorageType? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * Recompensa feita de comandos (rodam como console ao clicar). O título, o ícone e a descrição são o que o jogador vê no
 * menu; nos arquivos de config aceita as chaves em inglês ou em português (`title`/`titulo`, `icon`/`icone`,
 * `commands`/`comandos`).
 */
class CommandRewardSpec(
    @SerializedName("title", alternate = ["titulo"])
    val title: String? = null,
    @SerializedName("icon", alternate = ["icone"])
    val icon: String? = null,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val lore: List<String> = emptyList(),
    @SerializedName("commands", alternate = ["comandos"])
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val commands: List<String> = emptyList()
)

/** Uma recompensa guardada para um jogador. */
sealed class StorageEntry(
    val id: String,
    val owner: UUID,
    val createdAt: Long,
    val source: String?
) {
    abstract val type: StorageType

    class ItemEntry(
        id: String,
        owner: UUID,
        createdAt: Long,
        source: String?,
        val stack: ItemStack
    ) : StorageEntry(id, owner, createdAt, source) {
        override val type: StorageType get() = StorageType.ITEM
    }

    class CommandEntry(
        id: String,
        owner: UUID,
        createdAt: Long,
        source: String?,
        val spec: CommandRewardSpec
    ) : StorageEntry(id, owner, createdAt, source) {
        override val type: StorageType get() = StorageType.COMMAND
    }
}
