package com.f4xizzz.serverbuddy.core

import net.minecraft.commands.CommandSourceStack
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ganchos em todo comando executado no servidor (jogador, console, bloco de comando). Um módulo registra um ouvinte que recebe quem
 * rodou e o comando (sem a barra) e devolve true para BLOQUEAR a execução (usado pela cela, por exemplo). Quem só observa devolve false.
 */
object CommandHooks {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")
    private val listeners = CopyOnWriteArrayList<(CommandSourceStack, String) -> Boolean>()

    fun register(listener: (CommandSourceStack, String) -> Boolean) {
        listeners.add(listener)
    }

    /** Chamado pelo mixin de comandos. Devolve true se algum ouvinte quer bloquear. */
    @JvmStatic
    fun fire(source: CommandSourceStack, command: String): Boolean {
        var block = false
        for (listener in listeners) {
            try {
                if (listener(source, command)) block = true
            } catch (e: Throwable) {
                LOGGER.error("Error in a command listener", e)
            }
        }
        return block
    }
}
