// Daycare: leave a Pokémon, watch it gain levels over time, take it back. Needs Cobblemon. Prices are set to 0 so no economy is needed.
// (The test client cannot read Pokémon items, so the Pokémon themselves are checked through chat messages and /daycare admin list.)
const fs = require('fs');
const t = require('./lib.js');

const FILE = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Daycare/config.json';
const original = fs.readFileSync(FILE, 'utf8');
const item = (w, slot) => w.slots[slot];
const text = (w, slot) => { const it = item(w, slot); return it ? `${it.name} ${t.itemText(it)}` : '(empty)'; };
const levelOf = (listing) => Number((listing.match(/level (\d+)/) || [])[1] || 0);

const NAME = 'DcBot' + (Date.now() % 100000);

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('daycare module is running', /✔ daycare/.test(mods), mods.slice(-120));

  fs.writeFileSync(FILE, original.replace(/"base_price": [\d.]+/, '"base_price": 0.0').replace(/"minutes_to_level_100": \d+/, '"minutes_to_level_100": 1'));
  t.rcon('sb reload');
  await t.sleep(600);

  const bot = await t.join(NAME);
  t.rcon(`gamemode survival ${NAME}`);
  await t.sleep(300);
  t.rcon(`givepokemonother ${NAME} bulbasaur level=5`, `givepokemonother ${NAME} charmander level=5`);
  await t.sleep(1200);

  let w = await t.openByCommand(bot, '/daycare');
  t.check('title shows 0/1 slots used', /0\/1/.test(t.titleOf(w)) && /Daycare/.test(t.titleOf(w)), t.titleOf(w));
  t.check('slot 10 is free', /Free/.test(text(w, 10)), text(w, 10));
  t.check('slot 11 is locked (base capacity 1)', /Locked/.test(text(w, 11)), text(w, 11));

  // leave the Pokémon in the first party slot
  const picking = t.waitWindow(bot);
  await bot.clickWindow(10, 0, 0);
  const picker = await picking;
  await t.sleep(900);
  t.check('the picker opens', /choose a Pok/i.test(t.titleOf(picker)), t.titleOf(picker));
  const back = t.waitWindow(bot);
  await bot.clickWindow(10, 0, 0);
  w = await back;
  await t.sleep(1500);
  t.check('back in the daycare, the title shows 1/1', /1\/1/.test(t.titleOf(w)), t.titleOf(w));
  t.check('the slot no longer offers a deposit', !/Free/.test(text(w, 10)));

  const first = t.rcon(`daycare admin list ${NAME}`).replace(/\n/g, ' ');
  t.check('admin list shows the deposit just above level 5', levelOf(first) >= 5 && levelOf(first) < 15, first.slice(0, 200));

  console.log('  (waiting 14 s: one level every 0.6 s)');
  await t.sleep(14000);
  const later = t.rcon(`daycare admin list ${NAME}`).replace(/\n/g, ' ');
  t.check('the Pokémon gained levels while waiting', levelOf(later) >= 15, later.slice(0, 200));

  bot.chatLog.length = 0;
  await t.click(bot, 10);
  await t.sleep(1500);
  const msg = bot.chatLog.join(' | ');
  const m = msg.match(/level 5 → (\d+)/);
  t.check('taking it back reports the new level', !!m && Number(m[1]) >= 15, msg.slice(0, 200));
  w = bot.currentWindow;
  t.check('the slot is free again', /Free/.test(text(w, 10)), text(w, 10));
  bot.closeWindow(w);

  const admin = t.rcon(`daycare admin list ${NAME}`).replace(/\n/g, ' ');
  t.check('admin list shows nothing left', /nothing in the daycare/.test(admin), admin.slice(0, 120));

  fs.writeFileSync(FILE, original);
  t.rcon('sb reload');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { fs.writeFileSync(FILE, original); console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
