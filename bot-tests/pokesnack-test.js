// Poké Snack buffs: PokeSnack/config.json rewrites Cobblemon's bait/seasoning effects (needs the dev server WITH Cobblemon).
// Only RCON is used: /pokesnack buffs <item> prints the effects an ingredient gives right now.
const fs = require('fs');
const t = require('./lib.js');

const FILE = 'D:/mods/ServerBuddy/run/config/ServerBuddy/PokeSnack/config.json';
const original = fs.readFileSync(FILE, 'utf8');

const effectsOf = (item) => {
  const out = t.rcon(`pokesnack buffs ${item}`);
  const list = [];
  for (const m of out.matchAll(/(cobblemon:[a-z_]+)(@\S+)? chance ([\d.]+)%, value (-?[\d.]+)/g)) {
    list.push({ type: m[1], sub: m[2] || '', chance: Number(m[3]), value: Number(m[4]) });
  }
  return { list, out };
};
const find = (item, type) => effectsOf(item).list.find((e) => e.type === type);
const configure = (mutate) => {
  const cfg = t.parseJson(original);
  mutate(cfg);
  fs.writeFileSync(FILE, JSON.stringify(cfg, null, 2));
  t.rcon('sb reload');
};

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('pokesnack module is running', /✔ pokesnack/.test(mods), mods.slice(-200));

  const baseShiny = find('cobblemon:starf_berry', 'cobblemon:shiny_reroll');
  const baseNature = find('cobblemon:aguav_berry', 'cobblemon:nature');
  const baseIv = find('cobblemon:apicot_berry', 'cobblemon:iv');
  t.check('baseline: starf berry gives shiny rerolls', !!baseShiny && baseShiny.value > 0, JSON.stringify(baseShiny));
  t.check('baseline: aguav berry gives a nature chance', !!baseNature && baseNature.chance > 0, JSON.stringify(baseNature));
  t.check('baseline: apicot berry gives IVs', !!baseIv, JSON.stringify(baseIv));
  const baseApple = effectsOf('minecraft:golden_apple').list;

  console.log('== effect rules');
  configure((c) => {
    c.buffs.effects['cobblemon:shiny_reroll'].value = 50;
    c.buffs.effects['cobblemon:iv'].enabled = false;
    c.buffs.effects['cobblemon:nature'].chance_multiplier = 0.5;
  });
  await t.sleep(500);
  const shiny = find('cobblemon:starf_berry', 'cobblemon:shiny_reroll');
  t.check('forced value: shiny_reroll value is 50', shiny && shiny.value === 50, JSON.stringify(shiny));
  t.check('forced value keeps the chance', shiny && shiny.chance === baseShiny.chance, JSON.stringify(shiny));
  t.check('disabled effect: apicot berry loses its IV buff', !find('cobblemon:apicot_berry', 'cobblemon:iv'));
  const nature = find('cobblemon:aguav_berry', 'cobblemon:nature');
  t.check('chance_multiplier 0.5 halves the nature chance', nature && Math.abs(nature.chance - baseNature.chance / 2) < 0.01, JSON.stringify(nature) + ' vs ' + JSON.stringify(baseNature));
  const summary = t.rcon('pokesnack buffs').replace(/\n/g, ' ');
  t.check('/pokesnack buffs reports the adjusted entries', /adjusted/.test(summary) && !/ 0 bait entries and 0 seasonings/.test(summary), summary.slice(0, 160));

  console.log('== global multipliers and ingredient rules');
  configure((c) => {
    c.buffs.value_multiplier = 2;
    c.buffs.chance_multiplier = 0.5;
    c.buffs.ingredients['minecraft:golden_apple'] = { mode: 'replace', effects: [{ type: 'cobblemon:ha_chance', chance: 0.3, value: 1 }] };
  });
  await t.sleep(500);
  const shiny2 = find('cobblemon:starf_berry', 'cobblemon:shiny_reroll');
  t.check('global value x2 and chance x0.5 apply together', shiny2 && shiny2.value === baseShiny.value * 2 && Math.abs(shiny2.chance - baseShiny.chance / 2) < 0.01, JSON.stringify(shiny2));
  const apple = effectsOf('minecraft:golden_apple').list;
  t.check('mode "replace": golden apple gives exactly the listed effect', apple.length === 1 && apple[0].type === 'cobblemon:ha_chance', JSON.stringify(apple));

  console.log('== survives a datapack /reload');
  t.rcon('reload');
  await t.sleep(6000);
  const apple2 = effectsOf('minecraft:golden_apple').list;
  t.check('after /reload the rules are applied again (and not twice)', apple2.length === 1 && apple2[0].type === 'cobblemon:ha_chance' && find('cobblemon:starf_berry', 'cobblemon:shiny_reroll').value === baseShiny.value * 2,
    JSON.stringify(apple2));

  console.log('== back to the original');
  fs.writeFileSync(FILE, original);
  t.rcon('sb reload');
  await t.sleep(500);
  const shift = find('cobblemon:starf_berry', 'cobblemon:shiny_reroll');
  t.check('original config: shiny value is back', shift && shift.value === baseShiny.value && shift.chance === baseShiny.chance, JSON.stringify(shift));
  t.check('original config: iv effect is back', !!find('cobblemon:apicot_berry', 'cobblemon:iv'));
  t.check('original config: golden apple is back', JSON.stringify(effectsOf('minecraft:golden_apple').list) === JSON.stringify(baseApple));

  process.exit(process.exitCode || 0);
})().catch((e) => { fs.writeFileSync(FILE, original); console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
