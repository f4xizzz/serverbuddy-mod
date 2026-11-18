// Os grupos do LuckPerms no painel (plano Pro) contra o LuckPerms de verdade: criar e apagar grupo, peso, nome, prefixo e sufixo, herança (sem ciclo),
// permissões (sim/não, com validade e contexto), membros, as confirmações das ações perigosas e a auditoria. Confere o resultado lendo o
// LuckPerms de novo pela API depois de cada mudança (o console do LuckPerms não devolve texto por RCON). Precisa do servidor com `gradlew runServer -PcomLuckPerms` (sem ele o teste só avisa e sai).
// Apaga tudo o que cria. SÓ contra um site local (veja dash-rpc.js).
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const SUFFIX = String(Date.now() % 100000);
const GROUP = 'dashgrp' + SUFFIX;
const OTHER = 'dashoth' + SUFFIX;
const groupOf = async (dash, name) => (await dash.rpc('luckperms.group', { name })).result;

(async () => {
  const dash = await connect();
  let bot;
  try {
    const groups = await dash.rpc('luckperms.groups');
    if (!groups.ok && groups.error.code === 'needs_luckperms') {
      console.log('  (o servidor não tem o LuckPerms: suba com `gradlew runServer -PcomLuckPerms` para este teste)');
      return;
    }
    console.log('== a lista');
    const base = groups.result.groups.find((g) => g.name === 'default');
    t.check('luckperms.groups lista o grupo default com peso, prefixo/sufixo, pais e quantas permissões, marcado como protegido', groups.ok && base && typeof base.weight === 'number' && Array.isArray(base.parents) && typeof base.permissions === 'number' && base.protected === true, JSON.stringify(groups.error || base));

    console.log('== criar');
    const badName = await dash.rpc('luckperms.groupCreate', { name: 'Nome Feio' });
    t.check('um nome com espaço ou maiúscula é recusado', !badName.ok && badName.error.code === 'bad_params', JSON.stringify(badName.error || badName.result).slice(0, 100));
    const created = await dash.rpc('luckperms.groupCreate', { name: GROUP, weight: 77 });
    t.check('luckperms.groupCreate cria o grupo com o peso', created.ok && created.result.ok === true && (await groupOf(dash, GROUP)).weight === 77, JSON.stringify(created.error || created.result));
    t.check('o grupo aparece na lista dos grupos carregados do LuckPerms', (await dash.rpc('luckperms.groups')).result.groups.some((g) => g.name === GROUP && g.weight === 77));
    const dup = await dash.rpc('luckperms.groupCreate', { name: GROUP.toUpperCase() });
    t.check('criar de novo (mesmo com outra caixa no nome que já existe): already_exists', !dup.ok && dup.error.code === 'already_exists', JSON.stringify(dup.error || dup.result).slice(0, 100));
    await dash.rpc('luckperms.groupCreate', { name: OTHER });

    console.log('== peso, nome, prefixo e sufixo');
    const updated = await dash.rpc('luckperms.groupUpdate', { name: GROUP, weight: 80, displayName: 'Grupo do Teste', prefix: '&7[Dash] ', suffix: ' &e*' });
    const detail = await groupOf(dash, GROUP);
    t.check('luckperms.groupUpdate muda peso, nome, prefixo e sufixo', updated.ok && detail.weight === 80 && detail.displayName === 'Grupo do Teste' && detail.prefix === '&7[Dash] ' && detail.suffix === ' &e*', JSON.stringify(detail).slice(0, 220));
    t.check('na lista o prefixo, o peso e o nome do grupo aparecem', (await dash.rpc('luckperms.groups')).result.groups.some((g) => g.name === GROUP && g.prefix === '&7[Dash] ' && g.weight === 80 && g.displayName === 'Grupo do Teste'));
    await dash.rpc('luckperms.groupUpdate', { name: GROUP, suffix: '' });
    const cleared = await groupOf(dash, GROUP);
    t.check('um sufixo vazio TIRA o sufixo e o resto fica como estava', cleared.suffix === undefined && cleared.prefix === '&7[Dash] ' && cleared.weight === 80, JSON.stringify(cleared).slice(0, 200));

    console.log('== herança');
    const addParent = await dash.rpc('luckperms.groupParent', { name: GROUP, parent: OTHER, action: 'add' });
    t.check('luckperms.groupParent faz o grupo herdar de outro', addParent.ok && (await groupOf(dash, GROUP)).parents.some((p) => p.group === OTHER), JSON.stringify(addParent.error || addParent.result));
    const self = await dash.rpc('luckperms.groupParent', { name: GROUP, parent: GROUP, action: 'add' });
    t.check('um grupo não herda de si mesmo', !self.ok && self.error.code === 'bad_params');
    const cycle = await dash.rpc('luckperms.groupParent', { name: OTHER, parent: GROUP, action: 'add' });
    t.check('herança em círculo é recusada (A herda de B, então B não pode herdar de A)', !cycle.ok && cycle.error.code === 'bad_params' && /circles/.test(cycle.error.message || ''), JSON.stringify(cycle.error || cycle.result).slice(0, 160));
    const noParent = await dash.rpc('luckperms.groupParent', { name: GROUP, parent: 'grupo_que_nao_existe', action: 'add' });
    t.check('herdar de um grupo que não existe: not_found', !noParent.ok && noParent.error.code === 'not_found');

    console.log('== permissões');
    const give = await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'dash.test.use', action: 'set' });
    const deny = await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'dash.test.deny', value: false, action: 'set' });
    const timed = await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'dash.test.timed', action: 'set', expiresInSeconds: 3600, server: 'survival' });
    const withNodes = await groupOf(dash, GROUP);
    const node = (key) => withNodes.permissions.find((p) => p.key === key);
    t.check('dar, negar e dar por tempo (com contexto): cada um no grupo com o valor certo', give.ok && deny.ok && timed.ok && node('dash.test.use')?.value === true && node('dash.test.deny')?.value === false
      && node('dash.test.timed')?.expiresAt > Date.now() && node('dash.test.timed')?.contexts?.some((c) => c.key === 'server' && c.value === 'survival'), JSON.stringify(withNodes.permissions).slice(0, 260));
    t.check('a lista mostra quantas permissões o grupo tem', (await dash.rpc('luckperms.groups')).result.groups.find((g) => g.name === GROUP).permissions === 3);
    await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'dash.test.use', value: false, action: 'set' });
    const flipped = (await groupOf(dash, GROUP)).permissions.filter((p) => p.key === 'dash.test.use');
    t.check('dar a mesma permissão de novo com outro valor TROCA (não duplica)', flipped.length === 1 && flipped[0].value === false, JSON.stringify(flipped));
    const unset = await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'dash.test.deny', action: 'unset' });
    t.check('tirar uma permissão', unset.ok && !(await groupOf(dash, GROUP)).permissions.some((p) => p.key === 'dash.test.deny'));
    const star = await dash.rpc('luckperms.groupNode', { name: GROUP, key: '*', action: 'set' });
    t.check('dar "*" sem confirmar é recusado (confirm_required) e nada muda', !star.ok && star.error.code === 'confirm_required' && !(await groupOf(dash, GROUP)).permissions.some((p) => p.key === '*'), JSON.stringify(star.error || star.result).slice(0, 100));
    const starOk = await dash.rpc('luckperms.groupNode', { name: GROUP, key: '*', action: 'set', confirm: true });
    t.check('com confirm=true dá "*"', starOk.ok && (await groupOf(dash, GROUP)).permissions.some((p) => p.key === '*'));
    await dash.rpc('luckperms.groupNode', { name: GROUP, key: '*', action: 'unset' });
    const spaced = await dash.rpc('luckperms.groupNode', { name: GROUP, key: 'tem espaço', action: 'set' });
    t.check('uma permissão com espaço é recusada', !spaced.ok && spaced.error.code === 'bad_params');

    console.log('== membros');
    const name = 'Lpb' + SUFFIX.slice(-4);
    bot = await t.join(name);
    await t.sleep(1000);
    const add = await dash.rpc('luckperms.set', { player: name, group: GROUP, action: 'add' });
    const members = await groupOf(dash, GROUP);
    t.check('quem está no grupo aparece na lista de membros (com nome)', add.ok && members.membersTotal === 1 && members.members[0].name === name, JSON.stringify(members.members));
    await dash.rpc('luckperms.set', { player: name, group: GROUP, action: 'remove' });
    bot.quit();
    bot = null;

    console.log('== apagar');
    const noConfirm = await dash.rpc('luckperms.groupDelete', { name: GROUP });
    t.check('apagar sem confirmar: confirm_required e o grupo continua', !noConfirm.ok && noConfirm.error.code === 'confirm_required' && (await groupOf(dash, GROUP)).name === GROUP);
    const protectedGroup = await dash.rpc('luckperms.groupDelete', { name: 'default', confirm: true });
    t.check('o grupo default não pode ser apagado (protected), nem confirmando', !protectedGroup.ok && protectedGroup.error.code === 'protected');
    const deleted = await dash.rpc('luckperms.groupDelete', { name: GROUP, confirm: true });
    await dash.rpc('luckperms.groupDelete', { name: OTHER, confirm: true });
    const left = (await dash.rpc('luckperms.groups')).result.groups.map((g) => g.name);
    t.check('com confirm=true o grupo é apagado (e o outro também)', deleted.ok && !left.includes(GROUP) && !left.includes(OTHER), left.join(','));
    const gone = await dash.rpc('luckperms.group', { name: GROUP });
    t.check('depois de apagar: not_found', !gone.ok && gone.error.code === 'not_found');

    console.log('== auditoria');
    const audit = (await dash.rpc('audit.list', { limit: 100 })).result.entries.filter((e) => e.summary.includes(GROUP));
    const methods = new Set(audit.map((e) => e.method));
    t.check('a auditoria guardou criar, mudar, herança, permissão e apagar', ['luckperms.groupCreate', 'luckperms.groupUpdate', 'luckperms.groupParent', 'luckperms.groupNode', 'luckperms.groupDelete'].every((m) => methods.has(m)), [...methods].join(','));
    t.check('a auditoria diz o grupo e a permissão, e uma ação recusada (sem confirmar) não entrou', audit.some((e) => e.method === 'luckperms.groupNode' && e.summary.includes('dash.test.use')) && audit.filter((e) => e.method === 'luckperms.groupDelete').length === 1, JSON.stringify(audit.filter((e) => e.method === 'luckperms.groupDelete')));
  } finally {
    // o que sobrar de uma rodada que falhou
    for (const name of [GROUP, OTHER]) { try { await dash.rpc('luckperms.groupDelete', { name, confirm: true }); } catch (e) { /* já não existe */ } }
    if (bot) { try { bot.quit(); } catch (e) { /* já saiu */ } }
    dash.close();
  }
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
