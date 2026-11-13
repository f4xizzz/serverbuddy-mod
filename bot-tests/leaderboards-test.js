// Leaderboards: the playtime and Pokédex boards, the chat list, the head menu (and, with Impactor installed, one board per currency).
const t = require('./lib.js');

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('leaderboards module is running', /✔ leaderboards/.test(mods), mods.slice(-160));

  const list = t.rcon('leaderboard').replace(/\n/g, ' ');
  t.check('/leaderboard lists the playtime board', /playtime/.test(list), list.slice(0, 200));
  const withCobblemon = /pokedex/.test(list);
  console.log(`  (Cobblemon boards ${withCobblemon ? 'present' : 'absent'})`);

  t.rcon('leaderboard refresh');
  await t.sleep(3000);
  const top = t.rcon('leaderboard playtime').replace(/\n/g, ' ');
  t.check('playtime board shows ranked players with a duration', /#1 \S+ » \d+[smhd]/.test(top), top.slice(0, 200));
  t.check('unknown board is reported', /no board called/.test(t.rcon('leaderboard nothing')));

  const bot = await t.join('SbBot');
  t.rcon('gamemode survival SbBot');
  if (withCobblemon) {
    t.rcon('pokedex revoke SbBot all', 'pokedex grant SbBot all', 'leaderboard refresh');
    await t.sleep(3500);
    const dex = t.rcon('leaderboard pokedex').replace(/\n/g, ' ');
    t.check('pokedex board lists SbBot after the Pokédex was granted', /SbBot » \d+ caught/.test(dex), dex.slice(0, 220));
    const pct = t.rcon('leaderboard pokedex_percent').replace(/\n/g, ' ');
    t.check('pokedex_percent board shows the completion', /SbBot » [\d.]+%/.test(pct), pct.slice(0, 220));
    t.rcon('pokedex revoke SbBot all');
  }

  bot.chatLog.length = 0;
  bot.chat('/leaderboard playtime');
  await t.sleep(900);
  t.check('a player sees the list in chat too', bot.chatLog.some((m) => /#1 /.test(m)), bot.chatLog.join(' | ').slice(0, 160));

  const w = await t.openByCommand(bot, '/leaderboard gui playtime');
  t.check('the menu opens with the board title', /Leaderboards/.test(t.titleOf(w)) && /Most time played/.test(t.titleOf(w)), t.titleOf(w));
  const heads = t.slots(w).filter((s) => /player_head/.test(s));
  t.check('the menu shows player heads', heads.length >= 3, heads.join(' | ').slice(0, 200));
  t.check('the first head is #1', /#1/.test(t.itemText(w.slots[4])), t.itemText(w.slots[4]));
  bot.closeWindow(w);

  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
