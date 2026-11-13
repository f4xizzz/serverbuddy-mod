// Chunk limits (redstone parts per chunk) and craft block (recipes removed by config/tag) on the local dev server.
// Works with or without Cobblemon. Needs ops.json: SbBot at level 2 (no bypass) and CbAdm at level 3 (bypass).
const fs = require('fs');
const t = require('./lib.js');
const Vec3 = t.lib('vec3').Vec3;

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy';
const edit = (file, from, to) => {
  const path = `${CFG}/${file}`;
  const text = fs.readFileSync(path, 'utf8');
  if (!from.test(text)) throw new Error(`pattern ${from} not found in ${file}`);
  fs.writeFileSync(path, text.replace(from, to));
};
const passed = (cmd) => /Test passed/.test(t.rcon(cmd));

async function placeRepeaters(bot, count) {
  const repeater = bot.inventory.items().find((i) => i.name === 'repeater');
  await bot.equip(repeater, 'hand');
  const results = [];
  for (let i = 0; i < count; i++) {
    const ref = bot.blockAt(new Vec3(1000 + i, 99, 1000));
    try {
      await Promise.race([bot.placeBlock(ref, new Vec3(0, 1, 0)), t.sleep(2500)]);
    } catch (e) { /* a refused placement times out waiting for the block update */ }
    await t.sleep(500);
    results.push(passed(`execute if block ${1000 + i} 100 1000 minecraft:repeater`));
  }
  return results;
}

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('chunklimits module is running', /✔ chunklimits/.test(mods), mods.slice(0, 160));
  t.check('craftblock module is running', /✔ craftblock/.test(mods));

  console.log('== chunk limits');
  edit('ChunkLimits/config.json', /"max_per_chunk": 256/, '"max_per_chunk": 3');
  t.rcon('sb reload');
  await t.sleep(800);
  t.rcon('forceload add 1000 1000', 'fill 1000 99 1000 1007 99 1003 minecraft:stone', 'fill 1000 100 1000 1007 104 1003 minecraft:air');

  let bot = await t.join('SbBot');
  t.rcon('gamemode survival SbBot', 'clear SbBot', 'give SbBot minecraft:repeater 10', 'tp SbBot 1004 100 1002');
  await t.sleep(2500);
  bot.chatLog.length = 0;
  let placed = await placeRepeaters(bot, 4);
  t.check('the first 3 repeaters are placed', placed[0] && placed[1] && placed[2], JSON.stringify(placed));
  t.check('the 4th is refused (limit 3 per chunk)', placed[3] === false, JSON.stringify(placed));
  t.check('the player is told in the action bar', bot.chatLog.some((m) => /already has 3\/3/.test(m)), bot.chatLog.join(' | ').slice(0, 160));
  const info = t.rcon('execute as SbBot run chunklimits').replace(/\n/g, ' ');
  t.check('/chunklimits shows redstone 3/3', /redstone: 3\/3/.test(info), info.slice(0, 200));
  bot.quit();
  await t.sleep(1200);

  bot = await t.join('CbAdm');
  t.rcon('gamemode survival CbAdm', 'clear CbAdm', 'give CbAdm minecraft:repeater 10', 'tp CbAdm 1004 100 1002');
  await t.sleep(2500);
  const ref = bot.blockAt(new Vec3(1003, 99, 1000));
  const repeater = bot.inventory.items().find((i) => i.name === 'repeater');
  await bot.equip(repeater, 'hand');
  try { await Promise.race([bot.placeBlock(ref, new Vec3(0, 1, 0)), t.sleep(2500)]); } catch (e) { /* ignore */ }
  await t.sleep(600);
  t.check('a level 3 operator bypasses the limit', passed('execute if block 1003 100 1000 minecraft:repeater'));
  bot.quit();
  await t.sleep(800);
  t.rcon('fill 1000 99 1000 1007 104 1003 minecraft:air', 'forceload remove 1000 1000');
  edit('ChunkLimits/config.json', /"max_per_chunk": 3/, '"max_per_chunk": 256');

  console.log('== craft block');
  bot = await t.join('SbBot');
  const before = t.rcon('recipe give SbBot minecraft:tnt');
  t.check('tnt is craftable before blocking', !/Unknown recipe/.test(before), before.slice(0, 120));
  edit('CraftBlock/config.json', /"blocked": \[\]/, '"blocked": ["minecraft:tnt", "#minecraft:beds", "nomod:*", "recipe:minecraft:ender_chest"]');
  t.rcon('sb reload');
  await t.sleep(1500);
  let after = t.rcon('recipe give SbBot minecraft:tnt');
  t.check('tnt recipe is gone after /serverbuddy reload', /Unknown recipe/.test(after), after.slice(0, 120));
  after = t.rcon('recipe give SbBot minecraft:ender_chest');
  t.check('recipe:id rule removes ender_chest', /Unknown recipe/.test(after), after.slice(0, 120));
  after = t.rcon('recipe give SbBot minecraft:white_bed');
  t.check('#tag rule removes the beds', /Unknown recipe/.test(after), after.slice(0, 120));
  after = t.rcon('recipe give SbBot minecraft:stick');
  t.check('unrelated recipes stay', !/Unknown recipe/.test(after), after.slice(0, 120));
  const list = t.rcon('execute as SbBot run craftblock list').replace(/\n/g, ' ');
  t.check('/craftblock list names the rules', /minecraft:tnt/.test(list) && /minecraft:beds/.test(list), list.slice(0, 240));
  const check = t.rcon('execute as SbBot run craftblock check').replace(/\n/g, ' ');
  t.check('/craftblock check flags the rule that matches nothing', /nomod:\*/.test(check), check.slice(0, 240));
  t.rcon('reload');
  await t.sleep(5000);
  after = t.rcon('recipe give SbBot minecraft:tnt');
  t.check('the block survives a datapack /reload', /Unknown recipe/.test(after), after.slice(0, 120));

  edit('CraftBlock/config.json', /"blocked": \[[^\]]*\]/, '"blocked": []');
  t.rcon('sb reload');
  await t.sleep(1500);
  after = t.rcon('recipe give SbBot minecraft:tnt');
  t.check('removing the rule brings the recipe back', !/Unknown recipe/.test(after), after.slice(0, 120));

  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
