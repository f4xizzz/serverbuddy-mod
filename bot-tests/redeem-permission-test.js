// /redeem: códigos com permissão (com e sem LuckPerms), expiração (expires_at), chaves em inglês e em português na mesma config,
// código sem diferenciar maiúsculas, limite de usos, mensagem clara ao negar e /sb redeem list|reset.
// Sem LuckPerms (servidor normal) só roda a parte de operadores; com `gradlew runServer -PcomLuckPerms` roda também a parte do LuckPerms.
// Usa os jogadores RdpA e RdpB (o servidor só aceita 2 ao mesmo tempo).
const fs = require('fs');
const t = require('./lib.js');

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy';
const REDEEM = CFG + '/Redeem/redeem.conf';
const STORAGE = CFG + '/Storage/config.json';
const LOG = 'D:/mods/ServerBuddy/run/logs/latest.log';
const flat = (s) => s.replace(/\n/g, ' | ');
const joined = (bot) => bot.chatLog.join('\n');

/** O jogador digita o comando e devolve o que ele viu no chat. */
async function redeem(bot, text, wait = 1100) {
  bot.chatLog.length = 0;
  bot.chat('/redeem ' + text);
  await t.sleep(wait);
  return joined(bot);
}

const stick = (bot) => t.count(bot, 'stick');

