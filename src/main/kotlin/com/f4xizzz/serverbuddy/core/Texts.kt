package com.f4xizzz.serverbuddy.core

import net.kyori.adventure.platform.fabric.FabricAudiences
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.server.level.ServerPlayer

/**
 * Textos configuráveis dos módulos novos: MiniMessage (cores, gradientes, hover...) convertido para o texto do Minecraft.
 * Os valores entre %porcentagens% são trocados antes de interpretar o MiniMessage.
 */
object Texts {
    private val MINI: MiniMessage = MiniMessage.miniMessage()
    private val SERIALIZER = FabricAudiences.nonWrappingSerializer()

    // marcadores privados para encaixar componentes prontos (hover, item) no meio de um texto MiniMessage
    private const val OPEN = ''
    private const val CLOSE = ''

    /** MiniMessage -> texto do Minecraft, sem itálico por padrão (nomes e descrições de itens). */
    @JvmStatic
    fun parse(text: String?): Component {
        if (text.isNullOrEmpty()) return Component.empty()
        val adventure = MINI.deserialize(text).applyFallbackStyle({ it.decoration(TextDecoration.ITALIC, false) })
        return SERIALIZER.serialize(adventure)
    }

    /**
     * Só o que é seguro deixar um jogador escrever: cores, negrito/itálico/sublinhado/riscado/ofuscado, gradiente, arco-íris e reset.
     * Sem clique, hover, fonte, tradução, seletor, pontuação ou NBT (com eles um jogador faria uma placa ou mensagem rodar comandos
     * ou mostrar dados do servidor).
     */
    private val RESTRICTED: MiniMessage = MiniMessage.builder()
        .tags(
            TagResolver.resolver(
                StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset()
            )
        )
        .build()

    /** Como [parse], mas só com as tags de [RESTRICTED]: para texto escrito por jogadores (chat e placas). */
    @JvmStatic
    fun parseRestricted(text: String?): Component {
        if (text.isNullOrEmpty()) return Component.empty()
        val adventure = RESTRICTED.deserialize(text).applyFallbackStyle({ it.decoration(TextDecoration.ITALIC, false) })
        return SERIALIZER.serialize(adventure)
    }

    /** Troca cada %chave% do texto pelo valor correspondente. */
    @JvmStatic
    fun fill(text: String, vars: Map<String, Any?>): String {
        var out = text
        for ((key, value) in vars) out = out.replace("%$key%", value?.toString() ?: "")
        return out
    }

    /** `<lang:'chave'>`: texto traduzido no cliente (nomes de espécie, golpe, natureza...). */
    @JvmStatic
    fun lang(key: String): String = "<lang:'${key.replace("'", "")}'>"

    /** Escapa as tags de um texto digitado por jogadores (apelidos, nomes) antes de entrar num MiniMessage. */
    @JvmStatic
    fun escape(text: String): String = MINI.escapeTags(text)

    /**
     * Monta um texto: troca os %valores%, interpreta o MiniMessage e encaixa os componentes prontos de [parts]
     * (por exemplo o nome de um item com hover) no lugar de %chave%, herdando o estilo ao redor.
     */
    @JvmStatic
    @JvmOverloads
    fun render(template: String?, vars: Map<String, Any?> = emptyMap(), parts: Map<String, Component> = emptyMap()): Component {
        if (template.isNullOrEmpty()) return Component.empty()
        var text = fill(template, vars)
        val keys = parts.keys.toList()
        keys.forEachIndexed { index, key -> text = text.replace("%$key%", "$OPEN$index$CLOSE") }
        val parsed = parse(text)
        if (keys.isEmpty()) return parsed
        return replaceMarkers(parsed, keys.map { parts.getValue(it) })
    }

    private fun replaceMarkers(component: Component, parts: List<Component>): Component {
        val siblings = component.siblings.map { replaceMarkers(it, parts) }
        val contents = component.contents
        val result: MutableComponent
        if (contents is PlainTextContents && contents.text().indexOf(OPEN) >= 0) {
            result = Component.empty().setStyle(component.style)
            val text = contents.text()
            var i = 0
            while (i < text.length) {
                val start = text.indexOf(OPEN, i)
                if (start < 0) {
                    result.append(Component.literal(text.substring(i)))
                    break
                }
                if (start > i) result.append(Component.literal(text.substring(i, start)))
                val end = text.indexOf(CLOSE, start)
                if (end < 0) {
                    result.append(Component.literal(text.substring(start)))
                    break
                }
                val index = text.substring(start + 1, end).toIntOrNull()
                if (index != null && index in parts.indices) {
                    result.append(parts[index].copy())
                } else {
                    result.append(Component.literal(text.substring(start, end + 1)))
                }
                i = end + 1
            }
        } else {
            result = MutableComponent.create(contents).setStyle(component.style)
        }
        for (sibling in siblings) result.append(sibling)
        return result
    }

    /** Várias linhas juntas por quebra de linha (tooltip de hover). */
    @JvmStatic
    fun joinLines(lines: List<Component>): Component {
        val result = Component.empty()
        lines.forEachIndexed { index, line ->
            if (index > 0) result.append(Component.literal("\n"))
            result.append(line)
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    fun send(player: ServerPlayer, template: String?, vars: Map<String, Any?> = emptyMap(), parts: Map<String, Component> = emptyMap()) {
        if (template.isNullOrBlank()) return
        player.sendSystemMessage(render(template, vars, parts))
    }

    @JvmStatic
    @JvmOverloads
    fun reply(source: CommandSourceStack, template: String?, vars: Map<String, Any?> = emptyMap(), parts: Map<String, Component> = emptyMap(), broadcast: Boolean = false) {
        if (template.isNullOrBlank()) return
        val text = render(template, vars, parts)
        source.sendSuccess({ text }, broadcast)
    }

    @JvmStatic
    fun fail(source: CommandSourceStack, template: String?, vars: Map<String, Any?> = emptyMap()) {
        if (template.isNullOrBlank()) return
        source.sendFailure(render(template, vars))
    }
}
