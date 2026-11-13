package com.f4xizzz.serverbuddy.core.config

/**
 * Explicação de uma opção de config. O texto vira um comentário (`//`) em cima da opção quando o arquivo é criado
 * (veja [ConfigDocs]). Use `\n` para quebrar linha. Na classe raiz da config, vira o cabeçalho do arquivo.
 *
 * ```
 * class MotdConfig(
 *     @Doc("Liga ou desliga o módulo.")
 *     val enabled: Boolean = true
 * )
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Doc(val value: String)
