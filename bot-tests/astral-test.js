// Astral Trader: tokens, rotation, the offers, a trade with confirmation, sold-out, not-enough-tokens and the admin commands.
// (The test client cannot read Pokémon items, so offers are checked through "astral admin offers" and the rest through chat.)
const t = require('./lib.js');

const said = (bot, text) => bot.chatLog.some((m) => m.includes(text));
const N = String(Date.now() % 100000);
const A = 'AsA' + N;
const NL = String.fromCharCode(10);

(async () => {
  const mods = t.rcon('sb modules').split(NL).join(' ');
  t.check('astral module is running', /✔ astral/.test(mods), mods.slice(-160));

  const bot = await t.join(A);
  t.rcon(`op ${A}`, `gamemode survival ${A}`);
  await t.sleep(500);

  console.log('== rotation and tokens');
  const rotated = t.rcon('astral admin rotate').split(NL).join(' ');
  console.log('  ' + rotated);
  t.check('all four pools got offers', /legendary/.test(rotated) && /paradox/.test(rotated) && /ultra_beast/.test(rotated) && /shiny/.test(rotated), rotated);
  t.check('an unknown pool is refused', /Unknown pool/.test(t.rcon('astral admin rotate nope')));
  console.log('  ' + t.rcon(`astral admin give ${A} special 7`).split(NL).join(' '));
  console.log('  ' + t.rcon(`astral admin give ${A} shiny 3`).split(NL).join(' '));
  t.check('an unknown token is refused', /Unknown token/.test(t.rcon(`astral admin give ${A} gold 1`)));
  const balance = t.rcon(`astral balance ${A}`).split(NL).join(' ');
  t.check('balance shows 7 special and 3 shiny', /7/.test(balance) && /3/.test(balance) && /special/.test(balance), balance);

  console.log('== the offers and the menu');
  const offers = t.rcon('astral admin offers');
  console.log('  ' + offers.split(NL).join(' // '));
  const whole = offers.split(NL).join(' ');
  const order = ['legendary', 'paradox', 'ultra_beast', 'shiny'];
  const count = (pool) => {
    const from = whole.indexOf(pool + ' (');
    const next = order[order.indexOf(pool) + 1];
    const to = next ? whole.indexOf(next + ' (') : whole.length;
    return (whole.slice(from, to).match(/\d+: [a-z]+:[a-z_]+ lv/g) || []).length;
  };
  t.check('4 legendary, 3 paradox, 3 ultra beast and 4 shiny offers', count('legendary') === 4 && count('paradox') === 3 && count('ultra_beast') === 3 && count('shiny') === 4, count('legendary') + '/' + count('paradox') + '/' + count('ultra_beast') + '/' + count('shiny'));
  t.check('legendary costs 3 special tokens and shiny costs 1 shiny token', /legendary \(3 special tokens/.test(offers) && /shiny \(1 shiny token,/.test(offers), offers.slice(0, 300));
  t.check('the shiny offers are shiny and the legendary ones are not', /shiny [^\n]*lv30 shiny/.test(offers) && !/legendary [^\n]*lv70 shiny/.test(offers));
  let w = await t.openByCommand(bot, '/astral');
  console.log('  title: ' + t.titleOf(w));
  t.check('the menu opens with the Astral Trader title', /Astral Trader/.test(t.titleOf(w)));

  console.log('== a trade (legendary, 3 special tokens)');
  bot.chatLog.length = 0;
  const confirming = t.waitWindow(bot);
  await bot.clickWindow(10, 0, 0);
  const confirm = await confirming;
  await t.sleep(700);
  console.log('  confirm title: ' + t.titleOf(confirm));
  t.check('the confirm menu opened', /Trade for/.test(t.titleOf(confirm)), t.titleOf(confirm));
  const reopened = t.waitWindow(bot);
  await bot.clickWindow(11, 0, 0);
  w = await reopened;
  await t.sleep(900);
  console.log('  chat: ' + bot.chatLog.join(' | '));
  t.check('the player got the trade message', said(bot, 'You traded'), bot.chatLog.join(' | '));
  const after = t.rcon(`astral balance ${A}`).split(NL).join(' ');
  t.check('3 special tokens were spent (4 left)', /has 4 special/.test(after), after);
  await bot.clickWindow(10, 0, 0);
  await t.sleep(1200);
  t.check('the same offer can not be bought twice (sold out for this player): still 4 special', /has 4 special/.test(t.rcon(`astral balance ${A}`)), t.rcon(`astral balance ${A}`));

  console.log('== not enough tokens');
  t.rcon(`astral admin set ${A} special 1`);
  bot.chatLog.length = 0;
  const confirming2 = t.waitWindow(bot);
  await bot.clickWindow(11, 0, 0); // the second legendary offer, 3 special tokens
  await confirming2;
  await t.sleep(700);
  const back = t.waitWindow(bot);
  await bot.clickWindow(11, 0, 0);
  await back;
  await t.sleep(900);
  t.check('refused with the price and the balance', said(bot, 'You need'), bot.chatLog.join(' | '));
  t.check('the balance is untouched (1 special)', /has 1 special/.test(t.rcon(`astral balance ${A}`)));

  console.log('== a shiny trade (1 shiny token)');
  bot.chatLog.length = 0;
  const confirming3 = t.waitWindow(bot);
  await bot.clickWindow(37, 0, 0);
  await confirming3;
  await t.sleep(700);
  const shinyBack = t.waitWindow(bot);
  await bot.clickWindow(11, 0, 0);
  await shinyBack;
  await t.sleep(900);
  console.log('  chat: ' + bot.chatLog.join(' | '));
  t.check('the shiny trade went through, with the singular "shiny token"', said(bot, 'You traded') && /1 shiny token for/.test(bot.chatLog.join(' ')), bot.chatLog.join(' | '));
  t.check('1 shiny token spent (2 left)', /2 shiny/.test(t.rcon(`astral balance ${A}`)), t.rcon(`astral balance ${A}`));

  console.log('== take and set');
  t.check('take never goes below zero', /now has 0 shiny/.test(t.rcon(`astral admin take ${A} shiny 5`)), '');
  t.check('set writes the exact amount', /now has 12 special/.test(t.rcon(`astral admin set ${A} special 12`)), '');

  bot.quit();
  await t.sleep(500);
  t.rcon('astral admin rotate');
  process.exit(t.failures ? 1 : 0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
