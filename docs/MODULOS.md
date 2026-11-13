# Módulos da fase 2

Este guia explica os módulos que o ServerBuddy ganhou depois dos Skies: **MOTD, Tags, Blacklist, Trivia, Missões, Passe de
batalha, Essentials** (as mecânicas do Solstice), **Bloqueio de comandos**, **Gerenciador de chat** e **Flan** (entrada em claims e o que se ganha ao entrar), mais as peças que todos compartilham.
Cada um tem pasta própria em `config/ServerBuddy/<Módulo>/` e liga/desliga em `config/ServerBuddy/modules.conf`.

## Configs fáceis de entender

- Todo arquivo de config nasce **comentado**: um `//` explica cada opção, e o topo do arquivo tem um resumo de como o módulo
  funciona (as configs mais complexas têm exemplos prontos para copiar). O JSON aceita comentários, então você pode escrever os seus.
- Um arquivo que já existe **nunca é reescrito sozinho**. Quando uma versão nova do ServerBuddy cria opções novas:
  `/sb config check` lista as que faltam em cada arquivo e `/sb config update` completa (guarda um `.bak` antes e mantém os seus valores).
  As pools do Trivia e as categorias de Missões são conteúdo seu (cada uma só traz o que quer), então ficam fora dessa checagem.
- `/sb modules` mostra quais módulos estão rodando (e os recursos do Cobblemon que têm liga/desliga) e avisa o motivo de um que subiu mas
  está parado (falta o Cobblemon ou o Flan, ou outro mod faz o mesmo: Solstice, MiniMOTD, Cobblemon Blacklist). `modules.conf` liga e desliga cada um, troca o nome de qualquer comando
  (`"command_names": { "storage": "bau" }`) e escolhe quem fica com o nome quando outro mod tem um comando igual.
- Valores de lista (enum) aceitam maiúsculas ou minúsculas: `"period": "daily"` e `"DAILY"` valem igual.
- Menus de baú: `show_player_inventory: false` esvazia a parte do inventário do jogador (`player_inventory_filler` põe um item de fundo).
  O Minecraft ainda desenha os 36 espaços sob o baú; tirá-los de vez exige resource pack, então o mod só os esvazia e trava os cliques.

## Recompensas (o mesmo formato em tudo)

Trivia, Missões, Passe de batalha e Streaks usam o mesmo objeto, `{ "type": "...", ... }`. Tipos: `item` (aceita todos os campos do
item editável; o que não couber no inventário vai para o `/storage` ou, sem ele, cai no chão), `command` (console; `"as": "player"` roda
como o jogador; aceita lista), `money` (Impactor, CobbleDollars, BEconomy ou Pebbles), `xp`, `message`, `broadcast`, `title`, `sound`,
`pokemon` (`"spec": "pikachu level=10 shiny"`), `battlepass_xp` e `tag`. Em todos: `chance` (porcentagem), `weight` (peso quando se sorteia)
e `label` (como aparece nos menus). Outros módulos acrescentam tipos com `RewardService.register`.

## MOTD (`config/ServerBuddy/Motd`)

Substitui o MiniMOTD: perfis de MOTD escolhidos por condições (`whitelist`, `maintenance`, `full`, `players>=10`, `time:18:00-23:30`,
`day:sat,sun`, `date:2026-12-24..2026-12-26`, `event`, com `!` para negar e `when`/`when_any`), ícones, jogadores falsos, "só mais X",
contagem e hover editáveis, modo manutenção (`/sb motd maintenance on|off`) e mensagem própria para quem tenta entrar sem estar na whitelist.
`/sb motd preview [perfil]` mostra o resultado e `/sb motd profiles` qual perfil está valendo. Se o MiniMOTD estiver instalado este módulo fica parado.

## Tags (`/tags`)

Menu de baú com categorias, páginas, equipar/tirar, comprar com dinheiro e tags bloqueadas com dica. Cada tag pode ser dada (`GIVEN`),
livre (`FREE`), por permissão, por grupo do LuckPerms ou à venda (`PRICE`). Equipada, a tag vira prefixo no LuckPerms e/ou na equipe do
scoreboard, e `%serverbuddy:tag%`, `tag_name` e `tag_id` servem para chat, lista e scoreboard. Com o **GreatCosmetics** instalado o menu usa o
catálogo e a posse de tags dele; sem ele funciona com `Tags/tags.json`. A recompensa `{ "type": "tag", "tag": "campeao" }` dá uma tag.

