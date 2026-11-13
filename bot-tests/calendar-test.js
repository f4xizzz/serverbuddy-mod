// Calendar module: monthly calendar, weekly calendar and login streak (menus, claiming, claim modes, streak rollover).
// The calendar clock is moved with "/calendar admin clock" so the test does not depend on the real date.
const fs = require('fs');
const t = require('./lib.js');

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Calendar';
const edit = (file, from, to) => {
  const text = fs.readFileSync(`${CFG}/${file}`, 'utf8');
  if (!from.test(text)) throw new Error(`pattern ${from} not found in ${file}`);
  fs.writeFileSync(`${CFG}/${file}`, text.replace(from, to));
};
const item = (w, slot) => w.slots[slot];
const text = (w, slot) => { const it = item(w, slot); return it ? `${it.name} ${t.itemText(it)}` : '(empty)'; };

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('calendar module is running', /✔ calendar/.test(mods), mods.slice(-160));
  for (const f of ['config.json', 'monthly.json', 'weekly.json', 'streak.json']) t.check(`Calendar/${f} was created`, fs.existsSync(`${CFG}/${f}`));

  t.rcon('calendar admin clock 2026-03-15', 'calendar admin reset SbBot all');
  let bot = await t.join('SbBot');
  t.rcon('gamemode survival SbBot', 'clear SbBot', 'xp set SbBot 0 levels');
  await t.sleep(600);

  console.log('== monthly calendar (claim_mode = today)');
  let w = await t.openByCommand(bot, '/calendar monthly');
  t.check('title shows the month', /March 2026/.test(t.titleOf(w)), t.titleOf(w));
  t.check('day 1 is missed (past, mode today)', /Missed/.test(text(w, 9)), text(w, 9));
  t.check('day 15 (today) is ready to claim', /Click to claim/.test(text(w, 23)) && item(w, 23).name === 'bread', text(w, 23));
  t.check('day 16 is locked', /Not available yet/.test(text(w, 24)), text(w, 24));
  t.check('the item count is the day number', item(w, 23).count === 15, String(item(w, 23).count));
  await t.click(bot, 23);
  t.check('claiming gave 2 bread (day 15)', t.count(bot, 'bread') === 2, String(t.count(bot, 'bread')));
  w = bot.currentWindow;
  t.check('day 15 now shows as claimed', item(w, 23) && item(w, 23).name === 'lime_stained_glass_pane', text(w, 23));
  await t.click(bot, 23);
  t.check('claiming twice gives nothing more', t.count(bot, 'bread') === 2, String(t.count(bot, 'bread')));
  await t.click(bot, 9);
  t.check('a missed day cannot be claimed', t.count(bot, 'bread') === 2);

  console.log('== claim_mode = catch_up');
  bot.closeWindow(bot.currentWindow);
  edit('monthly.json', /"claim_mode": "today"/, '"claim_mode": "catch_up"');
  t.rcon('sb reload');
  await t.sleep(600);
  w = await t.openByCommand(bot, '/calendar monthly');
  t.check('day 14 is claimable now (catch up)', /Click to claim/.test(text(w, 22)), text(w, 22));
  await t.click(bot, 22);
  t.check('day 14 gave 2 diamonds', t.count(bot, 'diamond') === 2, String(t.count(bot, 'diamond')));
  w = bot.currentWindow;
  t.check('the claim all button is shown (slot 51)', item(w, 51) && /Claim all/.test(text(w, 51)), text(w, 51));
  await t.click(bot, 51);
  const breads = t.count(bot, 'bread');
  t.check('claim all took the other past days (13 more days of bread/diamonds)', breads > 2, String(breads));
  w = bot.currentWindow;
  t.check('nothing is left to claim (no claim all button)', !item(w, 51) || !/Claim all/.test(text(w, 51)), text(w, 51));
  bot.closeWindow(bot.currentWindow);

  console.log('== weekly calendar');
  w = await t.openByCommand(bot, '/calendar weekly');
  t.check('weekly title shows the week', /Weekly calendar/.test(t.titleOf(w)), t.titleOf(w));
  t.check('Sunday (today, slot 16) is claimable', /Click to claim/.test(text(w, 16)) && item(w, 16).name === 'emerald', text(w, 16));
  t.check('Monday (slot 10) was missed', /Missed/.test(text(w, 10)), text(w, 10));
  await t.click(bot, 16);
  t.check('Sunday gave 5 emeralds', t.count(bot, 'emerald') === 5, String(t.count(bot, 'emerald')));
  bot.closeWindow(bot.currentWindow);

  console.log('== login streak');
  t.rcon('calendar admin streak SbBot 3', 'clear SbBot');
  w = await t.openByCommand(bot, '/streak');
  t.check('streak title shows the streak', /3 days/.test(t.titleOf(w)), t.titleOf(w));
  t.check('days 1-3 are claimable', [10, 11, 12].every((s) => /Click to claim/.test(text(w, s))), [10, 11, 12].map((s) => text(w, s)).join(' | ').slice(0, 200));
  t.check('day 4 is locked', /Not available yet/.test(text(w, 13)), text(w, 13));
  await t.click(bot, 24);
  t.check('claim all gave 3+4+5 bread', t.count(bot, 'bread') === 12, String(t.count(bot, 'bread')));
  bot.closeWindow(bot.currentWindow);
  bot.quit();
  await t.sleep(800);

  t.rcon('calendar admin clock 2026-03-16');
  bot = await t.join('SbBot');
  await t.sleep(1500);
  t.check('rejoining the next day grows the streak to 4', bot.chatLog.some((m) => /Login streak: 4 days/.test(m)), bot.chatLog.join(' | ').slice(0, 200));
  w = await t.openByCommand(bot, '/streak');
  t.check('streak day 4 is claimable, 1-3 stay claimed', /Click to claim/.test(text(w, 13)) && item(w, 10).name === 'lime_stained_glass_pane', text(w, 13) + ' / ' + text(w, 10));
  bot.closeWindow(bot.currentWindow);
  bot.quit();
  await t.sleep(800);

  t.rcon('calendar admin clock 2026-03-19');
  bot = await t.join('SbBot');
  await t.sleep(1500);
  t.check('missing days restarts the streak at 1', bot.chatLog.some((m) => /streak of 4 days started over/.test(m)), bot.chatLog.join(' | ').slice(0, 200));

  console.log('== back to the original');
  t.rcon('calendar admin clock reset', 'calendar admin reset SbBot all', 'clear SbBot');
  edit('monthly.json', /"claim_mode": "catch_up"/, '"claim_mode": "today"');
  t.rcon('sb reload');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
