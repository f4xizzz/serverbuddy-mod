# Arquitetura do ServerBuddy

```
com.f4xizzz.serverbuddy
├── core/                 base compartilhada por todos os módulos
│   ├── Modules, SbModule, ModuleSettings   lista de módulos, liga/desliga (modules.conf), nomes e conflitos de comando
│   ├── SbCommands, ModuleCommands          registro de comandos (depois dos outros mods) e montagem em /serverbuddy
│   ├── SbGson, ConfigFiles, config/        leitura das configs (enums tolerantes), arquivo comentado (@Doc), check/update
│   ├── Texts, SoundSpec, ItemSpec, Durations, TimeConditions   MiniMessage, sons, itens editáveis, tempos e condições de horário
│   ├── MenuParts, SbGui                    botões, fundo e "sem inventário embaixo" dos menus de baú (sgui)
│   ├── Economy, SbPermissions, SbPlaceholders, ServerScheduler, CommandHooks
│   └── rewards/                            Reward + RewardService: o formato único de recompensa (item, money, command...)
├── modules/              módulos da fase 2 (um pacote cada)
│   ├── motd  tags  blacklist  trivia  missions  battlepass  essentials  commandblock  chatguard  flan
├── storage/ shout/ cobblemon/features/     módulos novos da fase 1
├── guis/ kits/ shop/ crates/ announcements/ clear/   código portado dos mods PokeSkies
└── cobblemon/ events/ redeem/ logs/ ...    módulos que vieram do GreatServerUtils
```

## Regras que todos os módulos seguem

1. **Um módulo = `SbModule`** (`id`, pasta em `config/ServerBuddy/<Pasta>`, `init()` e `reload()`), registrado em `core/Modules.kt`.
   Um módulo com problema ou desligado não derruba o servidor nem os outros.
2. **Config = uma classe Kotlin com `@Doc`**: o arquivo é gerado com os comentários, lido com `ConfigFiles.load(pasta, arquivo, Classe) { padrão }`
   e entra sozinho no `/sb config check|update` (arquivos que o usuário cria à vontade, como as pools e categorias, carregam com `track = false` e ficam de fora).
   Valores do dia a dia (textos, itens, sons, slots) ficam na config; nada de texto fixo para o jogador.
3. **Comandos** nascem com `SbCommands.register(dispatcher, "nome")` (o nome pode ser trocado em `modules.conf` e some do caminho se outro mod o usa).
   Apelidos de comando usam `SbCommands.alias` (o `redirect` sozinho do Brigadier não leva o `executes`: um apelido de comando sem argumentos dava "comando desconhecido",
   e um apelido com o nome de um comando que já existe, como o `/return` do Minecraft, se misturaria com ele). Condições escritas em texto (`tag:x`, `permission:y`, `time:...`) vêm de
   `core/Conditions`; bloquear/observar comandos de jogadores é pelo `CommandHooks` (a cela e o bloqueio de comandos usam). Os comandos de módulo
   são montados também em `/serverbuddy <módulo>`. Permissões: `serverbuddy.<módulo>.<ação>` com `SbPermissions` (aceita os nós dos mods antigos).
4. **Cobblemon, Flan, LuckPerms, GreatCosmetics são opcionais**: o código que toca neles fica em classes separadas, só carregadas depois de
   `Integrations.isLoaded(...)`. Mixins ficam em Java, com config própria (`serverbuddy.<nome>.mixins.json`) e um plugin que os desliga se o mod alvo não existe
   (ou se outro mod faria o mesmo, como o MiniMOTD).
5. **Banco**: tabelas `sb_*` no banco do ServerBuddy (SQLite ou MySQL, SQL simples dos dois lados). O que o jogador ganha é gravado **antes** de a recompensa sair.
6. **Recompensas** sempre por `RewardService`; um módulo novo que dá prêmio não inventa formato. Para um tipo novo: `RewardService.register(Type(...))`.
7. **Lógica pura separada do Minecraft** (decisões, contas, condições): fica em objetos/funções sem dependência do jogo e tem teste unitário em `src/test`.

## Onde ficam os mixins e os eventos do chat