## Blacklist (golpes proibidos) — precisa do Cobblemon

Quem escolhe um golpe proibido tem a escolha recusada, ganha um aviso na batalha e o menu de golpes reabre para escolher outro.
Contextos: `default` vale sempre; os outros valem por `when` (todas) / `when_any` (uma): `tag:...`, `permission:...`, `dimension:...`,
`battle:pvp|npc|wild`, `time:`, `day:`, `date:` (com `!`). Sem condições, o contexto vale para quem tem a tag do scoreboard com o nome dele
(como no mod original). Só vale para jogadores. Banir `hiddenpower` bane as variações. `/blacklist` mostra aos jogadores o que está proibido
para eles; a equipe usa `/blacklist contexts|list <contexto>|check <jogador> [golpe]|add|remove|reload`. O `blacklist.json` do mod
Cobblemon Blacklist é importado na primeira vez.

## Trivia (`/trivia`)

Perguntas no chat a cada intervalo aleatório; o primeiro que acerta ganha. As perguntas ficam em `Trivia/pools/*.json`: perguntas escritas,
geradas (`equation`, `typing`, `unscramble` e, com Cobblemon, as de Pokédex, tipo, habilidade, grupo de ovo, golpes...) ou misturadas, com
prêmio sorteado por peso (`rewards`) e prêmios sempre entregues (`extra_rewards`). Aceita resposta sem acento/maiúscula/símbolos e com um
errinho de digitação, dá dicas progressivas (`P _ k _ c h _`), mostra uma barra de tempo, evita repetir perguntas recentes, tem bônus de
rapidez e de **vitórias seguidas**, ranking (`/trivia top`, `/trivia stats`) e `/trivia toggle` para silenciar só para si.
Equipe: `/trivia start [pool]|stop|info|pools|reload`. As pools e a config do **SkiesTrivia** são convertidas na primeira vez (o original fica em `config/skiestrivia`).
Placeholders: `%serverbuddy:trivia_wins%`, `trivia_streak`, `trivia_best_streak`, `trivia_rank`, `trivia_next`, `trivia_top/1`, `trivia_top_wins/1`.

## Missões (`/missions`)

Missões do **Minecraft** (e, com Cobblemon, missões de Pokémon como brinde) em `Missions/categories/*.json`.
Cada objetivo é uma linha `verbo alvo quantidade`: `mine #minecraft:logs 50`, `kill any 20`, `craft minecraft:bread 10`, `walk 5000`,
`fish 3`, `play_time 30`, `level 30`, `has minecraft:diamond 5`, `capture shiny 1`, `manual visitar_spawn 1`... O progresso vem das
estatísticas do próprio jogo e só conta depois que a missão começa. Categorias podem ser diárias ou semanais (troca na hora configurada,
sorteando `pick` missões por período, as mesmas para todos), ter pré-requisitos (`requires`), ser repetíveis com espera (`cooldown`) e entregar o
prêmio sozinhas (`auto_claim`). `/missions` abre o menu (uma aba por categoria, barra de progresso, clique para resgatar); `/missions list`,
`claim [missão]`; equipe: `progress <jogador> <chave> [n]` (para NPCs e blocos de comando), `complete`, `reset`, `reload`.
Placeholders: `%serverbuddy:missions_ready%`, `missions_active`, `missions_done`.

**Objetivos do Cobblemon** (todos com o alvo `any` ou um filtro): `capture`, `defeat`, `battle_win`, `evolve`, `hatch` (alvos: `any`, a espécie, `shiny`, `legendary`, `type:fire`) e, desde que as
missões globais chegaram, `fossil` (reviver fóssil), `scan` (escanear com a Pokédex), `gained` (ganhar um Pokémon de qualquer jeito), `pokefish` (pescar Pokémon) — esses quatro com o mesmo filtro de Pokémon —,
`berry oran 10` (colher frutinhas; `any` = qualquer), `apricorn red 5` (a cor), `candy cobblemon:rare_candy 3` (doce de experiência que fez efeito), `held_item cobblemon:leftovers 1` (dar um item a um Pokémon) e
`level_up any 5` (Pokémon subindo de nível; `level_up pikachu@50 1` = um Pikachu chegando ao nível 50 ou mais). Os nomes dos eventos são os do Cobblemon 1.8.

