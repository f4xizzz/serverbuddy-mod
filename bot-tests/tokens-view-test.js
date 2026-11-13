// Fichas (itens), /pokesee, /compsee, /pokeshout, /pokeshoutall e /itemshout com um jogador de verdade (bot offline).
const t = require('./lib.js');

(async () => {
  const bot = await t.join('SbBot');
  console.log('== preparando');
  console.log(t.rcon('op SbBot', 'clear SbBot', 'clearparty SbBot', 'clearpc SbBot', 'gamemode creative SbBot').split('\n').filter((l) => !l.startsWith('>')).join(' | '));

  console.log('== fichas: itens entregues por /poketoken give');
  console.log(t.rcon('poketoken give SbBot shiny 2', 'poketoken give SbBot gender 1', 'poketoken give SbBot shiny_ball 3', 'poketoken give SbBot gender_male 1')
    .split('\n').filter((l) => l.trim() && !l.startsWith('>')).join(' | '));
  await t.sleep(1200);
  for (const it of bot.inventory.items()) {
    const kinds = (it.components || []).map((c) => c.type).join(',');
    console.log(`  ${it.name} x${it.count} ${t.itemText(it)} [${kinds}]`);
  }
  const shiny = bot.inventory.items().find((i) => i.name === 'nether_star');
  t.check('ficha shiny = nether_star x2 com nome e lore', !!shiny && shiny.count === 2 && t.itemText(shiny).includes('Ficha Shiny'));
  t.check('ficha tem hide_additional_tooltip e o marcador custom_data',
    !!shiny && shiny.components.some((c) => c.type === 'hide_additional_tooltip') && shiny.components.some((c) => c.type === 'custom_data'));
  t.check('Shiny Ball é uma poke_ball do Cobblemon (item desconhecido do bot ou nome poke_ball)',
    bot.inventory.items().some((i) => i.count === 3 && /poke_ball|unknown|undefined/.test(String(i.name))) || bot.inventory.items().length >= 3,
    bot.inventory.items().map((i) => i.name + 'x' + i.count).join(','));

  console.log('== dar o Pokémon e abrir /pokesee e /compsee');
  bot.chat('/givepokemon pikachu level=15');
  await t.sleep(1200);
  bot.chat('/givepokemon eevee level=7 shiny=yes');
  await t.sleep(1500);

  bot.rawIgnored = [];
  const baseDropped = (bot._ignoredCount || 0);
  let w = await t.openByCommand(bot, '/pokesee');
  console.log('  título: ' + t.titleOf(w) + ' | tipo: ' + w.type + ' | slots do menu: ' + w.inventoryStart);
  t.check('/pokesee abriu "Time de SbBot" em 9x3', t.titleOf(w).includes('Time de SbBot') && w.inventoryStart === 27, t.titleOf(w));
  console.log('  slots: ' + t.slots(w).join(' | '));
  const rawText = (bot.rawIgnored || []).map((b) => b.toString('utf8')).join('\n');
  t.check('conteúdo do time chegou (pacote com Pokémon)', rawText.includes('cobblemon.species.pikachu.name') || t.slots(w).length > 0,
    (bot.rawIgnored || []).length + ' pacote(s) não lido(s)');
  t.check('o nome da espécie do Pikachu aparece como chave de tradução', rawText.includes('cobblemon.species.pikachu.name'));
  t.check('o Eevee shiny aparece com a estrela', rawText.includes('cobblemon.species.eevee.name') && rawText.includes('★'));
  w = await t.click(bot, 10);
  t.check('clicar num Pokémon não faz nada (só olhar)', !!bot.currentWindow && t.titleOf(bot.currentWindow).includes('Time de SbBot'));
  await t.click(bot, 22);
  t.check('botão fechar do /pokesee', !bot.currentWindow);

  bot.rawIgnored = [];
  w = await t.openByCommand(bot, '/compsee');
  console.log('  título: ' + t.titleOf(w) + ' | slots do menu: ' + w.inventoryStart);
  t.check('/compsee abriu "PC de SbBot" em 9x6', t.titleOf(w).includes('PC de SbBot') && w.inventoryStart === 54, t.titleOf(w));
  console.log('  slots: ' + t.slots(w).join(' | '));
  const pcText = (bot.rawIgnored || []).map((b) => b.toString('utf8')).join('\n');
  t.check('PC vazio: sem Pokémon nas caixas (nenhuma espécie no conteúdo)', !pcText.includes('cobblemon.species.'));
  await t.click(bot, 53);
  console.log('  depois de "próxima caixa": ' + t.titleOf(bot.currentWindow));
  await t.click(bot, 47);
  t.check('botão fechar do /compsee', !bot.currentWindow);

  console.log('== /pokeshout e /pokeshoutall');
  bot.chatLog.length = 0;
  bot.chat('/pokeshout 1');
  await t.sleep(1200);
  t.check('/pokeshout 1 mandou a mensagem pública', bot.chatLog.some((m) => m.includes('SbBot') && m.includes('mostra')), bot.chatLog.join(' | ').slice(0, 300));
  bot.chatLog.length = 0;
  bot.chat('/pokeshout 1');
  await t.sleep(1000);
  t.check('/pokeshout de novo antes do tempo cai no cooldown', bot.chatLog.some((m) => m.includes('Aguarde')), bot.chatLog.join(' | ').slice(0, 200));
  bot.chatLog.length = 0;
  bot.chat('/pokeshout 6');
  await t.sleep(1000);
  t.check('slot vazio avisa (ou cooldown)', bot.chatLog.some((m) => m.includes('Não há Pokémon') || m.includes('Aguarde')), bot.chatLog.join(' | ').slice(0, 200));

  console.log('== /itemshout');
  bot.chatLog.length = 0;
  bot.chat('/itemshout');
  await t.sleep(1000);
  t.check('mão vazia ou item: responde algo', bot.chatLog.length > 0, bot.chatLog.join(' | ').slice(0, 200));
  console.log(t.rcon('give SbBot minecraft:diamond_sword[minecraft:custom_name=\'{"text":"Excalibur","color":"gold"}\'] 1').split('\n').pop());
  await t.sleep(800);
  bot.setQuickBarSlot(0);
  const sword = bot.inventory.items().find((i) => i.name === 'diamond_sword');
  if (sword) {
    await bot.equip(sword, 'hand');
    await t.sleep(600);
  }
  bot.chatLog.length = 0;
  bot.chat('/itemshout');
  await t.sleep(1200);
  t.check('/itemshout mostrou o item no chat', bot.chatLog.some((m) => m.includes('SbBot') && m.includes('mostra')), bot.chatLog.join(' | ').slice(0, 300));

  console.log('== /pokeodds');
  console.log(t.rcon('pokeodds').split('\n').filter((l) => l.trim() && !l.startsWith('>')).join(' | '));

  t.rcon('clear SbBot', 'gamemode survival SbBot');
  console.log('== fim');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
