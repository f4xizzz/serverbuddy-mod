// Trivia: arquivos criados, comandos, rodada completa com resposta no chat, prêmios, dicas, tempo esgotado, silenciar,
// sequência de vitórias com dois jogadores e os geradores de perguntas do Cobblemon.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Trivia';
const stripComments = (s) => s.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n');
const pool = (name, obj) => fs.writeFileSync(`${DIR}/pools/${name}.json`, JSON.stringify(obj, null, 2), 'utf8');
const joined = (bot) => bot.chatLog.join('\n');
const acceptedAnswers = () => {
  const out = t.rcon('trivia info');
  const m = out.match(/Accepted answers: (.*)/);
  return { out, answers: m ? m[1].trim().split(' / ') : [] };
};

(async () => {
  // These two fixture players must start without scores, even when the suite is repeated.
  const online = t.rcon('list');
  if (/TrBotA|TrBotB/.test(online)) throw new Error('Trivia fixture players are already online');
  const { DatabaseSync } = require('node:sqlite');
  const db = new DatabaseSync(DIR + '/../Database/serverbuddy.db');
  try { db.prepare('DELETE FROM sb_trivia_scores WHERE player_name IN (?, ?)').run('TrBotA', 'TrBotB'); }
  finally { db.close(); }
  console.log('== arquivos criados na primeira vez');
  t.check('Trivia/config.json existe com comentários', fs.existsSync(DIR + '/config.json') && /^\s*\/\/.*perguntas no chat/m.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('pool geral.json criada', fs.existsSync(DIR + '/pools/geral.json'));
  t.check('pool pokemon.json criada (Cobblemon instalado)', fs.existsSync(DIR + '/pools/pokemon.json'));
  const geral = fs.readFileSync(DIR + '/pools/geral.json', 'utf8');
  t.check('as perguntas da pool ficam uma por linha', /\{ "question": "Qual mob explode/.test(geral));

  // pools de teste (peso 0: nunca saem sozinhas, só com /trivia start <pool>)
  pool('t_fixed', { enabled: true, weight: 0, answer_time: 20, questions: [{ question: 'Quanto é 2+2?', answers: ['4', 'quatro'], hint: false }], rewards: [{ type: 'item', item: 'minecraft:iron_ingot', amount: 3 }], extra_rewards: [{ type: 'message', text: '<aqua>extra-ok' }] });
  pool('t_timeout', { enabled: true, weight: 0, answer_time: 5, questions: [{ question: 'Pergunta sem resposta possivel', answers: ['xyzzy'] }], rewards: [] });
  pool('t_hint', { enabled: true, weight: 0, answer_time: 10, questions: [{ question: 'Qual Pokémon elétrico é o mascote?', answers: ['pikachu'] }], rewards: [] });
  pool('t_fixed2', { enabled: true, weight: 0, answer_time: 20, questions: [{ question: 'Quanto é 3+3?', answers: ['6'], hint: false }], rewards: [{ type: 'item', item: 'minecraft:iron_ingot', amount: 1 }] });
  const gens = ['dex_number_to_species', 'species_to_dex_number', 'description_to_species', 'description_to_move', 'species_to_type', 'species_to_ability', 'species_to_egg_group', 'move_to_type', 'unscramble_species', 'unscramble_move', 'equation', 'typing', 'unscramble'];
  pool('t_cob', { enabled: true, weight: 0, answer_time: 20, questions: gens.map((g) => ({ generator: g })), rewards: [] });
  let r = t.rcon('trivia reload');
  console.log(r);
  t.check('reload acha as pools de teste', /active pool\(s\)/.test(r));
  console.log(t.rcon('trivia pools'));

  const a = await t.join('TrBotA');
  const b = await t.join('TrBotB');
  t.rcon('clear TrBotA', 'clear TrBotB');

  console.log('== comandos dos jogadores');
  a.chatLog.length = 0;
  a.chat('/trivia');
  await t.sleep(900);
  t.check('/trivia mostra a próxima pergunta e as vitórias', /próxima pergunta/.test(joined(a)) && /Your wins/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  a.chatLog.length = 0;
  a.chat('/trivia top');
  await t.sleep(700);
  t.check('/trivia top sem ranking avisa', /Nobody has won|Trivia rankings/.test(joined(a)), joined(a).replace(/\n/g, ' | '));

  console.log('== rodada completa');
  a.chatLog.length = 0; b.chatLog.length = 0;
  r = t.rcon('trivia start t_fixed');
  console.log(r);
  await t.sleep(900);
  t.check('os dois jogadores recebem a pergunta', /Quanto é 2\+2/.test(joined(a)) && /Quanto é 2\+2/.test(joined(b)), joined(a).replace(/\n/g, ' | '));
  t.check('/trivia start de novo é recusado enquanto há pergunta', /A question is already active/.test(t.rcon('trivia start t_fixed')));
  let info = acceptedAnswers();
  t.check('/trivia info mostra as respostas aceitas', info.answers.includes('4'), info.out.replace(/\n/g, ' | '));
  a.chat('quatro');
  await t.sleep(1800);
  console.log('  [A] ' + joined(a).split('\n').slice(-6).join(' | '));
  t.check('o vencedor recebe a mensagem de vitória e o prêmio no chat', /Você acertou/.test(joined(a)) && /Prêmio/.test(joined(a)) && /extra-ok/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  t.check('o outro jogador vê quem venceu', /TrBotA acertou/.test(joined(b)), joined(b).replace(/\n/g, ' | '));
  t.check('o prêmio (3 barras de ferro) entrou no inventário', t.count(a, 'iron_ingot') === 3, 'count=' + t.count(a, 'iron_ingot'));
  t.check('não sobra pergunta no ar', /No active question/.test(t.rcon('trivia info')));

  console.log('== ranking e sequência de vitórias');
  a.chatLog.length = 0;
  a.chat('/trivia stats');
  await t.sleep(900);
  t.check('stats do jogador A: 1 vitória, sequência 1', /Wins: 1\b/.test(joined(a)) && /Streak: 1\b/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  t.rcon('trivia start t_fixed2');
  await t.sleep(600);
  a.chat('6');
  await t.sleep(1500);
  a.chatLog.length = 0;
  a.chat('/trivia stats');
  await t.sleep(900);
  t.check('A venceu duas seguidas: sequência 2', /Wins: 2\b/.test(joined(a)) && /Streak: 2\b/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  t.rcon('trivia start t_fixed2');
  await t.sleep(600);
  b.chat('6');
  await t.sleep(1500);
  a.chatLog.length = 0;
  a.chat('/trivia stats');
  await t.sleep(900);
  t.check('B venceu: a sequência de A zera (melhor continua 2)', /Streak: 0\b/.test(joined(a)) && /best streak: 2\b/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  a.chatLog.length = 0;
  a.chat('/trivia top');
  await t.sleep(900);
  t.check('/trivia top lista A em primeiro com 2 vitórias', /#1 TrBotA.*2 win/.test(joined(a).replace(/\n/g, ' ')) && /TrBotB/.test(joined(a)), joined(a).replace(/\n/g, ' | '));

  console.log('== dicas');
  a.chatLog.length = 0;
  t.rcon('trivia start t_hint');
  await t.sleep(11500);
  const hints = (joined(a).match(/dica:/g) || []).length;
  console.log('  [A] ' + joined(a).split('\n').filter((l) => /dica|Ninguém/.test(l)).join(' | '));
  t.check('duas dicas durante a pergunta (50% e 80%)', hints === 2, 'dicas=' + hints);
  t.check('a segunda dica mostra mais letras que a primeira', (() => {
    const lines = joined(a).split('\n').filter((l) => /dica:/.test(l)).map((l) => l.split('dica:')[1].trim());
    return lines.length === 2 && (lines[0].match(/_/g) || []).length > (lines[1].match(/_/g) || []).length;
  })());
  t.check('tempo esgotado: aviso com a resposta', /Ninguém acertou a tempo.*pikachu/.test(joined(a).replace(/\n/g, ' ')), joined(a).slice(-200).replace(/\n/g, ' | '));

  console.log('== tempo esgotado');
  a.chatLog.length = 0;
  t.rcon('trivia start t_timeout');
  await t.sleep(7500);
  t.check('aviso de tempo esgotado depois de 5s', /Ninguém acertou a tempo/.test(joined(a)) && /xyzzy/.test(joined(a)), joined(a).replace(/\n/g, ' | '));

  console.log('== silenciar só para mim');
  a.chatLog.length = 0; b.chatLog.length = 0;
  a.chat('/trivia toggle');
  await t.sleep(900);
  t.check('toggle avisa que silenciou', /silenciada/.test(joined(a)), joined(a).replace(/\n/g, ' | '));
  t.rcon('trivia start t_fixed2');
  await t.sleep(1200);
  t.check('quem silenciou não recebe a pergunta', !/Quanto é 3\+3/.test(joined(a)));
  t.check('quem não silenciou recebe', /Quanto é 3\+3/.test(joined(b)));
  t.check('/trivia stop cancela a pergunta', /cancelled/.test(t.rcon('trivia stop')));
  a.chat('/trivia toggle');
  await t.sleep(900);
  t.check('toggle de novo liga a trivia', /ligada/.test(joined(a)), joined(a).slice(-120).replace(/\n/g, ' | '));

  console.log('== geradores (incluindo os do Cobblemon): 3 perguntas de cada');
  const seen = {};
  for (let i = 0; i < gens.length * 3; i++) {
    const out = t.rcon('trivia start t_cob');
    if (/Não deu|Já tem/.test(out)) { console.log('  ' + out.replace(/\n/g, ' ')); }
    info = acceptedAnswers();
    const q = (info.out.match(/Question: (.*)/) || [])[1] || '(sem pergunta)';
    seen[q] = info.answers;
    t.rcon('trivia stop');
  }
  const questions = Object.entries(seen);
  for (const [q, ans] of questions.slice(0, 40)) console.log('  ? ' + q.slice(0, 110) + '  =>  ' + ans.slice(0, 3).join(' / '));
  t.check('todas as perguntas geradas têm texto e pelo menos uma resposta', questions.length > 0 && questions.every(([q, ans]) => q !== '(sem pergunta)' && ans.length > 0 && ans.every((x) => x.length > 0)));
  t.check('geradas pelo menos 20 perguntas diferentes', questions.length >= 20, 'diferentes=' + questions.length);
  t.check('perguntas do Cobblemon apareceram (Pokédex/tipo/golpe)', questions.some(([q]) => /Pokédex|type|move|Pokémon|ability|egg/i.test(q)));

  console.log('== arquivos');
  const cfg = JSON.parse(stripComments(fs.readFileSync(DIR + '/config.json', 'utf8')));
  t.check('config.json tem interval_time e os bônus de exemplo', cfg.interval_time && cfg.fast_bonus.enabled === true && Object.keys(cfg.streak_bonus).length === 2);

  a.quit(); b.quit();
  await t.sleep(500);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
