// Trivia no pipeline de chat: a resposta tem que ser vista pelo Trivia mesmo com o formato do chat do Essentials ligado,
// com o gerenciador de chat (mensagem repetida), com mute e com o chat da equipe no caminho. Reproduz o relato
// "responder a trivia no chat não funciona" e confere a ordem fixa: mute/equipe -> trivia -> gerenciador -> formato.
// Precisa do servidor de teste no ar; usa os jogadores TcA e TcB (o servidor só aceita 2 ao mesmo tempo).
const fs = require('fs');
const t = require('./lib.js');

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy';
const ESS = CFG + '/Essentials/config.json';
const TRI = CFG + '/Trivia';
const GUARD = CFG + '/ChatGuard/config.json';
const joined = (bot) => bot.chatLog.join('\n');
const flat = (s) => s.replace(/\n/g, ' | ');
const write = (file, obj) => fs.writeFileSync(file, JSON.stringify(obj, null, 2), 'utf8');
const pool = (name, obj) => write(`${TRI}/pools/${name}.json`, obj);

/** `from` escreve e os outros observam: devolve o que cada um viu. */
async function say(from, text, wait = 1100, ...others) {
  from.chatLog.length = 0;
  for (const o of others) o.chatLog.length = 0;
  from.chat(text);
  await t.sleep(wait);
  return { self: joined(from), others: others.map(joined) };
}

/** Começa uma rodada de uma pool de teste (cancelando a que estiver aberta, para uma falha não derrubar o resto). */
const start = (name) => { t.rcon('trivia stop'); return t.rcon('trivia start ' + name); };

/** Vitórias do jogador segundo o próprio /trivia stats. */
async function winsOf(bot) {
  bot.chatLog.length = 0;
  bot.chat('/trivia stats');
  await t.sleep(900);
  const m = joined(bot).match(/Wins: (\d+)/);
  return m ? Number(m[1]) : -1;
}

