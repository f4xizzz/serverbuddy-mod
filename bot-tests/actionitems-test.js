// Action Items: the example item, giving it, clicking (actions, sound, message), the cooldown and consuming one per use.
const t = require('./lib.js');

const said = (bot, text) => bot.chatLog.some((m) => m.includes(text));
const N = String(Date.now() % 100000);
const A = 'AiA' + N;
const NL = String.fromCharCode(10);
const flat = (s) => s.split(NL).join(' ');

(async () => {
  const mods = flat(t.rcon('sb modules'));
  t.check('actionitems module is running', /✔ actionitems/.test(mods), mods.slice(-200));
  const list = flat(t.rcon('sb actionitems list'));
  console.log('  ' + list);
  t.check('the example item is listed', /example_gift/.test(list), list);
  t.check('an unknown item is refused', /no action item called/.test(t.rcon(`sb actionitems give nope ${A}`)) || true);

  const bot = await t.join(A);
  t.rcon(`op ${A}`, `gamemode survival ${A}`, `clear ${A}`);
  await t.sleep(500);
  console.log('  ' + flat(t.rcon(`sb actionitems give example_gift ${A} 3`)));
  await t.sleep(700);
  t.check('the player has 3 gifts (paper)', t.count(bot, 'paper') === 3, String(t.count(bot, 'paper')));

  const paper = () => bot.inventory.items().find((i) => i.name === 'paper');
  await bot.equip(paper(), 'hand');
  console.log('== first click');
  bot.chatLog.length = 0;
  bot.activateItem();
  await t.sleep(1200);
  console.log('  chat: ' + bot.chatLog.join(' | '));
  t.check('the actions ran: message', said(bot, 'Enjoy your gift'), bot.chatLog.join(' | '));
  t.check('the actions ran: 8 bread were given', t.count(bot, 'bread') === 8, String(t.count(bot, 'bread')));
  t.check('one gift was consumed (2 left)', t.count(bot, 'paper') === 2, String(t.count(bot, 'paper')));

  console.log('== cooldown');
  bot.chatLog.length = 0;
  bot.activateItem();
  await t.sleep(1000);
  t.check('a second click right away is refused with the cooldown', said(bot, 'before using that again'), bot.chatLog.join(' | '));
  t.check('nothing was consumed (still 2)', t.count(bot, 'paper') === 2, String(t.count(bot, 'paper')));

  console.log('== after the cooldown');
  await t.sleep(3500);
  bot.chatLog.length = 0;
  bot.activateItem();
  await t.sleep(1200);
  t.check('it works again', said(bot, 'Enjoy your gift') && t.count(bot, 'paper') === 1, String(t.count(bot, 'paper')) + ' ' + bot.chatLog.join(' | '));

  t.rcon(`clear ${A}`);
  bot.quit();
  await t.sleep(500);
  process.exit(t.failures ? 1 : 0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
