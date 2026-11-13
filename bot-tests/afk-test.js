// AFK tag: /afk adds the entity tag, announces it, and going back removes the tag; the tag is cleaned when the player leaves.
const t = require('./lib.js');

const said = (bot, text) => bot.chatLog.some((m) => m.includes(text));
const N = String(Date.now() % 100000);
const A = 'AfA' + N;
const B = 'AfB' + N;
const NL = String.fromCharCode(10);
const flat = (s) => s.split(NL).join(' ');

(async () => {
  const bot = await t.join(A);
  const watcher = await t.join(B);
  t.rcon(`op ${A}`, `gamemode survival ${A}`);
  await t.sleep(600);
  t.check('not AFK yet: no afk tag', !/afk/.test(flat(t.rcon(`tag ${A} list`))), flat(t.rcon(`tag ${A} list`)));

  console.log('== /afk on');
  watcher.chatLog.length = 0;
  bot.chat('/afk');
  await t.sleep(1200);
  const tags = flat(t.rcon(`tag ${A} list`));
  console.log('  tags: ' + tags);
  t.check('the player carries the afk entity tag', /afk/.test(tags), tags);
  t.check('a selector finds them: @a[tag=afk]', /passed|Test passed/i.test(flat(t.rcon(`execute if entity @a[tag=afk,name=${A}]`))), flat(t.rcon(`execute if entity @a[tag=afk,name=${A}]`)));
  t.check('everyone is told that the player is AFK', /AFK/.test(watcher.chatLog.join(' ')), watcher.chatLog.join(' | '));

  console.log('== /afk off');
  watcher.chatLog.length = 0;
  bot.chat('/afk');
  await t.sleep(1200);
  t.check('the tag is gone', !/afk/.test(flat(t.rcon(`tag ${A} list`))), flat(t.rcon(`tag ${A} list`)));
  t.check('everyone is told that the player is back', /voltou|is back/.test(watcher.chatLog.join(' ')), watcher.chatLog.join(' | '));

  console.log('== leaving while AFK');
  bot.chat('/afk');
  await t.sleep(900);
  t.check('AFK again: tag on', /afk/.test(flat(t.rcon(`tag ${A} list`))));
  bot.quit();
  await t.sleep(1500);
  const again = await t.join(A);
  await t.sleep(800);
  t.check('the tag did not stay with the player after they left', !/afk/.test(flat(t.rcon(`tag ${A} list`))), flat(t.rcon(`tag ${A} list`)));

  again.quit();
  watcher.quit();
  await t.sleep(500);
  process.exit(t.failures ? 1 : 0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
