# Testes com jogador de verdade (bot) para o ServerBuddy

Scripts em Node que entram no servidor de teste local (`D:\mods\ServerBuddy\run`, `gradlew runServer`) com um bot
([mineflayer](https://github.com/PrismarineJS/mineflayer)) e usam o RCON para preparar o cenário. Cobrem o que não dá
para conferir só compilando: `/storage` (menu, abas, páginas, resgate), `/redeem` entregando no storage, fichas num
Pokémon de verdade, Shiny Ball, `/pokesee`, `/compsee`, gritos, permissões e `/pokeodds` (fase 1) e, na fase 2, o MOTD,
as tags, a blacklist, o trivia, as missões, o passe de batalha, o essentials, o bloqueio de comandos e as regras de claims do Flan (entrada e o que se ganha ao entrar).

## Preparar (uma vez)

1. Instale o mineflayer numa pasta **separada dos scripts** (os scripts procuram em `..\bot-tests-lib`):

   ```
   npm install --prefix D:\mods\ServerBuddy-pesquisa\bot-tests-lib mineflayer --ignore-scripts
   ```

2. Servidor de teste (`D:\mods\ServerBuddy\run\server.properties`): `online-mode=false`, `enable-rcon=true` com
   `rcon.password` e `rcon.port`, e `op-permission-level=2` (assim o cooldown dos gritos vale também para os ops do teste).
3. Configs de teste (só no `run/`, que o Git ignora):
   - `config/ServerBuddy/PokeTokens/config.json`: `tokens.shiny_ball.item.item` = `cobblemon:master_ball` (captura garantida).
   - `config/ServerBuddy/Clear/config.json`: desligue o clear de exemplo (`"enabled": false`); ele limpa as entidades a cada
     60 s e derruba os testes de Pokémon e de bola.
   - `config/ServerBuddy/Redeem/redeem.conf`: os códigos `teste` e `teste2` (veja o que `redeem-test.js` espera).
   - `ops.json`: o jogador `SbBot` com `"level": 2`; `EsA` e `FlOwner` com `"level": 4` e `CbAdm` com `"level": 3` (o servidor só aceita 2
     jogadores ao mesmo tempo, `max-players=2`). Com o servidor **parado**: `node add-op.js EsA 4` (calcula o UUID offline e grava).
4. Suba o servidor (`gradlew runServer`) e espere o "Done". O Flan completo só entra no `runServer` (é `modLocalRuntime`; não vai no JAR).
   Para o teste **sem Cobblemon** suba com `gradlew runServer -PsemCobblemon` e rode só o `nocobblemon-test.js`; para ver o mod
   **sem o Flan** use `-PsemFlan` (os dois juntos também valem): o servidor tem que subir sem erro e o `/sb modules` mostra o módulo parado.

## Rodar

```
bash run-all.sh            # todos (menos o nocobblemon-test), com um resumo no fim
node storage-test.js       # ou um só
```

Os configs dos módulos novos (Motd, Tags, Blacklist, Trivia, Missions, BattlePass, Essentials, Flan) nascem na primeira
subida do servidor. Os testes de Trivia, Missões e Passe de batalha contam vitórias e progresso no banco: para repetir um
deles do zero, pare o servidor, apague `config/ServerBuddy/<Módulo>` **e** `config/ServerBuddy/Database/serverbuddy.db` e suba de
novo (repetir sem limpar dá falhas só de contagem, como "Vitórias: 3" onde o teste espera 1).

| Script | O que confere |
|---|---|
| `storage-test.js` | `/storage`: título, abas, 3 páginas (100 itens), resgatar, resgatar tudo, comando, inventário cheio, resgate parcial |
| `redeem-test.js` | `/redeem` indo para o storage (itens e comandos em abas), código só uma vez, modo direto (`redeem_to_storage: false`) |
| `tokens-view-test.js` | itens das fichas, `/pokesee`, `/compsee`, `/pokeshout`, `/itemshout`, `/pokeodds` |
| `pokemon-token-test.js` | fichas SHINY e SEXO num Pokémon solto do time, blacklist e "não é o dono" |
| `shinyball-test.js` | Shiny Ball: captura vira shiny, "já era shiny" e a bola que erra volta como Shiny Ball (o teste monta uma plataforma no céu, em 85..125 / 50..120, para a bola não bater no terreno) |
| `misc-test.js` | item da ficha editado (stackable, custom_model_data, hide_tooltip), cooldown, permissões, `/pokeodds setRate` |
| `offline-test.js` | `/pokesee` e `/compsee` de jogador offline |
| `motd-test.js` | MOTD: perfis por situação (whitelist, manutenção), jogadores falsos, dica ao passar o mouse e recusas de login |
| `tags-test.js` | Tags: menu de baú (categorias, páginas, equipar/tirar, comprar, bloqueada), comandos de admin, banco e "sem inventário" |
| `reset-blacklist.js` + `blacklist-test.js` | Blacklist: importação do mod original, contextos (tag e `when`), `/blacklist`, comandos da equipe e arquivos (rode o reset antes de repetir) |
| `trivia-test.js` | Trivia: rodada completa com resposta no chat, prêmios, dicas, tempo esgotado, silenciar, sequência de vitórias e os geradores do Cobblemon |
| `missions-test.js` | Missões: rotação, objetivos por estatística/estado/evento, pré-requisito, resgate por comando e menu, auto-resgate, repetível e banco |
| `missions-global-test.js` | Missões globais: começa sozinha depois do intervalo, o primeiro a completar leva o prêmio (os outros perdem), a próxima rodada, `/missions global start\|stop\|join`, tempo esgotado sem vencedor, modo `ui` (cada um aceita), `target` online x all e o lembrete ao entrar |
| `missions-cobblemon-test.js` | Missões do Cobblemon (objetivo novo `gained`, com filtro de espécie); os outros objetivos novos não têm como ser disparados pelo bot |
| `battlepass-test.js` | Passe de batalha: temporada, XP e níveis, resgate grátis e premium, bônus, recompensa `battlepass_xp` vinda de missão, menu |
| `essentials-test.js` | Essentials: casas, warps, tpa, rtp, msg/ignore, mute, AFK, god/fly/heal, bancadas, `/invsee`, `/tempban`, cela, anotações, `/sudo`, `/restart` |
| `essentials-extras-test.js` | Essentials, o que faltava do Solstice: `/homeother`, `/tppos`, `/tpoffline`, `/tpaall`, `/setfirstspawn` e `/firstspawn`, `/ignorelist`, `/plainbroadcast`, `/motd`, `/ignite`, `/effects`, `/serverstat`, `/activetime`, `/sleep` (de noite, sem cama), `/rocket`, `/nudge`, `/kittycannon`, `/checkjail`, `/doas`, `/powertool`, recarga de comandos (`/cooldown`) e `/timebar` |
| `styling-test.js` | Essentials, estilo: formato do chat por tag, cores só para quem pode (e tags de clique/hover recusadas), trocas, `/ignore` e mute, menções com som e `/notifications`, morte, conquista, `/me`, entrada, saída e primeira entrada, lista de jogadores (cabeçalho, rodapé, nomes, `[AFK]`) e placas coloridas |
| `chatguard-test.js` | Gerenciador de chat: bloqueia mensagens repetidas, 3 avisos, ban de chat que cresce com os bans e os avisos, comandos bloqueados no ban, `/chatguard` da equipe, esquecimento, bypass |
| `flan-test.js` | Flan: botões no menu da claim, entrada por tag e por permissão (união das duas listas), dono isento, `/claimaccess`; e o que a claim dá a quem entra: tag (com marca, só tira o que deu, limpa marca antiga, `remove_on_leave`) e permissão (via `/sb modules`), botões 14 e 15 |
| `commandblock-test.js` | Bloqueio de comandos: `/back` por tag, apelido (`/wb`), várias palavras, `when`/`!`/`time`, `*` com `except`, `/commandblock` da equipe, bypass nível 4, `list\|check\|reload`, regras inertes |
| `nocobblemon-test.js` | servidor **sem** Cobblemon: os módulos novos sobem, o que é de Pokémon não existe, `/storage` e `/itemshout` funcionam |
| `trivia-chat-test.js` | Trivia no caminho único do chat (`ChatPipeline`): a resposta vence com o formato do chat ligado, mesmo repetida (o gerenciador não a come nem avisa), mudo e chat da equipe não vencem, `hide_correct_answer` e resposta errada formatada. Liga e desliga o formato sozinho (restaura as configs) |
| `redeem-permission-test.js` | `/redeem`: permissão por código (jogador comum recusado com o texto do admin e o nó no log; operador passa), expiração (`expires_at` com data, data e hora, ilegível bloqueia), chaves em inglês e em português na mesma config, código sem diferenciar maiúsculas, limite de usos, `/sb redeem list` e `reset`. Com o servidor subido com `gradlew runServer -PcomLuckPerms` confere também a permissão dada a um jogador, o `false` explícito vencendo o nível de operador e a permissão por grupo (`group.<nome>`) |
| `dashboard-sync-test.js` | `/sb dashboard sync`: o código vem clicável (copia) e o link da dashboard abre o navegador (lê os pacotes do chat). **Só contra um site local**: suba o servidor com `JAVA_TOOL_OPTIONS="-Dserverbuddy.api=http://localhost:3001" gradlew runServer`; sem isso o comando fala com a produção |
| `dashboard-players-test.js` | Dashboard, jogadores (plano Plus): o mesmo jogador lido **online e offline** dá a mesma resposta (inventário, baú do fim, time, PC e Pokédex, com nomes e encantos); lista com filtro online/offline, busca por nome e por prefixo de UUID, ordem e paginação, contagens; erros claros (`player_not_found`, `player_data_unreadable`, e um UUID sem arquivo não cria dados do Cobblemon); o que foi lido de um jogador offline é esquecido quando ele volta a jogar; e **olhar não escreve**: quem o servidor conhece mas nunca teve dados do Cobblemon não ganha arquivo (Pokédex, time, PC, dados gerais, TMs), e a Pokédex no disco continua inteira depois das leituras. Usa o `dash-rpc.js`, que age como o navegador (pega o código do `/sb dashboard sync` com um bot, liga uma conta do site e abre o WebSocket). **Só contra um site local**, como o `dashboard-sync-test.js`; variáveis `SB_SITE`, `SB_SITE_DIR`, `SB_ACCOUNT` e `SB_PASSWORD` (no cabeçalho do `dash-rpc.js`) |
| `catchcombo-purge-test.js` | `/sb purgedisplays` e o holograma do ranking do Catch Combo (a entidade mais a linha de `catch_combo_leaderboard_display`, que o ticker de 1 s usa para recriá-lo): cria 2 hologramas a 400 blocos um do outro (`/catchcombo leaderboard`), limpa só o de perto e confere a entidade, a linha no SQLite, que ele NÃO volta 8–12 s depois, que o distante fica e que criar de novo funciona; mensagens em inglês. Lê o banco do servidor de teste (mesmo PC) e usa `forceload` e uma plataforma de pedra em y=250 (tira tudo no fim). Sem Cobblemon (`gradlew runServer -PsemCobblemon`) detecta sozinho e só confere que o purge apaga as entidades de texto, não fala de ranking e não suja o log |
| `config-coverage-test.js` | Cobertura das configs: todo arquivo de `config/ServerBuddy/**` está no painel (`config.list`) ou na lista de exclusão do teste (cada item com o motivo); o que está na lista NÃO aparece no painel (nem o login do banco); `config.get` abre todos os arquivos registrados e `config.validate` aceita os valores que o mod mesmo escreveu (sem falso erro). Um arquivo novo sem registro faz o teste FALHAR. Servidor COM Cobblemon e site local |
| `config-tools-test.js` | O editor de configs ponta a ponta no PokeSnack/config.json (volta ao texto original byte a byte): validar (erro por campo, aviso de opção desconhecida), gravação recusada não grava nem entra na auditoria, salvar guarda a cópia carimbada e devolve o diff, a auditoria anota só os nomes das opções, histórico, restaurar (só o id de uma cópia da lista; `../` recusado), recarregar com o resultado de cada arquivo e a busca em todos os arquivos. Site local |
| `config-secrets-test.js` | Config secrets end to end: injects disposable passwords and webhook URLs into real local files, checks raw RPC responses for all registered configs (including schema defaults), search, validation, no-op saves, replacements, explicit clears, restore, audit and outgoing logs. Fails if a new secret-looking option is neither protected nor justified in the coverage list. Restores the original bytes and removes only its own test backups. Local site and mod only, as in `config-tools-test.js`. |
| `redeem-dashboard-test.js` | Redeem no painel (plano Plus): a lista com estado (ativo, esgotado, vencido), validar (data ilegível, limite negativo), criar/editar/apagar um código (recusa sem gravar, `already_exists`, `not_found`), um bot de verdade resgata e o painel mostra quem e quando, zerar um jogador ou todos, a auditoria com o NOME das opções que mudaram. Deixa o `redeem.conf` como estava. Site local |
| `tags-dashboard-test.js` | Tags no painel (plano Plus): o catálogo (validar, criar as próprias tags do teste, editar, apagar), o que um jogador online e OFFLINE tem, dar, tirar e usar uma tag (`already_has`, `not_owned`, `player_offline`), o aviso no chat do jogador e a auditoria. Deixa o `tags.json` como estava. Site local |
| `luckperms-dashboard-test.js` | Grupos do LuckPerms no painel (plano Pro), contra o LuckPerms de verdade (suba com `gradlew runServer -PcomLuckPerms`; sem ele o teste avisa e sai): criar e apagar, peso/nome/prefixo/sufixo, herança (sem círculo), permissões sim/não com validade e contexto, `*` e apagar só com `confirm`, `default` protegido, membros e a auditoria. Apaga tudo o que cria. Site local |

Para conferir a memória (as lojas do Cobblemon de um jogador offline não podem ficar guardadas depois de uma consulta da dashboard), rode o teste e conte as instâncias vivas com `jcmd <pid> GC.class_histogram | grep -E "PlayerPartyStore|PCStore"`: sem nenhuma linha = nenhuma instância.

## Limitações do bot (não são do mod)

- O mineflayer não entende itens e entidades do Cobblemon. O `lib.js` troca o pacote que ele não consegue ler por um
  pacote vazio (senão a leitura de tudo que vem depois para) e desliga o agrupamento de pacotes. Por isso o conteúdo
  de `/pokesee` e `/compsee` é conferido pelos bytes do pacote (chaves de tradução, estrela do shiny).
- Os nomes saem como chaves (`cobblemon.species.pikachu.name`): o bot não tem o idioma do Cobblemon.
- Para soltar o Pokémon, o bot olha para o chão e manda o pacote `cobblemon:send_out_pokemon` (o Cobblemon só solta se o
  olhar achar chão seguro). O id de rede da entidade vem do payload `cobblemon:spawn_pokemon_entity`.
- O Snorlax é o alvo da Shiny Ball porque a bola do Cobblemon sobe e passa por cima de um Pokémon pequeno.
- A recusa de um golpe da Blacklist **dentro de uma batalha de verdade** não dá para fazer com o bot (as escolhas de batalha
  são pacotes do Cobblemon): a regra é testada pelos comandos e por testes unitários, e o mixin foi conferido no bytecode.
- Onde o bot entra (2,5 / 89 / −3,5) o terreno tem um degrau na altura da cabeça: uma bola lançada para o sul bate nele e
  cai como item ao lado do bot. Testes que lançam bola precisam de um chão aberto (o `shinyball-test.js` monta o dele).
- Os JSONs de config nascem com comentários `//`; use `readJson`/`parseJson` do `lib.js` para ler (o `JSON.parse` puro falha).