**Missões globais** (`global_missions` no `config.json`, vem desligado): uma categoria com `"global": true` guarda missões que o servidor sorteia **uma por vez**, valendo para todos numa corrida. De `interval` em
`interval` (o primeiro depois desse tempo, ou na hora com `/missions global start [missão]`) ele escolhe uma, avisa no chat e com um título, e **o primeiro jogador a completar leva o prêmio** (só ele); a missão
acaba para os outros. Passou `time_limit` sem ninguém completar, ela acaba sem vencedor. `target`: `online` (só quem estava online quando começou) ou `all` (todo mundo, inclusive quem entrar depois);
`assignment`: `auto` (todos os que participam já estão valendo) ou `ui` (cada um clica na missão no menu, ou usa `/missions global join`, e o progresso conta a partir daí). Quem entra no servidor com uma
missão em jogo recebe um lembrete. `/missions global` mostra a missão, o tempo e quem está na frente; equipe: `/missions global start [missão]` e `stop`. O estado fica no banco (sobrevive a reinício).
Placeholders: `%serverbuddy:missions_global_name%`, `_time`, `_top`, `_top_progress`, `_progress` (o do jogador) e `_next`. As categorias de exemplo (`globais.json`, `globais_pokemon.json`) só nascem numa instalação nova:
numa que já existe, copie uma categoria e ponha `"global": true`.

## Passe de batalha (`/battlepass`, `/bp`)

Temporadas com níveis, trilha grátis e premium, menu de baú (premium em cima, nível no meio, grátis embaixo). XP por capturar, derrotar,
evoluir, chocar e trocar Pokémon, por tempo online e pela recompensa `battlepass_xp` (missões, trivia...). Curva `FORMULA` ou `MANUAL`,
**bônus de XP** por horário/dia/data e por permissão (`boosts`), `auto_claim`, premium por compra, permissão ou comando. Os prêmios de cada
nível ficam em `BattlePass/tiers.json`. Equipe: `addxp`, `addlevels`, `removelevels`, `reset`, `resetrewards`, `premium give|remove`,
`season start [dias]|end|info`, `import`, `reload`. A config e os prêmios do **Great BattlePass** são convertidos na primeira vez e
`/battlepass import` traz o progresso dos jogadores (banco SQLite dele).

## Essentials (as mecânicas do Solstice)

Seis grupos que ligam em `Essentials/config.json`; todos os textos em `Essentials/messages.json`.

- **teleport**: `/sethome /home /delhome /homes` (limite por permissão), `/homeother <jogador> [casa]` (equipe), `/warp /warps /setwarp /delwarp`, `/spawn /setspawn`,
  `/firstspawn [jogadores]` e `/setfirstspawn [delete]` (o spawn de quem entra pela primeira vez; sem ele vale o `/spawn`), `/back` (inclui o local da morte),
  `/tpa /tpahere /tpaccept /tpdeny /tpcancel /tptoggle`, `/tpaall` (pede a todos que venham), `/tppos <x y z> [dimensão]`, `/tpoffline <jogador>` (onde ele saiu do servidor),
  `/rtp` (lugar seguro, raio e recarga na config), `/top`, `/tphere`. Espera de teleporte que cancela se o jogador se mexe e recarga (`bypass.warmup`/`bypass.cooldown` pulam).
- **chat**: `/msg /reply` (substituem os do Minecraft), `/ignore /unignore /ignorelist`, `/mute /unmute` (tempo e motivo), `/nick /realname`, `/broadcast` e `/plainbroadcast`
  (sem enfeite), `/staffchat`, `/helpop`, `/socialspy`, `/mail send|read|clear` e `/notifications` (o aviso sonoro de quem foi citado; veja "Estilo" abaixo).
- **player**: `/god /fly /heal /feed /speed /hat /near /seen /playtime /afk` (automático), `/suicide /ping /smite /extinguish`, `/ignite`, `/effects [jogador]`, `/sleep` (deita sem cama, só de noite),
  `/serverstat` (e `/tps`: TPS, tempo ligado, memória, entidades e chunks), `/activetime [jogador|top|set]` (tempo ativo, sem contar o AFK) e as brincadeiras `/kittycannon`, `/rocket`, `/nudge`.
