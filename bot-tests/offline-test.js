// /pokesee e /compsee de um jogador que está OFFLINE (e /pokeseeother).
const t = require('./lib.js');
(async () => {
  const stamp = Date.now() % 100000;
  const owner = 'OfOwn' + stamp, viewer = 'OfView' + stamp;
  const a = await t.join(owner);
  t.rcon(`op ${owner}`, `clearparty ${owner}`, `clearpc ${owner}`);
  a.chat('/givepokemon eevee level=9 shiny=yes');
  await t.sleep(1500);
  a.chat('/givepokemon pikachu level=12');
  await t.sleep(1500);
  a.quit();
  await t.sleep(2500);
  console.log('  dono offline: ' + owner);

  const b = await t.join(viewer);
  t.rcon(`op ${viewer}`);
  await t.sleep(800);
  b.rawIgnored = [];
  let w = await t.openByCommand(b, `/pokesee ${owner}`);
  console.log('  título: ' + t.titleOf(w));
  const raw = b.rawIgnored.map((x) => x.toString('utf8')).join('\n');
  t.check('/pokesee de jogador offline abre "Time de <nome>"', t.titleOf(w).includes('Time de ' + owner), t.titleOf(w));
  t.check('mostra o Eevee shiny e o Pikachu do dono offline', raw.includes('cobblemon.species.eevee.name') && raw.includes('cobblemon.species.pikachu.name') && raw.includes('★'));
  b.closeWindow(b.currentWindow);
  await t.sleep(400);

  b.rawIgnored = [];
  w = await t.openByCommand(b, `/pokeseeother ${owner}`);
  t.check('/pokeseeother também abre', t.titleOf(w).includes('Time de ' + owner), t.titleOf(w));
  b.closeWindow(b.currentWindow);
  await t.sleep(400);

  b.rawIgnored = [];
  w = await t.openByCommand(b, `/compsee ${owner}`);
  t.check('/compsee de jogador offline abre "PC de <nome>"', t.titleOf(w).includes('PC de ' + owner), t.titleOf(w));
  b.closeWindow(b.currentWindow);
  await t.sleep(400);

  // nota: com online-mode=false qualquer nome vira um perfil offline, então o aviso "não encontrado" só aparece em servidor online

  t.rcon(`clearparty ${owner}`, `clearpc ${owner}`);
  b.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
