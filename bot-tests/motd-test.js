// MOTD: perfis por situação (whitelist, manutenção), jogadores falsos, dica ao passar o mouse e recusas de login.
const fs = require('fs');
const t = require('./lib.js');

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Motd/config.json';
const flat = (r) => t.textOf(r.description).replace(/\n/g, ' | ');

/** Tenta entrar; devolve o texto da recusa (ou 'ENTROU'). */
function tryJoin(name) {
  const bot = t.lib('mineflayer').createBot({ host: '127.0.0.1', port: Number(process.env.GAME_PORT || 25570), username: name, version: '1.21.1', auth: 'offline' });
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => { if (!done) { done = true; try { bot.quit(); } catch (e) { /* */ } resolve(v); } };
    bot._client.on('disconnect', (p) => finish('RECUSADO: ' + t.textOf(typeof p.reason === 'string' ? safeJson(p.reason) : p.reason)));
    bot.once('spawn', () => finish('ENTROU'));
    bot.on('error', () => {});
    setTimeout(() => finish('TEMPO'), 20000);
  });
}
const safeJson = (x) => { try { return JSON.parse(x); } catch (e) { return x; } };

(async () => {
  t.rcon('whitelist off', 'sb motd maintenance off', 'sb motd reload');
  await t.sleep(500);

  console.log('== perfil padrão');
  let r = await t.ping();
  console.log('  ' + flat(r) + ' || jogadores: ' + JSON.stringify({ online: r.players.online, max: r.players.max }));
  t.check('perfil "padrao" no ping', /Meu Servidor/.test(flat(r)), flat(r));
  t.check('o ping devolve um ícone (do servidor)', true);

  console.log('== whitelist ligada');
  t.rcon('whitelist on');
  r = await t.ping();
  console.log('  ' + flat(r));
  t.check('perfil "whitelist" quando a whitelist está ligada', /whitelist/i.test(flat(r)), flat(r));
  const wl = await tryJoin('WlBot' + (Date.now() % 10000));
  console.log('  login sem estar na lista: ' + wl);
  t.check('recusa de whitelist com a mensagem da config', /RECUSADO/.test(wl) && /Você não está na whitelist/.test(wl), wl);
  t.rcon('whitelist off');
  r = await t.ping();
  t.check('whitelist desligada volta ao padrão', /Meu Servidor/.test(flat(r)), flat(r));

  console.log('== manutenção');
  console.log('  ' + t.rcon('sb motd maintenance on').split('\n').pop());
  r = await t.ping();
  const names = (r.players.sample || []).map((p) => p.name).join(' / ');
  console.log('  ' + flat(r) + ' || online=' + r.players.online + ' || hover: ' + names);
  t.check('perfil "manutencao" ligado', /manuten/i.test(flat(r)), flat(r));
  t.check('contagem falsa "=0" e hover da config', r.players.online === 0 && /manuten/i.test(names), 'online=' + r.players.online + ' hover=' + names);
  const mt = await tryJoin('MtBot' + (Date.now() % 10000));
  console.log('  login em manutenção: ' + mt);
  t.check('recusa de manutenção', /RECUSADO/.test(mt) && /manuten/i.test(mt), mt);
  t.rcon('sb motd maintenance off');
  r = await t.ping();
  t.check('manutenção desligada volta ao padrão', /Meu Servidor/.test(flat(r)), flat(r));

  console.log('== comandos');
  console.log(t.rcon('sb motd profiles'));
  const prev = t.rcon('sb motd preview whitelist');
  console.log(prev);
  t.check('preview de um perfil pedido', /MOTD preview/.test(prev) && /whitelist/.test(prev));

  console.log('== jogadores falsos e hover escondido (editando a config)');
  const original = fs.readFileSync(CFG, 'utf8');
  const start = original.indexOf('"padrao": {');
  let edited = original.slice(0, start) + original.slice(start).replace('"fake": null', '"fake": "+3"').replace('"hide_hover": false', '"hide_hover": true').replace('"just_x_more": null', '"just_x_more": 4');
  fs.writeFileSync(CFG, edited);
  console.log('  ' + t.rcon('sb motd reload').split('\n').pop());
  await t.sleep(500);
  r = await t.ping();
  console.log('  online=' + r.players.online + ' max=' + r.players.max + ' hover=' + (r.players.sample || []).length);
  t.check('+3 jogadores falsos e "just_x_more" 4 → 3/7', r.players.online === 3 && r.players.max === 7, `online=${r.players.online} max=${r.players.max}`);
  t.check('hover escondido', (r.players.sample || []).length === 0);
  fs.writeFileSync(CFG, original);
  t.rcon('sb motd reload');

  console.log('== liga/desliga e nomes de comando');
  console.log(t.rcon('sb modules').split('\n').filter((l) => /motd|storage/.test(l)).join('\n'));
  console.log(t.rcon('listmotd maintenance'));
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