- **staff**: `/invsee` (edita ao vivo), `/enderchest`, `/tempban`, `/tempbanip <jogador|IP> <tempo>`, cela (`/setjail /jail /unjail /jails`, `/checkjail` mostra quem prendeu, quando e a pena), `/notes`,
  `/sudo`, `/doas <jogador> <comando>` (roda como o jogador, com a permissão de quem digitou), `/restart [tempo]|cancel` (contagem e barra), `/commandspy`,
  `/powertool set|list|clear|toggle` (liga um comando ao item da mão e a um gesto: `use`, `attack_block`, `attack_entity`, `interact_block` ou `interact_entity`; roda como o próprio jogador; o item de inspeção do Flan (o graveto) é atendido pelo
  próprio Flan antes de qualquer outro mod, então ligue o powertool a outro item), `/cooldown list|check|clear` (veja "Recarga de comandos") e `/timebar start|custom|list|cancel` (barra de tempo para todos com um comando no fim;
  `%remaining_time%`, `%elapsed_time%` e `%total_time%` no texto).
- **utilities**: `/workbench /anvil /grindstone /stonecutter /loom /smithing /cartography /enchanting`, `/trash`, `/item name|lore|more|repair`, `/repair [all]`, `/skull`.
- **info**: `/rules`, `/info`, `/motd` (as linhas de `info.motd`; `motd_on_join` mostra ao entrar).

Permissões `serverbuddy.essentials.<comando>`: de jogador vêm liberadas e as da equipe pedem nível 2 ou 3. Se o mod **Solstice** estiver instalado, este módulo se desliga para não brigar com ele.

### Recarga de comandos (`cooldowns` em `Essentials/config.json`)

Grupos de comandos com um tempo de espera entre um uso e outro, valendo para **qualquer** comando que o jogador digita (o `/heal`, o `/kit`, o `/home` de outro mod...): `"commands": ["heal", "feed"]` (escrito como no bloqueio
de comandos: sem a barra, o apelido do comando entra sozinho, `"home set"` só pega essa forma), `"seconds": 60`, e condições `when`/`when_any` (as mesmas do bloqueio de comandos: `tag:`, `permission:`, `dimension:`...).
A espera conta a partir do uso (mesmo que o comando dê erro). Quem tem `serverbuddy.essentials.cooldown.exempt.<grupo>` (padrão: nível 3) não espera. `/cooldown check|clear <jogador> [grupo]` vê e zera.

### Estilo (`styling` em `Essentials/config.json`): chat, avisos, lista de jogadores, menções e placas

Tudo vem **desligado** (menos as menções e as placas), porque mexer no que todo mundo vê é escolha do dono. Se outro mod de chat está instalado, deixe `chat_format` desligado.

- **Formato do chat** (`chat_format`): uma linha por tipo de jogador, em MiniMessage, com `%player%` (apelido ou nome), `%name%`, `%message%`, `%tag%` (o prefixo da tag equipada do `/tags`), `%afk%`, `%world%` e os
  placeholders do PlaceholderAPI (`%luckperms:prefix%`, `%luckperms:suffix%`...). A chave é o nome de um grupo do LuckPerms (vale quem tem `group.<nome>`) ou uma condição (`tag:vip`, `permission:...`); vale o primeiro
  que casar, e `default` é o de todos os outros. `replacements` troca pedaços do texto (`:shrug:`). Quem tem `serverbuddy.essentials.chat.format` (padrão: nível 3) escreve com cores e formatação (`&c`, `&l`,
  `&#ff8800`, `<red>`, `<gradient>`...); para os outros o texto é puro. Só entram as tags seguras (cor, negrito, itálico, sublinhado, riscado, ofuscado, gradiente, arco-íris, reset): **sem `<click>` nem `<hover>`**.
  `respect_ignore` esconde a linha de quem o jogador ignora. A linha sai como mensagem do servidor (não dá para denunciá-la pelo menu do jogo) e quem escuta o chat de fora (ponte com o Discord) continua recebendo o evento.
  O mute, o chat da equipe e o gerenciador de chat continuam valendo: o estilo só vê o que já foi aprovado.
