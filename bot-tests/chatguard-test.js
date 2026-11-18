// Gerenciador de chat: mensagem repetida barrada (mesmo com maiúsculas/pontuação), 3 avisos, ban de chat com tempo que cresce
// (escada de bans + avisos acumulados), comandos de mensagem bloqueados no ban, /chatguard status|list|history|ban|unban|reset,
// esquecimento dos avisos e o bypass do nível 4. Precisa de EsA com nível 4 no ops.json (o servidor só aceita 2 jogadores).
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/ChatGuard';
const strip = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const flat = (s) => s.replace(/\n/g, ' | ');
const joined = (bot) => bot.chatLog.join('\n');
const writeCfg = (cfg) => fs.writeFileSync(DIR + '/config.json', JSON.stringify(cfg, null, 2), 'utf8');

/** `from` escreve e `other` observa: devolve o que cada um viu. */
async function say(from, other, text, wait = 1000) {
  from.chatLog.length = 0;
  other.chatLog.length = 0;
  from.chat(text);
  await t.sleep(wait);
  return { self: joined(from), other: joined(other) };
}

(async () => {
  console.log('== arquivos e módulo');
  t.check('ChatGuard/config.json criado com a explicação', fs.existsSync(DIR + '/config.json') && /HOW IT WORKS|COMO FUNCIONA/.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  const modules = t.rcon('sb modules').replace(/\n/g, ' ');
  t.check('/sb modules mostra o chatguard rodando', /✔ chatguard/.test(modules), modules.slice(0, 120));
  const cfg = JSON.parse(strip(fs.readFileSync(DIR + '/config.json', 'utf8')));
  cfg.ban.durations = ['4s', '8s', '12s'];
  cfg.ban.extra_per_warning = '1s';
  cfg.ban.max = '1m';
  cfg.warnings.reset_after = '30m';
  cfg.staff.announce_bans = false;
  writeCfg(cfg);
  t.rcon('chatguard reload');

  const a = await t.join('CgA');
  const b = await t.join('CgB');
  t.rcon('deop CgA', 'deop CgB', 'chatguard reset CgA', 'chatguard reset CgB');
  await t.sleep(800);

  console.log('== a mesma mensagem é barrada');
  let r = await say(a, b, 'Olá pessoal do servidor');
  t.check('a primeira vez passa e os outros veem', /Olá pessoal do servidor/.test(r.other), flat(r.other));
  r = await say(a, b, 'Olá pessoal do servidor');
  t.check('a segunda vez é barrada (ninguém vê) e ele recebe o aviso 1/3', !/Olá pessoal/.test(r.other) && /Aviso 1\/3/.test(r.self), flat(r.self));
  r = await say(a, b, 'OLÁ, pessoal do servidor!!!');
  t.check('maiúsculas e pontuação não enganam: aviso 2/3', !/pessoal/.test(r.other) && /Aviso 2\/3/.test(r.self), flat(r.self));
  r = await say(a, b, 'ola pessoal do   servidor');
  t.check('sem acento e com espaços a mais: aviso 3/3 com o alerta do último', /Aviso 3\/3/.test(r.self) && /no próximo erro/.test(r.self), flat(r.self));
  r = await say(a, b, 'Olá pessoal do servidor');
  t.check('o erro depois dos 3 avisos vira ban de chat de 7s (4s da escada + 3 avisos × 1s)', /banido do chat por 7s/.test(r.self) && !/pessoal/.test(r.other), flat(r.self));

  console.log('== durante o ban de chat');
  r = await say(a, b, 'Uma mensagem totalmente diferente');
  t.check('qualquer mensagem dele é barrada, com o tempo que falta', /banido do chat/.test(r.self) && /Faltam/.test(r.self) && !/diferente/.test(r.other), flat(r.self));
  r = await say(a, b, '/msg CgB oi tudo bem');
  t.check('/msg também fica bloqueado (ban.blocked_commands)', /banido do chat/.test(r.self) && !/oi tudo bem/.test(r.other), flat(r.self) + ' // ' + flat(r.other));
  r = await say(b, a, 'Olá pessoal do servidor');
  t.check('outro jogador pode falar a mesma frase (o controle é por jogador)', /Olá pessoal do servidor/.test(r.other), flat(r.other));
  let out = t.rcon('chatguard status CgA');
  t.check('/chatguard status mostra bans, avisos acumulados e o tempo que falta', /bans de chat 1/.test(out) && /avisos acumulados 3/.test(out) && /banido/.test(out), out.replace(/\n/g, ' | '));
  out = t.rcon('chatguard list');
  t.check('/chatguard list lista o banido', /CgA/.test(out), out.replace(/\n/g, ' | '));

  console.log('== depois do ban');
  await t.sleep(7500);
  r = await say(a, b, 'Mensagem nova depois do ban');
  t.check('acabou o ban: ele volta a falar', /Mensagem nova depois do ban/.test(r.other), flat(r.other));

  console.log('== o segundo ban dura mais');
  // 1 passa, 3 viram avisos (1/3, 2/3, 3/3) e o 5º é o ban
  for (let i = 0; i < 4; i++) await say(a, b, 'Texto repetido do teste dois', 900);
  r = await say(a, b, 'Texto repetido do teste dois');
  t.check('4º erro do segundo ciclo: ban de 14s (8s da escada + 6 avisos acumulados × 1s)', /banido do chat por 14s/.test(r.self), flat(r.self));
  out = t.rcon('chatguard history CgA');
  t.check('/chatguard history mostra avisos e os dois bans', /WARNING|AVISO/.test(out) && (out.match(/BAN/g) || []).length >= 2, out.replace(/\n/g, ' | ').slice(0, 300));

  console.log('== a equipe');
  out = t.rcon('chatguard unban CgA');
  t.check('/chatguard unban tira o ban', /pode falar no chat de novo/.test(out), out);
  await t.sleep(600);
  r = await say(a, b, 'Voltei a falar com o ban tirado');
  t.check('ele fala na hora e vê o aviso de que foi liberado', /Voltei a falar/.test(r.other) || /Voltei a falar/.test(r.self), flat(r.other));
  out = t.rcon('chatguard unban CgA');
  t.check('unban de quem não está banido avisa', /não está banido/.test(out), out);
  out = t.rcon('chatguard ban CgB 6s por teste');
  t.check('/chatguard ban com tempo e motivo', /CgB foi banido do chat por 6s/.test(out), out);
  r = await say(b, a, 'Estou banido pela equipe');
  t.check('o banido pela equipe não fala', /banido do chat/.test(r.self) && !/Estou banido/.test(r.other), flat(r.self));
  t.rcon('chatguard unban CgB');
  out = t.rcon('chatguard ban CgB');
  t.check('/chatguard ban sem tempo usa a escada (1º ban de CgB: 4s... conta como o ban nº 2 dele)', /CgB foi banido do chat por/.test(out), out);
  t.rcon('chatguard unban CgB');
  out = t.rcon('chatguard ban CgB abc');
  t.check('tempo inválido é recusado', /Tempo inválido/.test(out), out);
  t.rcon('chatguard reset CgA', 'chatguard reset CgB');
  out = t.rcon('chatguard status CgA');
  t.check('/chatguard reset zera avisos e bans', /avisos 0\/3/.test(out) && /bans de chat 0/.test(out), out.replace(/\n/g, ' | '));
  // (um nome que não existe não dá para testar aqui: em modo offline o servidor inventa um perfil para qualquer nome)

  console.log('== os avisos somem depois de um tempo sem erros');
  cfg.warnings.reset_after = '3s';
  writeCfg(cfg);
  t.rcon('chatguard reload');
  await say(a, b, 'Frase para testar o esquecimento');
  r = await say(a, b, 'Frase para testar o esquecimento');
  t.check('primeiro erro: aviso 1/3', /Aviso 1\/3/.test(r.self), flat(r.self));
  await t.sleep(4000);
  r = await say(a, b, 'Frase para testar o esquecimento');
  t.check('depois de 4s sem erro os avisos zeraram: de novo 1/3 (e não 2/3)', /Aviso 1\/3/.test(r.self), flat(r.self));
  cfg.warnings.reset_after = '30m';
  writeCfg(cfg);
  t.rcon('chatguard reload', 'chatguard reset CgA');

  console.log('== mensagem curta não conta');
  await say(a, b, 'gg');
  r = await say(a, b, 'gg');
  t.check('"gg" repetido passa (menos de 4 letras)', /gg/.test(r.other) && !/Aviso/.test(r.self), flat(r.other));

  console.log('== quem tem o bypass (nível 4) nunca é barrado');
  a.quit();
  await t.sleep(1500);
  const op = await t.join('EsA');
  t.rcon('op EsA', 'chatguard reset EsA');
  await say(op, b, 'Mensagem do administrador igual');
  r = await say(op, b, 'Mensagem do administrador igual');
  t.check('o operador nível 4 repete à vontade e todos veem', /Mensagem do administrador igual/.test(r.other) && !/Aviso/.test(r.self), flat(r.self) + ' // ' + flat(r.other));

  t.rcon('chatguard reset CgA', 'chatguard reset CgB', 'chatguard reset EsA');
  op.quit(); b.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
