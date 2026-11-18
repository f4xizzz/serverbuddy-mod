// Essentials · estilo: formato do chat (por tag), cores só para quem pode, trocas, /ignore, avisos de entrada, saída, primeira entrada,
// morte, conquista e /me, lista de jogadores (cabeçalho, rodapé, nomes), menções com som, /notifications e placas coloridas.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Essentials';
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const joined = (bot) => bot.chatLog.join('\n');
const flat = (s) => s.replace(/\n/g, ' | ');
async function say(bot, cmd, wait = 900) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return joined(bot); }
const sawText = (bot, re) => bot.chatLog.some((l) => re.test(l));

function watch(bot) {
  bot.sounds = [];
  bot.rawIgnored = [];
  bot._client.on('sound_effect', (d) => bot.sounds.push(JSON.stringify(d)));
  bot.headers = [];
  bot._client.on('playerlist_header', (d) => bot.headers.push(JSON.stringify(d)));
}
const heard = (bot, re) => bot.sounds.some((s) => re.test(s)) || bot.rawIgnored.some((b) => re.test(b.toString('latin1')));

(async () => {
  console.log('== preparação');
  const file = fs.readFileSync(DIR + '/config.json', 'utf8');
  const cfg = JSON.parse(strip(file));
  const originalStyling = JSON.stringify(cfg.styling);
  cfg.teleport.warmup_seconds = 1; cfg.teleport.cooldown_seconds = 0;
  cfg.styling = {
    chat_format: {
      enabled: true,
      formats: { default: '<gray>[J]</gray> %player%<gray>:</gray> %message%', 'tag:vipx': '<gold>[V]</gold> %player%<gray>:</gray> %message%' },
      message_formatting_permission: 'serverbuddy.essentials.chat.format',
      replacements: { ':shrug:': '(shrug)', ':coracao:': '<3' },
      respect_ignore: true,
    },
    events: {
      enabled: true,
      join: '<green>+</green> %player% entrou', leave: '<red>-</red> %player% saiu', first_join: '<light_purple>Bem-vindo %player%!</light_purple>',
      death: '<gray>☠ %message%</gray>', advancement_task: '<green>OK</green> %player% fez %title%',
      advancement_goal: '<aqua>META</aqua> %player% fez %title%', advancement_challenge: '<light_purple>DESAFIO</light_purple> %player% fez %title%',
      emote: '<gray>* %player% %message%</gray>',
    },
    tab_list: { enabled: true, update_ticks: 20, header: ['<gold>CABECALHO %online%/%max%</gold>'], footer: ['<gray>RODAPE %player%</gray>'], player_format: '%afk%[T] %player%' },
    mentions: { enabled: true, sound: 'minecraft:block.note_block.pling', pitch: 1.0, volume: 1.0, afk_only: false },
    signs: { enabled: true, permission: 'serverbuddy.essentials.sign.format' },
  };
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('serverbuddy reload');
  await t.sleep(800);

  // chão firme (y=88) e ar em cima nos pontos de teste
  const spots = [[2.5, -3.5], [6.5, -3.5]];
  for (const [x, z] of spots) t.rcon(`forceload add ${Math.floor(x) - 3} ${Math.floor(z) - 3} ${Math.floor(x) + 3} ${Math.floor(z) + 3}`);
  await t.sleep(3000);
  for (const [x, z] of spots) {
    const [x0, x1, z0, z1] = [Math.floor(x) - 2, Math.floor(x) + 2, Math.floor(z) - 2, Math.floor(z) + 2];
    t.rcon(`fill ${x0} 88 ${z0} ${x1} 88 ${z1} minecraft:stone`, `fill ${x0} 89 ${z0} ${x1} 93 ${z1} minecraft:air`);
  }

  const a = await t.join('EsA');
  const b = await t.join('EsB');
  watch(a); watch(b);
  // um teste anterior (essentials-test) deixa o apelido "Fofa" no EsA: aqui os nomes têm que ser os de verdade
  a.chat('/nick'); b.chat('/nick');
  await t.sleep(700);
  t.rcon('op EsA', 'deop EsB', 'gamemode survival EsA', 'gamemode survival EsB', 'clear EsA', 'clear EsB', 'tp EsA 2.5 89 -3.5', 'tp EsB 6.5 89 -3.5',
    'tag EsA add vipx', 'tag EsB remove vipx');
  await t.sleep(1200);
  let out;

  console.log('== formato do chat');
  b.chatLog.length = 0;
  out = await say(a, 'olá mundo');
  t.check('quem tem a tag vipx usa o formato [V]', sawText(b, /^\[V\] EsA: olá mundo$/), flat(joined(b)));
  t.check('quem escreve também vê a própria linha formatada', sawText(a, /^\[V\] EsA: olá mundo$/), flat(joined(a)));
  a.chatLog.length = 0;
  await say(b, 'oi gente');
  t.check('quem não tem usa o default [J]', sawText(a, /^\[J\] EsB: oi gente$/), flat(joined(a)));
  t.check('o chat de vanilla não aparece junto (nada de "<EsB>")', !sawText(a, /<EsB>/), flat(joined(a)));
  a.chatLog.length = 0;
  await say(b, 'olha :shrug: e :coracao:');
  t.check('as trocas (:shrug:) valem', sawText(a, /\[J\] EsB: olha \(shrug\) e <3/), flat(joined(a)));

  console.log('== cores só para quem pode');
  b.chatLog.length = 0;
  await say(a, '&cvermelho &lnegrito &#ff8800laranja');
  t.check('quem tem a permissão usa &c, &l e hexadecimal (os códigos somem)', sawText(b, /\[V\] EsA: vermelho negrito laranja$/), flat(joined(b)));
  a.chatLog.length = 0;
  await say(b, '&cvermelho <red>x</red>');
  t.check('quem não tem escreve texto puro (os códigos ficam como foram digitados)', sawText(a, /\[J\] EsB: &cvermelho <red>x<\/red>$/), flat(joined(a)));
  b.chatLog.length = 0;
  await say(a, "<click:run_command:'/say hacked'>clique</click> <hover:show_text:'x'>h</hover> <red>ok</red>");
  t.check('tags de clique e hover não são interpretadas, as de cor sim', sawText(b, /<click:run_command:'\/say hacked'>clique<\/click> <hover:show_text:'x'>h<\/hover> ok$/), flat(joined(b)));

  console.log('== /ignore e silenciar continuam valendo');
  await say(a, '/ignore EsB', 600);
  a.chatLog.length = 0;
  await say(b, 'você me vê?');
  t.check('quem ignora não vê a linha', !sawText(a, /você me vê/), flat(joined(a)));
  t.check('quem escreveu vê a própria linha', sawText(b, /você me vê/), flat(joined(b)));
  await say(a, '/unignore EsB', 600);
  t.rcon('mute EsB 20s');
  await t.sleep(500);
  a.chatLog.length = 0;
  await say(b, 'calado');
  t.check('silenciado não fala (o formato não fura o mute)', !sawText(a, /calado/), flat(joined(a)));
  t.rcon('unmute EsB');
  await t.sleep(400);

  console.log('== menções com som');
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(a, 'ei EsB, vem cá');
  await t.sleep(400);
  t.check('quem foi citado ouve o aviso (pling)', heard(b, /pling/), JSON.stringify(b.sounds).slice(0, 300));
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(a, 'EsBonito não é ninguém');
  await t.sleep(400);
  t.check('um nome só como parte de outra palavra não conta', !heard(b, /pling/), JSON.stringify(b.sounds).slice(0, 200));
  out = await say(b, '/notifications', 800);
  t.check('/notifications mostra os ajustes', /Notificações|Notifications/.test(out) && /(Som|Sound): minecraft:block.note_block.pling/.test(out) && /(Ligadas|Enabled): (sim|yes)/.test(out), flat(out));
  await say(b, '/notifications toggle', 600);
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(a, 'EsB de novo');
  await t.sleep(400);
  t.check('com as notificações desligadas não toca', !heard(b, /pling/), JSON.stringify(b.sounds).slice(0, 200));
  await say(b, '/notifications toggle', 600);
  await say(b, '/notifications chat false', 600);
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(a, 'EsB mais uma');
  await t.sleep(400);
  t.check('"chat false" desliga o aviso das menções no chat', !heard(b, /pling/), JSON.stringify(b.sounds).slice(0, 200));
  await say(b, '/notifications chat true', 600);
  await say(b, '/notifications afkonly true', 600);
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(a, 'EsB e o afkonly');
  await t.sleep(400);
  t.check('"afkonly true": parado não é o caso, então não toca', !heard(b, /pling/), JSON.stringify(b.sounds).slice(0, 200));
  await say(b, '/notifications afkonly false', 600);
  b.sounds.length = 0; b.rawIgnored.length = 0;
  await say(b, '/notifications sound minecraft:entity.experience_orb.pickup', 800);
  t.check('/notifications sound toca uma prévia do som novo', heard(b, /experience_orb/), JSON.stringify(b.sounds).slice(0, 200));
  out = await say(b, '/notifications volume 5', 500);
  t.check('volume acima do limite é recusado', /Incorrect|Unknown|incompleto|must not be more/i.test(out), flat(out));
  await say(b, '/notifications reset', 600);
  out = await say(b, '/notifications', 700);
  t.check('/notifications reset volta ao padrão do servidor', /pling/.test(out), flat(out));

  console.log('== morte, conquista e /me');
  a.chatLog.length = 0;
  t.rcon('kill EsB');
  await t.sleep(1200);
  t.check('a morte sai no formato ☠ (e a frase do Minecraft vem dentro)', sawText(a, /^☠ EsB/), flat(joined(a)));
  t.rcon('tp EsB 6.5 89 -3.5');
  await t.sleep(800);
  t.rcon('advancement revoke EsB everything');
  await t.sleep(500);
  a.chatLog.length = 0;
  t.rcon('advancement grant EsB only minecraft:story/mine_stone');
  await t.sleep(1000);
  t.check('a conquista sai no formato OK com o título', sawText(a, /^OK EsB fez \[Stone Age\]$/), flat(joined(a)));
  t.check('o aviso de conquista do Minecraft some', !sawText(a, /has made the advancement/), flat(joined(a)));
  a.chatLog.length = 0;
  await say(b, '/me dança no chão', 900);
  t.check('/me sai no formato de emote', sawText(a, /^\* EsB dança no chão$/), flat(joined(a)));

  console.log('== entrada, saída e primeira entrada');
  a.chatLog.length = 0;
  b.quit();
  await t.sleep(1500);
  t.check('a saída sai no formato (e o do Minecraft some)', sawText(a, /^- EsB saiu$/) && !sawText(a, /left the game/), flat(joined(a)));
  const stranger = 'EsN' + String(Date.now()).slice(-7);
  a.chatLog.length = 0;
  const c = await t.join(stranger);
  await t.sleep(1200);
  t.check('quem chega pela primeira vez gera o aviso de boas-vindas', sawText(a, new RegExp('^Bem-vindo ' + stranger + '!$')) && !sawText(a, /joined the game/), flat(joined(a)));
  a.chatLog.length = 0;
  c.quit();
  await t.sleep(1500);
  const b2 = await t.join('EsB');
  watch(b2);
  await t.sleep(1500);
  t.check('quem já veio antes usa o aviso comum de entrada', sawText(a, /^\+ EsB entrou$/) && !sawText(a, /Bem-vindo EsB/), flat(joined(a)));
  t.rcon('deop EsB', 'gamemode survival EsB', 'tp EsB 6.5 89 -3.5');

  console.log('== lista de jogadores (Tab)');
  await t.sleep(2500);
  t.check('o cabeçalho chega com os valores trocados', a.headers.some((h) => /CABECALHO 2\/2/.test(h)), (a.headers.slice(-1)[0] || '').slice(0, 200));
  t.check('o rodapé chega com o nome de quem recebe', a.headers.some((h) => /RODAPE EsA/.test(h)), (a.headers.slice(-1)[0] || '').slice(0, 200));
  const nameOf = (bot, who) => { const p = bot.players[who]; return p && p.displayName ? p.displayName.toString() : '(sem nome)'; };
  t.check('o nome na lista segue o formato [T]', /\[T\] EsB/.test(nameOf(a, 'EsB')), nameOf(a, 'EsB'));
  await say(b2, '/afk', 700);
  await t.sleep(2500);
  t.check('quem fica AFK aparece com [AFK] na lista', /\[AFK\] \[T\] EsB/.test(nameOf(a, 'EsB')), nameOf(a, 'EsB'));
  await say(b2, '/afk', 700);

  console.log('== placas coloridas');
  const target = { x: 2, y: 88, z: -3 };
  const signAt = { x: 2, y: 89, z: -3 };
  const placeSign = async (bot, lines) => {
    const opened = new Promise((res, rej) => { bot._client.once('open_sign_entity', res); setTimeout(() => rej(new Error('o editor da placa não abriu')), 6000); });
    bot._client.write('block_place', { hand: 0, location: target, direction: 1, cursorX: 0.5, cursorY: 1, cursorZ: 0.5, insideBlock: false, sequence: 1 });
    const ev = await opened;
    await t.sleep(300);
    bot._client.write('update_sign', { location: ev.location, isFrontText: true, text1: lines[0], text2: lines[1], text3: lines[2], text4: lines[3] });
    await t.sleep(900);
  };
  t.rcon('setblock 2 89 -3 minecraft:air', 'tp EsA 2.5 89 -1.5', 'item replace entity EsA weapon.mainhand with minecraft:oak_sign');
  await t.sleep(900);
  await placeSign(a, ['&cVermelho', '&lNegrito', 'normal', '<green>verde</green>']);
  const dataA = t.rcon('data get block 2 89 -3 front_text.messages');
  console.log('  placa de EsA: ' + dataA.replace(/\n/g, ' ').slice(0, 300));
  t.check('placa de quem pode: cor e negrito aplicados, sem os códigos', /red/.test(dataA) && /bold/.test(dataA) && /green/.test(dataA) && !/&c/.test(dataA) && !/&l/.test(dataA), dataA);
  t.rcon('setblock 2 89 -3 minecraft:air', 'tp EsB 2.5 89 -1.5', 'item replace entity EsB weapon.mainhand with minecraft:oak_sign');
  await t.sleep(900);
  await placeSign(b2, ['&cVermelho', '&lNegrito', 'normal', '<green>verde</green>']);
  const dataB = t.rcon('data get block 2 89 -3 front_text.messages');
  console.log('  placa de EsB: ' + dataB.replace(/\n/g, ' ').slice(0, 300));
  t.check('placa de quem não pode: o texto fica como foi digitado', /&cVermelho/.test(dataB) && /&lNegrito/.test(dataB) && /<green>verde<\/green>/.test(dataB), dataB);

  // volta a config ao que era
  cfg.styling = JSON.parse(originalStyling);
  fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');
  t.rcon('serverbuddy reload', 'setblock 2 89 -3 minecraft:air', 'tag EsA remove vipx', 'forceload remove all');
  b2.quit();
  a.quit();
  await t.sleep(800);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
