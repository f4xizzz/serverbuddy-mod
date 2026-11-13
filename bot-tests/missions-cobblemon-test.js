// Missões do Cobblemon (objetivos novos): "gained" (ganhar um Pokémon, com filtro de espécie). Os outros (level_up, held_item, fossil, berry,
// apricorn, candy, scan, pokefish) dependem de ações do jogo que o bot não faz (usar doce, reviver fóssil, colher frutinha, escanear com a
// Pokédex): ficam conferidos só pela leitura (unitários) e pelo código.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Missions';
const joined = (bot) => bot.chatLog.join('\n');
const flat = (s) => s.replace(/\n/g, ' | ');
async function say(bot, cmd, wait = 1200) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return joined(bot); }

(async () => {
  const mission = (name, objectives) => ({ name, description: ['teste'], icon: 'minecraft:paper', objectives, rewards: [{ type: 'message', text: '<green>premio-ok' }] });
  fs.writeFileSync(DIR + '/categories/cobb_test.json', JSON.stringify({
    enabled: true, name: '<aqua>CobbTeste', icon: 'minecraft:compass', description: ['teste'], period: 'none', pick: 0, requires_mod: 'cobblemon',
    missions: {
      ganhar: mission('Ganhar', ['gained any 1']),
      ganhar_raichu: mission('GanharRaichu', ['gained raichu 1']),
      nivel: mission('Subir', ['level_up any 1']),
      nivel_alto: mission('SubirAlto', ['level_up any@90 1']),
      segurar: mission('Segurar', ['held_item cobblemon:leftovers 1']),
    },
  }, null, 2), 'utf8');
  const r = t.rcon('missions reload');
  t.check('reload lê a categoria com os verbos novos', /category\/categories/.test(r), r);

  const a = await t.join('EsA');
  t.rcon('op EsA', 'gamemode survival EsA', 'clear EsA', 'tp EsA 2.5 89 -3.5');
  await t.sleep(1500);
  let out = await say(a, '/missions list', 1200);
  t.check('os objetivos novos aparecem com o texto certo', /Ganhar/.test(out) && /SubirAlto/.test(out) && /\[cobb_test\.ganhar\] */.test(out), flat(out).slice(0, 300));
  const state = async (key) => { out = await say(a, '/missions list', 1100); const line = out.split('\n').find((l) => l.includes('[cobb_test.' + key + ']')) || ''; return line; };

  console.log('== gained (ganhar um Pokémon)');
  await say(a, '/clearparty EsA', 800);
  out = await say(a, '/givepokemon pikachu level=5', 1500);
  console.log('  ' + flat(out).slice(0, 200));
  let line = await state('ganhar');
  t.check('ganhar um Pokémon completa "gained any 1"', /ready/.test(line), line);
  line = await state('ganhar_raichu');
  t.check('ganhar um Pikachu não completa "gained raichu 1" (filtro de espécie)', !/ready/.test(line), line);
  out = await say(a, '/givepokemon raichu level=5', 1500);
  line = await state('ganhar_raichu');
  t.check('ganhar um Raichu completa "gained raichu 1"', /ready/.test(line), line);

  // level_up e held_item: o /pokemonedit muda o nível direto (não passa pelo evento de subir de nível) e não há comando para dar item
  // segurado; só um doce ou o menu do time disparam. Ficam sem teste ao vivo.

  fs.rmSync(DIR + '/categories/cobb_test.json', { force: true });
  t.rcon('missions reload', 'clearparty EsA');
  a.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
