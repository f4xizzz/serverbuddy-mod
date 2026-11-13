package com.f4xizzz.serverbuddy.cobblemon.features.tokens

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Cria e reconhece os itens das fichas. O id da ficha fica no dado customizado do item (`serverbuddy_token`), então
 * mudar nome, descrição ou textura na config não quebra as fichas que os jogadores já têm.
 */
object TokenItems {
    const val KEY = "serverbuddy_token"

    fun create(id: String, def: TokenDef, amount: Int = 1): ItemStack {
        val stack = def.item.build(mapOf("token" to id), amount)
        if (stack.isEmpty) return stack
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putString(KEY, id) }
        return stack
    }

    /** O id da ficha, ou null se o item não é uma ficha. */
    fun identify(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        if (!data.contains(KEY)) return null
        return data.copyTag().getString(KEY).ifEmpty { null }
    }
}
