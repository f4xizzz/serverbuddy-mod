// STS (Sell To Server): price, selling through the menu, cooldown, blacklist, last-Pokémon rule. Needs the dev server started with
// -PcomEconomia (BEconomy) and Cobblemon. The test client cannot read Pokémon items, so results come from chat messages.
const t = require('./lib.js');

const NAME = 'StsBot' + (Date.now() % 100000);
const waitChat = async (bot, start, re, ms = 8000) => {
  for (let i = 0; i < ms / 250 && !bot.chatLog.slice(start).some((m) => re.test(m)); i++) await t.sleep(250);
  return bot.chatLog.slice(start).join(' | ');
};

async function sellFirst(bot, slot = 10) {
  const picking = t.waitWindow(bot);
  bot.chat('/sts');
  const picker = await picking;
  await t.sleep(900);
  const start = bot.chatLog.length;
  const confirming = t.waitWindow(bot, 4000).catch(() => null);
  await bot.clickWindow(slot, 0, 0);
  const confirm = await confirming;
  return { picker, confirm, start };
}

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('sts module is running', /✔ sts/.test(mods), mods.slice(-100));

  const bot = await t.join(NAME);
  t.rcon(`gamemode survival ${NAME}`, `eco set 0 CobbleCoins ${NAME}`);
  t.rcon(`givepokemonother ${NAME} pikachu level=20`, `givepokemonother ${NAME} bulbasaur level=5`, `givepokemonother ${NAME} charmander level=8`);
  await t.sleep(1500);

  bot.chatLog.length = 0;
  bot.chat('/sts price 1');
  const priceMsg = await waitChat(bot, 0, /worth/);
  const price = Number((priceMsg.match(/worth \$([\d.]+)/) || [])[1] || 0);
  t.check('/sts price shows a price from the formula (100 + 20 × 10, times the IV bonus)', price >= 300 && price <= 600, priceMsg.slice(0, 160));

  // sell the first party Pokémon through the menu
  let r = await sellFirst(bot);
  t.check('the picker opens', /STS/.test(t.titleOf(r.picker)), t.titleOf(r.picker));
  t.check('clicking a Pokémon opens the confirm menu', r.confirm && /Sell/.test(t.titleOf(r.confirm)), r.confirm ? t.titleOf(r.confirm) : 'no window');
  t.check('the release button is off by default (slot 22 is just the filler)', !r.confirm || !/Release/.test(t.itemText(r.confirm.slots[22])), r.confirm ? t.itemText(r.confirm.slots[22]) : '');
  await bot.clickWindow(11, 0, 0);
  const sold = await waitChat(bot, r.start, /You sold/);
  t.check('selling pays and says so', /You sold .* for \$[\d.]+/.test(sold), sold.slice(0, 200));

  // cooldown (30 s) blocks the next one (the party keeps its gap: the next Pokémon is now in slot 11)
  r = await sellFirst(bot, 11);
  const cooldown = await waitChat(bot, r.start, /sell again in/);
  t.check('a second sale right away waits for the cooldown', /You can sell again in/.test(cooldown), cooldown.slice(0, 160));
  bot.closeWindow(bot.currentWindow);

  // staff can clear the cooldown
  t.rcon(`op ${NAME}`);
  bot.chat(`/sts admin reset ${NAME}`);
  await t.sleep(900);
  t.rcon(`deop ${NAME}`);
  t.rcon(`pokedex revoke ${NAME} all`);
  r = await sellFirst(bot, 11);
  t.check('after the reset the next Pokémon can be sold', !!r.confirm, r.confirm ? t.titleOf(r.confirm) : 'no confirm window');
  bot.closeWindow(bot.currentWindow);

  // blacklist: legendaries are not bought
  t.rcon(`givepokemonother ${NAME} mewtwo level=50`);
  await t.sleep(1200);
  t.rcon(`sts admin reset ${NAME}`);
  const picking = t.waitWindow(bot);
  bot.chat('/sts');
  await picking;
  await t.sleep(900);
  const start = bot.chatLog.length;
  // the party now holds the remaining Pokémon and the Mewtwo; try every party slot until the refusal shows
  for (const slot of [10, 11, 12, 14, 15, 16]) {
    await bot.clickWindow(slot, 0, 0);
    await t.sleep(300);
    if (bot.chatLog.slice(start).some((m) => /does not buy/.test(m))) break;
    if (bot.currentWindow && /Sell/.test(t.titleOf(bot.currentWindow))) {
      // a normal Pokémon opened its confirm menu: go back
      await bot.clickWindow(15, 0, 0);
      await t.sleep(900);
    }
  }
  t.check('legendary Pokémon are on the blacklist', bot.chatLog.slice(start).some((m) => /does not buy/.test(m)), bot.chatLog.slice(start).join(' | ').slice(0, 200));

  t.rcon('clear ' + NAME);
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
