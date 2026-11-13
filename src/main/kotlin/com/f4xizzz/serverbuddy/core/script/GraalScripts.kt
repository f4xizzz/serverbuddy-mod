package com.f4xizzz.serverbuddy.core.script

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine

/**
 * Scripts JavaScript (requisitos "javascript" das GUIs e dos Kits) via GraalVM.
 *
 * O ServerBuddy NÃO embute o GraalVM: ele só usa o que outro mod já trouxe (o Cobblemon vem com o GraalVM JS
 * completo). Sem isso, [available] é falso e os requisitos JavaScript são ignorados com um aviso, enquanto o
 * resto dos módulos funciona normalmente. Nada fora desta classe pode tocar em `org.graalvm` sem checar [available].
 */
object GraalScripts {
    private val present: Boolean by lazy {
        try {
            Class.forName("org.graalvm.polyglot.Engine")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun available(): Boolean = present

    /**
     * Avalia [expression] como JavaScript. Retorna o valor booleano, ou null se a expressão não devolveu um boolean.
     * Lança [IllegalStateException] se o GraalVM não está instalado.
     */
    @JvmStatic
    fun evalBoolean(expression: String): Boolean? {
        check(present) { "GraalVM JS not found (it ships with Cobblemon)" }
        return GraalImpl.evalBoolean(expression)
    }
}

// Só é carregada quando o GraalVM existe; nunca use esta classe sem checar GraalScripts.available().
internal object GraalImpl {
    private val engine: Engine by lazy {
        Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build()
    }

    fun evalBoolean(expression: String): Boolean? {
        Context.newBuilder().engine(engine).build().use { context ->
            val result = context.eval("js", expression)
            return if (result.isBoolean) result.asBoolean() else null
        }
    }
}