- `serverbuddy.mixins.json` (sempre carregado, cada um confere uma lista/flag em memória e sai logo): `CommandExecuteMixin` (gancho de comandos digitados, `CommandHooks`), `LivingEntitySleepMixin` (o `/sleep`),
  `SignFormatMixin` (placas coloridas) e `ServerPlayerTabNameMixin` (nome na lista de jogadores). Os mixins de módulos opcionais ficam em arquivos próprios (`flan`, `motd`, `blacklist`, `crates`).
- Chat: **um só caminho** (`core/ChatPipeline.kt`) em `ALLOW_CHAT_MESSAGE`. Cada módulo registra o seu ouvinte numa **etapa** e as etapas rodam SEMPRE na mesma ordem, qualquer que seja o módulo que subiu primeiro:
  `GATE` (mute e chat da equipe do Essentials) → `ANSWER` (trivia: vê a mensagem antes de todos, para a resposta certa nunca ser comida pelo gerenciador ou pelo formato) → `GUARD` (`ChatGuard`: repetidas, flood e ban de chat;
  a resposta que acabou de vencer o trivia não conta como repetição, e quem está banido do chat não responde) → `FORMAT` (`Styling`, o formato do chat: cancela a linha do jogo e manda a própria). `GATE`, `ANSWER` e `GUARD` rodam
  numa fase **antes** dos ouvintes de outros mods e `FORMAT` numa fase **depois** (o que outro mod barrou não é reenviado com o nosso formato). Um módulo novo que mexe no chat registra uma etapa em vez de usar o evento direto
  (`ChatPipelineTest` confere a ordem com todas as permutações de registro). O aviso de entrada, saída, morte e conquista vem de `ALLOW_GAME_MESSAGE` (pela chave de tradução) e o `/me` de `ALLOW_COMMAND_MESSAGE`.
- Texto escrito por jogador (chat e placas) passa por `Texts.parseRestricted` (só cor, decoração, gradiente, arco-íris e reset); o MiniMessage completo é só para texto de config.

## Testes

- Unitários: `gradlew test` (regras de blacklist, trivia, missões, passe, flan, MOTD, config, permissões, slots, comandos).
- Com jogador de verdade (bot): `D:\mods\ServerBuddy-pesquisa\bot-tests` (veja o README de lá) contra `gradlew runServer`
  (o servidor de teste também carrega o Flan completo, só para o desenvolvimento; ele não vai no JAR).

## Dashboard e distribuição

O build gera um JAR com os módulos do ServerBuddy. O site aplica o teste gratuito e a assinatura da conta às funções da dashboard.

- **Dashboard** (`dashboard/*`): `DashboardClient` abre um WebSocket de saída com o token do pareamento; `DashboardProviders` atende os métodos (`overview.get`, `config.*`, `players.*`, `tops.*`,
  `console.run`, `luckperms.*`...) conferindo o plano de novo do lado do mod; tudo que muda algo vai para o registro de auditoria. `ConfigSchema` monta o formulário de cada config a partir dos `@Doc`.
- **Jogadores offline** (`dashboard/PlayersView`, `dashboard/ItemViews`, `core/PlayerData`): a lista junta quem está online com os arquivos de `world/playerdata`, o `usercache.json` e a última
  visita do Essentials (um filtro online/offline, busca por nome ou começo do UUID, ordem e paginação). Perfil, inventário e baú do fim de quem saiu vêm de `playerdata/<uuid>.dat` por um leitor
  NBT seguro e reaproveitável (`PlayerDataFiles`: só arquivo comum dentro da pasta, sem seguir link, limite de tamanho, devolve uma cópia, cache por data e tamanho). O time e o PC saem do
  armazenamento do Cobblemon, que carrega do disco sem o jogador; a Pokédex, direto do arquivo com o codec do próprio Cobblemon. Regra: **olhar não escreve**. O carregador do Cobblemon cria ou
  "conserta" arquivo que falta ou não abre, guarda o que carrega na memória e o regrava no próximo "salvar tudo" (e entrega esse objeto no próximo login do jogador), por isso a Pokédex offline
  é lida por `CobblemonDexStats.readOffline` e o time e o PC carregados só para olhar são soltos da memória logo em seguida (`CobblemonView.release`). Uma resposta lida nos primeiros segundos
  depois que o jogador saiu não vai para o cache (o Cobblemon grava em segundo plano) e o cache de um jogador é esquecido quando ele volta. Teste: `bot-tests/dashboard-players-test.js`.
