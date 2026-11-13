package com.f4xizzz.serverbuddy.core

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tirar do caminho o comando de outro mod com o mesmo nome (política SERVERBUDDY) mexe nos mapas internos do Brigadier. */
class SbCommandsTest {
    @Suppress("UNCHECKED_CAST")
    private fun dispatcher() = CommandDispatcher<Any?>() as CommandDispatcher<CommandSourceStack>

    private fun literal(name: String, result: Int): LiteralArgumentBuilder<CommandSourceStack> =
        LiteralArgumentBuilder.literal<CommandSourceStack>(name).executes { result }

    @Test
    fun `tira o comando e deixa registrar outro com o mesmo nome`() {
        val d = dispatcher()
        d.register(literal("storage", 1))
        d.register(literal("outro", 5))
        assertEquals(1, d.execute("storage", null))

        assertTrue(SbCommands.removeRoot(d, "storage"))
        assertNull(d.root.getChild("storage"))
        assertFalse(d.root.children.any { it.name == "storage" })
        // o resto da árvore continua
        assertEquals(5, d.execute("outro", null))

        // agora o nome está livre para o ServerBuddy
        d.register(literal("storage", 2))
        assertEquals(2, d.execute("storage", null))
    }

    @Test
    fun `comando que nao existe nao e problema`() {
        assertFalse(SbCommands.removeRoot(dispatcher(), "nada"))
    }

    @Test
    fun `o apelido de um comando sem argumentos tambem executa`() {
        val d = dispatcher()
        val back = d.register(literal("back", 7))
        // o redirect sozinho NÃO leva a ação: era o que deixava /return respondendo "comando desconhecido"
        d.register(LiteralArgumentBuilder.literal<CommandSourceStack>("sozinho").redirect(back))
        org.junit.jupiter.api.Assertions.assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException::class.java) { d.execute("sozinho", null) }

        SbCommands.alias(d, "return", back)
        assertEquals(7, d.execute("return", null))
        assertEquals(7, d.execute("back", null))
    }

    @Test
    fun `um apelido nao se mistura com um comando que ja existe`() {
        val d = dispatcher()
        val back = d.register(literal("back", 7))
        d.register(literal("return", 3).then(literal("fail", 4))) // como o /return do Minecraft
        assertNull(SbCommands.alias(d, "return", back))
        // o comando dos outros continua exatamente como era
        assertEquals(3, d.execute("return", null))
        assertEquals(4, d.execute("return fail", null))
        assertNull(d.root.getChild("return").redirect)
    }

    @Test
    fun `o apelido tambem leva os argumentos do comando`() {
        val d = dispatcher()
        val msg = d.register(
            LiteralArgumentBuilder.literal<CommandSourceStack>("msg").executes { 1 }
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<CommandSourceStack, String>("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes { 2 })
        )
        SbCommands.alias(d, "tell", msg)
        assertEquals(1, d.execute("tell", null))
        assertEquals(2, d.execute("tell oi tudo bem", null))
    }

    // a fonte do comando é nula nestes testes: o predicado precisa aceitar nulo (um lambda do Kotlin recusaria)
    @Suppress("UNCHECKED_CAST")
    private fun always(answer: Boolean): java.util.function.Predicate<CommandSourceStack> =
        object : java.util.function.Predicate<CommandSourceStack?> {
            override fun test(t: CommandSourceStack?): Boolean = answer
        } as java.util.function.Predicate<CommandSourceStack>

    @Test
    fun `o apelido usa a permissao do destino ou a que for dada`() {
        val d = dispatcher()
        val secret = d.register(literal("secreto", 1).requires(always(false)))
        val alias = SbCommands.alias(d, "s", secret)!!
        assertFalse(alias.requirement.test(null))
        val open = SbCommands.alias(d, "aberto", secret, always(true))!!
        assertTrue(open.requirement.test(null))
    }
}
