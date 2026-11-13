// Fichas SHINY e GENDER usadas num Pokémon de verdade (solto do time pelo pacote do Cobblemon), dono e não-dono.
const fs = require('fs');
const t = require('./lib.js');

const TOKENS_CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy/PokeTokens/config.json';

function uuidFromInts(text) {
  const m = text.match(/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/);
  if (!m) return null;
  const hex = m.slice(1, 5).map((n) => (Number(n) >>> 0).toString(16).padStart(8, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const useEntity = (bot, id) => bot._client.write('use_entity', { target: id, mouse: 0, hand: 0, sneaking: false });
const lastLines = (bot, n = 3) => bot.chatLog.slice(-n).join(' | ');
const nbt = (uuid, path) => t.rcon(`data get entity ${uuid} ${path}`).split('\n').pop();

(async () => {
  const name = 'TkBot' + (Date.now() % 100000);
  console.log('== jogador de teste: ' + name);
  const bot = await t.join(name);
  t.rcon(`op ${name}`, `clear ${name}`, `clearparty ${name}`, `clearpc ${name}`);
  await t.sleep(500);

  console.log('== Pokémon no time e fichas');
  bot.chat('/givepokemon pikachu level=15');
  await t.sleep(1500);
  console.log('  chat: ' + lastLines(bot, 2));
  t.rcon(`poketoken give ${name} shiny 2`, `poketoken give ${name} gender_male 2`, `poketoken give ${name} gender_female 2`);
  await t.sleep(1000);
  console.log('  inventário: ' + bot.inventory.items().map((i) => `${i.slot}:${i.name}x${i.count}`).join(' '));

  console.log('== soltando o Pokémon (pacote cobblemon:send_out_pokemon, slot 0), olhando para o chão');
  await bot.look(0, -0.7, true);
  await t.sleep(600);
  bot._client.write('custom_payload', { channel: 'cobblemon:send_out_pokemon', data: Buffer.from([0]) });
  await t.sleep(3000);
  const uuid = uuidFromInts(t.rcon(`execute at ${name} run data get entity @e[type=cobblemon:pokemon,limit=1,sort=nearest,distance=..20] UUID`));
  t.check('o Pokémon está fora da bola (achei a entidade)', !!uuid, String(uuid));
  if (!uuid) { bot.quit(); process.exit(1); }
  const entId = t.entityIdByUuid(bot, uuid);
  t.check('o bot recebeu o spawn dessa entidade', entId != null, 'id=' + entId);
  if (entId == null) { bot.quit(); process.exit(1); }
  const dump = t.rcon(`data get entity ${uuid} Pokemon`).split('\n').pop();
  console.log('  Pokemon NBT: ' + dump.slice(0, 500));
  const shinyNow = () => /Shiny:\s*1b/.test(t.rcon(`data get entity ${uuid} Pokemon`));
  const genderNow = () => (t.rcon(`data get entity ${uuid} Pokemon`).match(/Gender:\s*"?(\w+)"?/) || [])[1];

  console.log('== ficha SHINY (slot 0)');
  bot.setQuickBarSlot(0);
  await t.sleep(400);
  t.check('começa não-shiny', !shinyNow());
  bot.chatLog.length = 0;
  useEntity(bot, entId);
  await t.sleep(1200);
  console.log('  chat: ' + lastLines(bot));
  t.check('virou shiny', shinyNow());
  t.check('avisou no chat', bot.chatLog.some((m) => m.includes('shiny')));
  t.check('gastou 1 ficha (2 -> 1)', t.count(bot, 'nether_star') === 1, 'fichas=' + t.count(bot, 'nether_star'));
  bot.chatLog.length = 0;
  useEntity(bot, entId);
  await t.sleep(1200);
  console.log('  chat: ' + lastLines(bot));
  t.check('segunda vez: "já é shiny" e a ficha NÃO é gasta', bot.chatLog.some((m) => m.includes('já é shiny')) && t.count(bot, 'nether_star') === 1);

  console.log('== ficha de SEXO');
  const g0 = genderNow();
  console.log('  sexo atual: ' + g0);
  const toFemale = g0 === 'MALE';
  const slot = toFemale ? 2 : 1;
  bot.setQuickBarSlot(slot);
  await t.sleep(400);
  bot.chatLog.length = 0;
  useEntity(bot, entId);
  await t.sleep(1200);
  console.log('  chat: ' + lastLines(bot));
  const g1 = genderNow();
  t.check(`o sexo mudou (${g0} -> ${g1})`, g1 && g1 !== g0 && g1 === (toFemale ? 'FEMALE' : 'MALE'));
  t.check('gastou 1 ficha de sexo', t.count(bot, toFemale ? 'pink_dye' : 'lapis_lazuli') === 1);
  bot.chatLog.length = 0;
  useEntity(bot, entId);
  await t.sleep(1200);
  console.log('  chat: ' + lastLines(bot));
  t.check('mesmo sexo: não muda e não gasta', genderNow() === g1 && t.count(bot, toFemale ? 'pink_dye' : 'lapis_lazuli') === 1);

  console.log('== blacklist (espécie pikachu) depois de /sb reload');
  const cfg = t.readJson(TOKENS_CFG);
  cfg.tokens.gender_male.blacklist.species = ['pikachu'];
  cfg.tokens.gender_female.blacklist.species = ['pikachu'];
  fs.writeFileSync(TOKENS_CFG, JSON.stringify(cfg, null, 2));
  t.rcon('sb reload');
  await t.sleep(1500);
  bot.setQuickBarSlot(toFemale ? 1 : 2);
  await t.sleep(400);
  bot.chatLog.length = 0;
  useEntity(bot, entId);
  await t.sleep(1200);
  console.log('  chat: ' + lastLines(bot));
  t.check('espécie bloqueada: avisa e não gasta', bot.chatLog.some((m) => m.includes('não pode receber')) && genderNow() === g1);
  cfg.tokens.gender_male.blacklist.species = [];
  cfg.tokens.gender_female.blacklist.species = [];
  fs.writeFileSync(TOKENS_CFG, JSON.stringify(cfg, null, 2));
  t.rcon('sb reload');

  console.log('== outro jogador tenta usar a ficha no Pokémon dele (not_owner)');
  const other = await t.join('OtBot' + (Date.now() % 10000));
  t.rcon(`clear ${other.username}`, `poketoken give ${other.username} shiny 1`, `tp ${other.username} ${uuid}`);
  await t.sleep(1500);
  const entOther = t.entityIdByUuid(other, uuid);
  t.check('o outro jogador também vê o Pokémon', entOther != null, 'id=' + entOther);
  if (entOther != null) {
    other.setQuickBarSlot(0);
    await t.sleep(400);
    other.chatLog.length = 0;
    useEntity(other, entOther);
    await t.sleep(1200);
    console.log('  chat do outro: ' + lastLines(other));
    t.check('"só pode usar em um Pokémon seu" e a ficha não é gasta', other.chatLog.some((m) => m.includes('Pokémon seu')) && t.count(other, 'nether_star') === 1);
  }
  other.quit();

  t.rcon(`clear ${name}`, `clearparty ${name}`, `clearpc ${name}`, `kill ${uuid}`);
  console.log('== fim');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
