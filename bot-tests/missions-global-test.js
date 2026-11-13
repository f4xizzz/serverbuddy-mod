// Missões globais: a rodada começa sozinha, o primeiro a completar leva o prêmio e a missão acaba para os outros, o tempo acaba sem
// vencedor, /missions global start|stop|join, o modo "ui" (cada um aceita), "online" x "all" e o aviso ao entrar.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Missions';
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const joined = (bot) => bot.chatLog.join('\n');
const flat = (s) => s.replace(/\n/g, ' | ');
async function say(bot, cmd, wait = 1000) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return joined(bot); }
const sawText = (bot, re) => bot.chatLog.some((l) => re.test(l));
const diamonds = (name) => { const m = t.rcon(`clear ${name} minecraft:diamond 0`).match(/Found (\d+) matching/); return m ? Number(m[1]) : 0; };
async function waitFor(bot, re, ms = 14000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (sawText(bot, re)) return true; await t.sleep(300); }
  return false;
}

(async () => {
  console.log('== preparação');
  const cfgPath = DIR + '/config.json';
  const cfg = JSON.parse(strip(fs.readFileSync(cfgPath, 'utf8')));
  const original = JSON.stringify(cfg.global_missions === undefined ? null : cfg.global_missions);
  const setGlobal = (patch) => {
    const c = JSON.parse(strip(fs.readFileSync(cfgPath, 'utf8')));
    c.global_missions = { enabled: true, interval: '4s', time_limit: '40s', target: 'online', assignment: 'auto', ...patch };
    fs.writeFileSync(cfgPath, JSON.stringify(c, null, 2), 'utf8');
    return t.rcon('missions reload');
  };
  // numa instalação nova existem categorias globais de exemplo; a rodada sorteia entre todas, então elas ficam desligadas durante o teste
  const exemplos = ['globais.json', 'globais_pokemon.json'].filter((f) => fs.existsSync(DIR + '/categories/' + f)).map((f) => ({ f, raw: fs.readFileSync(DIR + '/categories/' + f, 'utf8') }));
  for (const { f, raw } of exemplos) {
    const off = JSON.parse(strip(raw));
    off.enabled = false;
    fs.writeFileSync(DIR + '/categories/' + f, JSON.stringify(off, null, 2), 'utf8');
  }
  fs.writeFileSync(DIR + '/categories/globais_teste.json', JSON.stringify({
    enabled: true, name: '<red>GlobaisTeste', icon: 'minecraft:beacon', description: ['teste'], period: 'none', pick: 0, global: true,
    missions: { corrida: { name: '<yellow>Corrida teste', description: ['teste'], icon: 'minecraft:paper', objectives: ['manual corrida 1'], rewards: [{ type: 'item', item: 'minecraft:diamond', amount: 2 }] } },
  }, null, 2), 'utf8');
  setGlobal({ interval: '600s' }); // nada começa sozinho enquanto a gente prepara
  // Reloading a longer interval intentionally keeps an already scheduled round.
  // Starting and stopping a fixture round establishes a fresh 600-second interval.
  t.rcon('missions global start corrida', 'missions global stop');

  const a = await t.join('EsA'); // equipe
  const b = await t.join('EsB');
  t.rcon('op EsA', 'deop EsB', 'gamemode survival EsA', 'gamemode survival EsB', 'clear EsA', 'clear EsB', 'tp EsA 2.5 89 -3.5', 'tp EsB 6.5 89 -3.5');
  await t.sleep(1200);
  let out;

  console.log('== começa sozinha depois do intervalo');
  out = t.rcon('missions global');
  t.check('sem missão em jogo o status diz quando vem a próxima', /There is no global mission/.test(out), flat(out));
  a.chatLog.length = 0; b.chatLog.length = 0;
  setGlobal({ interval: '4s' });
  t.check('a missão global é anunciada a todos (chat)', await waitFor(a, /Global mission!.*Corrida teste/) && await waitFor(b, /Global mission!.*Corrida teste/), flat(joined(a)));
  out = await say(b, '/missions global', 900);
  t.check('/missions global mostra a missão, o tempo e quem está na frente', /Global mission Corrida teste/.test(out) && /Ends in/.test(out) && /Ahead/.test(out) && /You: 0\/1/.test(out), flat(out));
  out = await say(b, '/missions list', 1200);
  t.check('a categoria global aparece na lista, com a missão', /GlobaisTeste/.test(out) && /\[globais_teste\.corrida\]/.test(out), flat(out));
  out = await say(b, '/missions global join', 900);
  t.check('no modo "auto" não precisa entrar', /No need/.test(out), flat(out));

  console.log('== o primeiro a completar leva o prêmio');
  const a0 = diamonds('EsA'); const b0 = diamonds('EsB');
  b.chatLog.length = 0; a.chatLog.length = 0;
  t.rcon('missions progress EsA corrida 1'); await t.sleep(1500);
  t.check('o vencedor recebe o prêmio da missão', diamonds('EsA') === a0 + 2 && diamonds('EsB') === b0, `A ${a0}->${diamonds('EsA')} B ${b0}->${diamonds('EsB')}`);
  t.check('todos veem quem venceu', sawText(b, /EsA completed the global mission Corrida teste and won the reward/), flat(joined(b)));
  out = await say(b, '/missions list', 1200);
  t.check('para os outros a missão acaba (some do menu)', !/GlobaisTeste/.test(out) && !/globais_teste/.test(out), flat(out));
  t.rcon('missions progress EsB corrida 1'); await t.sleep(800);
  t.check('quem perdeu não ganha mais nada', diamonds('EsB') === b0, 'B ' + diamonds('EsB'));

  console.log('== a próxima rodada começa sozinha e outro pode vencer');
  b.chatLog.length = 0;
  t.check('depois do intervalo (4s) vem outra missão global', await waitFor(b, /Global mission!.*Corrida teste/, 12000), flat(joined(b)));
  await t.sleep(1500);
  t.rcon('missions progress EsB corrida 1'); await t.sleep(1500);
  t.check('agora quem venceu foi o EsB', diamonds('EsB') === b0 + 2 && diamonds('EsA') === a0 + 2, `A ${diamonds('EsA')} B ${diamonds('EsB')}`);
  out = await say(a, '/missions list', 1200);
  t.check('e para o EsA a missão nova também acabou', !/GlobaisTeste/.test(out), flat(out));

  console.log('== equipe: start e stop');
  setGlobal({ interval: '600s' });
  t.rcon('missions global stop');
  out = await say(a, '/missions global start', 1200);
  t.check('/missions global start sorteia uma', /started/.test(out), flat(out));
  out = await say(a, '/missions global start', 900);
  t.check('com uma em jogo não começa outra', /A global mission is already active/.test(out), flat(out));
  b.chatLog.length = 0;
  out = await say(a, '/missions global stop', 1200);
  t.check('/missions global stop encerra', /stopped/.test(out) && sawText(b, /was ended by the staff/), flat(out) + ' // B: ' + flat(joined(b)));
  out = await say(a, '/missions global stop', 800);
  t.check('sem missão em jogo, stop avisa', /No global mission is active/.test(out), flat(out));
  out = await say(a, '/missions global start nada', 900);
  t.check('missão que não existe é recusada e lista as que existem', /Unknown global mission/.test(out) && /globais_teste\.corrida/.test(out), flat(out));
  out = await say(b, '/missions global start', 800);
  t.check('jogador comum não inicia missão global', /Incorrect|Unknown|incompleto|desconhecido/i.test(out), flat(out));

  console.log('== o tempo acaba sem vencedor');
  setGlobal({ interval: '600s', time_limit: '5s' });
  b.chatLog.length = 0;
  await say(a, '/missions global start corrida', 600);
  t.check('a missão começa com limite de tempo (5s)', await waitFor(b, /Global mission!.*Time: 5s/, 3000), flat(joined(b)));
  t.check('passado o tempo todos veem que ninguém completou', await waitFor(b, /nobody finished in time/, 9000), flat(joined(b)));
  out = await say(a, '/missions global', 800);
  t.check('depois de acabar o status volta a dizer que não há missão', /There is no global mission/.test(out), flat(out));
  t.rcon('missions progress EsA corrida 1'); await t.sleep(800);
  t.check('progresso depois de acabar não dá prêmio', diamonds('EsA') === a0 + 2, 'A ' + diamonds('EsA'));

  console.log('== modo "ui": cada um escolhe participar');
  setGlobal({ interval: '600s', time_limit: '60s', assignment: 'ui' });
  a.chatLog.length = 0; b.chatLog.length = 0;
  await say(a, '/missions global start corrida', 1200);
  t.check('o anúncio explica como participar', sawText(b, /Global mission!.*\/missions global join/), flat(joined(b)));
  out = await say(b, '/missions list', 1200);
  t.check('quem não aceitou vê "participar"', /● join/.test(out) && /Corrida teste/.test(out), flat(out));
  const b1 = diamonds('EsB'); const a1 = diamonds('EsA');
  t.rcon('missions progress EsB corrida 1'); await t.sleep(900);
  t.check('progresso de quem não entrou não conta', diamonds('EsB') === b1, 'B ' + diamonds('EsB'));
  out = await say(a, '/missions global join', 1200);
  t.check('/missions global join entra na missão', /You joined the global mission/.test(out), flat(out));
  out = await say(a, '/missions global join', 800);
  t.check('entrar de novo avisa', /already in the global mission/.test(out), flat(out));
  out = await say(a, '/missions list', 1200);
  t.check('quem entrou vê a missão em andamento (0/1)', /Corrida teste/.test(out) && /0\/1/.test(out) && !/● join/.test(out), flat(out));
  t.rcon('missions progress EsA corrida 1'); await t.sleep(1500);
  t.check('quem entrou vence e ganha o prêmio; quem não entrou, não', diamonds('EsA') === a1 + 2 && diamonds('EsB') === b1, `A ${a1}->${diamonds('EsA')} B ${b1}->${diamonds('EsB')}`);

  console.log('== "online": quem não estava online não participa; "all": todo mundo');
  setGlobal({ interval: '600s', time_limit: '60s', assignment: 'ui', target: 'online' });
  a.quit();
  await t.sleep(1500);
  t.rcon('missions global start corrida');
  await t.sleep(600);
  const a2 = await t.join('EsA');
  t.rcon('op EsA', 'gamemode survival EsA', 'tp EsA 2.5 89 -3.5');
  await t.sleep(1500);
  out = await say(a2, '/missions global join', 1000);
  t.check('quem entrou depois não pode participar (target online)', /only counts for players who were online/.test(out), flat(out));
  out = await say(a2, '/missions list', 1200);
  t.check('e não vê a missão global', !/GlobaisTeste/.test(out), flat(out));
  t.rcon('missions global stop');
  await t.sleep(600);
  setGlobal({ interval: '600s', time_limit: '60s', assignment: 'auto', target: 'all' });
  a2.quit();
  await t.sleep(1500);
  t.rcon('missions global start corrida');
  await t.sleep(600);
  const a3 = await t.join('EsA');
  t.rcon('op EsA', 'gamemode survival EsA', 'tp EsA 2.5 89 -3.5');
  await t.sleep(5500);
  t.check('com target "all" quem entra depois recebe o lembrete da missão em jogo', a3.chatLog.some((l) => /Global mission active: Corrida teste/.test(l)), flat(joined(a3)));
  out = await say(a3, '/missions list', 1200);
  t.check('e já participa dela', /GlobaisTeste/.test(out) && /Corrida teste/.test(out), flat(out));
  t.rcon('missions global stop');

  // volta ao que era
  const c = JSON.parse(strip(fs.readFileSync(cfgPath, 'utf8')));
  const old = JSON.parse(original);
  if (old === null) delete c.global_missions; else c.global_missions = old;
  fs.writeFileSync(cfgPath, JSON.stringify(c, null, 2), 'utf8');
  fs.rmSync(DIR + '/categories/globais_teste.json', { force: true });
  for (const { f, raw } of exemplos) fs.writeFileSync(DIR + '/categories/' + f, raw, 'utf8');
  t.rcon('missions reload', 'clear EsA', 'clear EsB');
  b.quit();
  a3.quit();
  await t.sleep(800);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
