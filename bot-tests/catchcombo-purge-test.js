// /sb purgedisplays e o holograma do ranking do Catch Combo. O holograma é a ENTIDADE (um TextDisplay) mais uma LINHA na tabela
// catch_combo_leaderboard_display, e o ticker (1 vez por segundo) recria a entidade quando ela some. Antes do conserto o purgedisplays só
// apagava a entidade e o holograma voltava em até 1 s; agora apaga também os registros que estão no raio.
//
// COM Cobblemon: cria 2 hologramas a 400 blocos um do outro (/catchcombo leaderboard), limpa só o de perto e confere a entidade, a linha do
// banco, que o de longe fica, que nada renasce depois de uns segundos e que criar de novo ainda funciona.
// SEM Cobblemon (gradlew runServer -PsemCobblemon): o /sb purgedisplays continua apagando as entidades de texto, não fala de ranking e
// não suja o log.
// O banco é lido direto do arquivo SQLite do servidor de teste (só leitura), então rode no mesmo PC do `gradlew runServer`.
const fs = require('fs');
const { DatabaseSync } = require('node:sqlite');
const t = require('./lib.js');

const RUN = 'D:/mods/ServerBuddy/run';
const DB = RUN + '/config/ServerBuddy/Database/serverbuddy.db';
const LOG = RUN + '/logs/latest.log';
const A = { x: 300.5, y: 250, z: 300.5 }; // lá em cima, acima de qualquer relevo, sobre uma plataforma de pedra que o teste põe e tira
const B = { x: 300.5, y: 250, z: 700.5 }; // 400 blocos de A: fora do raio máximo do comando (256)
const NAME = 'PgBot' + (Date.now() % 100000);
// o que no log seria culpa do ranking ou do purge (erros de outros assuntos, como chunks antigos de estruturas, não contam)
const LOG_PROBLEM = /Could not (load|save|delete) leaderboard display|catch_combo_leaderboard|purgedisplays[^\n]*(Exception|ERROR)|CatchComboLeaderboard[^\n]*Exception/i;

/** As linhas do ranking perto de um ponto (lê o SQLite do servidor, só leitura; tenta de novo se o servidor estiver gravando). */
async function rows(p) {
  for (let attempt = 0; ; attempt++) {
    try {
      const db = new DatabaseSync(DB, { readOnly: true });
      try {
        return db.prepare("SELECT display_id, x, y, z FROM catch_combo_leaderboard_display WHERE dimension = 'minecraft:overworld' AND abs(x - ?) < 4 AND abs(z - ?) < 4").all(p.x, p.z);
      } finally {
        db.close();
      }
    } catch (e) {
      if (attempt >= 8) throw e;
      await t.sleep(250);
    }
  }
}

/** Quantos TextDisplay há perto do ponto (o console conta as entidades; o chunk está sempre carregado por causa do forceload). */
function texts(p) {
  const out = t.rcon(`execute positioned ${p.x} ${p.y} ${p.z} if entity @e[type=minecraft:text_display,distance=..4]`);
  const m = out.match(/count:\s*(\d+)/);
  if (m) return Number(m[1]);
  return /Test passed/i.test(out) ? 1 : 0;
}

/** As linhas que o servidor escreveu no log desde o começo do teste. */
function logSince(offset) {
  const text = fs.readFileSync(LOG, 'utf8');
  return text.slice(Math.min(offset, text.length));
}

