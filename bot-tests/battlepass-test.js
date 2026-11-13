// Passe de batalha: arquivos e temporada, XP e níveis, resgate grátis e premium, bônus de XP, recompensa battlepass_xp vinda de uma
// missão, menu, remover níveis e trocar de temporada.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/BattlePass';
const MIS = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Missions';
const joined = (bot) => bot.chatLog.join('\n');
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');

async function say(bot, cmd, wait = 1000) {
  bot.chatLog.length = 0;
  bot.chat(cmd);
  await t.sleep(wait);
  return joined(bot);
}

(async () => {
  console.log('== arquivos e temporada');
  t.check('BattlePass/config.json existe e explica as fontes de XP', fs.existsSync(DIR + '/config.json') && /XP SOURCES/.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('tiers.json criado com os 30 níveis', fs.existsSync(DIR + '/tiers.json') && (fs.readFileSync(DIR + '/tiers.json', 'utf8').match(/"level":/g) || []).length === 30);
  t.check('season.json criado (a primeira temporada começa sozinha)', fs.existsSync(DIR + '/season.json'));
  const bp0 = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('config.json: níveis, curva e premium com valores legíveis', bp0.levels === 30 && bp0.xp.curve.mode === 'FORMULA' && bp0.xp.curve.xp_per_level === 1000 && bp0.premium.permission === 'serverbuddy.battlepass.premium');

  const a = await t.join('BpBot');
  const start = t.rcon('battlepass season start 30');
  const initialSeason = Number(start.match(/Season (\d+) started/)[1]);
  t.rcon('clear BpBot', 'gamemode survival BpBot');
  await t.sleep(1200);
  t.rcon('battlepass reset BpBot', 'battlepass premium remove BpBot');

  console.log('== /battlepass info');
  let out = await say(a, '/battlepass info');
  t.check('mostra a temporada, o nível 1 e que o premium está inativo', /season/i.test(out) && /Level 1\/30/.test(out) && /inactive/.test(out), out.replace(/\n/g, ' | '));

  console.log('== recompensa "battlepass_xp" vinda de uma missão');
  fs.writeFileSync(MIS + '/categories/t_bp.json', JSON.stringify({
    enabled: true, name: 'BP', icon: 'minecraft:paper', period: 'none',
    missions: { xp: { name: 'XP do passe', objectives: ['manual bp 1'], rewards: [{ type: 'battlepass_xp', amount: 700 }, { type: 'tag', tag: 'novato' }] } },
  }, null, 2), 'utf8');
  t.rcon('missions reload', 'missions progress BpBot bp 1');
  await t.sleep(1500);
  out = await say(a, '/missions claim t_bp.xp', 1500);
  t.check('a missão entregou 700 de XP do passe', /Prêmio resgatado/.test(out) && /700 battle pass XP/.test(out), out.replace(/\n/g, ' | '));
  out = await say(a, '/battlepass info');
  t.check('o XP chegou ao passe (700/1000 no nível 1)', /XP 700\/1000/.test(out), out.replace(/\n/g, ' | '));
  fs.unlinkSync(MIS + '/categories/t_bp.json');
  t.rcon('missions reload', 'missions reset BpBot t_bp');

  console.log('== subir de nível');
  a.chatLog.length = 0;
  let r = t.rcon('battlepass addxp BpBot 1800');
  console.log(r);
  await t.sleep(1200);
  // 700 + 1800 = 2500 → nível 3 com 400 de XP
  t.check('subiu para o nível 3 e avisou no chat', /nível 3/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  out = await say(a, '/battlepass info');
  t.check('nível 3 com 400/1210 de XP', /Level 3\/30/.test(out) && /XP 400\/1210/.test(out), out.replace(/\n/g, ' | '));

  console.log('== resgate dos prêmios grátis');
  out = await say(a, '/battlepass claim', 1500);
  const got = { bread: t.count(a, 'bread'), iron: t.count(a, 'iron_ingot'), beef: t.count(a, 'cooked_beef'), carrot: t.count(a, 'golden_carrot') };
  console.log('  recebido: ' + JSON.stringify(got));
  t.check('níveis 1 a 3 grátis: 4 pães, 4 ferros e 5 carnes', got.bread === 4 && got.iron === 4 && got.beef === 5, JSON.stringify(got));
  t.check('o premium não saiu sem o passe premium', got.carrot === 0);
  out = await say(a, '/battlepass claim');
  t.check('resgatar de novo avisa que não há nada', /não tem prêmios/.test(out), out.replace(/\n/g, ' | '));

  console.log('== passe premium');
  t.rcon('battlepass premium give BpBot');
  out = await say(a, '/battlepass claim', 1800);
  const gotP = { carrot: t.count(a, 'golden_carrot'), diamond: t.count(a, 'diamond'), apple: t.count(a, 'golden_apple') };
  console.log('  premium recebido: ' + JSON.stringify(gotP));
  t.check('níveis 1 a 3 premium: 2 cenouras, 2 diamantes e 2 maçãs douradas', gotP.carrot === 2 && gotP.diamond === 2 && gotP.apple === 2, JSON.stringify(gotP));
  out = await say(a, '/battlepass premium');
  t.check('/battlepass premium avisa que já tem', /já tem o passe premium/.test(out), out.replace(/\n/g, ' | '));
  out = await say(a, '/battlepass info');
  t.check('info mostra o premium ativo', /active/.test(out) && !/inactive/.test(out), out.replace(/\n/g, ' | '));

  console.log('== bônus de XP');
  const cfg = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  cfg.boosts = [{ when: [], permission: null, multiplier: 2.0 }];
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('battlepass reload', 'battlepass addxp BpBot 100');
  out = await say(a, '/battlepass info');
  t.check('com bônus x2 os 100 de XP viram 200 (400 + 200 = 600)', /XP 600\/1210/.test(out) && /XP bonus active/.test(out), out.replace(/\n/g, ' | '));
  cfg.boosts = [];
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('battlepass reload');

  console.log('== menu de baú');
  const w = await t.openByCommand(a, '/battlepass');
  const title = t.titleOf(w);
  console.log('  título: ' + title);
  t.check('o menu abre com o título do passe e o nível', /Passe de Batalha/.test(title) && /Nível 3/.test(title), title);
  a.closeWindow(w);
  await t.sleep(500);
  // apelido de comando SEM argumentos: o redirect sozinho dava "comando desconhecido"
  const wBp = await t.openByCommand(a, '/bp');
  t.check('o atalho /bp abre o mesmo menu', /Passe de Batalha/.test(t.titleOf(wBp)), t.titleOf(wBp));
  a.closeWindow(wBp);
  await t.sleep(500);

  console.log('== remover e devolver níveis');
  r = t.rcon('battlepass removelevels BpBot 1');
  console.log(r);
  out = await say(a, '/battlepass info');
  t.check('voltou ao nível 2 (sem prêmio novo para pegar)', /Level 2\/30/.test(out) && !/reward\(s\) to claim/.test(out), out.replace(/\n/g, ' | '));
  t.rcon('battlepass addlevels BpBot 1');
  out = await say(a, '/battlepass info');
  t.check('ao voltar ao nível 3 o prêmio do nível 3 está liberado de novo', /Level 3\/30/.test(out) && /2 reward\(s\) to claim/.test(out), out.replace(/\n/g, ' | '));
  await say(a, '/battlepass claim', 1800);
  t.check('resgatar de novo dá os prêmios do nível 3 (5 carnes a mais e 2 maçãs a mais)', t.count(a, 'cooked_beef') === 10 && t.count(a, 'golden_apple') === 4, `beef=${t.count(a, 'cooked_beef')} apple=${t.count(a, 'golden_apple')}`);

  console.log('== temporada');
  r = t.rcon('battlepass season start 5');
  console.log(r);
  out = await say(a, '/battlepass info');
  t.check('temporada nova: todo mundo no nível 1', out.toLowerCase().includes('season ' + (initialSeason + 1)) && /Level 1\/30/.test(out) && /inactive/.test(out), out.replace(/\n/g, ' | '));
  r = t.rcon('battlepass season info');
  t.check('season info mostra a temporada 2 em andamento', r.includes('Season ' + (initialSeason + 1)) && /in progress/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('battlepass season end');
  t.check('season end encerra', /ended/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('battlepass addxp BpBot 100');
  t.check('sem temporada em andamento ninguém ganha XP', /There is no season/.test(r), r.replace(/\n/g, ' | '));
  out = await say(a, '/battlepass info');
  t.check('o info avisa que a temporada acabou', /ended/.test(out), out.replace(/\n/g, ' | '));

  console.log('== persistência (sai e volta, numa temporada nova)');
  t.rcon('battlepass season start 30');
  t.rcon('battlepass addxp BpBot 1500');
  a.quit();
  await t.sleep(1500);
  const b = await t.join('BpBot');
  await t.sleep(1500);
  out = await say(b, '/battlepass info');
  t.check('o nível sobrevive a sair e entrar (nível 2 com 500 de XP)', out.toLowerCase().includes('season ' + (initialSeason + 2)) && /Level 2\/30/.test(out) && /XP 500\/1100/.test(out), out.replace(/\n/g, ' | '));

  b.quit();
  await t.sleep(500);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
