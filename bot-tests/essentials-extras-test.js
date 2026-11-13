// Essentials, parte 2 (o que faltava do Solstice): /homeother, /tpaall, /tppos, /tpoffline, /setfirstspawn, /firstspawn, /ignorelist,
// /plainbroadcast, /ignite, /effects, /sleep, /serverstat, /activetime, /motd, /kittycannon, /rocket, /nudge, /checkjail, /doas,
// /powertool, /cooldown (grupos de recarga) e /timebar.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Essentials';
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const joined = (bot) => bot.chatLog.join('\n');
const pos = (name) => { const m = t.rcon(`data get entity ${name} Pos`).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/); return m ? m.slice(1).map(Number) : [NaN, NaN, NaN]; };
const near = (a, b, r = 3) => Math.hypot(a[0] - b[0], a[2] - b[2]) <= r;
async function say(bot, cmd, wait = 1000) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return joined(bot); }
const flat = (s) => s.replace(/\n/g, ' | ');

(async () => {
  console.log('== preparação');
  const cfg = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  cfg.teleport.warmup_seconds = 1; cfg.teleport.cooldown_seconds = 0;
  const original = JSON.stringify({ motd: cfg.info.motd, cooldowns: cfg.cooldowns, warmup: cfg.teleport.warmup_seconds, cooldown: cfg.teleport.cooldown_seconds });
  cfg.info.motd = ['<green>Linha um do motd', '<yellow>Linha dois do motd'];
  cfg.cooldowns = { enabled: true, groups: { teste: { enabled: true, commands: ['ping'], seconds: 6 } } };
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('serverbuddy reload');

  const spots = [[2.5, -3.5], [6.5, -3.5], [30.5, 30.5], [40.5, 40.5], [60.5, 60.5], [80.5, 80.5], [90.5, 90.5], [100.5, 100.5], [-40.5, -40.5]];
  for (const [x, z] of spots) t.rcon(`forceload add ${Math.floor(x) - 3} ${Math.floor(z) - 3} ${Math.floor(x) + 3} ${Math.floor(z) + 3}`);
  await t.sleep(4000);
  for (const [x, z] of spots) {
    const [x0, x1, z0, z1] = [Math.floor(x) - 2, Math.floor(x) + 2, Math.floor(z) - 2, Math.floor(z) + 2];
    t.rcon(`fill ${x0} 88 ${z0} ${x1} 88 ${z1} minecraft:stone`, `fill ${x0} 89 ${z0} ${x1} 93 ${z1} minecraft:air`);
  }

  let a = await t.join('EsA'); // equipe (nível 4)
  let b = await t.join('EsB'); // jogador comum
  t.rcon('op EsA', 'deop EsB', 'gamemode survival EsA', 'gamemode survival EsB', 'clear EsA', 'clear EsB', 'tp EsA 2.5 89 -3.5', 'tp EsB 6.5 89 -3.5');
  await t.sleep(1500);
  let out;

  console.log('== /homeother');
  await say(b, '/delhome casab', 300);
  await say(b, '/sethome casab', 600);
  t.rcon('tp EsA 60.5 89 60.5');
  await t.sleep(700);
  out = await say(a, '/homeother EsB casab', 3500);
  t.check('/homeother leva até a casa de outro jogador', near(pos('EsA'), [6.5, 89, -3.5]), JSON.stringify(pos('EsA')) + ' | ' + flat(out));
  out = await say(a, '/homeother EsB nao_existe', 900);
  t.check('casa que não existe avisa e lista as dele', /não tem a casa/.test(out) && /casab/.test(out), flat(out));
  out = await say(b, '/homeother EsA', 900);
  t.check('jogador comum não usa /homeother', /Incorrect|Unknown|incompleto|Comando desconhecido/i.test(out), flat(out));

  console.log('== /tppos e /tpoffline');
  out = await say(a, '/tppos 60.5 89 60.5', 900);
  t.check('/tppos leva para a coordenada', near(pos('EsA'), [60.5, 89, 60.5], 1.5) && /Teleportado/.test(out), JSON.stringify(pos('EsA')) + ' | ' + flat(out));
  out = await say(a, '/tppos 0 99999 0', 900);
  t.check('/tppos recusa posição fora do mundo', /fora do mundo/.test(out), flat(out));
  t.rcon('tp EsB 80.5 89 80.5');
  await t.sleep(800);
  b.quit();
  await t.sleep(1800);
  out = await say(a, '/tpoffline EsB', 3500);
  t.check('/tpoffline leva até onde o jogador estava ao sair', near(pos('EsA'), [80.5, 89, 80.5]), JSON.stringify(pos('EsA')) + ' | ' + flat(out));
  b = await t.join('EsB');
  t.rcon('deop EsB', 'gamemode survival EsB', 'tp EsB 6.5 89 -3.5');
  await t.sleep(1200);

  console.log('== /tpaall');
  out = await say(a, '/tpaall', 900);
  t.check('/tpaall manda pedido a todo mundo', /enviado a 1 jogador/.test(out) && b.chatLog.some((l) => /EsA quer que você vá/.test(l)), flat(out) + ' // B: ' + flat(joined(b)));
  await say(b, '/tpdeny', 500);

  console.log('== spawn de primeira entrada');
  t.rcon('tp EsA 90.5 89 90.5');
  await t.sleep(600);
  out = await say(a, '/setfirstspawn', 700);
  t.check('/setfirstspawn grava', /Spawn de primeira entrada definido/.test(out), flat(out));
  out = await say(a, '/warps', 700);
  t.check('o spawn de primeira entrada não aparece na lista de warps', !/__firstspawn/.test(out), flat(out));
  t.rcon('tp EsA 2.5 89 -3.5');
  await t.sleep(600);
  await say(a, '/firstspawn', 3500);
  t.check('/firstspawn leva até lá', near(pos('EsA'), [90.5, 89, 90.5]), JSON.stringify(pos('EsA')));
  await say(a, '/firstspawn EsB', 800);
  t.check('/firstspawn <jogador> manda outro jogador (equipe)', near(pos('EsB'), [90.5, 89, 90.5]), JSON.stringify(pos('EsB')));
  out = await say(b, '/firstspawn EsA', 800);
  t.check('jogador comum não manda ninguém para o spawn', /Incorrect|Unknown|incompleto|desconhecido/i.test(out), flat(out));
  await say(a, '/setfirstspawn delete', 700);
  out = await say(a, '/firstspawn', 900);
  t.check('depois de apagar, /firstspawn avisa que não existe', /ainda não foi definido/.test(out), flat(out));
  t.rcon('tp EsA 2.5 89 -3.5', 'tp EsB 6.5 89 -3.5');
  await t.sleep(700);

  console.log('== chat: /ignorelist, /plainbroadcast, /motd');
  await say(a, '/ignore EsB', 500);
  out = await say(a, '/ignorelist', 700);
  t.check('/ignorelist mostra quem está ignorado', /EsB/.test(out), flat(out));
  await say(a, '/unignore EsB', 500);
  a.chatLog.length = 0; b.chatLog.length = 0;
  await say(a, '/plainbroadcast Aviso puro sem enfeite', 900);
  t.check('/plainbroadcast chega a todos', b.chatLog.some((l) => l.trim() === 'Aviso puro sem enfeite'), flat(joined(b)));
  out = await say(b, '/motd', 800);
  t.check('/motd mostra as linhas da config', /Linha um do motd/.test(out) && /Linha dois do motd/.test(out), flat(out));

  console.log('== jogador: /ignite, /effects, /serverstat, /activetime');
  out = await say(a, '/ignite EsB 100', 900);
  const fire = t.rcon('data get entity EsB Fire');
  t.check('/ignite avisa e põe fogo', /pegando fogo/.test(out) && /data: [1-9]\d*s/.test(fire), flat(out) + ' // ' + fire);
  t.rcon('effect clear EsB', 'effect give EsB minecraft:speed 60 1 true');
  await t.sleep(500);
  out = await say(b, '/effects', 800);
  t.check('/effects lista o efeito ativo', /Speed/i.test(out) && /\b2\b/.test(out), flat(out));
  t.rcon('effect clear EsB');
  out = await say(b, '/effects', 800);
  t.check('/effects sem efeito avisa', /não tem efeitos/.test(out), flat(out));
  out = await say(a, '/serverstat', 900);
  t.check('/serverstat mostra TPS, memória e jogadores', /TPS/.test(out) && /Memória/.test(out) && /Jogadores/.test(out), flat(out));
  out = await say(a, '/tps', 900);
  t.check('/tps é apelido do /serverstat', /TPS/.test(out), flat(out));
  out = await say(b, '/serverstat', 800);
  t.check('/serverstat é da administração', /Incorrect|Unknown|incompleto|desconhecido/i.test(out), flat(out));
  await say(a, '/activetime set EsB 2h', 800);
  out = await say(a, '/activetime EsB', 800);
  t.check('/activetime set e consulta de outro jogador', /2h/.test(out), flat(out));
  out = await say(b, '/activetime', 800);
  t.check('/activetime mostra o meu tempo', /Seu tempo ativo/.test(out) && /2h/.test(out), flat(out));
  out = await say(a, '/activetime top', 800);
  t.check('/activetime top lista o ranking', /Mais tempo ativo/.test(out) && /EsB/.test(out), flat(out));

  console.log('== /sleep, /rocket, /nudge, /kittycannon');
  // dormir só segura quem deita de noite (de dia o Minecraft acorda qualquer um); congela a hora para o teste
  t.rcon('gamerule doDaylightCycle false', 'time set 14000');
  await say(a, '/sleep EsB', 1500);
  const slept = t.rcon('data get entity EsB SleepingX');
  t.check('/sleep põe o jogador para dormir, mesmo sem cama', /data: -?\d+/.test(slept), slept);
  await t.sleep(2500);
  const slept2 = t.rcon('data get entity EsB SleepingX');
  t.check('e ele continua deitado (o Minecraft não o acorda por falta de cama)', /data: -?\d+/.test(slept2), slept2);
  t.rcon('time set day', 'gamerule doDaylightCycle true');
  await t.sleep(1500);
  t.check('de dia ele acorda sozinho', /no elements/.test(t.rcon('data get entity EsB SleepingX')), t.rcon('data get entity EsB SleepingX'));
  t.rcon('tp EsB 6.5 89 -3.5');
  await t.sleep(800);
  const y0 = pos('EsA')[1];
  await say(a, '/rocket EsA', 500);
  await t.sleep(700);
  t.check('/rocket lança o jogador para cima', pos('EsA')[1] > y0 + 2, y0 + ' -> ' + pos('EsA')[1]);
  await t.sleep(2500);
  t.rcon('tp EsA 2.5 89 -3.5');
  await t.sleep(600);
  const p0 = pos('EsA');
  await say(a, '/nudge EsA 3', 500);
  await t.sleep(600);
  const p1 = pos('EsA');
  t.check('/nudge empurra para o lado', Math.hypot(p1[0] - p0[0], p1[2] - p0[2]) > 0.3, JSON.stringify(p0) + ' -> ' + JSON.stringify(p1));
  await say(a, '/kittycannon', 400);
  t.check('/kittycannon solta um gato', /: [1-9]/.test(t.rcon('execute if entity @e[type=minecraft:cat]')) || /Test passed/.test(t.rcon('execute if entity @e[type=minecraft:cat]')), t.rcon('execute if entity @e[type=minecraft:cat]'));
  await t.sleep(1800);
  t.check('o gato some sozinho (com o barulho de explosão)', /Test failed/.test(t.rcon('execute if entity @e[type=minecraft:cat]')), t.rcon('execute if entity @e[type=minecraft:cat]'));

  console.log('== /checkjail e /doas');
  t.rcon('tp EsA 100.5 89 100.5');
  await t.sleep(500);
  await say(a, '/setjail cela', 600);
  out = await say(a, '/checkjail EsB', 700);
  t.check('/checkjail de quem não está preso', /não está preso/.test(out), flat(out));
  await say(a, '/jail EsB 5m briga no chat', 900);
  out = await say(a, '/checkjail EsB', 800);
  t.check('/checkjail mostra a cela, quem prendeu, a pena e o motivo', /cela/.test(out) && /EsA/.test(out) && /faltam/.test(out) && /briga no chat/.test(out), flat(out));
  t.rcon('unjail EsB');
  await t.sleep(1200);
  t.rcon('tp EsB 6.5 89 -3.5', 'gamemode survival EsB');
  await t.sleep(600);
  a.chatLog.length = 0;
  await say(a, '/doas EsB say oi do doas', 900);
  t.check('/doas roda o comando como o outro jogador', a.chatLog.some((l) => /\[EsB\] oi do doas/.test(l)), flat(joined(a)));
  await say(a, '/doas EsB gamemode creative EsB', 900);
  t.check('/doas usa a permissão de quem digitou (jogador comum virou criativo)', /1/.test(t.rcon('data get entity EsB playerGameType')), t.rcon('data get entity EsB playerGameType'));
  t.rcon('gamemode survival EsB');
  out = await say(b, '/doas EsA say oi', 800);
  t.check('/doas é da administração', /Incorrect|Unknown|incompleto|desconhecido/i.test(out), flat(out));

  console.log('== /tempbanip');
  out = await say(a, '/tempbanip ninguem_aqui 1m', 800);
  t.check('/tempbanip recusa quem não está online nem é um IP', /Não achei um jogador online nem um endereço IP/.test(out), flat(out));
  out = await say(a, '/tempbanip 10.9.8.7 1m teste de ip', 900);
  t.check('/tempbanip bane o endereço por um tempo', /10\.9\.8\.7 foi banido/.test(out) && /10\.9\.8\.7/.test(t.rcon('banlist ips')), flat(out) + ' // ' + flat(t.rcon('banlist ips')));
  out = await say(b, '/tempbanip 10.9.8.6 1m', 700);
  t.check('/tempbanip é da administração', /Incorrect|Unknown|incompleto|desconhecido/i.test(out), flat(out));
  t.rcon('pardon-ip 10.9.8.7');

  console.log('== /powertool');
  t.rcon('item replace entity EsA hotbar.0 with minecraft:feather');
  await t.sleep(600);
  out = await say(a, '/powertool', 700);
  t.check('/powertool sem nada avisa', /não tem powertools/.test(out), flat(out));
  out = await say(a, '/powertool set use say poder ativado', 800);
  t.check('/powertool set grava', /Pronto/.test(out) && /minecraft:feather/.test(out), flat(out));
  a.chatLog.length = 0;
  a.setQuickBarSlot(0);
  a.activateItem();
  await t.sleep(1200);
  t.check('usar o item roda o comando', a.chatLog.some((l) => /\[EsA\] poder ativado/.test(l)), flat(joined(a)));
  out = await say(a, '/powertool list', 700);
  t.check('/powertool list mostra o item e o gesto', /feather/.test(out) && /use/.test(out) && /poder ativado/.test(out), flat(out));
  await say(a, '/powertool toggle', 500);
  a.chatLog.length = 0;
  a.activateItem();
  await t.sleep(900);
  t.check('/powertool toggle desliga sem apagar', !a.chatLog.some((l) => /poder ativado/.test(l)), flat(joined(a)));
  await say(a, '/powertool toggle', 500);
  out = await say(a, '/powertool clear', 700);
  t.check('/powertool clear apaga', /apagado/.test(out), flat(out));
  out = await say(a, '/powertool clear', 700);
  t.check('apagar de novo avisa que não tem', /não tem powertool/.test(out), flat(out));
  out = await say(a, '/powertool set bobagem say x', 700);
  t.check('gesto inválido é recusado e lista os válidos', /inválido/.test(out) && /attack_block/.test(out), flat(out));

  console.log('== /cooldown (grupos de recarga)');
  out = await say(b, '/ping', 700);
  t.check('primeiro uso do /ping passa', /Ping de/.test(out), flat(out));
  out = await say(b, '/ping', 700);
  t.check('segundo uso é barrado com o tempo que falta', /Espere/.test(out) && /\/ping/.test(out), flat(out));
  out = await say(a, '/ping', 600);
  out = await say(a, '/ping', 700);
  t.check('quem tem a isenção (nível 3) não espera', /Ping de/.test(out), flat(out));
  out = await say(a, '/cooldown check EsB teste', 800);
  t.check('/cooldown check mostra quanto falta', /espera/.test(out) && /teste/.test(out), flat(out));
  out = await say(a, '/cooldown clear EsB teste', 800);
  t.check('/cooldown clear zera', /zerada/.test(out), flat(out));
  out = await say(b, '/ping', 700);
  t.check('depois do clear o comando volta a funcionar', /Ping de/.test(out), flat(out));
  out = await say(a, '/cooldown list', 700);
  t.check('/cooldown list mostra os grupos', /teste/.test(out) && /ping/.test(out), flat(out));
  await t.sleep(6500);
  out = await say(b, '/ping', 700);
  t.check('passado o tempo, libera sozinho', /Ping de/.test(out), flat(out));

  console.log('== /timebar');
  a.chatLog.length = 0;
  out = await say(a, '/timebar start 4s "Fim em %remaining_time%" say a barra acabou', 900);
  t.check('/timebar start cria a barra', /criada/.test(out) && /#\d+/.test(out), flat(out));
  out = await say(a, '/timebar list', 700);
  t.check('/timebar list mostra a barra', /Barras de tempo/.test(out) && /faltam/.test(out), flat(out));
  a.chatLog.length = 0;
  await t.sleep(4200);
  t.check('no fim, a barra roda o comando', a.chatLog.some((l) => /\[EsA\] a barra acabou/.test(l)), flat(joined(a)));
  out = await say(a, '/timebar list', 700);
  t.check('depois de acabar a barra some', /Não há barras/.test(out), flat(out));
  await say(a, '/timebar custom 30s red notched_10 up "Subindo %elapsed_time%"', 800);
  out = await say(a, '/timebar cancel all', 700);
  t.check('/timebar custom e cancel all', /cancelada/.test(out), flat(out));
  out = await say(a, '/timebar custom 30s azul notched_10 up "x"', 700);
  t.check('cor inválida é recusada com a lista', /inválido/.test(out) && /red/.test(out), flat(out));

  // volta a config ao que era
  const back = JSON.parse(original);
  const cfgBack = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  cfgBack.info.motd = back.motd; cfgBack.cooldowns = back.cooldowns; cfgBack.teleport.warmup_seconds = back.warmup; cfgBack.teleport.cooldown_seconds = back.cooldown;
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfgBack, null, 2), 'utf8');
  t.rcon('serverbuddy reload', 'unjail EsB', 'effect clear EsB', 'forceload remove all', 'deljail cela');
  b.quit();
  a.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