- **Avisos** (`events`): entrada, saída, **primeira entrada** (`first_join`, com boas-vindas), morte (`%message%` traz a frase do Minecraft, traduzida no cliente), conquistas (`task`, `goal`, `challenge`; `%title%` com a
  dica de descrição) e `/me`. O aviso do Minecraft some; um texto vazio esconde o aviso sem pôr nada no lugar.
- **Lista de jogadores / Tab** (`tab_list`): cabeçalho e rodapé (várias linhas; `%online%`, `%max%`, `%tps%`, `%ping%`, `%player%`) e o nome de cada jogador (`player_format`, ex.: `%afk%%luckperms:prefix%%player%`), atualizados de
  `update_ticks` em `update_ticks`.
- **Menções** (`mentions`): quem tem o nome (ou o apelido) citado numa mensagem do chat ouve um som (`sound`, `volume`, `pitch`); também toca quando chega uma mensagem privada, um correio ou um pedido de teleporte. Cada
  jogador ajusta o seu com `/notifications` (`toggle`, `sound <som>`, `volume`, `pitch`, `afkonly`, `chat`, `test`, `reset`). O nome só conta como palavra inteira ("Al" não é citado por "alguém").
- **Placas** (`signs`): quem tem `serverbuddy.essentials.sign.format` (padrão: nível 2) escreve placas com cor e formatação (mesmas tags seguras do chat). Ao editar de novo a placa, o editor mostra o texto sem
  formatação: é preciso escrever as cores outra vez.

Limites conhecidos do Essentials: `/sleep` só segura a pessoa deitada de noite (de dia o Minecraft acorda qualquer um, como com uma cama); a recarga de comandos e o bloqueio de comandos só pegam o que o jogador
**digita** (não o que roda por `/execute`, função ou bloco de comando); o nome do jogador na lista usa um mixin em `ServerPlayer.getTabListDisplayName`, então outro mod que mexa nisso pode brigar.

## Flan: quem entra nas claims e o que ganha ao entrar

O menu da claim do Flan ganha **quatro botões** (espaços 12 a 15), cada um com a própria tela para ver, remover, limpar e adicionar:

- **Entrada por permissão** e **Entrada por tag** (separados): só entra quem passar numa das regras preenchidas (com as duas listas, basta uma). Permissões prontas da config
  ou digitadas numa bigorna; tags escolhidas no catálogo do `/tags`. O dono, quem edita a claim pelo Flan e quem tem `serverbuddy.flan.bypass` sempre entram, e o Flan expulsa
  quem não passa do mesmo jeito que faz com a permissão `can_stay`. Tags do scoreboard (`/tag`) também contam.
- **Ao entrar: dar tag** e **Ao entrar: dar permissão**: quem entra na claim ganha na hora as tags (do scoreboard, como `/tag <jogador> add <tag>`) e as permissões escolhidas.
  Ao sair da claim ou do servidor, o mod tira o que deu (uma tag que o jogador já tinha nunca é tirada; cada tag dada leva uma marca `sb_claim_grant.<tag>` na lista de tags do
  jogador, então tudo continua certo depois de uma queda do servidor). Com `"remove_on_leave": false` em `grants` a tag dada fica para sempre. As permissões valem pela API de
  permissões do Fabric (os comandos do ServerBuddy e dos mods que respeitam o LuckPerms) e só enquanto a pessoa está dentro; não mudam o nível de operador. Quem não pode ficar na
  claim (regra de entrada) não ganha nada. Combine com o **bloqueio de comandos**: a claim dá a tag `em_arena` e uma regra barra `/home` e `/back` de quem tem essa tag.
  **Só quem tem `serverbuddy.flan.grants` (padrão: operadores nível 3) e pode editar a claim mexe nesses dois botões**: se qualquer dono pudesse digitar permissões, ele daria poderes a si mesmo.

Sem o menu: `/claimaccess [perms|tags|info]`, `add|remove|clear perm|tag` e, para o que a claim dá, `/claimaccess grant [perms|tags]` e `grant add|remove|clear perm|tag`.
Regras em `Flan/restrictions.json`; presets, limites e posições dos botões em `Flan/config.json`.

## Bloqueio de comandos (`/commandblock`)

