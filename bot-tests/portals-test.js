// Portals: the Portal Block item, placing it, blocks that touch = one portal, the destination, instant teleport, no-destination message, removal.
const t = require('./lib.js');

const V = t.lib('vec3');
const Vec3 = V.Vec3 || V;
const said = (bot, text) => bot.chatLog.some((m) => m.includes(text));
const N = String(Date.now() % 100000);
const A = 'PoA' + N;
const NL = String.fromCharCode(10);
const flat = (s) => s.split(NL).join(' ');
const FLOOR = 150; // a stone platform at y=150, the portal blocks go at y=151

async function place(bot, x, z) {
  // stand next to the spot, look at the stone block and place the portal block on top of it
  t.rcon(`tp ${A} ${x + 0.5} ${FLOOR + 1} ${z - 1.5}`);
  await t.sleep(900);
  const reference = bot.blockAt(new Vec3(x, FLOOR, z));
  const item = bot.inventory.items().find((i) => i.name === 'purple_stained_glass_pane');
  if (!item) throw new Error('the bot has no Portal Block item');
  await bot.equip(item, 'hand');
  await bot.lookAt(new Vec3(x + 0.5, FLOOR + 1, z + 0.5));
  await bot.placeBlock(reference, new Vec3(0, 1, 0));
  await t.sleep(600);
  return { x, y: FLOOR + 1, z };
}

(async () => {
  const mods = flat(t.rcon('sb modules'));
  t.check('portals module is running', /✔ portals/.test(mods), mods.slice(-200));

  const bot = await t.join(A);
  t.rcon(`op ${A}`, `gamemode survival ${A}`);
  await t.sleep(600);
  t.rcon(`fill -3 ${FLOOR} -3 12 ${FLOOR} 12 stone`, `fill -3 ${FLOOR + 1} -3 12 ${FLOOR + 6} 12 air`);
  await t.sleep(500);

  console.log('== the item');
  bot.chat('/sb portal give');
  await t.sleep(2500);
  t.check('the player received the item', bot.inventory.items().some((i) => i.name === 'purple_stained_glass_pane'), bot.inventory.items().map((i) => i.name).join(','));
  bot.chat('/sb portal give ' + A + ' 3');
  await t.sleep(800);

  console.log('== placing');
  const first = await place(bot, 2, 0);
  const second = await place(bot, 3, 0);
  const third = await place(bot, 9, 9);
  console.log(`  placed at ${JSON.stringify(first)} ${JSON.stringify(second)} ${JSON.stringify(third)}`);
  const list = flat(t.rcon('sb portal list'));
  console.log('  ' + list);
  t.check('two portals: the first two blocks touch and merged', /\(2\)/.test(list) && /2 block/.test(list) && /1 block/.test(list), list);
  const found = flat(t.rcon(`execute if block ${first.x} ${first.y} ${first.z} minecraft:nether_portal`));
  t.check('a nether_portal block really is at the first position', /passed/i.test(found), found);
  t.check('and at the second one (adjacent)', /passed/i.test(flat(t.rcon(`execute if block ${second.x} ${second.y} ${second.z} minecraft:nether_portal`))));

  console.log('== the portal does not break without a frame');
  await t.sleep(2500);
  t.check('still there after a few seconds', /passed/i.test(flat(t.rcon(`execute if block ${first.x} ${first.y} ${first.z} minecraft:nether_portal`))));

  console.log('== destination');
  t.rcon(`tp ${A} ${first.x + 0.5} ${first.y} ${first.z - 1.5}`);
  await t.sleep(800);
  await bot.lookAt(new Vec3(first.x + 0.5, first.y + 0.5, first.z + 0.5));
  bot.chatLog.length = 0;
  bot.chat('/sb portal edit tp 100 120 100');
  await t.sleep(1200);
  console.log('  chat: ' + bot.chatLog.join(' | '));
  t.check('the destination was set for the portal', said(bot, 'now leads to'), bot.chatLog.join(' | '));
  t.check('list shows the destination (centered on the block: 100.5 120.0 100.5)', /100\.5 120\.0 100\.5/.test(flat(t.rcon('sb portal list'))), flat(t.rcon('sb portal list')));

  console.log('== instant teleport');
  t.rcon(`tp ${A} ${first.x + 0.5} ${first.y} ${first.z + 0.5}`);
  await t.sleep(1500);
  const pos = flat(t.rcon(`data get entity ${A} Pos`));
  console.log('  position: ' + pos);
  const m = pos.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  t.check('the player was teleported to 100 120 100 at once', !!m && Math.abs(Number(m[1]) - 100) < 3 && Math.abs(Number(m[3]) - 100) < 3 && Number(m[2]) > 100, pos);

  console.log('== a portal with no destination');
  t.rcon(`tp ${A} ${third.x + 0.5} ${third.y} ${third.z + 0.5}`);
  bot.chatLog.length = 0;
  await t.sleep(1500);
  t.check('the player is told the portal leads nowhere', said(bot, 'does not lead anywhere'), bot.chatLog.join(' | '));

  console.log('== persistence and removal');
  t.rcon('sb reload');
  await t.sleep(800);
  t.check('the portals survive a reload', /\(2\)/.test(flat(t.rcon('sb portal list'))), flat(t.rcon('sb portal list')));
  t.rcon(`tp ${A} ${third.x + 0.5} ${third.y} ${third.z - 1.5}`);
  await t.sleep(800);
  await bot.lookAt(new Vec3(third.x + 0.5, third.y + 0.5, third.z + 0.5));
  bot.chatLog.length = 0;
  bot.chat('/sb portal remove');
  await t.sleep(1200);
  t.check('the portal was removed', said(bot, 'was removed'), bot.chatLog.join(' | '));
  t.check('one portal left', /\(1\)/.test(flat(t.rcon('sb portal list'))), flat(t.rcon('sb portal list')));
  t.check('the removed block is air again', /passed/i.test(flat(t.rcon(`execute if block ${third.x} ${third.y} ${third.z} minecraft:air`))));

  // clean up: the remaining portal and the platform
  t.rcon(`tp ${A} 0.5 ${FLOOR + 1} 0.5`);
  await t.sleep(500);
  await bot.lookAt(new Vec3(first.x + 0.5, first.y + 0.5, first.z + 0.5));
  bot.chat('/sb portal remove');
  await t.sleep(800);
  t.rcon(`fill -3 ${FLOOR} -3 12 ${FLOOR + 6} 12 air`);
  bot.quit();
  await t.sleep(500);
  process.exit(t.failures ? 1 : 0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
