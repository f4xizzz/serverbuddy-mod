package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.kits.utils.FlexibleListAdaptorFactory
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit as McUnit
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.ItemLore
import org.slf4j.LoggerFactory

/**
 * Definição de item usada nos módulos novos (fichas, storage, redeem, ícones de menu), no mesmo estilo dos outros
 * menus do ServerBuddy: tudo editável na config, textos em MiniMessage.
 *
 * - `stackable`: `true` (padrão) deixa o item juntar com outros iguais; `false` nunca junta (tamanho máximo 1).
 * - `hide_tooltip` esconde a descrição inteira; `hide_additional_tooltip` esconde só as linhas extras
 *   (encantamentos, atributos, etc.).
 * - `components` (ou `nbt`): componentes extras do item, como nos kits e nos menus.
 */
class ItemSpec(
    val item: String = "minecraft:paper",
    val amount: Int = 1,
    val name: String? = null,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val lore: List<String> = emptyList(),
    @SerializedName("custom_model_data")
    val customModelData: Int? = null,
    val stackable: Boolean = true,
    @SerializedName("hide_tooltip")
    val hideTooltip: Boolean = false,
    @SerializedName("hide_additional_tooltip")
    val hideAdditionalTooltip: Boolean = false,
    val glint: Boolean? = null,
    @SerializedName("components", alternate = ["nbt"])
    val components: CompoundTag? = null
) {
    /** Cópia com outros textos/item/quantidade (o resto vem desta definição). */
    fun copy(
        item: String = this.item,
        amount: Int = this.amount,
        name: String? = this.name,
        lore: List<String> = this.lore,
        glint: Boolean? = this.glint
    ): ItemSpec = ItemSpec(
        item, amount, name, lore, customModelData, stackable, hideTooltip, hideAdditionalTooltip, glint, components
    )

    /**
     * Monta o item. [vars] troca os %valores% do nome e da descrição; [count] (se informado) vale mais que `amount`.
     * Devolve [ItemStack.EMPTY] (e avisa no log) se o id do item não existir.
     */
    fun build(vars: Map<String, Any?> = emptyMap(), count: Int? = null): ItemStack {
        val id = ResourceLocation.tryParse(item.trim())
        val base = id?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
        if (base == null || base == Items.AIR) {
            LOGGER.error("Item '{}' does not exist: check the id in the config (e.g. minecraft:paper).", item)
            return ItemStack.EMPTY
        }

        val stack = ItemStack(base, (count ?: amount).coerceIn(1, 99))

        if (components != null) {
            DataComponentPatch.CODEC.decode(ServerHolder.nbtOps(), components).result().ifPresent { result ->
                stack.applyComponents(result.first)
            }
        }

        val patch = DataComponentPatch.builder()
        if (customModelData != null) patch.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(customModelData))
        if (name != null) patch.set(DataComponents.ITEM_NAME, Texts.parse(Texts.fill(name, vars)))
        if (lore.isNotEmpty()) {
            val lines = lore.flatMap { Texts.fill(it, vars).split("\n") }
            patch.set(DataComponents.LORE, ItemLore(lines.map { Texts.parse(it) }))
        }
        if (!stackable) patch.set(DataComponents.MAX_STACK_SIZE, 1)
        if (hideTooltip) patch.set(DataComponents.HIDE_TOOLTIP, McUnit.INSTANCE)
        if (hideAdditionalTooltip) patch.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, McUnit.INSTANCE)
        if (glint != null) patch.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint)
        stack.applyComponents(patch.build())
        return stack
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("ServerBuddy")
    }
}