Impede um comando (o `/back`, o `/home`, qualquer um, inclusive de outros mods) de quem cumpre certas condições. Em `CommandBlock/config.json` cada regra tem `commands`, as condições
(`when`: todas precisam casar; `when_any`: basta uma; `except`: se uma casar, libera) e um `message`. Condições (todas aceitam `!` para negar): `tag:` (tag do scoreboard, dada com `/tag`,
do menu `/tags` ou por uma claim do Flan), `equipped:` (tag do `/tags` em uso), `permission:`, `dimension:`, `time:`, `day:` e `date:`. Exemplo: `"sem_back": { "commands": ["back"], "when_any": ["tag:sem_back"] }`
(dê a tag com `/tag Fulano add sem_back`). `"commands"` aceita várias palavras (`"home set"` bloqueia só essa forma), o apelido do comando entra sozinho (`wb` vale como `workbench`), o nome
trocado em `modules.conf` também, e `"*"` pega tudo (combine com `except`). Só vale para o que o jogador digita (o console, blocos de comando e `/execute` não passam por aqui); quem tem
`serverbuddy.commandblock.bypass` (padrão: operadores nível 4) nunca é barrado, e quem administra o próprio `/commandblock` continua podendo usá-lo mesmo com uma regra `"*"`.
Equipe (`serverbuddy.commandblock.admin`, nível 3): `/commandblock list`, `check <jogador> <comando>` (diz se está barrado e por qual regra) e `reload`.

## Gerenciador de chat (`/chatguard`)

Barra quem **repete a mesma mensagem** no chat e dá um castigo que cresce. Em `ChatGuard/config.json`: uma mensagem igual (ou quase igual) a uma que o jogador acabou de mandar é barrada (ninguém a vê) e vale um **aviso**;
depois de `warnings.max_warnings` avisos (padrão **3**) o erro seguinte vira um **ban de chat**: três avisos e o quarto erro é ban. O tempo do ban depende de **quantos bans a pessoa já levou e de quantos avisos acumulou**:
`tempo = ban.durations[nº do ban] + ban.extra_per_warning × avisos acumulados` (no máximo `ban.max`). Com os padrões: 1º ban = 5m + 3 × 1m = 8m; 2º = 30m + 6 × 1m = 36m; 3º = 2h + 9m...; passando do último valor
da lista, o último se repete. Sem erros por um tempo os avisos atuais somem (`warnings.reset_after`); sem bans nem avisos por muitos dias o histórico zera (`ban.memory_days`).
O que conta como igual: o texto é limpo antes (maiúsculas, acentos, pontuação, espaços e letras repetidas não importam) e `duplicates.similarity` abaixo de 1.0 também pega variações pequenas; mensagens muito curtas
(`oi`, `gg`) nunca são barradas. Opcional: `flood` (mensagens demais em poucos segundos, desligado). Durante o ban a pessoa não fala no chat nem usa os comandos de `ban.blocked_commands` (`/msg`, `/me`...).
Quem tem `serverbuddy.chatguard.bypass` (padrão: nível 3) nunca é barrado; quem tem `serverbuddy.chatguard.notify` (padrão: nível 2) vê os avisos e bans. Equipe (`serverbuddy.chatguard.admin`, nível 2):
`/chatguard status [jogador]`, `list`, `history <jogador>`, `ban <jogador> [tempo] [motivo]`, `unban`, `reset` e `reload`. O estado de cada jogador fica no banco. Roda **antes** do mute, do chat da equipe e do estilo
do chat, então um jogador banido não fura nenhum deles.

## Convivência com outros mods

| Mod | O que acontece |
|---|---|
| Solstice | o módulo Essentials fica desligado (avisa no log) |
| MiniMOTD | só o MOTD do ServerBuddy fica desligado |
| Cobblemon Blacklist | só a Blacklist do ServerBuddy fica desligada |
| CobbleUtils / CobblemonExtras | `/storage`, `/pokesee`... brigam por nome: em `modules.conf` → `compat`: `AUTO` (com o outro instalado o nosso vira `/sbstorage`...), `SERVERBUDDY` (o nosso fica com o nome e o do outro sai) ou `OUTRO` |
| GreatCosmetics | o menu `/tags` usa o catálogo e a posse de tags dele |
| LuckPerms | prefixo das tags, grupos como tags, todas as permissões e os formatos de chat por grupo (`group.<nome>`; `%luckperms:prefix%` pelo PlaceholderAPI) |
| Mods de chat (Styled Chat...) | deixe `styling.chat_format` desligado; os outros recursos de estilo continuam valendo |
| Flan | os quatro botões no menu da claim (entrada por permissão e por tag, dar tag e dar permissão ao entrar) |

