// Bloqueio de comandos: /back (e qualquer comando) barrado por tag, apelidos, várias palavras, when/negação/horário, "*" com exceção,
// o /commandblock que a equipe sempre pode usar, o bypass do nível 4 e /commandblock list|check|reload.
// Precisa de: CbAdm com nível 3 e EsA com nível 4 no ops.json do servidor de teste (o servidor só aceita 2 jogadores ao mesmo tempo).
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/CommandBlock';
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const pos = (name) => { const m = t.rcon(`data get entity ${name} Pos`).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/); return m ? m.slice(1).map(Number) : [NaN, NaN, NaN]; };
const near = (a, b, r = 3) => Math.hypot(a[0] - b[0], a[2] - b[2]) <= r;
async function say(bot, cmd, wait = 1000) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return bot.chatLog.join('\n'); }
const flat = (s) => s.replace(/\n/g, ' | ');
const writeCfg = (cfg) => fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
const TAGS = ['sem_back', 'sem_casa2', 'sem_wb', 'isento', 'calado', 'calado_liberado', 'em_arena'];

(async () => {
  console.log('== arquivos e módulo');
  t.check('CommandBlock/config.json criado com comentários', fs.existsSync(DIR + '/config.json') && /COMO FUNCIONA/.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  const modules = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('/sb modules mostra o commandblock rodando', /✔ commandblock/.test(modules), modules.slice(0, 160));
  const cfg = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('as regras de exemplo existem (sem_back e arena)', !!(cfg.rules && cfg.rules.sem_back && cfg.rules.arena));
  cfg.enabled = true;
  cfg.rules.sem_back.enabled = true;
  cfg.rules.bancada = { commands: ['workbench'], when_any: ['tag:sem_wb'], message: '<red>Sem bancada (%command%).' };
  cfg.rules.casa2 = { commands: ['sethome casa2'], when_any: ['tag:sem_casa2'], message: '<red>Casa 2 bloqueada (%rule%).' };
  cfg.rules.sempre = { commands: ['rules'], when: ['time:00:00-23:59', '!tag:isento'], message: '<red>Regras bloqueadas (%command%).' };
  cfg.rules.calado = { commands: ['*'], when_any: ['tag:calado'], except: ['tag:calado_liberado'], message: '<red>Calado: %command%' };
  cfg.rules.quebrada = { commands: ['info'], when_any: ['nada:1'] };
  cfg.rules.sem_condicao = { commands: ['warps'] };
  writeCfg(cfg);
  console.log('  ' + t.rcon('commandblock reload').replace(/\n/g, ' | '));

  // os pontos do teste (y=89) precisam de ar e chão: no terreno do mundo de teste o bot pode sufocar
  const spots = [[2.5, -3.5], [6.5, -3.5]];
  for (const [x, z] of spots) t.rcon(`forceload add ${Math.floor(x) - 3} ${Math.floor(z) - 3} ${Math.floor(x) + 3} ${Math.floor(z) + 3}`);
  await t.sleep(3000);
  for (const [x, z] of spots) {
    const [x0, x1, z0, z1] = [Math.floor(x) - 2, Math.floor(x) + 2, Math.floor(z) - 2, Math.floor(z) + 2];
    t.rcon(`fill ${x0} 88 ${z0} ${x1} 88 ${z1} minecraft:stone`, `fill ${x0} 89 ${z0} ${x1} 93 ${z1} minecraft:air`);
  }

  const a = await t.join('CbBot');
  const adm = await t.join('CbAdm');
  t.rcon('op CbBot', 'op CbAdm', 'gamemode survival CbBot', 'gamemode survival CbAdm', 'clear CbBot', 'clear CbAdm');
  for (const who of ['CbBot', 'CbAdm', 'EsA']) for (const tag of TAGS) t.rcon(`tag ${who} remove ${tag}`);
  await t.sleep(800);
  for (const home of ['cb', 'casa2', 'casa3']) await say(a, '/delhome ' + home, 300);

  console.log('== /back barrado por tag');
  t.rcon('tp CbBot 2.5 89 -3.5');
  await t.sleep(800);
  await say(a, '/sethome cb', 800);
  t.rcon('tp CbBot 6.5 89 -3.5');
  await t.sleep(800);
  await say(a, '/home cb', 1500);
  t.check('/home leva o jogador (op: sem espera) e guarda de onde ele saiu', near(pos('CbBot'), [2.5, 89, -3.5]), JSON.stringify(pos('CbBot')));
  t.rcon('tag CbBot add sem_back');
  let out = await say(a, '/back', 1200);
  t.check('com a tag sem_back o /back é barrado com o aviso da regra', /Você não pode usar o \/back agora/.test(out), flat(out));
  t.check('e o jogador não sai do lugar', near(pos('CbBot'), [2.5, 89, -3.5]), JSON.stringify(pos('CbBot')));
  out = await say(a, '/ping', 1000);
  t.check('outros comandos seguem livres para quem tem a tag', !/Você não pode usar/.test(out), flat(out));
  t.rcon('tag CbBot remove sem_back');
  out = await say(a, '/back', 1800);
  t.check('sem a tag o /back volta a funcionar', near(pos('CbBot'), [6.5, 89, -3.5]) && !/Você não pode usar/.test(out), JSON.stringify(pos('CbBot')) + ' | ' + flat(out));

  console.log('== apelido de comando (o /wb vale como /workbench)');
  t.rcon('tag CbBot add sem_wb');
  out = await say(a, '/wb', 1000);
  t.check('o apelido /wb é barrado pela regra do /workbench', /Sem bancada \(workbench\)/.test(out), flat(out));
  out = await say(a, '/workbench', 1000);
  t.check('e o nome inteiro também', /Sem bancada \(workbench\)/.test(out), flat(out));
  t.rcon('tag CbBot remove sem_wb');
  const bench = await t.openByCommand(a, '/wb');
  t.check('sem a tag o apelido /wb abre a bancada (apelido de comando sem argumentos funciona)', !!bench, '');
  a.closeWindow(bench);
  await t.sleep(500);

  console.log('== comando de mais de uma palavra');
  t.rcon('tag CbBot add sem_casa2');
  out = await say(a, '/sethome casa2', 1000);
  t.check('"sethome casa2" é barrado', /Casa 2 bloqueada \(casa2\)/.test(out), flat(out));
  out = await say(a, '/sethome casa3', 1200);
  t.check('"sethome casa3" passa: só a forma da regra fica de fora', /Casa casa3 definida/.test(out), flat(out));
  await say(a, '/delhome casa3', 600);
  t.rcon('tag CbBot remove sem_casa2');

  console.log('== when com negação e horário');
  out = await say(a, '/rules', 1000);
  t.check('regra com time + !tag:isento barra o /rules de quem não tem a tag isento', /Regras bloqueadas \(rules\)/.test(out), flat(out));
  t.rcon('tag CbBot add isento');
  out = await say(a, '/rules', 1000);
  t.check('com a tag isento o /rules passa', !/Regras bloqueadas/.test(out), flat(out));
  t.rcon('tag CbBot remove isento');

  console.log('== "*" com exceção e o /commandblock da equipe');
  t.rcon('tag CbBot add calado', 'tag CbAdm add calado');
  out = await say(a, '/ping', 1000);
  t.check('com a tag calado qualquer comando é barrado', /Calado: ping/.test(out), flat(out));
  out = await say(adm, '/commandblock list', 1500);
  t.check('quem administra o /commandblock continua usando-o mesmo calado', /Bloqueio de comandos/.test(out) && /sem_back/.test(out), flat(out));
  out = await say(adm, '/sb commandblock list', 1500);
  t.check('e também pelo /sb commandblock', /Bloqueio de comandos/.test(out), flat(out));
  out = await say(adm, '/ping', 1000);
  t.check('mas os outros comandos dele são barrados (nível 3 não é o bypass)', /Calado: ping/.test(out), flat(out));
  t.rcon('tag CbBot add calado_liberado');
  out = await say(a, '/ping', 1000);
  t.check('a exceção calado_liberado solta tudo', !/Calado/.test(out), flat(out));
  t.rcon('tag CbBot remove calado', 'tag CbBot remove calado_liberado', 'tag CbAdm remove calado');

  console.log('== regras que não valem não barram');
  out = await say(a, '/info', 1000);
  t.check('condição que não existe nunca barra', !/Você não pode/.test(out), flat(out));
  out = await say(a, '/warps', 1000);
  t.check('regra sem condição nenhuma nunca barra', !/Você não pode/.test(out), flat(out));
  const log = fs.readFileSync('D:/mods/ServerBuddy/run/logs/latest.log', 'utf8');
  t.check('o log avisa da condição que não existe', /Regra 'quebrada': condição desconhecida/.test(log));
  t.check('e da regra sem condição', /A regra 'sem_condicao' não tem condição nenhuma/.test(log));

  console.log('== /commandblock check e list');
  t.rcon('tag CbBot add sem_back');
  let r = t.rcon('commandblock check CbBot back');
  t.check('check diz que o /back está barrado e por qual regra', /NÃO pode usar \/back/.test(r) && /sem_back/.test(r), r);
  t.rcon('tag CbBot add sem_wb');
  r = t.rcon('commandblock check CbBot wb');
  t.check('check também pega o apelido (wb → workbench)', /NÃO pode usar/.test(r) && /bancada/.test(r), r);
  t.rcon('tag CbBot remove sem_wb');
  r = t.rcon('commandblock check CbBot home');
  t.check('check diz que o /home está livre para ele', /pode usar \/home/.test(r) && !/NÃO/.test(r), r);
  t.rcon('tag CbBot remove sem_back');
  r = t.rcon('commandblock check CbBot back');
  t.check('sem a tag o /back está livre', /pode usar \/back/.test(r) && !/NÃO/.test(r), r);
  r = t.rcon('commandblock list');
  t.check('list mostra as regras, os comandos e as condições', /sem_back/.test(r) && /calado/.test(r) && /tag:sem_back/.test(r), r.slice(0, 300));

  console.log('== ligar e desligar');
  t.rcon('tag CbBot add sem_back');
  cfg.rules.sem_back.enabled = false;
  writeCfg(cfg);
  t.rcon('commandblock reload');
  out = await say(a, '/back', 1800);
  t.check('regra com enabled=false não barra', !/Você não pode usar o \/back/.test(out), flat(out));
  cfg.rules.sem_back.enabled = true;
  cfg.enabled = false;
  writeCfg(cfg);
  t.rcon('commandblock reload');
  out = await say(a, '/back', 1800);
  t.check('com "enabled": false no topo nenhuma regra vale', !/Você não pode usar o \/back/.test(out), flat(out));
  cfg.enabled = true;
  writeCfg(cfg);
  t.rcon('commandblock reload');
  out = await say(a, '/back', 1200);
  t.check('ligou de novo: barra', /Você não pode usar o \/back agora/.test(out), flat(out));
  t.rcon('tag CbBot remove sem_back');

  console.log('== quem tem a permissão de bypass (nível 4) nunca é barrado');
  adm.quit();
  await t.sleep(1500);
  const op = await t.join('EsA');
  t.rcon('op EsA', 'tag EsA add calado', 'tag EsA add sem_back');
  out = await say(op, '/ping', 1000);
  t.check('operador nível 4 usa qualquer comando mesmo com a tag calado', !/Calado/.test(out), flat(out));
  r = t.rcon('commandblock check EsA back');
  t.check('/commandblock check avisa do bypass', /serverbuddy\.commandblock\.bypass/.test(r), r);
  t.rcon('tag EsA remove calado', 'tag EsA remove sem_back');

  // limpeza
  for (const home of ['cb', 'casa2', 'casa3']) await say(a, '/delhome ' + home, 300);
  for (const who of ['CbBot', 'CbAdm', 'EsA']) for (const tag of TAGS) t.rcon(`tag ${who} remove ${tag}`);
  t.rcon('forceload remove all');
  a.quit(); op.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
