// O painel de Redeem ponta a ponta contra o mod: lista com estado, criar/editar/apagar um código, validar, quem resgatou e quando, zerar um jogador
// ou todos e a auditoria. Um jogador de verdade (bot) resgata o código. Deixa o Redeem/redeem.conf como estava (byte a byte) no fim.
// SÓ contra um site local (veja dash-rpc.js). Com `gradlew runServer -PcomLuckPerms` confere também as sugestões de permissão do LuckPerms.
const fs = require('fs');
const path = require('path');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Redeem';
const FILE = DIR + '/redeem.conf';
const CODE = 'DashTest' + (Date.now() % 10000);
const stamped = () => fs.readdirSync(DIR).filter((name) => /^redeem\.conf\.\d{8}-\d{6}-\d{3}\.bak$/.test(name));
const clone = (value) => JSON.parse(JSON.stringify(value));
const find = (result, code) => result.codes.find((c) => c.code.toLowerCase() === code.toLowerCase());

(async () => {
  const originalText = fs.readFileSync(FILE, 'utf8');
  const backupsBefore = new Set(stamped());
  const dash = await connect();
  const bots = [];
  try {
    console.log('== a lista');
    const list = await dash.rpc('redeem.list');
    t.check('redeem.list devolve os códigos, o esquema de um código e as sugestões de permissão', list.ok && Array.isArray(list.result.codes) && Array.isArray(list.result.schema.fields) && list.result.schema.fields.some((f) => f.key === 'max_uses') && list.result.schema.fields.some((f) => f.key === 'expires_at' && f.doc) && Array.isArray(list.result.suggestions.permissions), JSON.stringify(list.error || Object.keys(list.result)).slice(0, 160));
    const hasLuckPerms = list.result.suggestions.permissions.some((p) => /^group\./.test(p));
    console.log(`  (LuckPerms ${hasLuckPerms ? 'presente: as sugestões têm os grupos' : 'ausente'})`);

    console.log('== validar e recusar');
    const good = { max_uses: 2, permission: '', expires_at: '2099-12-31', commands: ['say %player% resgatou o código de teste'], items: [] };
    const bad = { ...good, expires_at: 'quando der', max_uses: -3 };
    const checked = await dash.rpc('redeem.validate', { definition: bad });
    const errs = checked.result.errors.filter((e) => !e.warning);
    t.check('redeem.validate acusa a data ilegível e o limite negativo, cada um no seu campo', checked.result.ok === false && errs.some((e) => e.path === 'expires_at' && e.code === 'expiry') && errs.some((e) => e.path === 'max_uses'), JSON.stringify(errs).slice(0, 200));
    const okCheck = await dash.rpc('redeem.validate', { definition: good });
    t.check('a definição boa é válida', okCheck.result.ok === true, JSON.stringify(okCheck.result).slice(0, 120));
    const refused = await dash.rpc('redeem.save', { code: CODE, create: true, definition: bad });
    t.check('salvar com erro devolve ok=false e NÃO cria o código', refused.ok && refused.result.ok === false && !find((await dash.rpc('redeem.list')).result, CODE), JSON.stringify(refused.result).slice(0, 160));
    const badName = await dash.rpc('redeem.save', { code: 'tem espaço', create: true, definition: good });
    t.check('um nome com espaço é recusado', !badName.ok && badName.error.code === 'bad_params', JSON.stringify(badName.error || badName.result).slice(0, 120));
    const missing = await dash.rpc('redeem.save', { code: 'NaoExiste' + CODE, definition: good });
    t.check('editar um código que não existe: not_found', !missing.ok && missing.error.code === 'not_found', JSON.stringify(missing.error || missing.result).slice(0, 120));

    console.log('== criar');
    const created = await dash.rpc('redeem.save', { code: CODE, create: true, definition: good });
    t.check('redeem.save cria o código (created=true) e diz o que entrou', created.ok && created.result.ok === true && created.result.created === true && created.result.changes.length >= 1, JSON.stringify(created.result || created.error).slice(0, 200));
    t.check('o arquivo no disco tem o código e uma cópia do arquivo antigo foi guardada', fs.readFileSync(FILE, 'utf8').includes(CODE) && stamped().some((name) => !backupsBefore.has(name) && fs.readFileSync(path.join(DIR, name), 'utf8') === originalText));
    const dup = await dash.rpc('redeem.save', { code: CODE.toLowerCase(), create: true, definition: good });
    t.check('criar de novo com o mesmo nome (mesmo com outras maiúsculas): already_exists', !dup.ok && dup.error.code === 'already_exists', JSON.stringify(dup.error || dup.result).slice(0, 120));
    const afterCreate = find((await dash.rpc('redeem.list')).result, CODE);
    t.check('na lista: ativo, 0 usos de 2, sem permissão, vence em 2099', afterCreate && afterCreate.state === 'active' && afterCreate.uses === 0 && afterCreate.maxUses === 2 && /2099/.test(afterCreate.expiresLabel), JSON.stringify(afterCreate));

    console.log('== um jogador resgata');
    const botA = await t.join('RdbA' + (Date.now() % 10000));
    bots.push(botA);
    botA.chat('/redeem ' + CODE);
    await t.sleep(1800);
    const got = await dash.rpc('redeem.get', { code: CODE.toLowerCase() });
    t.check('redeem.get acha o código por qualquer caixa, com a definição e quem resgatou (nome e hora)', got.ok && got.result.code.code === CODE && got.result.code.uses === 1 && got.result.claims.length === 1 && got.result.claims[0].name === botA.username && Math.abs(Date.now() - got.result.claims[0].at) < 120000 && got.result.definition.max_uses === 2, JSON.stringify(got.result || got.error).slice(0, 260));
    const listed = find((await dash.rpc('redeem.list')).result, CODE);
    t.check('na lista o código mostra 1 uso e a hora do último resgate', listed.uses === 1 && listed.lastRedeemedAt > 0, JSON.stringify(listed));

    console.log('== editar: esgotar e vencer');
    const edit1 = clone(good); edit1.max_uses = 1;
    const exhausted = await dash.rpc('redeem.save', { code: CODE, definition: edit1 });
    t.check('editar o limite para 1 diz o que mudou', exhausted.result.ok === true && exhausted.result.created === false && exhausted.result.changes.some((c) => c.path === 'max_uses'), JSON.stringify(exhausted.result).slice(0, 200));
    t.check('com 1 uso de 1 o estado é "esgotado"', find((await dash.rpc('redeem.list')).result, CODE).state === 'exhausted');
    const edit2 = clone(good); edit2.expires_at = '2000-01-01';
    await dash.rpc('redeem.save', { code: CODE, definition: edit2 });
    t.check('com a data no passado o estado é "vencido"', find((await dash.rpc('redeem.list')).result, CODE).state === 'expired');
    botA.quit();
    await t.sleep(600);

    console.log('== zerar');
    const noTarget = await dash.rpc('redeem.reset', { code: CODE });
    t.check('zerar sem dizer quem nem "all" é recusado', !noTarget.ok && noTarget.error.code === 'bad_params', JSON.stringify(noTarget.error || noTarget.result).slice(0, 100));
    const unknownPlayer = await dash.rpc('redeem.reset', { code: CODE, player: 'NinguemAssim' + CODE });
    t.check('zerar um jogador que o servidor não conhece: player_not_found', !unknownPlayer.ok && unknownPlayer.error.code === 'player_not_found', JSON.stringify(unknownPlayer.error || unknownPlayer.result).slice(0, 100));
    const one = await dash.rpc('redeem.reset', { code: CODE, player: botA.username });
    t.check('zerar UM jogador devolve reset=1 e o código volta a ter 0 usos', one.result.reset === 1 && find((await dash.rpc('redeem.list')).result, CODE).uses === 0, JSON.stringify(one.result));
    const again = await dash.rpc('redeem.reset', { code: CODE, player: botA.username });
    t.check('zerar de novo quem já foi zerado: reset=0', again.result.reset === 0);
    // dois resgatam (limite 5), depois zera todos
    const edit3 = clone(good); edit3.max_uses = 5;
    await dash.rpc('redeem.save', { code: CODE, definition: edit3 });
    const b1 = await t.join('RdbB' + (Date.now() % 10000));
    const b2 = await t.join('RdbC' + (Date.now() % 10000));
    bots.push(b1, b2);
    b1.chat('/redeem ' + CODE);
    b2.chat('/redeem ' + CODE);
    await t.sleep(2200);
    t.check('dois jogadores resgataram: 2 usos e 2 nomes na lista de quem resgatou', (await dash.rpc('redeem.get', { code: CODE })).result.claims.length === 2 && find((await dash.rpc('redeem.list')).result, CODE).uses === 2);
    const all = await dash.rpc('redeem.reset', { code: CODE, all: true });
    t.check('zerar TODOS devolve reset=2; 0 usos e ninguém na lista', all.result.reset === 2 && find((await dash.rpc('redeem.list')).result, CODE).uses === 0 && (await dash.rpc('redeem.get', { code: CODE })).result.claims.length === 0, JSON.stringify(all.result));
    b1.quit();
    b2.quit();
    await t.sleep(500);

    console.log('== apagar e a auditoria');
    const deleted = await dash.rpc('redeem.delete', { code: CODE });
    t.check('redeem.delete apaga o código', deleted.ok && deleted.result.ok === true && !find((await dash.rpc('redeem.list')).result, CODE), JSON.stringify(deleted.result || deleted.error));
    const gone = await dash.rpc('redeem.get', { code: CODE });
    t.check('depois de apagar: not_found', !gone.ok && gone.error.code === 'not_found');
    const audit = (await dash.rpc('audit.list', { limit: 60 })).result.entries.filter((e) => e.summary.includes(CODE) || e.summary.includes(CODE.toLowerCase()));
    const methods = new Set(audit.map((e) => e.method));
    t.check('a auditoria guardou criar/editar (com o nome das opções), zerar e apagar', ['redeem.save', 'redeem.reset', 'redeem.delete'].every((m) => methods.has(m)) && audit.some((e) => e.method === 'redeem.save' && /\[.*max_uses.*\]/.test(e.summary)), JSON.stringify(audit.slice(0, 4)));
    t.check('uma gravação recusada (com erro) não foi registrada', !audit.some((e) => /expires_at.*quando der/.test(e.summary)));
  } finally {
    for (const bot of bots) { try { bot.quit(); } catch (e) { /* já saiu */ } }
    // deixa tudo como estava: o texto original do arquivo e sem as cópias que o teste fez
    fs.writeFileSync(FILE, originalText);
    for (const name of stamped().filter((n) => !backupsBefore.has(n))) fs.unlinkSync(path.join(DIR, name));
    t.rcon('sb redeem reload');
    dash.close();
  }
  t.check('no fim o redeem.conf é o texto original', fs.readFileSync(FILE, 'utf8') === originalText);
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