(async () => {
  const online = t.rcon('list');
  if (/TcA|TcB/.test(online)) throw new Error('Os jogadores de teste do trivia-chat já estão online');
  const { DatabaseSync } = require('node:sqlite');
  const db = new DatabaseSync(CFG + '/Database/serverbuddy.db');
  try { db.prepare('DELETE FROM sb_trivia_scores WHERE player_name IN (?, ?)').run('TcA', 'TcB'); } finally { db.close(); }

  const original = { ess: fs.readFileSync(ESS, 'utf8'), guard: fs.readFileSync(GUARD, 'utf8'), trivia: fs.readFileSync(TRI + '/config.json', 'utf8') };
  let a;
  let b;
  try {
    console.log('== preparação: formato do chat ligado, gerenciador de chat com repetição bloqueada');
    const ess = t.parseJson(original.ess);
    ess.styling.chat_format.enabled = true;
    ess.styling.chat_format.formats = { default: '<gray>[J]</gray> %player%<gray>:</gray> %message%' };
    write(ESS, ess);
    const guard = t.parseJson(original.guard);
    guard.enabled = true;
    guard.duplicates = { enabled: true, window_seconds: 60, max_repeats: 1, min_length: 4, similarity: 0.9, history_size: 8 };
    guard.flood.enabled = false;
    guard.ban.durations = ['4s', '8s', '12s'];
    write(GUARD, guard);
    const triviaCfg = t.parseJson(original.trivia);
    triviaCfg.hide_correct_answer = false;
    write(TRI + '/config.json', triviaCfg);

    const reward = [{ type: 'item', item: 'minecraft:iron_ingot', amount: 1 }];
    pool('t_chat_a', { enabled: true, weight: 0, answer_time: 25, questions: [{ question: 'Quanto é 5+5?', answers: ['dez', '10'], hint: false }], rewards: reward });
    pool('t_chat_pika', { enabled: true, weight: 0, answer_time: 25, questions: [{ question: 'Qual Pokémon elétrico é o mascote?', answers: ['pikachu'], hint: false }], rewards: reward });
    pool('t_chat_hide', { enabled: true, weight: 0, answer_time: 25, questions: [{ question: 'Quanto é 7+7?', answers: ['quatorze', '14'], hint: false }], rewards: reward });
    t.rcon('serverbuddy reload', 'trivia reload');
    await t.sleep(800);

    a = await t.join('TcA');
    b = await t.join('TcB');
    t.rcon('deop TcA', 'deop TcB', 'chatguard reset TcA', 'chatguard reset TcB', 'unmute TcB', 'clear TcA');
    await t.sleep(800);

    console.log('== o formato do chat está de pé (senão o resto do teste não prova nada)');
    let r = await say(a, 'bom dia pessoal', 1100, b);
    t.check('quem escreve vê a linha formatada do Essentials, não o "<nome>" do jogo', /\[J\] TcA: bom dia pessoal/.test(r.self), flat(r.self));
    t.check('o outro jogador também vê a linha formatada', /\[J\] TcA: bom dia pessoal/.test(r.others[0]), flat(r.others[0]));

    console.log('== resposta correta com o formato do chat ligado (a hipótese do relato)');
    a.chatLog.length = 0; b.chatLog.length = 0;
    start('t_chat_a');
    await t.sleep(900);
    t.check('a pergunta chegou aos dois', /Quanto é 5\+5/.test(joined(a)) && /Quanto é 5\+5/.test(joined(b)), flat(joined(a)));
    r = await say(a, 'dez', 1800, b);
    t.check('o vencedor recebe o aviso de vitória (formato ligado)', /acertou|got it/i.test(r.self), flat(r.self));
    t.check('o outro jogador vê quem venceu', /TcA acertou|TcA .*got it/i.test(r.others[0]), flat(r.others[0]));
    t.check('não sobra pergunta no ar', /No active question/.test(t.rcon('trivia info')), t.rcon('trivia info').replace(/\n/g, ' | '));
    t.check('TcA tem 1 vitória no ranking', (await winsOf(a)) === 1);

    console.log('== a mesma resposta já dita antes (gerenciador de chat bloqueia repetida)');
    r = await say(a, 'pikachu', 1100, b);
    t.check('"pikachu" dito sem pergunta passa normalmente', /\[J\] TcA: pikachu/.test(r.others[0]), flat(r.others[0]));
    start('t_chat_pika');
    await t.sleep(900);
    r = await say(a, 'pikachu', 1800, b);
    t.check('a resposta repetida ainda vale como resposta do trivia (o gerenciador não a come antes)', /acertou|got it/i.test(r.self), flat(r.self));
    t.check('e o gerenciador não dá aviso a quem acabou de acertar', !/Aviso|Warning/i.test(r.self), flat(r.self));
    t.check('TcA tem 2 vitórias', (await winsOf(a)) === 2);

    console.log('== mute: quem está mudo não vence escrevendo a resposta');
    t.rcon('mute TcB 60s teste');
    await t.sleep(500);
    start('t_chat_a');
    await t.sleep(900);
    r = await say(b, 'dez', 1500, a);
    t.check('a resposta do mudo não conta (a pergunta continua)', !/No active question/.test(t.rcon('trivia info')), t.rcon('trivia info').replace(/\n/g, ' | '));
    t.check('o jogador mudo não tem vitória', (await winsOf(b)) === 0);
    t.rcon('unmute TcB');
    await t.sleep(400);
    r = await say(b, '10', 1800, a);
    t.check('depois do /unmute a resposta vale', /No active question/.test(t.rcon('trivia info')) && (await winsOf(b)) === 1, flat(r.self));

    console.log('== chat da equipe: o que vai para a equipe não conta como resposta');
    t.rcon('op TcA');
    await t.sleep(800);
    start('t_chat_a');
    await t.sleep(900);
    await say(a, '/sc dez', 1200, b);
    t.check('a resposta dita no chat da equipe não vence', !/No active question/.test(t.rcon('trivia info')), t.rcon('trivia info').replace(/\n/g, ' | '));
    t.rcon('trivia stop');
    t.rcon('deop TcA');

    console.log('== hide_correct_answer: a resposta certa vence e não aparece no chat');
    triviaCfg.hide_correct_answer = true;
    write(TRI + '/config.json', triviaCfg);
    t.rcon('trivia reload');
    await t.sleep(500);
    start('t_chat_hide');
    await t.sleep(900);
    r = await say(a, 'quatorze', 1800, b);
    t.check('o vencedor ganha', /acertou|got it/i.test(r.self) && (await winsOf(a)) === 3, flat(r.self));
    t.check('o outro jogador não vê a resposta escrita', !/\[J\] TcA: quatorze/.test(r.others[0]), flat(r.others[0]));
    triviaCfg.hide_correct_answer = false;
    write(TRI + '/config.json', triviaCfg);
    t.rcon('trivia reload');

    console.log('== uma resposta errada continua sendo chat comum, com o formato');
    start('t_chat_a');
    await t.sleep(900);
    r = await say(b, 'sei lá, onze', 1200, a);
    t.check('a errada aparece formatada para todos', /\[J\] TcB: sei lá, onze/.test(r.others[0]) && /No active question/.test(t.rcon('trivia info')) === false, flat(r.others[0]));
    t.rcon('trivia stop');
  } finally {
    fs.writeFileSync(ESS, original.ess, 'utf8');
    fs.writeFileSync(GUARD, original.guard, 'utf8');
    fs.writeFileSync(TRI + '/config.json', original.trivia, 'utf8');
    for (const name of ['t_chat_a', 't_chat_pika', 't_chat_hide']) fs.rmSync(`${TRI}/pools/${name}.json`, { force: true });
    // deixa o ranking como estava (os outros testes de trivia contam vitórias)
    const cleanup = new DatabaseSync(CFG + '/Database/serverbuddy.db');
    try { cleanup.prepare('DELETE FROM sb_trivia_scores WHERE player_name IN (?, ?)').run('TcA', 'TcB'); } finally { cleanup.close(); }
    t.rcon('deop TcA', 'deop TcB', 'unmute TcB', 'trivia stop', 'serverbuddy reload', 'trivia reload', 'chatguard reset TcA', 'chatguard reset TcB');
    console.log('== fim (configs restauradas)');
    if (a) a.quit();
    if (b) b.quit();
    await t.sleep(500);
    process.exit(process.exitCode || 0);
  }
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