(async () => {
  const online = t.rcon('list');
  if (/RdpA|RdpB/.test(online)) throw new Error('Os jogadores de teste do redeem já estão online');
  const original = { redeem: fs.readFileSync(REDEEM, 'utf8'), storage: fs.readFileSync(STORAGE, 'utf8') };
  const hasLuckPerms = !/Unknown|unknown|Incomplete/i.test(t.rcon('lp info'));
  console.log(hasLuckPerms ? '== LuckPerms presente: roda a parte com LuckPerms' : '== sem LuckPerms: só a parte dos operadores');
  let a;
  let b;
  try {
    // entrega direta (sem passar pelo /storage), para contar os itens no inventário
    const storage = t.parseJson(original.storage);
    storage.redeem_to_storage = false;
    fs.writeFileSync(STORAGE, JSON.stringify(storage, null, 2), 'utf8');
    t.rcon('sb storage reload');

    const give = (item = 'stick') => [`give %player% minecraft:${item} 1`];
    fs.writeFileSync(REDEEM, JSON.stringify({
      aliases: ['redeem'],
      // as mensagens ficam nas chaves ANTIGAS (português) de propósito: o arquivo velho tem que continuar valendo
      mensagens: { sem_permissao_codigo: '<red>[sem-permissao] {codigo} precisa de {permission}', sucesso: '<green>[ok] {codigo} resgatado' },
      codes: {
        open: { max_uses: 0, commands: give() },
        vip: { max_uses: 0, permission: 'vip.codes', commands: give('apple') },
        MixedCase: { commands: give('carrot') },
        antigo: { max_usos: 0, permissao: 'vip.codes', comandos: give('bread') }, // chaves antigas
        vencido: { expires_at: '2020-01-01', commands: give('cookie') },
        vencido_hora: { expires_at: '2020-01-01 10:30', commands: give('cookie') },
        futuro: { expires_at: '2999-12-31 12:00', commands: give('melon_slice') },
        data_ruim: { expires_at: '31/12/2999', commands: give('cookie') },
        so_um: { max_uses: 1, commands: give('beef') },
        grupo: { permission: 'group.rdvip', commands: give('cake') },
      },
    }, null, 2), 'utf8');
    const reload = t.rcon('sb redeem reload');
    console.log('  ' + flat(reload).slice(0, 120));
    await t.sleep(600);

    console.log('== o arquivo com chaves mistas foi lido (inglês e português na mesma config)');
    const list = t.rcon('sb redeem list');
    console.log('  ' + flat(list).slice(0, 700));
    t.check('/sb redeem list mostra os 10 códigos', (list.match(/- [A-Za-z_]+: \d+\//g) || []).length === 10, flat(list).slice(0, 200));
    t.check('a lista mostra a permissão do código velho (chave "permissao")', /antigo: 0\/∞ \(permission: vip\.codes\)/.test(list), flat(list).slice(0, 300));
    t.check('a lista marca o código vencido e mostra quando o futuro vence', /vencido: 0\/∞ \(EXPIRED 2020-01-01 23:59/.test(list) && /futuro: 0\/∞ \(expires 2999-12-31 12:00/.test(list), flat(list).slice(0, 700));
    t.check('data que não dá para ler aparece como inválida na lista', /data_ruim: .*EXPIRED invalid \(31\/12\/2999\)/.test(list), flat(list).slice(0, 700));
    const log = fs.readFileSync(LOG, 'utf8');
    t.check('o console avisa o admin da data ruim (com o formato certo)', /The code 'data_ruim' has an expires_at that cannot be read/.test(log));

    a = await t.join('RdpA');
    b = await t.join('RdpB');
    t.rcon('deop RdpA', 'deop RdpB', 'clear RdpA', 'clear RdpB', 'sb storage clear RdpA', 'sb storage clear RdpB');
    for (const code of ['open', 'vip', 'MixedCase', 'antigo', 'vencido', 'vencido_hora', 'futuro', 'data_ruim', 'so_um', 'grupo']) t.rcon(`sb redeem reset RdpA ${code}`, `sb redeem reset RdpB ${code}`);
    await t.sleep(600);

    console.log('== código comum');
    let out = await redeem(a, 'open');
    t.check('resgata e recebe o item', /\[ok\] open resgatado/.test(out) && stick(a) === 1, flat(out) + ' stick=' + stick(a));
    out = await redeem(a, 'OPEN');
    t.check('o mesmo código com maiúsculas é o mesmo código (já resgatado)', /already redeemed/.test(out) && stick(a) === 1, flat(out));
    out = await redeem(a, 'naoexiste');
    t.check('código que não existe avisa', /does not exist/.test(out) && /naoexiste/.test(out), flat(out));
    out = await redeem(a, 'mixedcase');
    t.check('o código com maiúsculas na config vale digitado em minúsculas (e usa o nome da config)', /\[ok\] MixedCase resgatado/.test(out) && t.count(a, 'carrot') === 1, flat(out));

    console.log('== permissão por código (jogador comum)');
    out = await redeem(a, 'vip');
    t.check('sem a permissão: recusa com o texto do admin, o código e a permissão exigida', /\[sem-permissao\] vip precisa de vip\.codes/.test(out) && t.count(a, 'apple') === 0, flat(out));
    out = await redeem(a, 'antigo');
    t.check('o código escrito com as chaves antigas também exige a permissão', /\[sem-permissao\] antigo/.test(out) && t.count(a, 'bread') === 0, flat(out));
    t.check('a recusa fica no log do servidor, com o nó que falta', /RdpA was refused the code 'vip': it needs the permission 'vip\.codes'/.test(fs.readFileSync(LOG, 'utf8')));
    t.check('quem foi recusado não "gastou" o código: /sb redeem list continua 0 usos', /vip: 0\/∞/.test(t.rcon('sb redeem list')));

    console.log('== operador (nível 2) passa quando ninguém nega a permissão');
    t.rcon('op RdpA');
    await t.sleep(800);
    out = await redeem(a, 'vip');
    t.check('o operador resgata o código com permissão', /\[ok\] vip resgatado/.test(out) && t.count(a, 'apple') === 1, flat(out));
    out = await redeem(a, 'antigo');
    t.check('e o código com as chaves antigas', /\[ok\] antigo resgatado/.test(out) && t.count(a, 'bread') === 1, flat(out));
    t.rcon('deop RdpA');

    console.log('== expiração');
    t.rcon('op RdpA');
    await t.sleep(600);
    out = await redeem(a, 'vencido');
    t.check('código vencido (só a data): recusa dizendo que venceu', /has expired/.test(out) && t.count(a, 'cookie') === 0, flat(out));
    out = await redeem(a, 'vencido_hora');
    t.check('código vencido (data e hora): recusa', /has expired/.test(out) && t.count(a, 'cookie') === 0, flat(out));
    out = await redeem(a, 'data_ruim');
    t.check('data que não dá para ler: bloqueia (nunca vira código eterno)', /has expired/.test(out) && t.count(a, 'cookie') === 0, flat(out));
    out = await redeem(a, 'futuro');
    t.check('código que ainda não venceu resgata normalmente', /\[ok\] futuro resgatado/.test(out) && t.count(a, 'melon_slice') === 1, flat(out));
    t.rcon('deop RdpA');

    console.log('== limite de usos');
    out = await redeem(a, 'so_um');
    t.check('o primeiro resgata', /\[ok\] so_um resgatado/.test(out) && t.count(a, 'beef') === 1, flat(out));
    out = await redeem(b, 'so_um');
    t.check('o segundo recebe o aviso de limite atingido', /reached its usage limit/.test(out) && t.count(b, 'beef') === 0, flat(out));
    t.check('/sb redeem list mostra 1/1', /so_um: 1\/1/.test(t.rcon('sb redeem list')));

    console.log('== /sb redeem reset');
    const reset = t.rcon('sb redeem reset RdpA OPEN');
    t.check('reset aceita o código com maiúsculas e avisa', /reset for 1 player/.test(reset), flat(reset));
    out = await redeem(a, 'open');
    t.check('depois do reset o jogador resgata de novo', /\[ok\] open resgatado/.test(out) && stick(a) === 2, flat(out));

    if (hasLuckPerms) {
      console.log('== LuckPerms: permissão dada a um jogador comum');
      t.rcon('lp user RdpB permission set vip.codes true');
      await t.sleep(900);
      out = await redeem(b, 'vip');
      t.check('RdpB (não é operador) recebe a permissão pelo LuckPerms e resgata', /\[ok\] vip resgatado/.test(out) && t.count(b, 'apple') === 1, flat(out));
      t.rcon('lp user RdpB permission unset vip.codes');

      console.log('== LuckPerms: um "false" explícito vence até o nível de operador');
      t.rcon('op RdpA', 'lp user RdpA permission set vip.codes false');
      await t.sleep(900);
      t.rcon('sb redeem reset RdpA vip');
      out = await redeem(a, 'vip');
      t.check('operador com a permissão negada no LuckPerms é recusado', /\[sem-permissao\] vip/.test(out) && t.count(a, 'apple') === 1, flat(out));
      t.rcon('lp user RdpA permission unset vip.codes', 'deop RdpA');

      console.log('== LuckPerms: a permissão vem de um grupo (group.rdvip)');
      t.rcon('lp creategroup rdvip', 'lp user RdpB parent add rdvip');
      await t.sleep(900);
      out = await redeem(b, 'grupo');
      t.check('quem está no grupo resgata o código "group.rdvip"', /\[ok\] grupo resgatado/.test(out) && t.count(b, 'cake') === 1, flat(out));
      t.rcon('lp user RdpB parent remove rdvip');
      await t.sleep(900);
      t.rcon('sb redeem reset RdpB grupo', 'clear RdpB');
      out = await redeem(b, 'grupo');
      t.check('tirado do grupo, volta a ser recusado', /\[sem-permissao\] grupo/.test(out) && t.count(b, 'cake') === 0, flat(out));
      t.rcon('lp deletegroup rdvip');
    }
  } finally {
    fs.writeFileSync(REDEEM, original.redeem, 'utf8');
    fs.writeFileSync(STORAGE, original.storage, 'utf8');
    t.rcon('deop RdpA', 'deop RdpB', 'sb storage reload', 'sb redeem reload', 'clear RdpA', 'clear RdpB', 'sb storage clear RdpA', 'sb storage clear RdpB');
    if (hasLuckPerms) t.rcon('lp user RdpA permission unset vip.codes', 'lp user RdpB permission unset vip.codes', 'lp deletegroup rdvip');
    // o banco guarda os resgates de teste: apaga
    try {
      const { DatabaseSync } = require('node:sqlite');
      const db = new DatabaseSync(CFG + '/Database/serverbuddy.db');
      try {
        const uuids = [];
        for (const n of ['RdpA', 'RdpB']) {
          const m = require('crypto').createHash('md5').update('OfflinePlayer:' + n).digest();
          m[6] = (m[6] & 0x0f) | 0x30; m[8] = (m[8] & 0x3f) | 0x80;
          const h = m.toString('hex');
          uuids.push(`${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`);
        }
        db.prepare('DELETE FROM redeem_claims WHERE player_uuid IN (?, ?)').run(...uuids);
      } finally { db.close(); }
    } catch (e) { console.log('  (não limpou o banco: ' + e.message + ')'); }
    console.log('== fim (configs restauradas)');
    if (a) a.quit();
    if (b) b.quit();
    await t.sleep(500);
    process.exit(process.exitCode || 0);
  }
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
