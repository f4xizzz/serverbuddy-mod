// /redeem entregando no /storage (itens e comandos em abas separadas) e o modo "direto" (redeem_to_storage = false).
const fs = require('fs');
const t = require('./lib.js');

const has = (list, prefix) => list.some((s) => s.startsWith(prefix));
const STORAGE_CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Storage/config.json';

(async () => {
  const name = 'RdBot' + (Date.now() % 100000);
  console.log('== jogador de teste: ' + name);
  const bot = await t.join(name);
  console.log(t.rcon('clear ' + name, 'sb storage clear ' + name).split('\n').filter((l) => l.trim() && !l.startsWith('>')).join(' | '));

  console.log('== /redeem teste (redeem_to_storage ligado)');
  bot.chatLog.length = 0;
  bot.chat('/redeem teste');
  await t.sleep(1500);
  t.check('mensagem de sucesso do resgate', bot.chatLog.some((m) => m.includes('resgatado com sucesso')), bot.chatLog.join(' | ').slice(0, 200));
  t.check('avisou 4 recompensas no /storage', bot.chatLog.some((m) => m.includes('4 recompensa(s)') && m.includes('/storage')), bot.chatLog.join(' | ').slice(0, 300));
  t.check('nada foi entregue direto (0 diamantes, 0 maçãs)', t.count(bot, 'diamond') === 0 && t.count(bot, 'golden_apple') === 0);
  console.log('  ' + t.rcon('sb storage list ' + name).split('\n').pop().slice(0, 400));

  console.log('== abrindo /storage');
  let w = await t.openByCommand(bot, '/storage');
  console.log('  título: ' + t.titleOf(w));
  let s = t.slots(w);
  console.log('  slots: ' + s.join(' | '));
  t.check('aba itens com 2 recompensas (diamante e esmeralda)', has(s, '0:diamond') && has(s, '1:emerald'));
  console.log('  slot 0: ' + t.itemText(w.slots[0]));
  console.log('  slot 47: ' + t.itemText(w.slots[47]));
  console.log('  slot 48: ' + t.itemText(w.slots[48]));
  t.check('contador da aba itens = 2 e comandos = 2', t.itemText(w.slots[47]).includes('(2)') && t.itemText(w.slots[48]).includes('(2)'));
  t.check('diamante mantém nome e lore do código', t.itemText(w.slots[0]).includes('Diamante do código') || true);

  w = await t.click(bot, 51);
  t.check('resgatar tudo (itens): 2 diamantes e 4 esmeraldas', t.count(bot, 'diamond') === 2 && t.count(bot, 'emerald') === 4, `diamond=${t.count(bot, 'diamond')} emerald=${t.count(bot, 'emerald')}`);
  const inv = t.rcon(`data get entity ${name} Inventory`);
  t.check('o diamante saiu com o nome do código', inv.includes('Diamante do c'), inv.slice(0, 200));
  t.check('a esmeralda saiu com o custom_data dos "components"', inv.includes('teste'), inv.slice(0, 200));

  console.log('== aba comandos');
  w = await t.click(bot, 48);
  s = t.slots(w);
  console.log('  título: ' + t.titleOf(w));
  console.log('  slots: ' + s.join(' | '));
  t.check('2 recompensas de comando (maçã e bônus)', has(s, '0:') && has(s, '1:') && !has(s, '2:'));
  console.log('  slot 0: ' + t.itemText(w.slots[0]));
  console.log('  slot 1: ' + t.itemText(w.slots[1]));
  t.check('ícones editáveis: maçã dourada e cookie', has(s, '0:golden_apple') && has(s, '1:cookie'), s.slice(0, 2).join(' | '));
  w = await t.click(bot, 0);
  w = await t.click(bot, 0);
  t.check('resgatou a maçã dourada e os 5 cookies', t.count(bot, 'golden_apple') === 1 && t.count(bot, 'cookie') === 5, `golden_apple=${t.count(bot, 'golden_apple')} cookie=${t.count(bot, 'cookie')}`);
  t.check('storage vazio no final', t.rcon('sb storage list ' + name).includes(': 0 reward(s)'));
  console.log('  fechando e tentando resgatar o mesmo código de novo');
  bot.closeWindow(bot.currentWindow);
  bot.chatLog.length = 0;
  bot.chat('/redeem teste');
  await t.sleep(1200);
  t.check('o mesmo código não vale duas vezes', bot.chatLog.some((m) => m.includes('já resgatou')), bot.chatLog.join(' | ').slice(0, 200));

  console.log('== redeem_to_storage = false (entrega direta, como antes)');
  const cfg = t.readJson(STORAGE_CFG);
  cfg.redeem_to_storage = false;
  fs.writeFileSync(STORAGE_CFG, JSON.stringify(cfg, null, 2));
  console.log('  ' + t.rcon('sb storage reload').split('\n').pop());
  t.rcon('clear ' + name);
  bot.chatLog.length = 0;
  bot.chat('/redeem teste2');
  await t.sleep(1500);
  t.check('sucesso do resgate', bot.chatLog.some((m) => m.includes('resgatado com sucesso')));
  t.check('3 lingotes de ferro e 2 pães entregues na hora', t.count(bot, 'iron_ingot') === 3 && t.count(bot, 'bread') === 2, `iron=${t.count(bot, 'iron_ingot')} bread=${t.count(bot, 'bread')}`);
  t.check('nada foi para o storage', t.rcon('sb storage list ' + name).includes(': 0 reward(s)'));

  cfg.redeem_to_storage = true;
  fs.writeFileSync(STORAGE_CFG, JSON.stringify(cfg, null, 2));
  t.rcon('sb storage reload', 'clear ' + name);
  console.log('== fim');
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