# Módulos do ecossistema (fase 3)

## Gerenciar módulos e comandos

- `/sb modules`: estado de cada módulo. `/sb modules enable <módulo>` e `disable <módulo>` escrevem em `modules.conf` (valem ao reiniciar).
- `/sb modules commands [módulo]`: todos os comandos de cada módulo (com os apelidos); `/sb modules command <nome> off` tira UM comando da árvore na hora
  (o módulo continua funcionando), `on` volta a ligar (precisa reiniciar). A lista fica em `modules.conf` → `disabled_commands`.

## AFK com tag (Essentials)

`essentials/config.json` → `afk`: `entity_tag` (a tag de entidade que o jogador leva enquanto está AFK; use em seletores, `@a[tag=afk]`, e em datapacks; vazio = sem tag),
`label` (o que `%afk%` mostra no chat e na lista de jogadores), `announce` (avisar todos). A tag sai quando o jogador volta ou sai do servidor.
Outros módulos podem ouvir `AfkEvents.CHANGED`.

## Portais (Plus)

`/sb portal give [jogador] [quantidade]` entrega o **Portal Block**; ao colocar, vira um bloco de portal sem moldura. Blocos que se tocam são UM portal.
Mire no portal e use `/sb portal edit tp <x y z> [dimensão]` (ou `edit tp here`) para dizer para onde ele leva; `/sb portal list` e `/sb portal remove`.
Encostar teleporta na hora (sem espera, sem animação de portal). Os portais ficam em `Portals/portals.json`. Permissão para usar: `serverbuddy.portal.use` (todos).
Funciona com as dimensões do módulo Dimensões (`edit tp 0 65 0 serverbuddy:<nome>`).

## Action Items (Plus)

Itens que fazem coisas quando clicados. Cada um é um arquivo em `ActionItems/items/<id>.json` (veja `example_gift.json`): o item, os cliques (`right_click`,
`left_click`), `cooldown_seconds`, `consume`, `requirements` (as mesmas condições do bloqueio de comandos: `permission:`, `tag:`, `dimension:`, `time:`...),
`actions` e `deny_actions` (qualquer tipo de recompensa: `command`, `message`, `sound`, `title`, `item`, `money`, `pokemon`...) e `random_actions` (sorteia
`random_count` por `weight`). `/sb actionitems give <id> [jogador] [quantidade]` e `list`.

## Astral Trader (Pro, precisa do Cobblemon)

`/astral` abre a troca de fichas por Pokémon. Há dois tipos de ficha no banco: **special** (lendários, paradoxo e Ultra Beast) e **shiny**. Quatro pools, cada uma com UM preço
para todas as suas ofertas: lendário (3 special), paradoxo (2 special), Ultra Beast (1 special) e shiny (1 shiny). Tudo em `AstralTrader/config.json`: tamanho de cada pool,
preço, nível, IVs perfeitos, estoque do servidor (`stock`), limite por jogador (`per_player_limit`), filtros por rótulo/espécie e `reset_hours` (quando as ofertas mudam).
Uma oferta esgotada aparece como SOLD OUT. Admin: `/astral admin give|take|set <jogador> <special|shiny> <quantidade>`, `rotate [pool]` e `offers`; `/astral balance`.
Placeholders: `%serverbuddy:astral_special%`, `%serverbuddy:astral_shiny%`. Missões, passe de batalha ou caixas entregam fichas com um comando de recompensa.

## Dimensões (Pro)

Mundos extras criados com o servidor ligado (Nucleoid Fantasy, dentro do JAR Pro): `/sb world create <nome> [overworld|nether|end|void|flat] [semente]`, `delete`, `list`,
`tp <nome> [jogador]`, `spawn`, `setspawn`. Ficam em `dimensions/serverbuddy/<nome>` do mundo e `Worlds/worlds.json` lembra a lista (reabrem sozinhos ao ligar o servidor).
Apagar um mundo leva antes quem estiver nele para o spawn do mundo principal.
