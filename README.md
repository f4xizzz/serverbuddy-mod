# ServerBuddy

Mod de servidor para Minecraft (Fabric): **kits, loja, caixas, menus (GUIs), anúncios, limpeza, eventos, resgates,
storage de recompensas, logs, MOTD, tags, trivia, missões (com missões globais), passe de batalha, blacklist de golpes, os comandos de
essentials (casas, warps, tpa, mensagens, moderação, formato do chat, lista de jogadores...), gerenciador de chat (anti-spam com avisos e ban de chat), bloqueio de comandos por tag e regras de claims do Flan**,
tudo num JAR só e com todas as configurações, comentadas, em `config/ServerBuddy/`. Os módulos da fase 2 estão explicados em [docs/MODULOS.md](docs/MODULOS.md).

Se o **Cobblemon** estiver instalado, o ServerBuddy liga também caçadas, bosses, áreas de captura, combo de
captura, recompensas da Pokédex, o editor/builder de Pokémon, as fichas (Shiny, Sexo e Shiny Ball), `/pokesee`,
`/compsee`, `/pokeshout` e `/pokeodds`.

- **Minecraft 1.21.1**, **Fabric**
- **Não depende do Cobblemon**: sem ele, os recursos de Pokémon ficam desligados e o resto funciona normalmente.
  Com ele, funciona do **1.8.0 ao 1.8.4** (versões mais antigas são recusadas).
- Só no servidor (nada é exigido no cliente)
- Código em Java (partes do antigo GreatServerUtils) e Kotlin (módulos portados dos mods PokeSkies)
- Nomes do Minecraft: mappings oficiais da Mojang

> O ServerBuddy substitui o **GreatServerUtils** e os mods **SkiesGUIs, SkiesKits, SkiesShop, SkiesCrates,
> SkiesAnnouncements e SkiesClear** (código portado) e traz, reescritas, as mecânicas de **SkiesTrivia, CobbleMissions
> (agora com missões do Minecraft), Great BattlePass, Cobblemon Blacklist, MiniMOTD** e as principais do **Solstice**
> (essentials; o que ainda não tem e os limites conhecidos estão em [docs/MODULOS.md](docs/MODULOS.md) e [docs/PROGRESSO.md](docs/PROGRESSO.md)).
> As configs e os dados desses mods são importados na primeira execução.

## Planos e JARs

Instale o JAR único `serverbuddy-<versão>.jar` na pasta `mods/`. Ao iniciar o servidor, o mod avisa no console e no chat dos administradores sobre a dashboard.

