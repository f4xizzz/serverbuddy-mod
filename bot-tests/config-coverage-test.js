// Cobertura das configs: TODO arquivo de config/ServerBuddy/** ou aparece no painel (config.list) ou está na lista EXCLUDED abaixo, cada um com o motivo.
// Um arquivo novo (um *ConfigManager novo, uma pasta de conteúdo nova) que ninguém registrou em core/ConfigAdapters.kt faz este teste FALHAR.
// Também confere que o painel consegue abrir (esquema + valores) e validar TODOS os arquivos que o próprio mod escreveu, sem falso erro.
// SÓ contra um site local (veja dash-rpc.js) e com o servidor de teste COM Cobblemon, depois de ele ter subido uma vez (ele cria os arquivos padrão).
const fs = require('fs');
const path = require('path');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const ROOT = 'D:/mods/ServerBuddy/run/config/ServerBuddy';

/**
 * O que NÃO é config de módulo e por isso não precisa estar no painel. Cada linha: [onde, por quê]. Se algo daqui virar config de verdade,
 * tire da lista e registre em core/ConfigAdapters.kt (o teste de baixo garante que o que está na lista NÃO aparece no painel por engano).
 */
const EXCLUDED = [
  [/^Logs\//, 'logs que o mod escreve (de jogadores e de mortes, compactados por dia)'],
  [/\.(bak|old|tmp)$/, 'cópias de segurança e temporários do mod (o histórico do painel é feito destas cópias)'],
  [/\.db(-shm|-wal)?$/, 'bancos de dados SQLite'],
  [/^dashboard-audit\.log$/, 'registro do que o painel fez'],
  [/^instance\.id$/, 'identificador desta instalação (gerado pelo mod)'],
  [/^Database\/database\.conf$/, 'login do banco de dados (host, usuário, senha): edita-se à mão e NUNCA aparece no painel'],
  [/^Motd\/state\.json$/, 'estado que o módulo MOTD guarda (não é opção)'],
  [/^BattlePass\/season\.json$/, 'estado da temporada do passe de batalha (número, início e fim): o mod escreve'],
  [/^Events\/status_evento\.json$/, 'estado dos eventos (ativos, última atualização): o mod escreve'],
  [/^Portals\/portals\.json$/, 'portais criados com /sb portal (dados, não opções)'],
  [/^Worlds\/worlds\.json$/, 'mundos criados com /sb worlds (dados, não opções)'],
  [/^Flan\/restrictions\.json$/, 'regras de cada claim, gravadas pelos botões do menu do Flan (dados)'],
  [/^DexRewards\//, 'módulo portado que guarda as opções em campos estáticos com leitor próprio (/dexrewards reload): não há classe para descrever'],
  [/^(Crates|Shop)\/lang\.json$/, 'arquivos de idioma lidos por um `object` de propriedades: não há classe para descrever; edita-se à mão'],
  [/^Shop\/presets\//, 'pedaços de entradas de loja (um mapa de entradas), não um arquivo de config inteiro; editados dentro das lojas'],
];

/** Pastas que só existem com Cobblemon: sem ele os arquivos de uma rodada antiga ficam no disco mas não são carregados. */
const COBBLEMON_ONLY = /^(CatchArea|CatchCombo|Hunt|PokeBuilder|PokeEditor|DexRewards|Daycare|STS|WonderTrade|GTS|PokeSnack|PokeTokens|PokeView|Blacklist|AstralTrader)\//;

function walk(dir, base = '') {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const rel = base ? base + '/' + entry.name : entry.name;
    if (entry.isDirectory()) out.push(...walk(path.join(dir, entry.name), rel));
    else out.push(rel);
  }
  return out;
}

const excludedBecause = (file) => (EXCLUDED.find(([pattern]) => pattern.test(file)) || [])[1];

(async () => {
  const withCobblemon = !/Unknown or incomplete/i.test(t.rcon('catchcombo info'));
  console.log(`== servidor ${withCobblemon ? 'COM' : 'SEM'} Cobblemon`);
  const dash = await connect();
  try {
    const list = await dash.rpc('config.list');
    const files = list.result.files;
    const key = (f) => (f.module ? f.module + '/' : '') + f.file;
    const registered = new Set(files.map(key));
    t.check('config.list não repete arquivo', registered.size === files.length, `${files.length} linhas, ${registered.size} diferentes`);

    console.log('== todo arquivo do disco está no painel ou na lista de exclusão');
    const disk = walk(ROOT);
    const unexplained = disk.filter((f) => !registered.has(f) && !excludedBecause(f) && (withCobblemon || !COBBLEMON_ONLY.test(f)));
    t.check(`nenhum arquivo de config sem registro nem motivo (${disk.length} no disco, ${registered.size} no painel)`, unexplained.length === 0, unexplained.slice(0, 12).join(', ') + (unexplained.length > 12 ? ` ... +${unexplained.length - 12}` : ''));
    const leaked = [...registered].filter((f) => excludedBecause(f));
    t.check('o que está na lista de exclusão NÃO aparece no painel (nem o login do banco)', leaked.length === 0, leaked.join(', '));
    t.check('os gerenciadores Java estão no painel (Redeem, serverconfig' + (withCobblemon ? ', Catch Combo, Catch Area, Hunt, PokeBuilder, PokeEditor' : '') + ')',
      ['Redeem/redeem.conf', 'serverconfig.conf', ...(withCobblemon ? ['CatchCombo/catchcomboconfig.conf', 'CatchArea/catcharea.conf', 'Hunt/hunt.conf', 'PokeBuilder/lang.json', 'PokeEditor/prices.json'] : [])].every((f) => registered.has(f)),
      ['Redeem/redeem.conf', 'serverconfig.conf', 'CatchCombo/catchcomboconfig.conf'].filter((f) => !registered.has(f)).join(', '));

    console.log('== o painel abre e valida todos os arquivos que o mod escreveu');
    const openFailures = [];
    const validateFailures = [];
    let opened = 0;
    for (const f of files) {
      const params = { module: f.module, file: f.file };
      const got = await dash.rpc('config.get', params);
      if (!got.ok || !got.result.values || !Array.isArray(got.result.schema?.fields)) { openFailures.push(`${key(f)}: ${JSON.stringify(got.error || 'sem valores').slice(0, 100)}`); continue; }
      opened++;
      if (!fs.existsSync(path.join(ROOT, key(f)))) continue; // ainda sem arquivo: o painel mostra os padrões
      const checked = await dash.rpc('config.validate', { ...params, values: got.result.values });
      const errors = checked.ok ? checked.result.errors.filter((e) => !e.warning) : [{ path: '', message: JSON.stringify(checked.error) }];
      if (errors.length) validateFailures.push(`${key(f)}: ${errors.slice(0, 2).map((e) => `${e.path || '(arquivo)'} ${e.message}`).join(' | ').slice(0, 160)}`);
    }
    t.check(`config.get abre os ${files.length} arquivos (valores e esquema)`, openFailures.length === 0, `${opened} abriram; falhas: ${openFailures.slice(0, 5).join(' || ')}`);
    t.check('config.validate aceita os valores atuais de todos (nenhum falso erro)', validateFailures.length === 0, validateFailures.slice(0, 5).join(' || '));
  } finally {
    dash.close();
  }
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
