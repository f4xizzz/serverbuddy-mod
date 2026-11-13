# Andamento da fase 2 (atualizado em 11/11/2026)

Pedido: levar para o ServerBuddy as mecânicas de Solstice, CobbleMissions (+ missões vanilla), SkiesTrivia, Great
BattlePass, Cobblemon Blacklist e MiniMOTD, mais GUI de tags, Flan, CobbleUtils, GUI sem inventário, configs
comentadas. Relatório da análise: `D:\mods\ServerBuddy-pesquisa\analise-fase2.md`. Guia de uso: `docs/MODULOS.md`;
como o código se organiza: `docs/ARQUITETURA.md`.

## Pronto e testado (bot em D:\mods\ServerBuddy-pesquisa\bot-tests + testes unitários)

| Item | Onde | Teste |
|---|---|---|
| Configs comentadas (`@Doc` → JSON com `//`), `/sb config check\|update`, `/sb modules`, `modules.conf` (liga/desliga módulos, nomes de comando, convivência com CobbleUtils/CobblemonExtras), enums sem diferença de maiúsculas | `core/config`, `core/ModuleSettings.kt`, `core/SbGson.kt` | unitários + ao vivo (`config update` completou o `modules.conf` com `.bak`) |
| GUI sem inventário embaixo (`show_player_inventory`, `player_inventory_filler`) | `core/SbGui.kt`, `guis/gui/ChestGUI.kt` | `tags-test` (o efeito visual no cliente não foi visto) |
| MOTD com perfis (whitelist, manutenção, lotado, horário, data, dia, evento), ícones, jogadores falsos | `modules/motd`, mixins `mixin/motd` | `motd-test` + unitários |
| Tags em GUI de baú, LuckPerms, equipe, GreatCosmetics (reflexão) | `modules/tags` | `tags-test` (LuckPerms/GreatCosmetics só por código) |
| Blacklist de golpes (contextos por tag/permissão/dimensão/PvP/horário, import do mod original) | `modules/blacklist`, mixin `mixin/blacklist` | `blacklist-test` + unitários; **recusa dentro de batalha real não testada** (mixin confirmado no bytecode) |
| Trivia (pools, geradores incl. Cobblemon, dicas, ranking, sequência, silenciar, import do SkiesTrivia) | `modules/trivia` | `trivia-test` + unitários |
| Recompensas compartilhadas (`item`, `command`, `money`, `xp`, `message`, `broadcast`, `title`, `sound`, `pokemon`, `battlepass_xp`, `tag`) | `core/rewards` | usadas nos testes acima |
| Missões vanilla (estatísticas do jogo) + Cobblemon como brinde; rotação diária/semanal; pré-requisitos; GUI | `modules/missions` | `missions-test` + unitários (eventos do Cobblemon só por código; a captura da Shiny Ball completou uma missão de verdade) |
| **Missões globais** (uma missão por vez para o servidor, o primeiro a completar leva o prêmio, tempo limite, `ui`/`auto`, `online`/`all`, estado no banco, `/missions global start\|stop\|join`, placeholders) | `modules/missions` (`GlobalMissions`) | `missions-global-test` (rodadas, vencedor, perdedores, expiração, `ui`, `online` x `all`, lembrete ao entrar) |
| **Objetivos novos do Cobblemon** (`fossil`, `berry`, `apricorn`, `candy`, `held_item`, `scan`, `level_up`, `gained`, `pokefish`) | `modules/missions` (`CobblemonMissionEvents`, `EventTargets`) | unitários dos alvos; `missions-cobblemon-test` só dispara o `gained` (ver limites) |
| Passe de batalha (temporadas, grátis/premium, XP, bônus, import do Great BattlePass) | `modules/battlepass` | `battlepass-test` + unitários |
| Essentials (as mecânicas do Solstice: teleporte, chat, jogador, equipe, bancadas, utilidades, info) | `modules/essentials` | `essentials-test` |
| **Essentials, o que faltava do Solstice**: `/homeother`, `/tpaall`, `/tppos`, `/tpoffline`, `/firstspawn`, `/setfirstspawn`, `/ignorelist`, `/plainbroadcast`, `/motd`, `/ignite`, `/effects`, `/sleep`, `/serverstat`, `/activetime`, `/kittycannon`, `/rocket`, `/nudge`, `/checkjail`, `/doas`, `/powertool`, `/cooldown` (recarga de qualquer comando), `/timebar`, `/tempbanip` | `modules/essentials` (`TeleportExtras`, `PlayerExtras`, `StaffExtras`, `StaffTools`), mixin `LivingEntitySleepMixin` | `essentials-extras-test` |
| **Essentials, estilo**: formato do chat por grupo/tag, cores e trocas, avisos de entrada/saída/primeira entrada/morte/conquista/`/me`, lista de jogadores (cabeçalho, rodapé, nomes), menções com som e `/notifications`, placas coloridas | `modules/essentials` (`Styling`, `Notifications`, `StylingLogic`), mixins `SignFormatMixin`, `ServerPlayerTabNameMixin`, `core/Texts.parseRestricted` | `styling-test` + unitários (`StylingLogicTest`) |
| **Gerenciador de chat**: barra mensagem repetida, 3 avisos, ban de chat que cresce com os bans e os avisos, `/chatguard` | `modules/chatguard` | `chatguard-test` + unitários |
| Flan: entrada na claim só por permissão e/ou por tag, em dois botões do menu da claim | `modules/flan`, mixin `mixin/flan` | `flan-test` (botões no menu real do Flan 1.12.8, expulsão, união das listas, dono isento) + unitários |
| Flan: **dar tag e/ou permissão a quem entra** na claim (mais dois botões no menu, 14 e 15; `/claimaccess grant`; só quem tem `serverbuddy.flan.grants`); tira ao sair (marca `sb_claim_grant.<tag>`), `remove_on_leave` | `modules/flan` (`FlanGrants`) | `flan-test` (tag dada e tirada, marca, tag que já era do jogador, limpeza de marca antiga, permissão dada via `/sb modules`, botões e telas, `remove_on_leave=false`) + unitários |
| **Bloqueio de comandos** por tag, permissão, dimensão ou horário (`/back` e qualquer comando; `*`, `except`, apelidos, várias palavras, bypass nível 4) | `modules/commandblock`, `core/Conditions.kt`, `core/CommandHooks.kt` | `commandblock-test` + unitários |
| Apelidos de comando sem argumentos (`SbCommands.alias`; antes davam "comando desconhecido": `/wb`, `/pt`, `/nearby`, `/bp`...) | `core/SbCommands.kt`, `modules/essentials`, `modules/battlepass` | `essentials-test`, `battlepass-test` + unitários |
| Convivência com CobbleUtils / CobblemonExtras (renomear ou tirar o comando do outro) | `core/SbCommands.kt`, `core/ModuleSettings.kt` | unitários (`SbCommandsTest`); com o mod real não deu para testar |
| Sem Cobblemon: os módulos novos sobem e o que é de Pokémon não existe | — | `nocobblemon-test` |
| Módulos antigos (storage, redeem, fichas, Shiny Ball, /pokesee, /pokeodds...) | — | regressão completa depois de tudo |

