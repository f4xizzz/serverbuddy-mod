// /skull copy: copies the custom head the player is looking at (a placed head, or the head an armor stand wears).
const t = require('./lib.js');
const Vec3 = t.lib('vec3').Vec3;

(async () => {
  t.rcon('forceload add 1010 1010', 'fill 1008 99 1008 1014 99 1016 minecraft:stone', 'fill 1008 100 1008 1014 104 1016 minecraft:air', 'kill @e[type=minecraft:armor_stand]');
  t.rcon('setblock 1010 100 1010 minecraft:player_head[rotation=0]{profile:{name:"Dinnerbone"}}');
  t.rcon('summon minecraft:armor_stand 1013.5 100 1010.5 {ArmorItems:[{},{},{},{id:"minecraft:player_head",count:1,components:{"minecraft:profile":{name:"Notch"}}}]}');
  const bot = await t.join('SbBot');
  t.rcon('gamemode survival SbBot', 'clear SbBot', 'tp SbBot 1011.5 100 1013.5');
  await t.sleep(2500);

  bot.chatLog.length = 0;
  bot.chat('/skull copy');
  await t.sleep(900);
  t.check('looking at nothing: tells the player', /look|head/i.test(bot.chatLog.join(' ')) && t.count(bot, 'player_head') === 0, bot.chatLog.join(' | ').slice(0, 140));

  await bot.lookAt(new Vec3(1010.5, 100.4, 1010.5), true);
  await t.sleep(400);
  bot.chatLog.length = 0;
  bot.chat('/skull copy');
  await t.sleep(900);
  t.check('placed head: one head lands in the inventory', t.count(bot, 'player_head') === 1, bot.chatLog.join(' | ').slice(0, 140));
  const data = t.rcon('data get entity SbBot Inventory');
  t.check('the copy keeps the profile (Dinnerbone)', /Dinnerbone/.test(data), data.slice(0, 200));

  t.rcon('clear SbBot');
  await bot.lookAt(new Vec3(1013.5, 101.4, 1010.5), true);
  await t.sleep(400);
  bot.chatLog.length = 0;
  bot.chat('/skull copy');
  await t.sleep(900);
  t.check('armor stand: copies the worn head', t.count(bot, 'player_head') === 1, bot.chatLog.join(' | ').slice(0, 140));
  t.check('the copy keeps the profile (Notch)', /Notch/.test(t.rcon('data get entity SbBot Inventory')));

  t.rcon('clear SbBot', 'kill @e[type=minecraft:armor_stand]', 'fill 1008 99 1008 1014 104 1016 minecraft:air', 'forceload remove 1010 1010');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