(async () => {
  const logStart = fs.statSync(LOG).size;
  const bot = await t.join(NAME);
  t.rcon(`op ${NAME}`);
  const withCobblemon = !/Unknown or incomplete/i.test(t.rcon('catchcombo info'));
  console.log(`== ${withCobblemon ? 'COM' : 'SEM'} Cobblemon (jogador ${NAME})`);

  /** Leva o jogador até o ponto e o deixa em pé numa plataforma de pedra 3x3, para a posição ser sempre a mesma (a plataforma vem antes do jogador). */
  async function standAt(p) {
    const x = Math.floor(p.x);
    const z = Math.floor(p.z);
    for (let attempt = 0; attempt < 30; attempt++) {
      const out = t.rcon(`fill ${x - 1} ${p.y - 1} ${z - 1} ${x + 1} ${p.y - 1} ${z + 1} minecraft:stone`);
      if (!/not loaded/i.test(out)) break; // o chunk do forceload ainda está carregando
      await t.sleep(500);
    }
    t.rcon(`fill ${x - 1} ${p.y} ${z - 1} ${x + 1} ${p.y + 3} ${z + 1} minecraft:air`);
    t.rcon(`tp ${NAME} ${p.x} ${p.y} ${p.z}`);
    await t.sleep(1800);
  }
  /** Roda um comando como o jogador e devolve o que ele recebeu no chat. */
  async function say(command, wait = 1500) {
    bot.chatLog.length = 0;
    bot.chat(command);
    await t.sleep(wait);
    return bot.chatLog.join(' | ');
  }

  try {
    t.rcon('forceload add 300 300', 'forceload add 300 700');
    await t.sleep(800);

    if (!withCobblemon) {
      await standAt(A);
      t.rcon(`summon minecraft:text_display ${A.x} ${A.y} ${A.z} {text:'"um"'}`, `summon minecraft:text_display ${A.x + 1} ${A.y} ${A.z} {text:'"dois"'}`);
      await t.sleep(500);
      t.check('duas entidades de texto no lugar', texts(A) === 2, String(texts(A)));
      const out = await say('/sb purgedisplays');
      t.check('apaga as duas e avisa em inglês', /Removed 2 text displays within 32 blocks/.test(out), out);
      t.check('sem Cobblemon, não fala de ranking do Catch Combo', !/Catch Combo|leaderboard/i.test(out), out);
      t.check('as entidades sumiram', texts(A) === 0, String(texts(A)));
      await t.sleep(3000);
      const log = logSince(logStart);
      t.check('o log não tem erro do ranking nem do purge', !LOG_PROBLEM.test(log), (log.match(new RegExp('.*(' + LOG_PROBLEM.source + ').*', 'i')) || [''])[0].slice(0, 160));
      return;
    }

    // ---- estado limpo antes de começar (sobra de uma rodada que falhou)
    for (const [label, p] of [['A', A], ['B', B]]) {
      if ((await rows(p)).length || texts(p)) {
        await standAt(p);
        await say('/sb purgedisplays');
        await t.sleep(1500);
      }
      t.check(`começa sem holograma em ${label}`, (await rows(p)).length === 0 && texts(p) === 0, `${(await rows(p)).length} linhas, ${texts(p)} entidades`);
    }

    // ---- dois hologramas longe um do outro
    console.log('== cria dois hologramas a 400 blocos um do outro');
    await standAt(A);
    let out = await say('/catchcombo leaderboard n');
    t.check('o holograma de A foi criado', /leaderboard created/i.test(out), out);
    await standAt(B);
    out = await say('/catchcombo leaderboard s');
    t.check('o holograma de B foi criado', /leaderboard created/i.test(out), out);
    await t.sleep(3000); // o ticker passa umas vezes: não pode duplicar
    t.check('exatamente 1 TextDisplay em A e 1 em B', texts(A) === 1 && texts(B) === 1, `A=${texts(A)} B=${texts(B)}`);
    t.check('uma linha no banco para cada um', (await rows(A)).length === 1 && (await rows(B)).length === 1, `A=${(await rows(A)).length} B=${(await rows(B)).length}`);

    // ---- limpa só o de perto
    console.log('== /sb purgedisplays perto de A (B está a 400 blocos)');
    await standAt(A);
    out = await say('/sb purgedisplays');
    t.check('avisa que apagou 1 entidade e 1 registro, em inglês', /Removed 1 text displays within 32 blocks/.test(out) && /deleted 1 Catch Combo leaderboard hologram record/i.test(out), out);
    t.check('na hora: a entidade de A sumiu', texts(A) === 0, String(texts(A)));
    t.check('na hora: a linha de A saiu do banco', (await rows(A)).length === 0, String((await rows(A)).length));
    await t.sleep(8000); // ticker de 1 s + recarga do cache de 5 s: antes do conserto o holograma voltava aqui
    t.check('8 s depois: o holograma de A NÃO voltou', texts(A) === 0, String(texts(A)));
    t.check('8 s depois: a linha de A continua fora do banco (o ticker não a gravou de novo)', (await rows(A)).length === 0, String((await rows(A)).length));
    t.check('o holograma de B continua lá, com a linha', texts(B) === 1 && (await rows(B)).length === 1, `B=${texts(B)} linhas=${(await rows(B)).length}`);
    await t.sleep(4000);
    t.check('mais 4 s depois: A continua sem holograma e B continua com o dele', texts(A) === 0 && (await rows(A)).length === 0 && texts(B) === 1, `A=${texts(A)} B=${texts(B)}`);

    // ---- criar de novo ainda funciona
    console.log('== criar de novo no mesmo lugar');
    out = await say('/catchcombo leaderboard e');
    t.check('o holograma de A foi criado de novo', /leaderboard created/i.test(out), out);
    await t.sleep(3000);
    t.check('1 TextDisplay e 1 linha em A', texts(A) === 1 && (await rows(A)).length === 1, `A=${texts(A)} linhas=${(await rows(A)).length}`);
  } finally {
    // ---- limpeza: tira os hologramas de teste (com o próprio comando) e o que o teste colocou no mundo
    if (withCobblemon) {
      for (const p of [A, B]) {
        await standAt(p);
        await say('/sb purgedisplays', 1200);
      }
      await t.sleep(1500);
      t.check('limpeza: nenhuma linha nem entidade sobrou', (await rows(A)).length === 0 && (await rows(B)).length === 0 && texts(A) === 0 && texts(B) === 0, `linhas A=${(await rows(A)).length} B=${(await rows(B)).length}, entidades A=${texts(A)} B=${texts(B)}`);
    }
    t.rcon(`tp ${NAME} 100.5 89 100.5`);
    for (const p of [A, B]) t.rcon(`fill ${Math.floor(p.x) - 1} ${p.y - 1} ${Math.floor(p.z) - 1} ${Math.floor(p.x) + 1} ${p.y - 1} ${Math.floor(p.z) + 1} minecraft:air`);
    t.rcon('forceload remove 300 300', 'forceload remove 300 700', `deop ${NAME}`);
    bot.quit();
    await t.sleep(600);
  }
  const log = logSince(logStart);
  t.check('o log do servidor não tem erro do ranking nem do purge durante o teste', !LOG_PROBLEM.test(log), (log.match(new RegExp('.*(' + LOG_PROBLEM.source + ').*', 'i')) || [''])[0].slice(0, 200));
})().then(() => process.exit(process.exitCode || 0)).catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
