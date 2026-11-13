// Blacklist: importação do mod original, contextos (tag e "when"), /blacklist para jogadores, comandos da equipe e arquivos.
// Obs.: a recusa dentro de uma batalha de verdade não dá para fazer com o bot (as escolhas de batalha são pacotes do Cobblemon).
const fs = require('fs');
const t = require('./lib.js');

const RUN = 'D:/mods/ServerBuddy/run';
const CFG = RUN + '/config/ServerBuddy/Blacklist/config.json';

/** Lê o config.json tirando as linhas de comentário (o arquivo do mod é JSON com // na frente de cada opção). */
const readCfg = () => JSON.parse(fs.readFileSync(CFG, 'utf8').split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n'));

(async () => {
  console.log('== config importada do mod original (config/blacklist.json)');
  t.check('config.json criado em Blacklist/', fs.existsSync(CFG));
  let text = fs.readFileSync(CFG, 'utf8');
  t.check('veio com comentários explicando os contextos', /^\s*\/\/.*CONTEXTOS/m.test(text));
  t.check('golpes importados e normalizados', /"thunderbolt"/.test(text) && /"sheercold"/.test(text) && /"explosion"/.test(text));
  t.check('o arquivo do mod original foi mantido', fs.existsSync(RUN + '/config/blacklist.json'));

  // This test creates pvp itself; a previous run must not leave its conditions behind.
  const initial = readCfg();
  delete initial.contexts.pvp;
  fs.writeFileSync(CFG, JSON.stringify(initial, null, 2));
  t.rcon('blacklist reload');

  const bot = await t.join('BlBot');
  t.rcon('tag BlBot remove mode_tower', 'tag BlBot remove pvp');
  const mine = async () => { bot.chatLog.length = 0; bot.chat('/blacklist'); await t.sleep(1100); return bot.chatLog.join('\n'); };

  console.log('== /blacklist de um jogador comum');
  let out = await mine();
  t.check('mostra o cabeçalho e o contexto default', /Golpes proibidos/.test(out) && /default/.test(out), out.replace(/\n/g, ' | '));
  t.check('lista os golpes do default', /thunderbolt/.test(out) && /sheercold|sheer cold/i.test(out));
  t.check('mode_tower não aparece sem a tag', !/mode_tower/.test(out));

  console.log('== contexto ligado por tag (como no mod original)');
  t.rcon('tag BlBot add mode_tower');
  out = await mine();
  t.check('com a tag mode_tower o contexto aparece', /mode_tower/.test(out) && /explosion/.test(out), out.replace(/\n/g, ' | '));
  let check = t.rcon('blacklist check BlBot explosion');
  console.log(check);
  t.check('check: explosion proibida em PvP, NPC e selvagem', (check.match(/banned/g) || []).length === 3, check.replace(/\n/g, ' | '));
  t.rcon('tag BlBot remove mode_tower');

  console.log('== comandos da equipe (RCON)');
  console.log(t.rcon('blacklist contexts'));
  let r = t.rcon('blacklist add pvp fissure');
  console.log(r);
  t.check('add cria o contexto novo e avisa que vale por tag', /banned in/.test(r) && /tag/.test(r), r.replace(/\n/g, ' | '));
  t.check('add não reclama de golpe que existe', !/does not know the move/.test(r));
  t.check('o config.json ganhou o golpe e guardou o .bak', /"fissure"/.test(fs.readFileSync(CFG, 'utf8')) && fs.existsSync(CFG + '.bak'));
  r = t.rcon('blacklist add pvp fissure');
  t.check('add repetido é recusado', /already banned/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('blacklist add default thundrbolt');
  console.log(r);
  t.check('golpe com erro de digitação gera aviso', /does not know the move/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('blacklist remove default thundrbolt');
  t.check('remove tira o golpe', /allowed/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('blacklist remove default nuncaexistiu');
  t.check('remove de golpe que não está na lista avisa', /was not in the list/.test(r), r.replace(/\n/g, ' | '));

  console.log('== contexto só para PvP (editando o arquivo e recarregando, como um admin faria)');
  const cfg = readCfg();
  t.check('o contexto pvp existe no arquivo e ainda não tem condições', cfg.contexts.pvp && cfg.contexts.pvp.when.length === 0);
  cfg.contexts.pvp.when = ['battle:pvp'];
  fs.writeFileSync(CFG, JSON.stringify(cfg, null, 2), 'utf8');
  r = t.rcon('blacklist reload');
  console.log(r);
  t.check('reload lê o arquivo editado', /reloaded/.test(r), r.replace(/\n/g, ' | '));
  out = await mine();
  t.check('/blacklist mostra o contexto com o aviso "só contra jogadores"', /pvp/.test(out) && /only against players/.test(out) && /fissure/.test(out), out.replace(/\n/g, ' | '));
  check = t.rcon('blacklist check BlBot fissure');
  console.log(check);
  t.check('check: fissure proibida só em PvP', /PvP[^\n]*banned/.test(check) && /NPC[^\n]*allowed/.test(check) && /wild[^\n]*allowed/.test(check), check.replace(/\n/g, ' | '));
  check = t.rcon('blacklist check BlBot');
  console.log(check);
  t.check('check sem golpe mostra os contextos de cada tipo de batalha', /PvP/.test(check) && /NPC/.test(check) && /wild/.test(check));

  console.log('== permissões');
  const before = readCfg().contexts.default.moves.length;
  bot.chatLog.length = 0;
  bot.chat('/blacklist add default willowisp');
  await t.sleep(1200);
  out = bot.chatLog.join('\n');
  t.check('jogador comum não consegue usar o add', /Incorrect argument|Unknown or incomplete|Comando desconhecido|incompleto/i.test(out), out.replace(/\n/g, ' | '));
  t.check('a lista não foi mexida pelo jogador comum', readCfg().contexts.default.moves.length === before);

  console.log('== volta ao estado inicial');
  t.rcon('blacklist remove pvp fissure');
  bot.quit();
  await t.sleep(500);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
