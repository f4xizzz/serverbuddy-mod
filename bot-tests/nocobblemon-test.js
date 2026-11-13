// Servidor de teste SEM Cobblemon (gradlew runServer -PsemCobblemon): o que é de Pokémon não existe; /storage e /itemshout funcionam;
// e os módulos da fase 2 (MOTD, tags, trivia, missões, passe de batalha, essentials) sobem e abrem os menus sem erro no log.
const t = require('./lib.js');

(async () => {
  const name = 'NcBot' + (Date.now() % 100000);
  const bot = await t.join(name);
  t.rcon(`op ${name}`, `clear ${name}`, `sb storage clear ${name}`);
  await t.sleep(600);

  for (const cmd of ['/pokesee', '/compsee', '/pokeshout 1', '/pokeshoutall', '/poketoken list', '/pokeodds']) {
    bot.chatLog.length = 0;
    bot.chat(cmd);
    await t.sleep(700);
    t.check(`sem Cobblemon, ${cmd} não existe`, bot.chatLog.some((m) => /Unknown|incomplete/i.test(m)), bot.chatLog.join(' | ').slice(0, 120));
  }

  t.rcon(`give ${name} minecraft:stick 1`);
  await t.sleep(800);
  const stick = bot.inventory.items().find((i) => i.name === 'stick');
  if (stick) await bot.equip(stick, 'hand');
  await t.sleep(500);
  bot.chatLog.length = 0;
  bot.chat('/itemshout');
  await t.sleep(900);
  t.check('/itemshout funciona sem o Cobblemon', bot.chatLog.some((m) => m.includes('mostra')), bot.chatLog.join(' | ').slice(0, 160));

  t.rcon(`sb storage add ${name} item minecraft:diamond 2`);
  await t.sleep(600);
  const w = await t.openByCommand(bot, '/storage');
  t.check('/storage abre sem o Cobblemon', t.titleOf(w).includes('Storage'), t.titleOf(w));
  t.check('com a recompensa no slot 0', t.slots(w).some((s) => s.startsWith('0:diamond')), t.slots(w).slice(0, 2).join(' | '));
  await t.click(bot, 0);
  t.check('resgatou os 2 diamantes', t.count(bot, 'diamond') === 2);

  console.log('== módulos da fase 2 sem o Cobblemon');
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  for (const id of ['motd', 'tags', 'trivia', 'missions', 'battlepass', 'essentials', 'commandblock']) {
    t.check(`o módulo ${id} sobe sem o Cobblemon`, new RegExp('✔ ' + id).test(mods), mods.slice(0, 200));
  }
  t.check('as fichas avisam que precisam do Cobblemon', /✖ poketokens parado: precisa do Cobblemon/.test(mods));
  t.check('a Blacklist aparece parada (não há o que vigiar sem o Cobblemon)', /✖ blacklist parado: precisa do Cobblemon/.test(mods));
  const pools = t.rcon('trivia pools').replace(/\n/g, ' | ');
  t.check('trivia: só a pool geral vale (a de Pokémon fica sem perguntas válidas)', /geral/.test(pools) && !/pokemon/.test(pools), pools.slice(0, 200));

  for (const [cmd, title] of [['/tags', 'Tags'], ['/missions', 'Missões'], ['/battlepass', 'Passe de Batalha']]) {
    const win = await t.openByCommand(bot, cmd);
    t.check(`${cmd} abre o menu sem o Cobblemon`, t.titleOf(win).includes(title), t.titleOf(win));
    bot.closeWindow(win);
    await t.sleep(400);
  }
  bot.chatLog.length = 0;
  bot.chat('/sethome nc');
  await t.sleep(900);
  t.check('essentials: /sethome funciona sem o Cobblemon', /nc/.test(bot.chatLog.join(' ')) && !/Unknown|incomplete/i.test(bot.chatLog.join(' ')), bot.chatLog.join(' | ').slice(0, 160));
  bot.chat('/delhome nc');
  await t.sleep(700);

  const log = require('fs').readFileSync('D:/mods/ServerBuddy/run/logs/latest.log', 'utf8').split(/\r?\n/);
  // Esperados e antigos (não são da fase 2): o Shop avisa que o Impactor não está, e a caixa de exemplo de Pokémon que sobrou
  // de uma subida COM Cobblemon não carrega sem ele ("Reward type 'POKEMON' requires the mod(s): cobblemon"); as outras caixas carregam.
  const errors = log.filter((l) => /\/ERROR\]|Exception|NoClassDefFoundError/.test(l) && /serverbuddy|f4xizzz/i.test(l) && !/Impactor|example_pokemon_crate/.test(l));
  t.check('nenhum erro do ServerBuddy no log (nem NoClassDefFoundError de classes do Cobblemon)', errors.length === 0, errors.slice(0, 3).join(' | ').slice(0, 300));

  t.rcon(`clear ${name}`, `sb storage clear ${name}`);
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