Use `/sb dashboard sync` e informe o código de uso único em [servebuddy.site/dashboard](https://servebuddy.site/dashboard). O primeiro pareamento libera cinco dias grátis de dashboard Basic. Depois, a dashboard exige Plus ou Pro ativo na conta.

**Gerenciar módulos**: `/sb modules` mostra o estado, `/sb modules enable|disable <módulo>` liga/desliga (vale ao reiniciar),
`/sb modules commands [módulo]` lista os comandos de cada módulo e `/sb modules command <nome> off|on` desliga UM comando sem desligar o módulo
(`disabled_commands` em `modules.conf`).

## Requisitos

| | |
|---|---|
| Obrigatórios | Fabric Loader ≥ 0.18.3, Fabric API, [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) ≥ 1.11 (o Cobblemon 1.8 já traz um), [Placeholder API](https://modrinth.com/mod/placeholder-api) |
| Recomendados | LuckPerms (permissões), Impactor (economia), MiniPlaceholders |
| Opcionais (integrações) | **Cobblemon**, Flan, Styled Sidebars, CobbleDollars, BEconomy, Pebbles Economy, HoloDisplays, Plan |

## Módulos

| Módulo | Vem de | Comando | Pasta em `config/ServerBuddy/` | Precisa do Cobblemon? |
|---|---|---|---|---|
| Caçadas | GreatServerUtils | `/hunt` · admin: `/serverbuddy hunt` | `Hunt` | sim |
| Áreas de captura | GreatServerUtils | `/catcharea` · admin: `/serverbuddy catcharea` | `CatchArea` | sim |
| Combo de captura | GreatServerUtils | `/catchcombo` | `CatchCombo` | sim |
| Bosses | GreatServerUtils | `/serverbuddy boss` | `Bosses` | sim |
| Recompensas da Pokédex | GreatServerUtils | `/dexrewards` | `DexRewards` | sim |
| Editor / Builder de Pokémon | GreatServerUtils | `/pokeeditor`, `/pokebuilder` | `PokeEditor`, `PokeBuilder` | sim |
| Eventos | GreatServerUtils | `/serverbuddy events` | `Events` | não (a taxa de Shiny do evento só com Cobblemon) |
| Resgates (códigos) | GreatServerUtils | `/redeem` · admin: `/serverbuddy redeem` | `Redeem` | não (as recompensas vão para o [Storage](#storage-storage)) |
| Logs (mortes, contas alternativas, Discord) | GreatServerUtils | — | `Logs` | não |
| **GUIs** | SkiesGUIs | `/serverbuddy guis` + comandos de cada GUI | `GUIs` | não (Molang só com Cobblemon) |
| **Kits** | SkiesKits | `/serverbuddy kits` + os comandos da sua config (`/kit`, `/kits`) | `Kits` | não |
| **Shop** | SkiesShop | `/serverbuddy shop` + os comandos da sua config (`/shop`) | `Shop` | não (Molang só com Cobblemon) |
| **Crates** | SkiesCrates | `/serverbuddy crates` + os comandos de chaves da config | `Crates` | não (recompensa Pokémon só com Cobblemon) |
| **Announcements** | SkiesAnnouncements | `/serverbuddy announcements` | `Announcements` | não |
| **Clear** | SkiesClear | `/serverbuddy clear` | `Clear` | não (limpar Pokémon só com Cobblemon) |
| **Storage** | novo | `/storage` · admin: `/serverbuddy storage` | `Storage` | não |
| **Gritos (chat)** | novo | `/itemshout`, `/pokeshout`, `/pokeshoutall` | `Shout` | só o de Pokémon |
| **Fichas** | novo | `/poketoken` | `PokeTokens` | sim |
| **Ver time e PC** | novo | `/pokesee`, `/compsee` | `PokeView` | sim |
| **Taxa de Shiny** | novo | `/pokeodds` | usa a config do próprio Cobblemon | sim |
| **MOTD** | MiniMOTD (reescrito) | `/sb motd` | `Motd` | não |
| **Tags** | GreatCosmetics (menu novo) | `/tags` | `Tags` | não |
| **Blacklist** | Cobblemon Blacklist (reescrito) | `/blacklist` | `Blacklist` | sim |
| **Trivia** | SkiesTrivia (reescrito) | `/trivia` | `Trivia` | não (perguntas de Pokémon só com Cobblemon) |
| **Missões** | CobbleMissions (reescrito, com Minecraft e missões globais) | `/missions`, `/missions global` | `Missions` | não (brindes de Pokémon só com Cobblemon) |
| **Passe de batalha** | Great BattlePass (reescrito) | `/battlepass`, `/bp` | `BattlePass` | não (XP de Pokémon só com Cobblemon) |
| **Essentials** | Solstice (reescrito) | `/home`, `/warp`, `/tpa`, `/msg`, `/powertool`, `/cooldown`, `/timebar`, `/notifications`... | `Essentials` | não |
| **Bloqueio de comandos** | novo | `/commandblock` | `CommandBlock` | não |
| **Gerenciador de chat** | novo | `/chatguard` | `ChatGuard` | não |
| **Flan: entrada e "ao entrar"** | novo | `/claimaccess` + 4 botões no menu do Flan (quem entra por permissão ou tag; tag e permissão dadas ao entrar) | `Flan` | não (precisa do Flan) |

O comando raiz é **`/serverbuddy`**, com o alias **`/sb`**. Exemplos: `/sb reload` (recarrega tudo),
`/sb kits reload`, `/sb crates give <jogador> <caixa>`, `/sb clear force`, `/sb announcements send <grupo>`.

Configurações gerais (webhook do Discord, gamerules forçadas, `/pokeheal`, Flan e as chaves de compatibilidade)
ficam em `config/ServerBuddy/serverconfig.conf`.

### Sem o Cobblemon

O log mostra "Cobblemon não encontrado" e os módulos de Pokémon não carregam (nem criam pastas de config). Também
ficam desligados: o tipo `molang` nas GUIs e no Shop, as recompensas `pokemon` do Crates, o limpador `cobblemon` do
Clear e os requisitos `javascript` das GUIs e dos Kits (o ServerBuddy não embute o GraalVM; usa o que o Cobblemon traz).
Uma config que usa algo disso é recusada com um aviso, sem derrubar o resto.

## Item editável (o mesmo em todos os módulos novos)

Fichas, Storage, resgates e os ícones dos menus novos usam a mesma definição de item. Todos os campos aparecem no
arquivo de config criado na primeira execução (os vazios ficam como `null`).

| Campo | Padrão | O que faz |
|---|---|---|
| `item` | `minecraft:paper` | id do item (ex.: `minecraft:nether_star`, `cobblemon:poke_ball`) |
| `amount` | `1` | quantidade (de 1 a 99) |
| `name` | vazio | nome do item, em MiniMessage |
| `lore` | vazio | linhas da descrição, em MiniMessage |
| `custom_model_data` | vazio | número do *custom model data* (para pacotes de recursos) |
| `stackable` | `true` | `false` = nunca junta com outros (tamanho máximo da pilha 1) |
| `hide_tooltip` | `false` | esconde o tooltip inteiro |
| `hide_additional_tooltip` | `false` | esconde só as linhas extras (encantamentos, atributos, efeitos...) |
| `glint` | vazio | `true`/`false` força o brilho de encantado |
| `components` (ou `nbt`) | vazio | componentes extras do item em JSON, como nos kits e nos menus |

## Storage (`/storage`)

Um baú de recompensas por jogador. Quando alguém resgata um código com `/redeem`, os itens e os comandos do código
**não são entregues na hora**: vão para o `/storage` e o jogador resgata clicando.

- Os itens ficam na aba **Itens** e as recompensas de comando na aba **Comandos**, separadas.
- Quanto mais recompensas o jogador tem, mais páginas aparecem (45 por página por padrão; "anterior" e "próxima"
  só aparecem quando existem). **Resgatar tudo** resgata a aba atual.
- Se o inventário não comporta tudo, sai o que cabe e o resto continua no storage. Cada recompensa só pode ser
  resgatada uma vez (a recompensa sai do banco antes de o item ou o comando sair).
- Fica no banco do ServerBuddy (SQLite ou MySQL, tabela `storage_entries`) e sobrevive a reinícios.
- O jogador é avisado quando recebe uma recompensa e quando entra com recompensas pendentes.
- Para entregar direto, como antes: `"redeem_to_storage": false` em `Storage/config.json`.

### Códigos de resgate com itens e comandos

Em `Redeem/redeem.conf`, cada código aceita:

```json
"verao": {
  "max_usos": 0,
  "itens": [
    { "item": "minecraft:diamond", "amount": 2, "name": "<aqua>Diamante do verão", "lore": ["<gray>Código %code%"] },
    { "item": "cobblemon:poke_ball", "amount": 10 }
  ],
  "comandos": [ "give %player% minecraft:golden_apple 1" ],
  "exibicao": { "title": "<gold>Maçã do código <yellow>%code%", "icon": "minecraft:golden_apple", "lore": ["<gray>Um presente"] },
  "entradas": [
    { "title": "<green>Bônus", "icon": "minecraft:cookie", "lore": ["<gray>5 cookies"], "commands": ["give %player% minecraft:cookie 5"] }
  ]
}
```

- `itens`: cada item vira uma recompensa na aba Itens (mesmos campos do [item editável](#item-editável-o-mesmo-em-todos-os-módulos-novos)).
- `comandos`: todos juntos viram **uma** recompensa na aba Comandos; `exibicao` define o título, o ícone e a descrição dela.
- `entradas`: recompensas de comando separadas, cada uma com o seu título, ícone e comandos.
- Os comandos rodam como console quando o jogador clica; `%player%` é o jogador. Os códigos antigos (só `comandos`) continuam valendo.

### Comandos de admin

```
/sb storage add <jogador> hand [quantidade]            o item da sua mão
/sb storage add <jogador> item <item> [quantidade]     ex.: minecraft:diamond 5
/sb storage add <jogador> command <comando>            roda como console quando o jogador clica
/sb storage list <jogador>
/sb storage clear <jogador> [items|commands]
/sb storage reload
```

O jogador pode estar offline (nome de quem já entrou no servidor, ou o UUID). A quantidade vai até 99 (o limite de
uma pilha guardada); para mais, guarde várias recompensas. Guardar um **comando** exige `serverbuddy.storage.admin.command`
(padrão: nível 4), porque ele roda como console.

### Config (`Storage/config.json`)

Todos os textos são MiniMessage e todos os ícones usam o [item editável](#item-editável-o-mesmo-em-todos-os-módulos-novos).
O menu segue o formato dos outros menus do ServerBuddy:

- `title`, `type` (`GENERIC_9x6` etc.), `entry_slots` (onde ficam as recompensas; aceita `"0-44"`), `fillers` (fundo).
- `tabs` (`items`, `commands`): `slots`, `title`, `icon` e `selected_icon` (a aba aberta).
- `buttons` (`previous`, `next`, `close`, `claim_all`): `slots`, `enabled`, `hide_when_unavailable`, `icon`.
- `empty_icon` (aba vazia), `item_entry.extra_lore`, `command_entry.icon` e `command_entry.default_title`.
- `click.claim` (quais cliques resgatam), `close_on_claim`, `default_tab`, `notify_on_join`, `notify_on_receive`.
- `messages`: todas as mensagens.

Valores disponíveis: no título `%tab% %page% %pages% %amount% %total%`; nos botões e abas `%page% %pages% %tab% %amount%`;
nas recompensas `%source%` (de onde veio); no ícone de comando `%title% %lore% %source%`.

Outros módulos (em Java ou Kotlin) podem mandar prêmios para cá com `StorageService.addItem(uuid, stack, origem)` e
`StorageService.addCommands(uuid, spec, origem)`.

## Fichas (`/poketoken`) — precisa do Cobblemon

Itens que mudam um Pokémon. Os tipos:

| Tipo | O que faz |
|---|---|
| `SHINY` | clique direito num Pokémon **seu** e ele vira shiny |
| `GENDER` | clique direito num Pokémon seu e muda o sexo: `"gender": "MALE"`, `"FEMALE"` ou `"SWITCH"` (vira o outro) |
| `SHINY_BALL` | uma bola do Cobblemon: se ela capturar um Pokémon, ele vira shiny |

Já vêm configuradas `shiny`, `shiny_ball`, `gender` (troca), `gender_male` e `gender_female`.

- Comandos: `/poketoken give <jogador> <ficha> [quantidade]` e `/poketoken list` (também em `/sb poketoken ...`).
  Permissão: `serverbuddy.poketoken.give`.
- A ficha SHINY/GENDER só funciona no próprio Pokémon, fora de batalha. Ela é gasta quando funciona (`"consume": true`)
  e **não** é gasta se o Pokémon já era shiny, já tinha aquele sexo ou não pode mudar.
- `blacklist` por ficha: `species` (ex.: `["mewtwo"]`) e `labels` do Cobblemon (ex.: `["legendary", "mythical"]`).
  Na Shiny Ball, um Pokémon bloqueado é capturado normalmente, só não vira shiny.
- **Shiny Ball**: o `item` precisa ser uma bola do Cobblemon (`cobblemon:poke_ball`, `cobblemon:master_ball`...). O jogador
  joga como uma bola comum. Se a bola errar e cair no chão, ela volta como Shiny Ball (e não como uma bola comum).
- O item de cada ficha é o [item editável](#item-editável-o-mesmo-em-todos-os-módulos-novos): item, nome, descrição,
  `custom_model_data`, `stackable` (juntar ou nunca juntar), `hide_tooltip` e `hide_additional_tooltip`.
  Dá para criar quantas fichas quiser em `PokeTokens/config.json` (`tokens`). Recarregue com `/sb reload`.
- As fichas ficam marcadas por dentro (`serverbuddy_token`), então mudar o nome ou o item na config não quebra as fichas
  que os jogadores já têm.

## Ver o time e o PC (`/pokesee`, `/compsee`) — precisa do Cobblemon

- `/pokesee [jogador]` abre o time (6 Pokémon) e `/compsee [jogador]` o PC, caixa por caixa. Só para ver: os cliques
  não mexem em nada. Funciona com jogadores offline. `/pokeseeother <jogador>` e `/compseeother <jogador>` são atalhos.
- `PokeView/config.json`: título, tamanho, posição dos Pokémon (`pokemon_slots`), fundo, botões e como cada Pokémon
  aparece (`pokemon_item.name` e `pokemon_item.lore`).
- Valores do Pokémon (menus e gritos): `%name% %species% %nickname% %level% %shiny% %gender% %nature% %ability%
  %held_item% %ball% %ivs% %evs% %move1%`..`%move4% %health% %friendship% %trainer%`. Espécie, natureza, habilidade e golpes saem
  traduzidos no idioma de cada jogador.

## Gritos no chat (`/itemshout`, `/pokeshout`, `/pokeshoutall`)

- `/itemshout` mostra no chat o item da sua mão (passe o mouse para ver o tooltip).
- `/pokeshout <1-6>` mostra um Pokémon do seu time e `/pokeshoutall` o time inteiro; passe o mouse para ver nível,
  natureza, habilidade, IVs, EVs e golpes. (Os dois de Pokémon precisam do Cobblemon.)
- `Shout/config.json`: formatos, textos do hover, tempo de espera (`cooldown_seconds`: 30 s no item e 60 s nos Pokémon;
  `/pokeshout` e `/pokeshoutall` dividem o mesmo tempo) e mensagens. Quem tem `serverbuddy.shout.bypass` nunca espera.

## Taxa de Shiny (`/pokeodds`) — precisa do Cobblemon

`/pokeodds` mostra a taxa atual (`1/8192`) e `/pokeodds setRate <N>` muda a taxa padrão e grava `shinyRate` em
`config/cobblemon/main.json` (vale depois de reiniciar também). Se houver um evento de Shiny ativo (módulo Eventos),
a nova taxa passa a valer quando ele terminar.

## Permissões dos recursos novos

Padrão = quem tem esse nível de operador (ou a permissão, com LuckPerms etc.).

| Permissão | Para quê | Padrão |
|---|---|---|
| `serverbuddy.storage.use` | `/storage` | todos |
| `serverbuddy.storage.admin` | `/sb storage add\|list\|clear\|reload` | nível 2 |
| `serverbuddy.storage.admin.command` | guardar um comando no storage de alguém | nível 4 |
| `serverbuddy.pokesee.use` / `.other` | `/pokesee` / ver o de outro jogador | nível 2 |
| `serverbuddy.compsee.use` / `.other` | `/compsee` / ver o de outro jogador | nível 2 |
| `serverbuddy.pokeshout.use` / `.all` | `/pokeshout` / `/pokeshoutall` | nível 2 |
| `serverbuddy.itemshout.use` | `/itemshout` | nível 2 |
| `serverbuddy.shout.bypass` | sem tempo de espera nos gritos | nível 4 |
| `serverbuddy.poketoken.give` | `/poketoken` | nível 2 |
| `serverbuddy.pokeodds.use` / `.set` | `/pokeodds` / `/pokeodds setRate` | nível 2 |

Com `legacyPermissions` ligado, quem já tem `cobblemonextras.command.pokesee`, `pokeseeother`, `compsee`, `compseeother`,
`pokeshout`, `pokeshoutall`, `itemshout` ou `pokeodds` continua com o equivalente.

> **Mods com comandos de mesmo nome:** `/storage`, `/pokesee`, `/compsee`, `/pokeshout`, `/itemshout` e `/pokeodds`
> também existem em mods como o CobbleUtils e o CobblemonExtras. Em `config/ServerBuddy/modules.conf` → `compat` você
> escolhe: `AUTO` (com o outro mod instalado, o do ServerBuddy vira `/sbstorage`, `/sbpokesee`...), `SERVERBUDDY`
> (o do ServerBuddy fica com o nome e o do outro sai) ou `OUTRO`. Veja [docs/MODULOS.md](docs/MODULOS.md#convivência-com-outros-mods).
> As permissões dos módulos novos estão no topo de cada arquivo de config e em `docs/MODULOS.md`.

## Trocando os mods antigos pelo ServerBuddy

1. Pare o servidor e tire da pasta `mods/` o `great-server-utils` e os JARs `SkiesGUIs`, `SkiesKits`, `SkiesShop`,
   `SkiesCrates`, `SkiesAnnouncements` e `SkiesClear`. Coloque o `serverbuddy-<versão>.jar`.
2. Na **primeira execução** o ServerBuddy copia as configurações antigas, sem apagar nada:
   `config/GreatServerUtils` → `config/ServerBuddy` (a pasta `GreatLogs` vira `Logs`) e
   `config/skiesguis`, `skieskits`, `skiesshop`, `skiescrates`, `skiesannouncements`, `skiesclear` →
   `config/ServerBuddy/GUIs`, `Kits`, `Shop`, `Crates`, `Announcements`, `Clear`.
   O formato dos arquivos é o mesmo dos mods originais.
3. Compatibilidade (ligada por padrão, em `serverconfig.conf`):
   - `legacyCommands`: os comandos raiz antigos continuam existindo (`/skieskits`, `/crates`, `/gui`, `/sg`...).
     Atenção: as configs das GUIs costumam chamar esses comandos ao clicar (ex.: `gui open rtp`). Se você desligar
     essa chave, troque-os pelos novos (`sb guis open rtp`). Os comandos definidos nas próprias configs (`kit`, `kits`,
     `shop`...) valem sempre, com a chave ligada ou não.
   - `legacyPermissions`: os nós de permissão antigos continuam valendo (`skieskits.kit.diario`,
     `skiesguis.open.<gui>`, `greatserverutils.hunt`...), além dos novos `serverbuddy.<módulo>.…`.
   - Placeholders: continuam os namespaces antigos (`skieskits`, `skiesshop`, `skiescrates`) e existem os novos
     (`serverbuddy_kits`, `serverbuddy_shop`, `serverbuddy_crates`).
   - Itens de boss e chaves de caixa que os jogadores já têm continuam valendo.
4. O que mudou de nome: `/greatutils` → `/serverbuddy` (`/sb`), `/greateditor` → `/pokeeditor`,
   `/greatbuilder` → `/pokebuilder`, `%greatutils:event_timer%` → `%serverbuddy:event_timer%`.

### Caçadas: uma mensagem só

Quando várias caçadas vencem juntas (o normal, porque elas nascem juntas), o ServerBuddy troca todas e manda **uma
única mensagem** ("As caçadas foram resetadas! Novos Pokémon estão disponíveis em /hunt") em vez de um "expirou" e
um "nova caçada" para cada uma. Em `Hunt/`: `agruparAvisos` (liga/desliga) e `mensagens.resetadas` (o texto).

## Compilando

Precisa de **JDK 21**. No Windows:

```
gradlew.bat build
```

O JAR fica em `build/libs/serverbuddy-<versão>.jar`. O primeiro build baixa o Gradle, o Minecraft e as dependências.

Servidor de teste local (cria `run/`; é preciso aceitar o EULA da Mojang em `run/eula.txt`):

```
gradlew.bat runServer                    (com Cobblemon)
gradlew.bat runServer -PsemCobblemon     (sem Cobblemon, para testar o ServerBuddy sozinho)
```

Os arquivos de `libs-compile/` servem para compilar as integrações. A API do GooeyLibs também é embutida.
Para habilitar hologramas nas caixas, instale o HoloDisplays no servidor. As demais bibliotecas
embutidas estão listadas no [componentes aninhados](#componentes-aninhados).

### JAR para distribuição

```powershell
.\gradlew.bat clean build
```

O JAR para o Modrinth é `build/libs/serverbuddy-<versão>.jar`. Use `-PapiBase=http://localhost:3001` para apontar um build de teste para o site local.

## English project description

ServerBuddy is a server-side toolkit for Minecraft Java 1.21.1 on Fabric and Java 21. It adds
server commands, menus, kits, shops, crates, announcements, events, moderation and reward storage.
Cobblemon is optional and adds Pokémon features such as hunts, bosses and Pokédex rewards.
Install the ServerBuddy JAR and Fabric API on the server. The mod includes Fabric Language Kotlin
and Text Placeholder API. HoloDisplays is optional for crate holograms.

The web dashboard is optional. It connects only after a server administrator runs
`/sb dashboard sync`. Pairing sends the installation ID, server name and requesting administrator’s
name to servebuddy.site. During a linked session, server telemetry and data requested by the dashboard
can be sent to that service. Set `enabled` to `false` in `config/ServerBuddy/Dashboard/config.json`
to disable pairing. Some dashboard tools require a paid plan on the website.

## Inspiração e código usado

O ServerBuddy nasceu inspirado em outros mods de Minecraft. Para criar os módulos de menus, kits, loja, caixas, anúncios e limpeza, usei e adaptei código dos mods da equipe PokeSkies:

- [SkiesGUIs](https://github.com/PokeSkies/SkiesGUIs), [SkiesKits](https://github.com/PokeSkies/SkiesKits), [SkiesShop](https://github.com/PokeSkies/SkiesShop), [SkiesCrates](https://github.com/PokeSkies/SkiesCrates) e [SkiesAnnouncements](https://github.com/PokeSkies/SkiesAnnouncements), sob MPL-2.0.
- [SkiesClear](https://github.com/PokeSkies/SkiesClear), sob Apache-2.0.

O mod também reaproveita código do GreatServerUtils, meu projeto anterior, sob CC0-1.0. As licenças acompanham o JAR em `META-INF/licenses/`.

### Componentes aninhados

| Componente | Origem |
|---|---|
| Adventure Platform for Fabric e MiniMessage | [KyoriPowered](https://github.com/KyoriPowered) |
| Blockbench Import Library | [tomalbrc/blockbench-import-library](https://github.com/tomalbrc/blockbench-import-library) |
| Caffeine | [ben-manes/caffeine](https://github.com/ben-manes/caffeine) |
| Fabric Language Kotlin | [FabricMC/fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin) |
| Fabric Permissions API | [lucko/fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) |
| Fantasy | [NucleoidMC/fantasy](https://github.com/NucleoidMC/fantasy), LGPL-3.0 |
| GooeyLibs API | [landonjw/GooeyLibs](https://github.com/landonjw/GooeyLibs), versão 3.1.1 |
| H2 | [h2database/h2database](https://github.com/h2database/h2database) |
| HikariCP | [brettwooldridge/HikariCP](https://github.com/brettwooldridge/HikariCP) |
| MariaDB Connector/J | [mariadb-corporation/mariadb-connector-j](https://github.com/mariadb-corporation/mariadb-connector-j) |
| MongoDB Java Driver e BSON | [mongodb/mongo-java-driver](https://github.com/mongodb/mongo-java-driver) |
| MySQL Connector/J | [mysql/mysql-connector-j](https://github.com/mysql/mysql-connector-j) |
| Polymer | [Patbox/polymer](https://github.com/Patbox/polymer) |
| SGui | [Patbox/sgui](https://github.com/Patbox/sgui) |
| SQLite JDBC | [xerial/sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) |
| Text Placeholder API | [Patbox/TextPlaceholderAPI](https://github.com/Patbox/TextPlaceholderAPI) |

HoloDisplays é uma integração opcional. Instale o [mod original](https://modrinth.com/mod/holodisplays) para
usar hologramas nas caixas. Os scripts que usam GraalVM dependem da integração com Cobblemon.

### Dependências locais para compilar

O checkout público contém o código-fonte do mod. O Gradle também precisa dos seguintes
artefatos em `libs-compile/`, obtidos com os respectivos projetos:

- `GooeyLibs-Fabric-3.1.1-1.21.x.jar` e seu `api-repack-3.1.1-1.21.x.jar` (extraído de `META-INF/jars/`)
- `holodisplays-0.4.8+1.20.6.jar`
- `CobbleDollars-fabric-2.0.0+Beta-5.1+1.21.1.jar`
- `pebbles-economy-1.0.0.jar`
- `beconomy-1.5.jar`

Com esses arquivos e Java 21, execute `gradlew build`.
