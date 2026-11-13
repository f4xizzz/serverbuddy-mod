// Dimensions (Pro): create/tp/setspawn/list a void world (phase 1), then after a server restart check it was reopened and delete it (phase 2).
// usage: node worlds-test.js 1   ...restart the server...   node worlds-test.js 2
const t = require('./lib.js');

const phase = process.argv[2] || '1';
const NAME = 'wtest';
const N = String(Date.now() % 100000);
const A = 'WdA' + N;
const NL = String.fromCharCode(10);
const flat = (s) => s.split(NL).join(' ');
const dimension = (player) => flat(t.rcon(`data get entity ${player} Dimension`));

(async () => {
  const mods = flat(t.rcon('sb modules'));
  t.check('worlds module is running', /✔ worlds/.test(mods), mods.slice(-200));
  const bot = await t.join(A);
  t.rcon(`op ${A}`, `gamemode survival ${A}`);
  await t.sleep(600);

  if (phase === '1') {
    console.log('== create');
    t.check('bad names are refused', /1 to 16/.test(t.rcon('sb world create Bad-Name')));
    t.check('unknown types are refused', /Unknown type/.test(t.rcon(`sb world create ${NAME} lava`)));
    const created = flat(t.rcon(`sb world create ${NAME} void 12345`));
    console.log('  ' + created);
    t.check('the void world was created', /created/.test(created), created);
    t.check('the same name again is refused', /already a world/.test(t.rcon(`sb world create ${NAME}`)));
    const list = flat(t.rcon('sb world list'));
    console.log('  ' + list);
    t.check('list shows it with its type and seed', new RegExp(NAME + '.*void.*12345').test(list), list);

    console.log('== tp and spawn');
    console.log('  ' + flat(t.rcon(`sb world tp ${NAME} ${A}`)));
    await t.sleep(1500);
    const dim = dimension(A);
    console.log('  dimension: ' + dim);
    t.check('the player is in serverbuddy:' + NAME, dim.includes('serverbuddy:' + NAME), dim);
    const pos = flat(t.rcon(`data get entity ${A} Pos`));
    console.log('  position: ' + pos);
    t.check('they arrived on the platform (0.5, 65, 0.5)', /\[0\.5d, 65\.0d, 0\.5d\]/.test(pos), pos);
    t.check('the platform is stone', /passed/i.test(flat(t.rcon(`execute in serverbuddy:${NAME} if block 0 64 0 minecraft:stone`))));
    t.rcon(`execute in serverbuddy:${NAME} run tp ${A} 1.5 65 1.5`);
    await t.sleep(600);
    bot.chat('/sb world setspawn');
    await t.sleep(900);
    t.rcon(`execute in minecraft:overworld run tp ${A} 0 100 0`);
    await t.sleep(900);
    console.log('  ' + flat(t.rcon(`sb world tp ${NAME} ${A}`)));
    await t.sleep(1500);
    t.check('after setspawn, tp lands on the new spawn (1.5, 65, 1.5)', /\[1\.5d, 65\.0d, 1\.5d\]/.test(flat(t.rcon(`data get entity ${A} Pos`))), flat(t.rcon(`data get entity ${A} Pos`)));
    bot.chat('/sb world spawn');
    await t.sleep(900);
    t.check('/sb world spawn keeps the player in the world', dimension(A).includes('serverbuddy:' + NAME), dimension(A));
    t.rcon(`execute in minecraft:overworld run tp ${A} 0 100 0`);
    console.log('phase 1 done: restart the server and run phase 2');
  } else {
    console.log('== after the restart');
    const list = flat(t.rcon('sb world list'));
    console.log('  ' + list);
    t.check('the world is still listed', new RegExp(NAME).test(list), list);
    t.check('and it is open (a world with 0 player(s), not closed)', /player\(s\)/.test(list) && !/closed/.test(list), list);
    console.log('  ' + flat(t.rcon(`sb world tp ${NAME} ${A}`)));
    await t.sleep(1500);
    t.check('tp still works', dimension(A).includes('serverbuddy:' + NAME), dimension(A));
    t.check('the platform and spawn survived (1.5, 65, 1.5)', /\[1\.5d, 65\.0d, 1\.5d\]/.test(flat(t.rcon(`data get entity ${A} Pos`))), flat(t.rcon(`data get entity ${A} Pos`)));

    console.log('== delete with a player inside');
    const gone = flat(t.rcon(`sb world delete ${NAME}`));
    console.log('  ' + gone);
    await t.sleep(1500);
    t.check('the world was deleted', /deleted/.test(gone), gone);
    t.check('the player was moved out to the overworld', dimension(A).includes('overworld'), dimension(A));
    t.check('the list is empty again', /No extra worlds/.test(t.rcon('sb world list')), t.rcon('sb world list'));
    t.check('the dimension is gone', !/serverbuddy:wtest/.test(flat(t.rcon('execute in serverbuddy:wtest run say hi'))) || /Unknown|dimension/i.test(flat(t.rcon('execute in serverbuddy:wtest run say hi'))), flat(t.rcon('execute in serverbuddy:wtest run say hi')));
  }
  bot.quit();
  await t.sleep(500);
  process.exit(t.failures ? 1 : 0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
