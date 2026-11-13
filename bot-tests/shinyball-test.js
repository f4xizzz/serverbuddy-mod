// Shiny Ball (na config de teste: cobblemon:master_ball, captura garantida): captura -> shiny, já-shiny, e bola que erra.
const t = require('./lib.js');
const Vec3 = t.lib('vec3').Vec3;

const lastLines = (bot, n = 3) => bot.chatLog.slice(-n).join(' | ');

function uuidFromInts(text) {
  const m = text.match(/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/);
  if (!m) return null;
  const hex = m.slice(1, 5).map((n) => (Number(n) >>> 0).toString(16).padStart(8, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function posOf(uuid) {
  const m = t.rcon(`data get entity ${uuid} Pos`).split('\n').pop().match(/\[(-?[\d.]+)d,\s*(-?[\d.]+)d,\s*(-?[\d.]+)d\]/);
  return m ? new Vec3(Number(m[1]), Number(m[2]), Number(m[3])) : null;
}

/** Cria um Pokémon selvagem parado a 3 blocos na frente do bot e devolve o UUID da entidade. */
async function spawnWildInFront(name, props) {
  t.rcon(`execute as ${name} at ${name} run spawnpokemon ${props}`);
  await t.sleep(700);
  const uuid = uuidFromInts(t.rcon(`execute at ${name} run data get entity @e[type=cobblemon:pokemon,limit=1,sort=nearest,distance=..6] UUID`));
  if (!uuid) return null;
  t.rcon(`data merge entity ${uuid} {NoAI:1b}`);
  t.rcon(`execute as ${name} at ${name} run tp ${uuid} ^ ^ ^3`);
  await t.sleep(400);
  return uuid;
}

async function throwAt(bot, uuid) {
  const target = posOf(uuid);
  if (!target) throw new Error('não achei a posição do Pokémon');
  await bot.lookAt(target.offset(0, 1.0, 0), true);
  await t.sleep(300);
  bot.setQuickBarSlot(0);
  await t.sleep(300);
  bot.activateItem();
  await t.sleep(300);
  bot.deactivateItem();
}

(async () => {
  const name = 'BlBot' + (Date.now() % 100000);
  console.log('== jogador de teste: ' + name);
  const bot = await t.join(name);
  t.rcon(`op ${name}`, `clear ${name}`, `clearparty ${name}`, `clearpc ${name}`);
  await t.sleep(500);
  // Arena: uma plataforma lisa no céu. No ponto de entrada dos bots o terreno tem um degrau na altura da cabeça e a bola
  // lançada para o sul bate nele na hora (cai como item ao lado do bot) — o teste não pode depender do terreno.
  t.rcon('forceload add 80 48 128 128');
  await t.sleep(3500);
  t.rcon('fill 85 199 50 125 199 120 minecraft:stone', 'fill 85 200 50 125 206 120 minecraft:air', `tp ${name} 105.5 200 115.5`);
  await t.sleep(1500);
  t.rcon(`poketoken give ${name} shiny_ball 6`, 'kill @e[type=item]');
  await t.sleep(1000);
  console.log('  inventário: ' + bot.inventory.items().map((i) => `${i.slot}:${i.name}x${i.count}`).join(' '));

  console.log('== 1) capturar um Pokémon selvagem com a Shiny Ball');
  let wild = await spawnWildInFront(name, 'snorlax level=1');
  t.check('Pokémon selvagem criado na frente do bot', !!wild, String(wild));
  if (!wild) { bot.quit(); process.exit(1); }
  console.log('  pos do bot: ' + bot.entity.position + ' | pos do Pokémon: ' + posOf(wild));
  bot.chatLog.length = 0;
  await throwAt(bot, wild);
  await t.sleep(9000);
  console.log('  chat: ' + lastLines(bot, 4));
  t.check('a Shiny Ball avisou que transformou em shiny', bot.chatLog.some((m) => m.includes('Shiny Ball transformou')), lastLines(bot, 4));
  t.check('a bola foi gasta (6 -> 5)', t.count(bot, 'cobblemon:master_ball') === 5, 'bolas=' + t.count(bot, 'cobblemon:master_ball'));
  const log1 = require('fs').readFileSync('D:/mods/ServerBuddy/run/logs/latest.log', 'utf8');
  t.check('o log registrou a captura shiny', log1.includes(`${name} capturou um`) && log1.includes("shiny com a 'shiny_ball'"));

  // o Pokémon capturado está no time: o /pokesee mostra a estrela do shiny
  bot.rawIgnored = [];
  const waiting = new Promise((res) => bot._client.once('open_window', () => setTimeout(res, 800)));
  bot.chat('/pokesee');
  await waiting;
  const raw = bot.rawIgnored.map((b) => b.toString('utf8')).join('\n');
  t.check('no /pokesee o snorlax capturado aparece com a estrela de shiny', raw.includes('cobblemon.species.snorlax.name') && raw.includes('\u2605'));
  bot.closeWindow(bot.currentWindow);
  await t.sleep(500);

  console.log('== 2) capturar um Pokémon que JÁ é shiny');
  wild = await spawnWildInFront(name, 'snorlax level=1 shiny=yes');
  t.check('segundo Pokémon selvagem criado', !!wild, String(wild));
  if (wild) {
    bot.chatLog.length = 0;
    await throwAt(bot, wild);
    await t.sleep(9000);
    console.log('  chat: ' + lastLines(bot, 4));
    t.check('avisou que já era shiny', bot.chatLog.some((m) => m.includes('já era shiny')), lastLines(bot, 4));
  }

  console.log('== 3) bola que erra e cai no chão volta como Shiny Ball');
  t.rcon('kill @e[type=item]', 'kill @e[type=cobblemon:pokemon]', 'kill @e[type=cobblemon:empty_pokeball]');
  await t.sleep(500);
  const bolasAntes = t.count(bot, 'cobblemon:master_ball');
  await bot.look(0, 0.2, true); // um pouco para cima: a bola faz um arco e cai a ~30 blocos, ainda sobre a plataforma
  await t.sleep(500);
  bot.setQuickBarSlot(0);
  await t.sleep(300);
  bot.activateItem();
  await t.sleep(300);
  bot.deactivateItem();
  await t.sleep(5000);
  console.log('  bolas no inventário: ' + bolasAntes + ' -> ' + t.count(bot, 'cobblemon:master_ball'));
  // só a resposta do servidor: sem item achado, o RCON devolve apenas o eco do comando (que também contém "master_ball")
  const drop = t.rcon(`execute at ${name} run data get entity @e[type=item,nbt={Item:{id:"cobblemon:master_ball"}},limit=1,sort=nearest,distance=..80] Item`)
    .split('\n').filter((l) => l.trim() && !l.startsWith('>')).join(' ');
  console.log('  item caído: ' + (drop.slice(0, 600) || '(nenhum)'));
  t.check('a bola caiu como item (não sumiu)', drop.includes('master_ball'), drop.slice(0, 200));
  t.check('o item caído é a Shiny Ball (tem o marcador serverbuddy_token)', drop.includes('serverbuddy_token'), drop.slice(0, 300));

  t.rcon(`clear ${name}`, `clearparty ${name}`, `clearpc ${name}`, 'kill @e[type=item]', 'forceload remove all');
  console.log('== fim');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
