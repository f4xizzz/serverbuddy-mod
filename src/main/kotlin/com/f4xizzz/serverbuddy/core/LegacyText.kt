package com.f4xizzz.serverbuddy.core

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

/**
 * Texto do Minecraft → texto com códigos `§` (o formato antigo). Serve para os poucos lugares em que o cliente só
 * entende `§`, como os nomes da lista de jogadores que aparece ao passar o mouse na lista de servidores.
 */
object LegacyText {
    @JvmStatic
    fun of(component: Component): String {
        val out = StringBuilder()
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                if (text.isNotEmpty()) {
                    out.append("§r")
                    appendStyle(out, style)
                    out.append(text)
                }
                Optional.empty()
            },
            Style.EMPTY
        )
        return out.toString()
    }

    private fun appendStyle(out: StringBuilder, style: Style) {
        style.color?.let { color ->
            val name = color.serialize()
            if (name.startsWith("#") && name.length == 7) {
                out.append("§x")
                for (digit in name.substring(1)) out.append('§').append(digit.lowercaseChar())
            } else {
                ChatFormatting.getByName(name)?.let { out.append('§').append(it.char) }
            }
        }
        if (style.isBold) out.append("§l")
        if (style.isItalic) out.append("§o")
        if (style.isUnderlined) out.append("§n")
        if (style.isStrikethrough) out.append("§m")
        if (style.isObfuscated) out.append("§k")
    }
}
