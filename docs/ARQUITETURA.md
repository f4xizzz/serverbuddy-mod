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
- Chat: o `ChatGuard` ouve `ALLOW_CHAT_MESSAGE` numa fase **antes** do padrão; o mute e o chat da equipe do Essentials ficam na fase padrão; o estilo (`Styling`) numa fase **depois**. O aviso de entrada, saída,
  morte e conquista vem de `ALLOW_GAME_MESSAGE` (pela chave de tradução) e o `/me` de `ALLOW_COMMAND_MESSAGE`.
- Texto escrito por jogador (chat e placas) passa por `Texts.parseRestricted` (só cor, decoração, gradiente, arco-íris e reset); o MiniMessage completo é só para texto de config.

## Testes

- Unitários: `gradlew test` (regras de blacklist, trivia, missões, passe, flan, MOTD, config, permissões, slots, comandos).
- Com jogador de verdade (bot): `D:\mods\ServerBuddy-pesquisa\bot-tests` (veja o README de lá) contra `gradlew runServer`
  (o servidor de teste também carrega o Flan completo, só para o desenvolvimento; ele não vai no JAR).

## Dashboard e distribuição

O build gera um JAR com os módulos do ServerBuddy. O site aplica o teste gratuito e a assinatura da conta às funções da dashboard.

- **Dashboard** (`dashboard/*`): `DashboardClient` abre um WebSocket de saída com o token do pareamento; `DashboardProviders` atende os métodos (`overview.get`, `config.*`, `players.*`, `tops.*`,
  `console.run`, `luckperms.*`...) conferindo o plano de novo do lado do mod; tudo que muda algo vai para o registro de auditoria. `ConfigSchema` monta o formulário de cada config a partir dos `@Doc`.
