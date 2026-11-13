package com.f4xizzz.serverbuddy.core

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.commands.CommandSourceStack

/**
 * Onde cada comando de módulo entra na árvore do Brigadier.
 *
 * - O primeiro nó com o nome do módulo (`clear`, `kits`, `crates`...) vira filho de `/serverbuddy`
 *   (e, por redirect, também de `/sb`).
 * - Qualquer outro nó (os comandos raiz antigos, como `/skiesclear` ou `/crates`) é registrado como comando
 *   raiz só se a compatibilidade `legacyCommands` estiver ligada.
 * - [mountRoot] registra comandos raiz que vêm da server configuration (ex.: `/kit`, `/shop`), sempre.
 */
object ModuleCommands {
    const val ROOT = "serverbuddy"

    @JvmStatic
    fun mount(dispatcher: CommandDispatcher<CommandSourceStack>, node: LiteralCommandNode<CommandSourceStack>, module: String) {
        val root = dispatcher.root.getChild(ROOT)
        if (root != null && node.name == module && root.getChild(module) == null) {
            root.addChild(node)
            return
        }
        if (LegacyCompat.commands) {
            dispatcher.root.addChild(node)
        }
    }

    /** Comando raiz definido pela server configuration: não depende da compatibilidade com os mods antigos. */
    @JvmStatic
    fun mountRoot(dispatcher: CommandDispatcher<CommandSourceStack>, node: LiteralCommandNode<CommandSourceStack>) {
        dispatcher.root.addChild(node)
    }

    /** Nó do módulo diretamente sob /serverbuddy (para módulos cujos comandos raiz vêm da config). */
    @JvmStatic
    fun mountModule(dispatcher: CommandDispatcher<CommandSourceStack>, node: LiteralCommandNode<CommandSourceStack>) {
        val root = dispatcher.root.getChild(ROOT) ?: return
        if (root.getChild(node.name) == null) root.addChild(node)
    }
}