- **Configurações no painel** (`core/ConfigFiles`, `core/ConfigAdapters`, `dashboard/ConfigSchema`, `dashboard/ConfigTools`): todo arquivo de `config/ServerBuddy` que é opção de módulo aparece no painel
  (`config.list/get/schema`) e pode ser editado (`config.validate/save/restore/reload`). Um arquivo entra de três jeitos: `ConfigFiles.load` (os módulos novos, em Kotlin: o arquivo também entra no
  `/sb config check|update`), `ConfigFiles.register` (o código que tem carregador próprio: os `*ConfigManager` Java e os módulos portados, com a classe que descreve o arquivo, o Gson do módulo e,
  se houver, a recarga) e `ConfigFiles.collection` (uma pasta de conteúdo: cada arquivo aparece sozinho, como kits, caixas e piscinas de trivia). **Um `*ConfigManager` ou um arquivo novo precisa
  ser registrado em `core/ConfigAdapters.kt` (os que dependem do Cobblemon em `CobblemonConfigAdapters.kt`)**; o que não é opção de módulo (logs, bancos, `.bak`, estado e dados que o mod escreve, o
  login do banco) fica na lista de exclusão comentada de `bot-tests/config-coverage-test.js`, que FALHA se sobrar um arquivo sem registro nem motivo. O formulário sai da classe por reflexão
  (com os `@Doc` como ajuda); `config.validate` confere o formato de cada opção e a carga pelo próprio Gson do arquivo (um valor de formato inesperado é deixado para a carga, porque muitas
  opções aceitam mais do que o tipo Java diz, como listas de slots `"0-44"`; opção desconhecida é só aviso). Salvar guarda antes uma cópia `<arquivo>.<aaaammdd-hhmmss-mmm>.bak` (as 20 últimas, mais o
  `.bak` único de sempre), anota na auditoria os nomes das opções que mudaram e a restauração só aceita o nome de uma cópia da lista. Testes: `ConfigFilesTest`, `ConfigToolsTest`,
  `bot-tests/config-coverage-test.js` e `bot-tests/config-tools-test.js`.
- **Redeem, Tags e grupos do LuckPerms no painel** (`dashboard/RedeemView`, `TagsView`, `LuckPermsView`): Redeem e Tags (Plus) mostram o que o mod tem (código com estado ativo, esgotado ou vencido, quem resgatou
  e quando, tags do catálogo e o que cada jogador tem, também offline) e deixam criar, editar e apagar (o formulário sai da classe do código ou da tag por reflexão, com a validação do mod no campo);
  salvar guarda antes uma cópia do arquivo no histórico das configs e relê o arquivo antes de gravar, para uma edição à mão não se perder. O primeiro salvamento pelo painel normaliza um
  `redeem.conf` antigo para as chaves em inglês (o conteúdo é o mesmo; a cópia do arquivo antigo fica no histórico). Os grupos do LuckPerms (Pro) usam a API do LuckPerms: criar e apagar grupo,
  peso, nome, prefixo, sufixo, herança (recusa círculo), permissões sim/não com validade e contexto e os membros. Ação que pode machucar pede `confirm: true` (apagar grupo, dar `*`) e o grupo
  `default` não se apaga. Toda ação que muda algo vai para a auditoria com o NOME do que mudou (nunca valores). Testes: `bot-tests/redeem-dashboard-test.js`, `tags-dashboard-test.js` e
  `luckperms-dashboard-test.js` (este com `gradlew runServer -PcomLuckPerms`).

- **Config secrets** (`dashboard/ConfigSecrets`): one canonical-name list covers database passwords, connection strings and Discord webhook URLs, including suffixes and Gson aliases. The mod sends only set/empty markers, never values or secret defaults. Saving resolves markers against the current file, preserves omitted/null secrets, rejects an unreadable source and leaves a no-op byte-for-byte unchanged. Diffs, search, errors and outgoing logs are redacted; known previous credentials stay hidden throughout the server session. A restore captures its bytes before backup retention can replace or prune its source. The web form keeps new values in private markers, hides them from JSON/export/review and retains each list item's identity across reordering. Tests: `ConfigSecretsTest` and `bot-tests/config-secrets-test.js` (all registered files; raw RPC responses, aliases, save/clear/restore, audit and logs).