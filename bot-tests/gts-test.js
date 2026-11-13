// GTS: listing rules, browsing, buying (with tax), history, cancelling and the Returned tab. Needs the dev server started with
// -PcomEconomia (BEconomy) and Cobblemon. The test client cannot read Pokémon items, so results come from chat messages and slots.
const t = require('./lib.js');

const N = String(Date.now() % 100000);
const SELLER = 'GtsS' + N;
const BUYER = 'GtsB' + N;
const waitChat = async (bot, start, re, ms = 8000) => {
  for (let i = 0; i < ms / 250 && !bot.chatLog.slice(start).some((m) => re.test(m)); i++) await t.sleep(250);
  return bot.chatLog.slice(start).join(' | ');
};
const say = async (bot, cmd, re) => {
  const start = bot.chatLog.length;
  bot.chat(cmd);
  return waitChat(bot, start, re || /./);
};

(async () => {
  const mods = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('gts module is running', /✔ gts/.test(mods), mods.slice(-100));

  const seller = await t.join(SELLER);
  t.rcon(`gamemode survival ${SELLER}`, `eco set 0 CobbleCoins ${SELLER}`);
  t.rcon(`givepokemonother ${SELLER} bulbasaur level=10`, `givepokemonother ${SELLER} eevee level=12`, `give ${SELLER} minecraft:diamond 4`);
  await t.sleep(1500);

  console.log('== listing rules');
  let out = await say(seller, '/gts list pokemon 2 0', /between|least|listed/);
  t.check('a price of 0 is refused (below min_price)', /price must be between/.test(out), out.slice(0, 160));
  out = await say(seller, '/gts list pokemon 6 100', /no Pok|listed/);
  t.check('an empty party slot is refused', /no Pokémon in that party slot/.test(out), out.slice(0, 160));
  out = await say(seller, '/gts list pokemon 2 150', /listed|least|between/);
  t.check('listing a Pokémon works', /You listed .* for \$150/.test(out), out.slice(0, 200));
  await seller.equip(seller.inventory.items().find((i) => i.name === 'diamond'), 'hand').catch(() => {});
  await t.sleep(400);
  out = await say(seller, '/gts list item 100 4', /least|listed|Hold/);
  t.check('items cannot be listed below their minimum (4 diamonds ≥ 200)', /least you can ask for .* is \$200/.test(out), out.slice(0, 160));
  out = await say(seller, '/gts list item 400 4', /least|listed|Hold/);
  t.check('listing 4 diamonds for 400 works', /You listed 4× .* for \$400/.test(out), out.slice(0, 160));
  t.check('the diamonds left the inventory', t.count(seller, 'diamond') === 0, String(t.count(seller, 'diamond')));

  console.log('== buying');
  const buyer = await t.join(BUYER);
  t.rcon(`gamemode survival ${BUYER}`, `eco set 1000 CobbleCoins ${BUYER}`);
  await t.sleep(800);
  const picker = await t.openByCommand(buyer, '/gts');
  t.check('the market opens on the Pokémon tab', /GTS/.test(t.titleOf(picker)) && /Pok/.test(t.titleOf(picker)), t.titleOf(picker));
  // the newest Pokémon listing is in the first listing slot (10)
  const confirming = t.waitWindow(buyer);
  await buyer.clickWindow(10, 0, 0);
  const confirm = await confirming;
  t.check('clicking a listing asks for confirmation with the price', /Buy for \$150/.test(t.titleOf(confirm)), t.titleOf(confirm));
  const bstart = buyer.chatLog.length;
  const sstart = seller.chatLog.length;
  await buyer.clickWindow(11, 0, 0);
  const bought = await waitChat(buyer, bstart, /You bought/);
  t.check('the buyer is told', /You bought .* from .* for \$150/.test(bought), bought.slice(0, 200));
  const sold = await waitChat(seller, sstart, /bought/);
  t.check('the seller is told with the 5% tax (150 → 142.5)', /received \$142\.5/.test(sold) && /tax \$7\.5/.test(sold), sold.slice(0, 220));

  out = await say(buyer, '/gts history', /sales and purchases|no GTS history/);
  t.check('the buyer history lists the purchase', /bought from/.test(out) && /\$150/.test(out), out.slice(0, 200));
  out = await say(seller, '/gts history', /sales and purchases|no GTS history/);
  t.check('the seller history lists the sale', /sold to/.test(out), out.slice(0, 200));

  console.log('== not enough money, own listing');
  t.rcon(`eco set 0 CobbleCoins ${BUYER}`);
  await t.sleep(500);
  const tabItems = await t.openByCommand(buyer, '/gts');
  // the Items tab is the button in slot 2
  await t.click(buyer, 2);
  await t.sleep(600);
  const cstart = buyer.chatLog.length;
  const confirm2 = t.waitWindow(buyer);
  await buyer.clickWindow(10, 0, 0);
  await confirm2;
  await buyer.clickWindow(11, 0, 0);
  const poor = await waitChat(buyer, cstart, /need|bought/);
  t.check('with no money the purchase is refused', /You need \$400/.test(poor), poor.slice(0, 200));
  buyer.closeWindow(buyer.currentWindow);

  const own = await t.openByCommand(seller, '/gts');
  await t.click(seller, 2);
  await t.sleep(600);
  const ostart = seller.chatLog.length;
  const confirm3 = t.waitWindow(seller);
  await seller.clickWindow(10, 0, 0);
  const cw = await confirm3;
  t.check('clicking your own listing offers to cancel it', /Cancel this listing/.test(t.titleOf(cw)), t.titleOf(cw));
  await seller.clickWindow(11, 0, 0);
  const cancelled = await waitChat(seller, ostart, /cancelled/);
  t.check('cancelling moves it to the Returned tab', /was cancelled/.test(cancelled), cancelled.slice(0, 200));

  console.log('== returned tab');
  const w = bot => bot.currentWindow;
  await t.sleep(800);
  let win = await t.openByCommand(seller, '/gts');
  t.check('the Returned tab button shows 1 waiting', /1 waiting/.test(t.itemText(win.slots[6])), t.itemText(win.slots[6]));
  await t.click(seller, 6);
  await t.sleep(600);
  const rstart = seller.chatLog.length;
  await seller.clickWindow(10, 0, 0);
  const back = await waitChat(seller, rstart, /took .* back/);
  t.check('clicking the returned listing gives the diamonds back', /You took 4× .* back/.test(back) && t.count(seller, 'diamond') === 4, back.slice(0, 160) + ' / diamonds=' + t.count(seller, 'diamond'));
  seller.closeWindow(seller.currentWindow);

  console.log('== moderation');
  t.rcon(`gts admin timeout ${SELLER} 2h`);
  out = await say(seller, '/gts list item 400 4', /not allowed|listed|least/);
  t.check('a timed-out player cannot list', /not allowed to list/.test(out), out.slice(0, 160));
  t.rcon(`gts admin untimeout ${SELLER}`);

  t.rcon(`clear ${SELLER}`, `clear ${BUYER}`);
  seller.quit();
  buyer.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
