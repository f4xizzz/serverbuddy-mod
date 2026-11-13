// Flan: entrada em claim por tag e por permissão (duas regras separadas), comandos /claimaccess e os dois botões no menu da claim.
// Precisa do Flan no servidor de teste (o build.gradle liga ele só no runServer).
const fs = require('fs');
const t = require('./lib.js');

const FLAN_DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Flan';
const joined = (bot) => bot.chatLog.join('\n');
const pos = (name) => { const m = t.rcon(`data get entity ${name} Pos`).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/); return m ? m.slice(1).map(Number) : [NaN, NaN, NaN]; };
const inside = (p) => p[0] >= 200 && p[0] <= 221 && p[2] >= 200 && p[2] <= 221;
async function say(bot, cmd, wait = 1000) { bot.chatLog.length = 0; bot.chat(cmd); await t.sleep(wait); return joined(bot); }
const flat = (s) => s.replace(/\n/g, ' | ');

(async () => {
  console.log('== arquivos');
  t.check('Flan/config.json criado e explica os botões novos', fs.existsSync(FLAN_DIR + '/config.json') && /botões novos/.test(fs.readFileSync(FLAN_DIR + '/config.json', 'utf8')));

  const owner = await t.join('FlOwner');
  const visitor = await t.join('FlVisitor');
  owner.physicsEnabled = false; visitor.physicsEnabled = false; // o bot não enxerga o terreno distante e cairia sem parar
  t.rcon('op FlOwner', 'deop FlVisitor', 'gamemode creative FlOwner', 'gamemode creative FlVisitor', 'tag FlVisitor remove vip', 'forceload add 190 190 235 235');
  t.rcon('tp FlOwner 210 83 210', 'tp FlVisitor 210 83 190');
  await t.sleep(4000);

  console.log('== criar a claim (como o dono)');
  let r = t.rcon('flan add 200 60 200 220 120 220 minecraft:overworld FlOwner');
  console.log('  ' + r.replace(/\n/g, ' | '));
  await t.sleep(1000);
  t.rcon('tp FlOwner 210 83 210');
  await t.sleep(2500);
  await say(owner, '/claimaccess clear perm', 600);
  await say(owner, '/claimaccess clear tag', 600);
  let out = await say(owner, '/claimaccess info', 1200);
  t.check('o dono está dentro de uma claim e ela ainda não tem regras', /Entrada na claim/.test(out) && /Sem regras/.test(out), flat(out));

  console.log('== sem regras, visitante entra e fica');
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(3500);
  t.check('visitante fica dentro da claim', inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));

  console.log('== regra por TAG');
  out = await say(owner, '/claimaccess add tag vip', 1200);
  t.check('o dono adiciona a regra de tag', /Regra vip adicionada/.test(out), flat(out));
  t.check('a regra foi gravada em restrictions.json', /"tags": \[\s*"vip"\s*\]/.test(fs.readFileSync(FLAN_DIR + '/restrictions.json', 'utf8')), fs.existsSync(FLAN_DIR + '/restrictions.json') ? '' : 'arquivo não existe');
  await t.sleep(4000);
  const p1 = pos('FlVisitor');
  t.check('o visitante sem a tag vip é levado para fora', !inside(p1), JSON.stringify(p1));
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(3500);
  t.check('teleportar de novo para dentro também é barrado', !inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));
  t.check('o dono continua dentro', inside(pos('FlOwner')), JSON.stringify(pos('FlOwner')));
  t.rcon('tag FlVisitor add vip');
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(3500);
  t.check('com a tag vip (do scoreboard) o visitante entra e fica', inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));

  console.log('== regra por PERMISSÃO (separada da de tag)');
  out = await say(owner, '/claimaccess add perm serverbuddy.teste.ninguem', 1200);
  t.check('o dono adiciona a regra de permissão', /Regra serverbuddy\.teste\.ninguem adicionada/.test(out), flat(out));
  await t.sleep(3000);
  t.check('com as duas regras entra quem passa em UMA (tag vip) e continua dentro', inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));
  out = await say(owner, '/claimaccess remove tag vip', 1200);
  t.check('o dono remove a regra de tag', /removida/.test(out), flat(out));
  await t.sleep(4000);
  t.check('só com a regra de permissão, a tag não basta e o visitante sai', !inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));
  t.check('o dono continua dentro', inside(pos('FlOwner')), JSON.stringify(pos('FlOwner')));
  out = await say(owner, '/claimaccess info', 1200);
  t.check('/claimaccess info lista a regra de permissão', /serverbuddy\.teste\.ninguem/.test(out), flat(out));

  console.log('== limites e erros');
  out = await say(owner, '/claimaccess add perm serverbuddy.teste.ninguem', 1000);
  t.check('regra repetida é recusada', /já está na lista/.test(out), flat(out));
  out = await say(visitor, '/claimaccess info', 1000);
  t.check('quem não pode editar a claim não mexe nas regras', /Unknown|Incorrect|incompleto|não está dentro|não pode editar/i.test(out), flat(out));

  console.log('== botões no menu do Flan');
  t.rcon('tp FlOwner 210 83 210');
  await t.sleep(2000);
  const menu = await t.openByCommand(owner, '/flan menu');
  const items = t.slots(menu);
  console.log('  menu do Flan: ' + items.join(' ; '));
  t.check('o menu do Flan tem os botões novos nos espaços 12 e 13', items.some((s) => /^12:name_tag/.test(s)) && items.some((s) => /^13:paper/.test(s)), items.join(' ; '));
  const gui = await t.click(owner, 12);
  const title = gui ? t.titleOf(gui) : '(sem janela)';
  console.log('  tela aberta: ' + title);
  t.check('clicar no botão de permissões abre a tela de edição', /Entrada por permissão/.test(title), title);
  const itemsPerm = gui ? t.slots(gui) : [];
  t.check('a tela mostra a regra de agora e as permissões prontas da config', itemsPerm.some((s) => /lime_dye/.test(s)) && itemsPerm.some((s) => /book/.test(s)), itemsPerm.join(' ; '));
  if (gui) owner.closeWindow(gui);
  await t.sleep(600);

  console.log('== limpar e voltar ao normal');
  out = await say(owner, '/claimaccess clear perm', 1000);
  t.check('/claimaccess clear apaga a lista', /Lista limpa/.test(out), flat(out));
  await t.sleep(1500);
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(3500);
  t.check('sem regras o visitante volta a poder ficar', inside(pos('FlVisitor')), JSON.stringify(pos('FlVisitor')));

  // ---------------------------------------------------------------- o que a claim dá a quem entra
  console.log('== dar uma tag ao entrar na claim');
  const tagsOf = (name) => t.rcon(`data get entity ${name} Tags`);
  t.rcon('tp FlVisitor 210 83 190');
  await t.sleep(2000);
  out = await say(visitor, '/claimaccess grant add tag em_arena', 1000);
  t.check('quem não é da equipe não vê nem usa o "grant"', /Unknown|Incorrect|incompleto/i.test(out), flat(out));
  out = await say(owner, '/claimaccess grant add tag em_arena', 1200);
  t.check('a equipe adiciona uma tag para dar a quem entra', /Regra em_arena adicionada/.test(out), flat(out));
  t.check('a regra foi gravada em restrictions.json (grant_tags)', /"grant_tags": \[\s*"em_arena"\s*\]/.test(fs.readFileSync(FLAN_DIR + '/restrictions.json', 'utf8')));
  t.check('fora da claim o visitante não tem a tag', !/em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(1800);
  t.check('ao entrar na claim o visitante ganha a tag em_arena', /em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.check('a tag leva a marca do mod (para tirar só o que ele deu)', /sb_claim_grant.em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  out = await say(visitor, '/home', 1000);
  t.check('junto com o bloqueio de comandos: com a tag, o /home é barrado dentro da claim', /Você não pode usar \/home dentro da arena/.test(out), flat(out));
  t.rcon('tp FlVisitor 210 83 190');
  await t.sleep(1800);
  t.check('ao sair da claim a tag e a marca somem', !/em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  out = await say(visitor, '/home', 1000);
  t.check('fora da claim o /home deixa de ser barrado', !/dentro da arena/.test(out), flat(out));

  console.log('== o mod só tira o que ele mesmo deu');
  t.rcon('tag FlVisitor add em_arena');
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(1800);
  t.check('quem já tinha a tag por fora não ganha a marca', !/sb_claim_grant.em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.rcon('tp FlVisitor 210 83 190');
  await t.sleep(1800);
  t.check('ao sair continua com a tag que já era dele', /em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.rcon('tag FlVisitor remove em_arena');
  t.rcon('tag FlVisitor add fantasma', 'tag FlVisitor add sb_claim_grant.fantasma');
  await t.sleep(1800);
  t.check('marca que sobrou de uma queda do servidor é limpa (tag e marca saem)', !/fantasma/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));

  console.log('== dar uma permissão ao entrar na claim');
  out = await say(owner, '/claimaccess grant add perm serverbuddy.admin.modules', 1200);
  t.check('a equipe adiciona uma permissão para dar a quem entra', /Regra serverbuddy\.admin\.modules adicionada/.test(out), flat(out));
  out = await say(visitor, '/sb modules', 1200);
  t.check('fora da claim o visitante (sem a permissão) não usa o /sb modules', /Unknown|Incorrect|incompleto/i.test(out), flat(out));
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(1800);
  out = await say(visitor, '/sb modules', 1500);
  t.check('dentro da claim a permissão dada deixa usar o /sb modules', /módulos/.test(out) && /clear/.test(out), flat(out));
  t.rcon('tp FlVisitor 210 83 190');
  await t.sleep(1800);
  out = await say(visitor, '/sb modules', 1200);
  t.check('ao sair a permissão some', /Unknown|Incorrect|incompleto/i.test(out), flat(out));
  out = await say(owner, '/claimaccess info', 1200);
  t.check('/claimaccess info lista o que a claim dá', /Ao entrar dá as tags: em_arena/.test(out) && /Ao entrar dá as permissões: serverbuddy\.admin\.modules/.test(out), flat(out));

  console.log('== botões "Ao entrar" no menu do Flan');
  t.rcon('tp FlOwner 210 83 210');
  await t.sleep(2000);
  const menu2 = await t.openByCommand(owner, '/flan menu');
  const items2 = t.slots(menu2);
  t.check('o menu do Flan tem os quatro botões (12 a 15)', items2.some((s) => /^12:name_tag/.test(s)) && items2.some((s) => /^13:paper/.test(s)) && items2.some((s) => /^14:golden_carrot/.test(s)) && items2.some((s) => /^15:experience_bottle/.test(s)), items2.join(' ; '));
  const guiTag = await t.click(owner, 15);
  const titleTag = guiTag ? t.titleOf(guiTag) : '(sem janela)';
  t.check('o botão "dar tag" abre a tela com a tag de agora', /Ao entrar: dar tag/.test(titleTag) && t.slots(guiTag).some((s) => /lime_dye "em_arena"/.test(s) || /lime_dye/.test(s)), titleTag + ' | ' + (guiTag ? t.slots(guiTag).join(' ; ') : ''));
  if (guiTag) owner.closeWindow(guiTag);
  await t.sleep(600);
  const menu3 = await t.openByCommand(owner, '/flan menu');
  const guiPerm = await t.click(owner, 14);
  const titlePerm = guiPerm ? t.titleOf(guiPerm) : '(sem janela)';
  t.check('o botão "dar permissão" abre a tela de permissões dadas', /Ao entrar: dar permissão/.test(titlePerm), titlePerm);
  if (guiPerm) owner.closeWindow(guiPerm);
  await t.sleep(600);

  console.log('== remove_on_leave = false: a tag dada fica para sempre');
  const flanCfg = t.readJson(FLAN_DIR + '/config.json');
  flanCfg.grants.remove_on_leave = false;
  fs.writeFileSync(FLAN_DIR + '/config.json', JSON.stringify(flanCfg, null, 2), 'utf8');
  t.rcon('claimaccess reload');
  t.rcon('tp FlVisitor 210 83 190', 'tag FlVisitor remove em_arena');
  await t.sleep(1800);
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(1800);
  t.check('com remove_on_leave=false a tag é dada sem a marca', /em_arena/.test(tagsOf('FlVisitor')) && !/sb_claim_grant\.em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.rcon('tp FlVisitor 210 83 190');
  await t.sleep(1800);
  t.check('e continua com ela depois de sair da claim', /em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));
  t.rcon('tag FlVisitor remove em_arena');
  flanCfg.grants.remove_on_leave = true;
  fs.writeFileSync(FLAN_DIR + '/config.json', JSON.stringify(flanCfg, null, 2), 'utf8');
  t.rcon('claimaccess reload');

  console.log('== limpar o que a claim dá');
  out = await say(owner, '/claimaccess grant clear tag', 1000);
  t.check('/claimaccess grant clear tag limpa a lista', /Lista limpa/.test(out), flat(out));
  out = await say(owner, '/claimaccess grant clear perm', 1000);
  t.check('/claimaccess grant clear perm limpa a lista', /Lista limpa/.test(out), flat(out));
  t.check('a claim não guarda mais nada em restrictions.json', !/grant_tags|grant_permissions/.test(fs.readFileSync(FLAN_DIR + '/restrictions.json', 'utf8').replace(/"grant_(tags|permissions)": \[\s*\]/g, '')));
  t.rcon('tp FlVisitor 210 83 205');
  await t.sleep(1800);
  t.check('sem nada para dar, quem entra não ganha tag', !/em_arena/.test(tagsOf('FlVisitor')), tagsOf('FlVisitor'));

  // limpeza: apaga a claim de teste
  await say(owner, '/flan delete', 1200);
  await say(owner, '/flan confirm', 1200);
  t.rcon('forceload remove 190 190 235 235');
  owner.quit(); visitor.quit();
  await t.sleep(600);
  console.log(process.exitCode ? '\nTEM FALHAS' : '\nTUDO OK');
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO: ' + e.stack); process.exit(1); });


