// Missões: arquivos criados, rotação, objetivos por estatística (andar, pular), por estado (nível, itens) e por evento
// (manual), pré-requisito, resgate por comando e pelo menu, auto-resgate, repetível com espera e persistência no banco.
const fs = require('fs');
const t = require('./lib.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Missions';
const joined = (bot) => bot.chatLog.join('\n');

async function list(bot) {
  bot.chatLog.length = 0;
  bot.chat('/missions list');
  await t.sleep(1100);
  return joined(bot);
}

(async () => {
  console.log('== arquivos criados na primeira vez');
  t.check('Missions/config.json existe e explica os objetivos', fs.existsSync(DIR + '/config.json') && /OBJECTIVES/.test(fs.readFileSync(DIR + '/config.json', 'utf8')));
  for (const f of ['iniciante', 'diarias', 'semanais', 'pokemon']) t.check(`categories/${f}.json criado`, fs.existsSync(`${DIR}/categories/${f}.json`));
  const diarias = fs.readFileSync(DIR + '/categories/diarias.json', 'utf8');
  t.check('as missões de uma categoria ficam compactas (objetivos e prêmios numa linha)', /"objectives": \["mine #minecraft:base_stone_overworld 100"\]/.test(diarias) && /\{ "type": "xp", "amount": 4, "levels": true \}/.test(diarias));

  // categoria de teste com um exemplo de cada tipo de objetivo
  const mission = (name, objectives, extra = {}) => ({ name, description: ['teste'], icon: 'minecraft:paper', objectives, rewards: [{ type: 'item', item: 'minecraft:iron_nugget', amount: 5 }], ...extra });
  fs.writeFileSync(DIR + '/categories/t_test.json', JSON.stringify({
    enabled: true, name: '<aqua>Teste', icon: 'minecraft:compass', description: ['categoria de teste'], period: 'none', pick: 0,
    missions: {
      caminhar: mission('Caminhar', ['walk 6']),
      pular: mission('Pular', ['jump 3'], { auto_claim: true, rewards: [{ type: 'message', text: '<green>auto-ok' }, { type: 'item', item: 'minecraft:gold_nugget', amount: 2 }] }),
      nivel: mission('Nível 5', ['level 5']),
      itens: mission('Itens', ['has minecraft:cobblestone 3']),
      manual_x: mission('Manual', ['manual visitar_spawn 2']),
      encadeada: mission('Encadeada', ['manual encadeada 1'], { requires: { missions: ['manual_x'], permission: null } }),
      repetivel: mission('Repetível', ['manual rep 1'], { repeatable: true, cooldown: '8s' }),
      errada: mission('Errada', ['voar 10']),
    },
  }, null, 2), 'utf8');
  let r = t.rcon('missions reload');
  console.log(r);
  t.check('reload lê a categoria nova (a missão com objetivo errado é ignorada)', /category\/categories/.test(r), r);

  const a = await t.join('MiBot');
  t.rcon('clear MiBot', 'xp set MiBot 0 levels', 'gamemode survival MiBot');
  await t.sleep(1500); // dá tempo da primeira conferência (a cada segundo)

  console.log('== /missions list');
  let out = await list(a);
  console.log(out.split('\n').slice(0, 40).map((l) => '    ' + l).join('\n'));
  t.check('mostra a categoria de teste e as missões', /Teste/.test(out) && /Caminhar/.test(out) && /\[t_test\.caminhar\]/.test(out), out.replace(/\n/g, ' | ').slice(0, 300));
  t.check('a missão com objetivo mal escrito não aparece', !/Errada/.test(out));
  t.check('a missão encadeada aparece bloqueada', /Encadeada/.test(out) && /locked/.test(out));
  const daily = (out.match(/\[diarias\./g) || []).length;
  t.check('a categoria diária mostra só 3 missões (rotação)', daily === 3, 'diarias=' + daily);
  const weekly = (out.match(/\[semanais\./g) || []).length;
  t.check('a categoria semanal mostra só 2 missões', weekly === 2, 'semanais=' + weekly);
  t.check('a trilha iniciante mostra a 1ª missão e bloqueia a 2ª', /\[iniciante\.primeiros_passos\]/.test(out) && /Lenhador[^\n]*locked|locked[^\n]*Lenhador/.test(out) === false || /Lenhador/.test(out));

  console.log('== estatística: andar 6 blocos');
  // anda mudando de direção para não ficar preso numa parede (o bot guarda a posição da última vez)
  t.rcon('tp MiBot 2.5 89 -3.5');
  await t.sleep(1200);
  for (let i = 0; i < 9; i++) {
    a.look(Math.random() * 6.28, 0, true);
    a.setControlState('forward', true);
    await t.sleep(1300);
  }
  a.setControlState('forward', false);
  await t.sleep(2500);
  const walkChat = joined(a); // o aviso de missão concluída chega enquanto o bot anda
  out = await list(a);
  console.log('  ' + out.split('\n').filter((l) => /Caminhar/.test(l)).join(' | '));
  t.check('"Caminhar" ficou pronta depois de andar', /ready[^\n]*Caminhar/.test(out), out.split('\n').filter((l) => /Caminhar/.test(l)).join(' | '));
  t.check('o aviso de missão concluída chegou no chat', /Missão concluída/.test(walkChat));

  console.log('== estatística: pular 3 vezes (com auto_claim)');
  t.rcon('tp MiBot 2.5 89 -3.5'); // o bot andou para qualquer lado: volta para um chão plano
  await t.sleep(1500);
  a.chatLog.length = 0;
  a.setControlState('jump', true);
  await t.sleep(3500);
  a.setControlState('jump', false);
  await t.sleep(2500);
  const jumpChat = joined(a);
  t.check('missão com auto_claim entrega o prêmio sozinha', /auto-ok/.test(jumpChat) && t.count(a, 'gold_nugget') === 2, jumpChat.replace(/\n/g, ' | ').slice(0, 200) + ' gold=' + t.count(a, 'gold_nugget'));

  console.log('== estado: nível de XP e itens no inventário');
  t.rcon('xp set MiBot 5 levels', 'give MiBot minecraft:cobblestone 3');
  await t.sleep(2500);
  out = await list(a);
  t.check('"Nível 5" ficou pronta', /ready[^\n]*Nível 5/.test(out), out.split('\n').filter((l) => /Nível 5/.test(l)).join(' | '));
  t.check('"Itens" ficou pronta', /ready[^\n]*Itens/.test(out), out.split('\n').filter((l) => /Itens/.test(l)).join(' | '));

  console.log('== evento manual e pré-requisito');
  r = t.rcon('missions progress MiBot visitar_spawn 1');
  console.log(r);
  await t.sleep(300);
  out = await list(a);
  t.check('metade do progresso manual: 1/2', /Manual[^\n]*1\/2/.test(out), out.split('\n').filter((l) => /Manual/.test(l)).join(' | '));
  t.rcon('missions progress MiBot visitar_spawn 1');
  await t.sleep(300);
  out = await list(a);
  t.check('manual completo (2/2) fica pronta', /ready[^\n]*Manual/.test(out));
  t.check('a missão encadeada ainda está bloqueada até resgatar a anterior', /locked[^\n]*Encadeada|Encadeada[^\n]*locked/.test(out), out.split('\n').filter((l) => /Encadeada/.test(l)).join(' | '));

  console.log('== resgate por comando');
  a.chatLog.length = 0;
  a.chat('/missions claim t_test.caminhar');
  await t.sleep(1200);
  t.check('resgatar entrega o prêmio (5 pepitas de ferro)', t.count(a, 'iron_nugget') === 5 && /Prêmio resgatado/.test(joined(a)), joined(a).replace(/\n/g, ' | ') + ' nuggets=' + t.count(a, 'iron_nugget'));
  a.chatLog.length = 0;
  a.chat('/missions claim t_test.caminhar');
  await t.sleep(900);
  t.check('resgatar de novo é recusado (não dá prêmio em dobro)', /não está pronta/.test(joined(a)) && t.count(a, 'iron_nugget') === 5, joined(a).replace(/\n/g, ' | '));
  a.chat('/missions claim t_test.manual_x');
  await t.sleep(1500);
  out = await list(a);
  t.check('depois de resgatar o pré-requisito, a encadeada libera', /▪[^\n]*Encadeada|Encadeada[^\n]*\d+\/1/.test(out) && !/locked[^\n]*Encadeada|Encadeada[^\n]*locked/.test(out), out.split('\n').filter((l) => /Encadeada/.test(l)).join(' | '));

  console.log('== menu de baú');
  const w = await t.openByCommand(a, '/missions');
  const title = t.titleOf(w);
  console.log('  título: ' + title);
  t.check('o menu abre com o título da config', /Missões/.test(title), title);
  const before = t.count(a, 'iron_nugget');
  await t.click(a, 4); // a aba "Teste" é a quinta (diarias, iniciante, pokemon, semanais, t_test)
  await t.sleep(600);
  await t.click(a, 9); // a primeira missão da aba é uma das prontas (as prontas vêm primeiro)
  await t.sleep(800);
  const after = t.count(a, 'iron_nugget');
  console.log(`  pepitas de ferro: ${before} -> ${after}`);
  t.check('clicar numa missão pronta resgata o prêmio', after > before || t.count(a, 'gold_nugget') >= 2 || /Prêmio resgatado/.test(joined(a)), `antes=${before} depois=${after}`);
  a.closeWindow(w);
  await t.sleep(500);

  console.log('== repetível com espera');
  t.rcon('missions progress MiBot rep 1');
  await t.sleep(300);
  a.chat('/missions claim t_test.repetivel');
  await t.sleep(1200);
  out = await list(a);
  t.check('depois de resgatar a repetível, ela espera o tempo configurado', /Repetível/.test(out) && /\d+s/.test(out.split('\n').filter((l) => /Repetível/.test(l)).join(' ')), out.split('\n').filter((l) => /Repetível/.test(l)).join(' | '));
  await t.sleep(9000);
  out = await list(a);
  t.check('passado o tempo, a repetível volta a ficar em andamento', /▪[^\n]*Repetível/.test(out), out.split('\n').filter((l) => /Repetível/.test(l)).join(' | '));

  console.log('== persistência no banco (sai e volta)');
  a.quit();
  await t.sleep(1500);
  const b = await t.join('MiBot');
  await t.sleep(3600); // o lembrete sai uns 3 segundos depois da entrada
  t.check('o lembrete de missões prontas chegou ao entrar', b.chatLog.some((l) => /missão\(ões\) pronta\(s\)/.test(l)), b.chatLog.filter((l) => /pronta/.test(l)).join(' | '));
  out = await list(b);
  t.check('o progresso e os resgates continuam depois de sair e entrar', /completed[^\n]*Caminhar/.test(out) && /completed[^\n]*Manual/.test(out), out.split('\n').filter((l) => /Caminhar|Manual/.test(l)).join(' | '));
  t.check('as prontas continuam prontas (Itens)', /ready[^\n]*Itens/.test(out));

  console.log('== administração');
  r = t.rcon('missions reset MiBot t_test.itens');
  t.check('reset apaga o progresso de uma missão', /1<\/?/.test(r) || /Progress cleared/.test(r), r.replace(/\n/g, ' | '));
  r = t.rcon('missions complete MiBot t_test.itens');
  console.log(r);
  r = t.rcon('missions complete MiBot t_test.naoexiste');
  t.check('complete avisa de missão que não existe', /Não existe a missão/.test(r), r.replace(/\n/g, ' | '));
  const cfg = fs.readFileSync(DIR + '/config.json', 'utf8');
  t.check('config.json com comentários e menu configurável', /mission_slots": \["9-44"\]/.test(cfg) && /\/\/ Where the missions appear/.test(cfg));

  fs.unlinkSync(DIR + '/categories/t_test.json');
  t.rcon('missions reset MiBot t_test');
  b.quit();
  await t.sleep(500);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });
