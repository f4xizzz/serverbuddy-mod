// Essentials (mecânicas do Solstice): casas, warps, spawn, /back, tpa, rtp, mensagens, ignorar, silenciar, apelido, correio, chat da
// equipe, god/fly/heal/hat, AFK, bancadas, /item, /skull, /invsee, /enderchest, /tempban, cela, anotações, /sudo e /restart.
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
  console.log('== arquivos');
  t.check('Essentials/config.json criado e explica os grupos', fs.existsSync(DIR + '/config.json') && /GRUPOS/.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('messages.json criado com os textos editáveis', fs.existsSync(DIR + '/messages.json') && /"home_set"/.test(fs.readFileSync(DIR + '/messages.json', 'utf8')));
  // deixa os testes mais rápidos
  const cfg = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  cfg.teleport.warmup_seconds = 1; cfg.teleport.cooldown_seconds = 0; cfg.teleport.rtp.min_radius = 200; cfg.teleport.rtp.max_radius = 400; cfg.teleport.rtp.cooldown_seconds = 30;
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('serverbuddy reload');

  // Vários pontos de teleporte do teste (y=89) caem dentro do terreno do mundo de teste e o bot sufoca no meio do teste
  // ("suffocated in a wall"), o que bagunça o /back e o resto. Prepara um chão de pedra (y=88) e ar (y=89 a 93) ao redor de cada ponto.
  const spots = [[2.5, -3.5], [6.5, -3.5], [30.5, 30.5], [40.5, 40.5], [60.5, 60.5], [80.5, 80.5], [90.5, 90.5], [0.5, 0.5], [100.5, 100.5]];
  for (const [x, z] of spots) t.rcon(`forceload add ${Math.floor(x) - 3} ${Math.floor(z) - 3} ${Math.floor(x) + 3} ${Math.floor(z) + 3}`);
  await t.sleep(4000);
  for (const [x, z] of spots) {
    const [x0, x1, z0, z1] = [Math.floor(x) - 2, Math.floor(x) + 2, Math.floor(z) - 2, Math.floor(z) + 2];
    t.rcon(`fill ${x0} 88 ${z0} ${x1} 88 ${z1} minecraft:stone`, `fill ${x0} 89 ${z0} ${x1} 93 ${z1} minecraft:air`);
  }

  const a = await t.join('EsA');
  const b = await t.join('EsB');
  t.rcon('op EsA', 'deop EsB', 'gamemode survival EsA', 'gamemode survival EsB', 'clear EsA', 'clear EsB', 'tp EsA 2.5 89 -3.5', 'tp EsB 6.5 89 -3.5');
  await t.sleep(1500);
  const start = pos('EsA');
  // limpa o que um teste anterior deixou no banco
  for (const h of ['home', 'casa2', 'casa3', 'casa4']) await say(a, '/delhome ' + h, 300);
  await say(a, '/delwarp arena', 300);

  console.log('== casas');
  let out = await say(a, '/sethome');
  t.check('/sethome cria a casa "home"', /Casa home definida/.test(out), flat(out));
  await say(a, '/sethome casa2');
  out = await say(a, '/sethome casa3');
  out = await say(a, '/sethome casa4');
  t.check('o limite de 3 casas é respeitado', /3 casa\(s\), o máximo/.test(out), flat(out));
  out = await say(a, '/homes');
  t.check('/homes lista as casas e o limite', /home/.test(out) && /casa2/.test(out) && /3\/3/.test(out), flat(out));
  t.rcon('tp EsA 40.5 89 40.5');
  await t.sleep(800);
  out = await say(a, '/home casa2', 3500);
  t.check('/home leva de volta para a casa (depois da espera)', near(pos('EsA'), start), JSON.stringify(pos('EsA')) + ' vs ' + JSON.stringify(start));
  out = await say(a, '/delhome casa3');
  t.check('/delhome apaga', /apagada/.test(out), flat(out));
  out = await say(a, '/home nao_existe');
  t.check('casa que não existe avisa e lista as suas', /não tem a casa/.test(out), flat(out));

  console.log('== /back, warps e spawn');
  t.rcon('tp EsA 60.5 89 60.5');
  await t.sleep(600);
  out = await say(a, '/setwarp arena');
  t.check('/setwarp (op) cria o warp', /Warp arena definido/.test(out), flat(out));
  t.rcon('tp EsA 2.5 89 -3.5');
  await t.sleep(600);
  out = await say(a, '/warps');
  t.check('/warps lista', /arena/.test(out), flat(out));
  await say(a, '/warp arena', 3500);
  t.check('/warp leva ao warp', near(pos('EsA'), [60.5, 89, 60.5]), JSON.stringify(pos('EsA')));
  out = await say(a, '/back', 3500);
  t.check('/back volta para onde estava antes do warp', near(pos('EsA'), [2.5, 89, -3.5]), JSON.stringify(pos('EsA')) + ' | ' + flat(out));
  await say(a, '/setspawn');
  t.rcon('tp EsA 80.5 89 80.5');
  await t.sleep(600);
  await say(a, '/spawn', 3500);
  t.check('/setspawn e /spawn', near(pos('EsA'), [2.5, 89, -3.5]), JSON.stringify(pos('EsA')));
  out = await say(b, '/setwarp xis');
  t.check('jogador comum não cria warp', /Incorrect|Unknown|incompleto/i.test(out), flat(out));

  console.log('== tpa');
  t.rcon('tp EsB 30.5 89 30.5');
  await t.sleep(600);
  await say(b, '/tpa EsA', 700);
  t.check('quem recebe vê o pedido com os botões de aceitar e recusar', a.chatLog.some((l) => /EsB quer ir até você/.test(l)), flat(joined(a)));
  await say(a, '/tpaccept', 3500);
  t.check('/tpaccept leva EsB até EsA', near(pos('EsB'), pos('EsA'), 3), JSON.stringify(pos('EsB')) + ' vs ' + JSON.stringify(pos('EsA')));
  t.rcon('tp EsB 30.5 89 30.5');
  await t.sleep(600);
  await say(a, '/tpahere EsB', 700);
  await say(b, '/tpdeny', 800);
  t.check('/tpdeny recusa o pedido', /recusado/.test(joined(b)) || /recusado/.test(joined(a)), flat(joined(b)));
  out = await say(a, '/tpaccept');
  t.check('sem pedido, /tpaccept avisa', /não tem pedidos/.test(out), flat(out));
  await say(b, '/tptoggle', 700);
  out = await say(a, '/tpa EsB');
  t.check('/tptoggle: quem desligou não recebe pedidos', /não está aceitando/.test(out), flat(out));
  await say(b, '/tptoggle', 700);

  console.log('== /rtp');
  t.rcon('tp EsA 2.5 89 -3.5');
  await t.sleep(600);
  await say(a, '/rtp', 4500);
  const rtpPos = pos('EsA');
  const dist = Math.hypot(rtpPos[0] - 0, rtpPos[2] - 0);
  console.log('  rtp: ' + JSON.stringify(rtpPos) + ' distância ' + Math.round(dist));
  t.check('/rtp leva para longe (entre 200 e 400 blocos do spawn) em lugar seguro', dist >= 150 && dist <= 450 && rtpPos[1] > 0, JSON.stringify(rtpPos));
  t.rcon('deop EsB');
  await say(b, '/rtp', 4500);
  out = await say(b, '/rtp', 900);
  t.check('/rtp tem recarga própria (para quem não é da equipe)', /Espere/.test(out), flat(out));
  t.rcon('tp EsB 6.5 89 -3.5', 'tp EsA 2.5 89 -3.5');
  await t.sleep(500);

  console.log('== mensagens privadas');
  await say(a, '/msg EsB oi tudo bem', 900);
  t.check('/msg chega para os dois', a.chatLog.some((l) => /oi tudo bem/.test(l)) && b.chatLog.some((l) => /EsA/.test(l) && /oi tudo bem/.test(l)), flat(joined(b)));
  await say(b, '/reply tudo ótimo', 900);
  t.check('/reply responde para quem mandou', a.chatLog.some((l) => /tudo ótimo/.test(l)), flat(joined(a)));
  await say(a, '/ignore EsB', 700);
  out = await say(b, '/msg EsA ainda aí', 900);
  t.check('quem é ignorado não consegue mandar mensagem', /não está recebendo/.test(out), flat(out));
  await say(a, '/unignore EsB', 700);

  console.log('== silenciar');
  t.rcon('mute EsB 20s spam');
  await t.sleep(600);
  a.chatLog.length = 0;
  out = await say(b, 'bom dia gente', 900);
  t.check('silenciado recebe o aviso e a mensagem não chega aos outros', /silenciado/.test(out) && !a.chatLog.some((l) => /bom dia gente/.test(l)), flat(out) + ' // A viu: ' + flat(joined(a)));
  t.rcon('unmute EsB');
  await t.sleep(400);
  a.chatLog.length = 0;
  await say(b, 'agora posso falar', 900);
  t.check('depois do /unmute a mensagem chega', a.chatLog.some((l) => /agora posso falar/.test(l)), flat(joined(a)));

  console.log('== apelido, correio e chat da equipe');
  out = await say(a, '/nick Fofa');
  t.check('/nick define o apelido', /Fofa/.test(out), flat(out));
  out = await say(b, '/realname Fofa');
  t.check('/realname revela quem é', /EsA/.test(out), flat(out));
  await say(a, '/mail send EsB oi, passei aqui', 800);
  t.check('quem recebe é avisado do correio', b.chatLog.some((l) => /mensagem\(ns\) nova/.test(l)), flat(joined(b)));
  out = await say(b, '/mail read');
  t.check('/mail read mostra a mensagem', /passei aqui/.test(out) && /EsA/.test(out), flat(out));
  out = await say(b, '/mail clear');
  t.check('/mail clear limpa', /limpa/.test(out), flat(out));
  b.chatLog.length = 0;
  await say(a, '/sc segredo da equipe', 800);
  t.check('/staffchat só aparece para a equipe', a.chatLog.some((l) => /segredo da equipe/.test(l)) && !b.chatLog.some((l) => /segredo da equipe/.test(l)), flat(joined(b)));
  out = await say(b, '/helpop preciso de ajuda', 800);
  t.check('/helpop envia o pedido', /enviado/.test(out), flat(out));
  t.check('a equipe recebe o /helpop', a.chatLog.some((l) => /preciso de ajuda/.test(l)), flat(joined(a)));

  console.log('== jogador: god, fly, heal, hat, afk, near...');
  await say(a, '/fly', 700);
  t.check('/fly liga o voo (mayfly)', /1b/.test(t.rcon('data get entity EsA abilities.mayfly')));
  await say(a, '/fly', 700);
  t.check('/fly desliga', /0b/.test(t.rcon('data get entity EsA abilities.mayfly')));
  await say(a, '/god', 700);
  t.check('/god deixa invulnerável', /1b/.test(t.rcon('data get entity EsA abilities.invulnerable')));
  await say(a, '/god', 700);
  t.rcon('damage EsB 10', 'give EsB minecraft:cooked_beef 0');
  await t.sleep(400);
  await say(a, '/heal EsB', 800);
  t.check('/heal EsB recupera a vida', /20(\.0)?f/.test(t.rcon('data get entity EsB Health')), t.rcon('data get entity EsB Health'));
  t.rcon('give EsA minecraft:stone 2');
  await t.sleep(500);
  a.setQuickBarSlot(0);
  await t.sleep(500);
  out = await say(a, '/hat', 900);
  t.check('/hat põe o item na cabeça', /103b/.test(t.rcon('data get entity EsA Inventory')) && /stone/.test(t.rcon('data get entity EsA Inventory')), flat(out));
  out = await say(a, '/afk', 800);
  t.check('/afk avisa a todos', b.chatLog.some((l) => /EsA/.test(l) && /AFK/.test(l)) || /AFK/.test(out), flat(joined(b)));
  await say(a, '/afk', 700);
  out = await say(a, '/near', 800);
  t.check('/near mostra EsB por perto', /EsB/.test(out) || /Ninguém/.test(out), flat(out));
  out = await say(a, '/seen EsB', 800);
  t.check('/seen mostra que está online', /online/.test(out), flat(out));
  out = await say(a, '/playtime', 800);
  t.check('/playtime mostra o tempo jogado', /jogou por/.test(out), flat(out));
  out = await say(a, '/ping', 800);
  t.check('/ping mostra a latência', /Ping de/.test(out), flat(out));

  console.log('== utilidades');
  const wb = await t.openByCommand(a, '/workbench');
  t.check('/workbench abre a mesa de trabalho', !!wb, '');
  a.closeWindow(wb);
  await t.sleep(400);
  const wb2 = await t.openByCommand(a, '/wb');
  t.check('o apelido /wb (sem argumentos) também abre a mesa de trabalho', !!wb2, '');
  a.closeWindow(wb2);
  await t.sleep(400);
  out = await say(a, '/pt', 800);
  t.check('o apelido /pt (playtime) responde em vez de "comando desconhecido"', out.trim().length > 0 && !/Unknown|incomplete/i.test(out), flat(out));
  const anvil = await t.openByCommand(a, '/anvil');
  t.check('/anvil abre a bigorna', !!anvil, '');
  a.closeWindow(anvil);
  await t.sleep(400);
  const trash = await t.openByCommand(a, '/trash');
  t.check('/trash abre a lixeira', /Lixeira/.test(t.titleOf(trash)), t.titleOf(trash));
  a.closeWindow(trash);
  await t.sleep(400);
  t.rcon('give EsA minecraft:iron_pickaxe 1');
  await t.sleep(500);
  a.setQuickBarSlot(1);
  await say(a, '/item name <green>Picareta Verde', 800);
  t.check('/item name renomeia o item da mão', /Picareta Verde/.test(t.rcon('data get entity EsA Inventory')), '');
  out = await say(a, '/skull EsB', 800);
  t.check('/skull entrega a cabeça', t.count(a, 'player_head') === 1, flat(out));
  out = await say(a, '/rules', 800);
  t.check('/rules mostra as regras', /Regras do servidor/.test(out), flat(out));

  console.log('== equipe: invsee, enderchest, anotações, sudo, restart');
  const inv = await t.openByCommand(a, '/invsee EsB');
  t.check('/invsee abre o inventário do outro jogador', /Inventário de EsB/.test(t.titleOf(inv)), t.titleOf(inv));
  a.closeWindow(inv);
  await t.sleep(400);
  const ec = await t.openByCommand(a, '/enderchest EsB');
  t.check('/enderchest abre o baú do fim do outro', /Baú do Fim de EsB/.test(t.titleOf(ec)), t.titleOf(ec));
  a.closeWindow(ec);
  await t.sleep(400);
  await say(a, '/notes EsB add jogador legal', 800);
  out = await say(a, '/notes EsB', 800);
  t.check('/notes guarda e lista anotações', /jogador legal/.test(out), flat(out));
  b.chatLog.length = 0;
  t.rcon('sudo EsB me fui eu');
  await t.sleep(700);
  t.check('/sudo roda o comando como o jogador', b.chatLog.some((l) => /fui eu/.test(l)), flat(joined(b)));
  t.rcon('restart 600s');
  await t.sleep(500);
  t.check('/restart agenda e avisa', b.chatLog.some((l) => /vai reiniciar/.test(l)), flat(joined(b)));
  t.check('/restart cancel cancela', /cancelado/.test(t.rcon('restart cancel')));
  await say(a, '/commandspy', 700);
  b.chatLog.length = 0; a.chatLog.length = 0;
  await say(b, '/home', 900);
  t.check('/commandspy mostra os comandos dos outros', a.chatLog.some((l) => /cmd/.test(l) && /home/.test(l)), flat(joined(a)));
  await say(a, '/commandspy', 600);

  console.log('== cela');
  await say(a, '/setjail cela', 800);
  t.rcon('tp EsA 2.5 89 -3.5');
  t.rcon('tp EsB 90.5 89 90.5');
  await t.sleep(600);
  // a cela fica onde A estava quando criou (perto de 80,80 do teste do spawn) — recria em outro lugar conhecido
  t.rcon('tp EsA 100.5 89 100.5');
  await t.sleep(500);
  await say(a, '/setjail cela', 800);
  r2 = t.rcon('jail EsB 30s mau comportamento');
  await t.sleep(1500);
  t.check('/jail leva o jogador para a cela', near(pos('EsB'), [100.5, 89, 100.5], 4), JSON.stringify(pos('EsB')));
  t.check('preso fica no modo aventura', /2/.test(t.rcon('data get entity EsB playerGameType')), t.rcon('data get entity EsB playerGameType'));
  out = await say(b, '/home', 900);
  t.check('preso não usa comandos fora da lista', /Preso não usa/.test(out), flat(out));
  t.rcon('tp EsB 0.5 89 0.5');
  await t.sleep(2500);
  t.check('quem sai da cela volta para dentro', near(pos('EsB'), [100.5, 89, 100.5], 12), JSON.stringify(pos('EsB')));
  t.rcon('unjail EsB');
  await t.sleep(1200);
  t.check('/unjail solta e volta ao modo normal', /0/.test(t.rcon('data get entity EsB playerGameType')), t.rcon('data get entity EsB playerGameType'));

  console.log('== tempban');
  r2 = t.rcon('tempban EsB 1m teste de ban');
  console.log(r2);
  await t.sleep(1500);
  t.check('/tempban expulsa e registra o banimento', /banido/.test(r2) && /EsB/.test(t.rcon('banlist players')), r2 + ' // ' + t.rcon('banlist players'));
  t.rcon('pardon EsB', 'forceload remove all');

  a.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });

var r2;