## Não testado / limites conhecidos

- Recusa de um golpe da Blacklist **dentro de uma batalha de verdade** (o bot não consegue escolher golpes): a regra e o mixin foram
  conferidos por unitários e pelo bytecode.
- Caminhos com **LuckPerms** e **GreatCosmetics** instalados (só por código/reflexão).
- Eventos do Cobblemon para Missões e Passe de batalha (captura, derrota, evolução, ovo, troca): compilam, e a captura da Shiny Ball
  completou uma missão no teste, mas as outras não foram disparadas de verdade.
- Flan: o caminho "permissão que libera" a entrada é lógica testada por unitário; no servidor real foram testados os caminhos que bloqueiam e o "ao entrar" (tag e permissão dadas).
  A permissão dada ao entrar vale pela API de permissões do Fabric: com o LuckPerms instalado a ordem em que os dois respondem não foi testada.
- Bloqueio de comandos: não pega o que roda por dentro de `/execute`, de função ou de bloco de comando (só o que o jogador digita); a condição `equipped:` só tem teste unitário.
- O Flan completo só entra no `runServer` (`modLocalRuntime`); o JAR compila só contra a API dele e funciona sem ele.
- **Objetivos de missão do Cobblemon sem teste ao vivo**: `fossil`, `berry`, `apricorn`, `candy`, `held_item`, `scan`, `level_up` e `pokefish` compilam, os eventos foram conferidos no jar do Cobblemon 1.8 e os alvos
  têm teste unitário, mas o bot não consegue reviver fóssil, colher frutinha, usar doce, escanear com a Pokédex nem pescar; o `/pokemonedit` muda o nível sem passar pelo evento de subir de nível. Só o `gained`
  (via `/givepokemon`) foi disparado de verdade.
