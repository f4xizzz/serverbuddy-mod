// Wonder Trade with two players: the first trade gets a generated Pokémon (empty pool), the second gets the first player's Pokémon.
// (The test client cannot read Pokémon items, so everything is checked through chat messages and the admin commands.)
const fs = require('fs');
const t = require('./lib.js');

const FILE = 'D:/mods/ServerBuddy/run/config/ServerBuddy/WonderTrade/config.json';
const original = fs.readFileSync(FILE, 'utf8');
const N = String(Date.now() % 100000);
const A = 'WtA' + N;
const B = 'WtB' + N;

async function trade(bot, slot) {
  const picking = t.waitWindow(bot);
  bot.chat('/wondertrade');
  const picker = await picking;
  await t.sleep(900);
  const confirming = t.waitWindow(bot);
  await bot.clickWindow(slot, 0, 0);
  const confirm = await confirming;
  await t.sleep(700);
  const start = bot.chatLog.length;
  await bot.clickWindow(11, 0, 0);
  for (let i = 0; i < 20 && !bot.chatLog.slice(start).some((m) => /You traded|cannot|can trade/.test(m)); i++) await t.sleep(500);
  return { picker, confirm, chat: bot.chatLog.slice(start).join(' | ') };
}

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('wondertrade module is running', /✔ wondertrade/.test(mods), mods.slice(-120));
  fs.writeFileSync(FILE, original.replace(/"cooldown_minutes": \d+/, '"cooldown_minutes": 0').replace(/"serverbuddy.wondertrade.cooldown.vip": \d+/, '"serverbuddy.wondertrade.cooldown.vip": 0'));
  t.rcon('sb reload');
  await t.sleep(600);
  const cleared = t.rcon('wondertrade admin clear').replace(/\n/g, ' ');
  t.check('/wondertrade admin clear empties the pool', /emptied/.test(cleared), cleared.slice(0, 120));
  t.check('the pool is empty', /: 0\b/.test(t.rcon('wondertrade admin pool')));

  const a = await t.join(A);
  t.rcon(`gamemode survival ${A}`);
  t.rcon(`givepokemonother ${A} bulbasaur level=12`, `givepokemonother ${A} charmander level=15`);
  await t.sleep(1200);

  const first = await trade(a, 10);
  t.check('the picker shows the party', /Wonder Trade/.test(t.titleOf(first.picker)), t.titleOf(first.picker));
  t.check('the confirm menu names the trade', /Trade/.test(t.titleOf(first.confirm)), t.titleOf(first.confirm));
  t.check('first trade (empty pool) gets a generated Pokémon', /You traded .* and received .*from the wild/.test(first.chat), first.chat.slice(0, 220));
  t.check('the offered Pokémon joined the pool', /: 1\b/.test(t.rcon('wondertrade admin pool')));
  a.chatLog.length = 0;
  a.chat('/wondertrade history');
  await t.sleep(1200);
  const hist = a.chatLog.join(' | ');
  t.check('/wondertrade history lists the trade', /Your last Wonder Trades/.test(hist) && /Bulbasaur/i.test(hist), hist.slice(0, 200));
  a.quit();
  await t.sleep(1200);

  const b = await t.join(B);
  t.rcon(`gamemode survival ${B}`);
  t.rcon(`givepokemonother ${B} squirtle level=14`, `givepokemonother ${B} pikachu level=14`);
  await t.sleep(1200);
  const second = await trade(b, 10);
  t.check("second player receives the first player's Pokémon", new RegExp(`received .*from ${A}`).test(second.chat), second.chat.slice(0, 240));
  t.check('the pool holds the second offer (1 Pokémon)', /: 1\b/.test(t.rcon('wondertrade admin pool')));

  // cooldown
  fs.writeFileSync(FILE, original.replace(/"cooldown_minutes": \d+/, '"cooldown_minutes": 60'));
  t.rcon('sb reload');
  await t.sleep(500);
  b.chatLog.length = 0;
  const picking = t.waitWindow(b);
  b.chat('/wondertrade');
  await picking;
  await t.sleep(900);
  await b.clickWindow(10, 0, 0);
  await t.sleep(1200);
  t.check('during the cooldown the Pokémon is refused', b.chatLog.some((m) => /You can trade again in/.test(m)), b.chatLog.join(' | ').slice(0, 200));

  fs.writeFileSync(FILE, original);
  t.rcon('sb reload', 'wondertrade admin clear');
  b.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { fs.writeFileSync(FILE, original); console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
