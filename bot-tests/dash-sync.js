// Joins as an operator and runs /sb dashboard sync; prints the pairing code the mod shows to that admin.
// usage: SB_RUN=D:/mods/ServerBuddy-pesquisa/prod-test node dash-sync.js [command]
const { join, rcon, sleep } = require('./lib.js');

(async () => {
  console.log(rcon('op EsA').split('\n')[1] || '');
  const bot = await join('EsA');
  const command = process.argv[2] || '/sb dashboard sync';
  bot.chat(command);
  await sleep(5000);
  const lines = bot.chatLog.filter((l) => /Pairing code|Dashboard|dashboard|code|Error|error/.test(l));
  console.log('RESULT ' + (lines.length ? lines.join(' | ') : 'no matching chat line'));
  bot.quit();
  await sleep(500);
  process.exit(0);
})().catch((e) => { console.log('FAILED ' + e.message); process.exit(1); });