- **Estilo do Essentials**: o chat formatado sai como mensagem do servidor (não dá para denunciar pelo menu do jogo); o jogador que entra não vê a própria mensagem de entrada (é assim no Minecraft, conferido com um bot);
  o nome na lista (Tab) usa um mixin em `ServerPlayer.getTabListDisplayName`; os formatos por grupo do LuckPerms (`group.<nome>`) e `%luckperms:prefix%` não foram testados com o LuckPerms de verdade (o teste usa `tag:`);
  placeholder do PlaceholderAPI no formato do chat só foi conferido pela leitura. O powertool no graveto não funciona (o Flan atende o graveto antes).
- `/sleep` só segura a pessoa deitada de noite (de dia o Minecraft acorda qualquer um). `/tempbanip` foi testado com um IP qualquer (banir o IP de um jogador do teste baniria o localhost inteiro).
- Missões globais: o estado sobrevive a reinício por leitura do banco, mas isso não foi simulado (só a rodada em andamento dentro de uma execução). A recompensa da missão global é só do vencedor (não há prêmio de consolação).
- Nada foi commitado no repositório do mod (ele ainda não tem commits).

## Armadilhas

- Nunca escrever arquivos grandes com `node -e`/heredoc no bash (as `\n` viram quebra de linha): use a ferramenta Write.
- Mixins ficam em Java e em config própria (`serverbuddy.<nome>.mixins.json`) com plugin que desliga se o outro mod existe.
- O serializador legado do Adventure não vem no jar: usar `core/LegacyText.kt`.
- `Stats.CUSTOM.get(x)` precisa do `ResourceLocation` que o Minecraft registrou (comparação por instância): ver `StatReader`.
- Enum em config: usar sempre `SbGson` (aceita minúsculas); Gson puro devolve null e o Kotlin estoura mais adiante.
- Apelido de comando: `SbCommands.alias`, nunca `Commands.literal(x).redirect(y)` sozinho (não leva o `executes`). E não registrar apelido com nome que já existe
  (o `/return` do Minecraft se misturava com o `/back`): o `alias` recusa e avisa no log.
- O `/tag` do Minecraft só aceita `[A-Za-z0-9_.+-]`: tags e marcas que o mod dá usam só isso (assim o admin vê e tira pelo comando e o teste consegue simular).
- O servidor de teste aceita só 2 jogadores (`max-players=2`) e o ops.json precisa de: `EsA` nível 4, `FlOwner` nível 4, `CbAdm` nível 3 (`node add-op.js <nome> <nível>` em bot-tests, com o servidor parado, calcula o UUID offline).
- Arquivo que o usuário cria à vontade (pool de trivia, categoria de missões): carregar com `ConfigFiles.load(..., track = false)`,
  senão o `/sb config check` acusa "faltando" numa pool enxuta e válida.
- Bot de teste: o ponto de entrada (2,5 / 89 / −3,5) tem um degrau na altura da cabeça (a bola lançada para o sul cai como item ao lado do bot),
  e ele anda para qualquer lado: leve-o para um chão plano com `tp` (ou monte uma plataforma, como o `shinyball-test`) antes de pulos, passos e bolas.
- `gradlew build runServer` sobe o servidor antes do `:test`: rode `gradlew test` à parte.

# Ecossistema: site + API + mod (atualizado em 12/11/2026)

Feito e testado (bots no servidor de desenvolvimento e no JAR Pro ofuscado de um servidor "de produção" local, ativando contra o site local):

| Item | Onde |
|---|---|
| 3 JARs (Basic, Plus, Pro) com ProGuard, `fabric.mod.json` próprio e bibliotecas por plano | `tiers.gradle`, `proguard/serverbuddy.pro` |
| Ativação por chave (cache AES, janela offline, trava dura x suave, mensagem clara para chave de outro plano) | `license/` |
| Dashboard ligada ao site (pareamento, WebSocket, visão geral, logs, configs, jogadores, rankings, console, auditoria) | `dashboard/` |
| Gerenciar módulos e comandos por módulo | `core/CoreCommands.kt`, `core/SbCommands.kt` |
| AFK com tag | `modules/essentials` (`afk-test`) |
| Astral Trader, Portais, Action Items, Dimensões | `modules/astral`, `portals`, `actionitems`, `worlds` (`astral-test`, `portals-test`, `actionitems-test`, `worlds-test`) |

Não testado ao vivo: Impactor (economia dos módulos de dinheiro), o webhook do Discord, o `/sb world` em nether/end, plano vencido e vazamento de chave no mod real (só nos testes do site),
LuckPerms (`luckperms.*` do dashboard devolve `needs_luckperms` sem ele).
