// Teste de ponta a ponta do /storage com um jogador de verdade (bot em modo offline).
const t = require('./lib.js');

const has = (list, prefix) => list.some((s) => s.startsWith(prefix));

(async () => {
  const bot = await t.join('SbBot');
  console.log('== preparando: op, limpar inventário e storage, 3 recompensas');
  t.rcon('op SbBot', 'clear SbBot', 'sb storage clear SbBot',
    'sb storage add SbBot item minecraft:diamond 3',
    'sb storage add SbBot item minecraft:diamond_sword 1',
    'sb storage add SbBot command give %player% minecraft:emerald 5');
  await t.sleep(1000);
  t.check('avisou que chegou recompensa no /storage', bot.chatLog.some((m) => m.includes('recompensa') && m.includes('/storage')));

  console.log('== abrindo /storage');
  let w = await t.openByCommand(bot, '/storage');
  console.log('  título: ' + t.titleOf(w));
  let s = t.slots(w);
  console.log('  slots: ' + s.join(' | '));
  t.check('menu é 9x6 (54 slots de menu)', w.inventoryStart === 54, 'inventoryStart=' + w.inventoryStart);
  t.check('título "Storage » Itens (1/1)"', t.titleOf(w).includes('Storage') && t.titleOf(w).includes('Itens') && t.titleOf(w).includes('(1/1)'), t.titleOf(w));
  t.check('slot 0 = diamante x3', has(s, '0:diamondx3'));
  t.check('slot 1 = espada de diamante', has(s, '1:diamond_sword'));
  t.check('aba itens (47) e comandos (48) existem', has(s, '47:chest') && has(s, '48:command_block'));
  t.check('fechar (49) e resgatar tudo (51) existem', has(s, '49:barrier') && has(s, '51:hopper'));
  t.check('sem páginas extras: anterior/próxima escondidos', !has(s, '45:arrow') && !has(s, '53:arrow'));
  console.log('  slot 0: ' + t.itemText(w.slots[0]));
  console.log('  slot 47 (aba selecionada): ' + t.itemText(w.slots[47]));
  console.log('  slot 48 (aba não selecionada): ' + t.itemText(w.slots[48]));

  console.log('== clicando no diamante (slot 0)');
  w = await t.click(bot, 0);
  t.check('resgatou 3 diamantes', t.count(bot, 'diamond') === 3, 'diamantes=' + t.count(bot, 'diamond'));
  s = t.slots(w);
  console.log('  slots depois: ' + s.join(' | '));
  t.check('a espada subiu para o slot 0', has(s, '0:diamond_sword') && !has(s, '1:diamond_sword'));
  console.log('  ' + t.rcon('sb storage list SbBot').split('\n').pop().slice(0, 200));

  console.log('== "resgatar tudo" (slot 51) na aba itens');
  w = await t.click(bot, 51);
  t.check('resgatou a espada', t.count(bot, 'diamond_sword') === 1);
  s = t.slots(w);
  console.log('  slots depois: ' + s.join(' | '));
  t.check('aba vazia mostra o ícone de vazio (slot 22 barreira)', has(s, '22:barrier'));

  console.log('== aba comandos (slot 48)');
  w = await t.click(bot, 48);
  console.log('  título: ' + t.titleOf(w));
  s = t.slots(w);
  console.log('  slots: ' + s.join(' | '));
  t.check('título da aba de comandos', t.titleOf(w).includes('Comandos'), t.titleOf(w));
  t.check('mostra a recompensa de comando no slot 0', has(s, '0:command_block'));
  console.log('  slot 0: ' + t.itemText(w.slots[0]));
  w = await t.click(bot, 0);
  t.check('rodou o comando: 5 esmeraldas', t.count(bot, 'emerald') === 5, 'esmeraldas=' + t.count(bot, 'emerald'));
  console.log('  ' + t.rcon('sb storage list SbBot').split('\n').pop().slice(0, 200));

  console.log('== várias páginas: +100 pedras');
  const cmds = [];
  for (let i = 0; i < 100; i++) cmds.push('sb storage add SbBot item minecraft:stone 1');
  t.rcon(...cmds);
  await t.sleep(800);
  bot.closeWindow(bot.currentWindow);
  await t.sleep(500);
  w = await t.openByCommand(bot, '/storage');
  console.log('  título: ' + t.titleOf(w));
  t.check('aba itens, página 1 de 3 (100 pedras / 45 por página)', t.titleOf(w).includes('(1/3)'), t.titleOf(w));
  s = t.slots(w);
  t.check('tem "próxima" e não tem "anterior"', has(s, '53:arrow') && !has(s, '45:arrow'));
  t.check('45 pedras na página 1', s.filter((x) => /^\d+:stone/.test(x) && Number(x.split(':')[0]) <= 44).length === 45);
  w = await t.click(bot, 53);
  t.check('foi para a página 2', t.titleOf(w).includes('(2/3)'), t.titleOf(w));
  s = t.slots(w);
  t.check('agora tem "anterior" e "próxima"', has(s, '45:arrow') && has(s, '53:arrow'));
  w = await t.click(bot, 53);
  t.check('foi para a página 3', t.titleOf(w).includes('(3/3)'), t.titleOf(w));
  s = t.slots(w);
  const stones3 = s.filter((x) => /^\d+:stone/.test(x) && Number(x.split(':')[0]) <= 44).length;
  t.check('última página: 10 pedras e sem "próxima"', stones3 === 10 && !has(s, '53:arrow'), stones3 + ' pedras');
  w = await t.click(bot, 45);
  t.check('voltou para a página 2', t.titleOf(w).includes('(2/3)'), t.titleOf(w));

  console.log('== inventário cheio');
  bot.closeWindow(bot.currentWindow);
  t.rcon('clear SbBot', 'give SbBot minecraft:dirt 2304');
  await t.sleep(1500);
  w = await t.openByCommand(bot, '/storage');
  const before = t.rcon('sb storage list SbBot').match(/: (\d+) reward/)[1];
  w = await t.click(bot, 0);
  t.check('avisou que o inventário está cheio', bot.chatLog.some((m) => m.includes('inventário está cheio')));
  const after = t.rcon('sb storage list SbBot').match(/: (\d+) reward/)[1];
  t.check('a recompensa continua no storage', before === after, `antes=${before} depois=${after}`);

  console.log('== resgate parcial: pilha de 99 com só 1 espaço livre');
  bot.closeWindow(bot.currentWindow);
  t.rcon('sb storage clear SbBot', 'clear SbBot', 'give SbBot minecraft:dirt 2240', 'sb storage add SbBot item minecraft:cobblestone 99');
  await t.sleep(1500);
  w = await t.openByCommand(bot, '/storage');
  console.log('  slots: ' + t.slots(w).join(' | '));
  w = await t.click(bot, 0);
  const cobble = t.count(bot, 'cobblestone');
  const left = t.rcon('sb storage list SbBot');
  t.check('recebeu 64 e ficaram 35 no storage', cobble === 64 && left.includes('35x'), `cobblestone=${cobble}; ${left.split('\n').pop().slice(0, 160)}`);
  t.check('avisou resgate parcial', bot.chatLog.some((m) => m.includes('64 de 99')));

  console.log('== fechar (slot 49)');
  w = await t.click(bot, 49);
  t.check('o botão fechar fechou o menu', !bot.currentWindow);

  t.rcon('clear SbBot', 'sb storage clear SbBot');
  console.log('== fim');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
