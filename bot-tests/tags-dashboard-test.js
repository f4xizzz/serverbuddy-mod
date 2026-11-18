// O painel de Tags ponta a ponta contra o mod: o catálogo (validar, criar, editar, apagar), o que cada jogador tem (online e OFFLINE), dar, tirar e
// usar uma tag, e a auditoria. O teste cria as PRÓPRIAS tags (não depende do catálogo do servidor) e deixa o Tags/tags.json como estava (byte a
// byte) no fim. SÓ contra um site local (veja dash-rpc.js).
const fs = require('fs');
const path = require('path');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Tags';
const FILE = DIR + '/tags.json';
const SUFFIX = String(Date.now() % 100000);
const GIVEN = 'dashgiv' + SUFFIX; // só quem recebe tem
const FREE = 'dashfree' + SUFFIX; // todo mundo tem
const PRICED = 'dashprice' + SUFFIX; // à venda
const stamped = () => fs.readdirSync(DIR).filter((name) => /^tags\.json\.\d{8}-\d{6}-\d{3}\.bak$/.test(name));
const byId = (result, id) => result.tags.find((tag) => tag.id === id);
const unlock = (type, extra = {}) => ({ type, value: '', price: 0, currency: '', hint: '', ...extra });
const definition = (name, unlockSpec, extra = {}) => ({ name, description: ['<gray>Feita pelo teste do painel.'], prefix: `${name} `, category: '', unlock: unlockSpec, weight: 90, ...extra });

(async () => {
  const originalText = fs.readFileSync(FILE, 'utf8');
  const backupsBefore = new Set(stamped());
  const dash = await connect();
  let bot;
  try {
    console.log('== o catálogo e o formulário');
    const list = await dash.rpc('tags.list');
    t.check('tags.list devolve o catálogo, as categorias e o esquema de uma tag', list.ok && list.result.editable === true && list.result.tags.length >= 1 && list.result.categories.length >= 1 && list.result.schema.fields.some((f) => f.key === 'unlock' && f.type === 'object'), JSON.stringify(list.error || Object.keys(list.result)).slice(0, 160));
    const first = list.result.tags[0];
    t.check('cada tag vem com o nome SEM MiniMessage, a categoria, a origem e a forma de ganhar', typeof first.name === 'string' && !/[<>]/.test(first.name) && first.source === 'own' && typeof first.unlock.type === 'string', JSON.stringify(first));

    const noValue = await dash.rpc('tags.validate', { definition: definition('<red>X', unlock('PERMISSION')) });
    const noPrice = await dash.rpc('tags.validate', { definition: definition('<red>X', unlock('PRICE')) });
    const noName = await dash.rpc('tags.validate', { definition: definition('', unlock('GIVEN')) });
    t.check('validar: PERMISSION sem valor, PRICE sem preço e tag sem nome são erros, cada um no seu campo', noValue.result.errors.some((e) => e.path === 'unlock.value' && !e.warning) && noPrice.result.errors.some((e) => e.path === 'unlock.price' && !e.warning) && noName.result.errors.some((e) => e.path === 'name' && !e.warning), JSON.stringify([noValue.result.errors, noPrice.result.errors, noName.result.errors]).slice(0, 240));
    const refused = await dash.rpc('tags.save', { id: GIVEN, create: true, definition: definition('', unlock('GIVEN')) });
    t.check('salvar com erro devolve ok=false e nada é criado', refused.ok && refused.result.ok === false && !byId((await dash.rpc('tags.list')).result, GIVEN));
    const badId = await dash.rpc('tags.save', { id: 'Tem Espaço', create: true, definition: definition('<red>X', unlock('GIVEN')) });
    t.check('um id com espaço ou maiúscula é recusado', !badId.ok && badId.error.code === 'bad_params');

    console.log('== criar as tags do teste');
    const created = await dash.rpc('tags.save', { id: GIVEN, create: true, definition: definition('<red>Dash Dada', unlock('GIVEN', { hint: '<gray>só por comando' })) });
    t.check('tags.save cria a tag (created=true) e a lista já mostra, sem MiniMessage no nome', created.ok && created.result.ok === true && created.result.created === true && byId((await dash.rpc('tags.list')).result, GIVEN)?.name === 'Dash Dada', JSON.stringify(created.result || created.error).slice(0, 200));
    await dash.rpc('tags.save', { id: FREE, create: true, definition: definition('<green>Dash Livre', unlock('FREE')) });
    await dash.rpc('tags.save', { id: PRICED, create: true, definition: definition('<gold>Dash Cara', unlock('PRICE', { price: 250 })) });
    t.check('o arquivo guardou as tags e uma cópia do arquivo antigo ficou no histórico', fs.readFileSync(FILE, 'utf8').includes(GIVEN) && stamped().some((n) => !backupsBefore.has(n) && fs.readFileSync(path.join(DIR, n), 'utf8') === originalText));
    const dup = await dash.rpc('tags.save', { id: GIVEN, create: true, definition: definition('<red>Outra', unlock('GIVEN')) });
    t.check('criar com um id que já existe: already_exists', !dup.ok && dup.error.code === 'already_exists');
    const one = await dash.rpc('tags.get', { id: GIVEN.toUpperCase() });
    t.check('tags.get acha por qualquer caixa e traz a definição para o formulário', one.ok && one.result.tag.id === GIVEN && one.result.definition.unlock.hint === '<gray>só por comando', JSON.stringify(one.error || '').slice(0, 120));

    console.log('== um jogador online');
    const name = 'Tgb' + SUFFIX;
    bot = await t.join(name);
    await t.sleep(1200);
    const before = await dash.rpc('tags.player', { player: name });
    t.check('tags.player: online, com todas as tags e o estado de cada uma para ele', before.ok && before.result.online === true && byId(before.result, GIVEN).state === 'locked' && byId(before.result, GIVEN).given === false, JSON.stringify(before.error || byId(before.result, GIVEN)).slice(0, 160));
    t.check('a tag livre já é dele pela regra, mas não está "dada" (não dá para tirar); a à venda mostra que pode comprar', ['owned', 'equipped'].includes(byId(before.result, FREE).state) && byId(before.result, FREE).given === false && byId(before.result, PRICED).state === 'purchasable', JSON.stringify([byId(before.result, FREE), byId(before.result, PRICED)]));
    const given = await dash.rpc('tags.give', { player: name, tag: GIVEN });
    t.check('tags.give dá a tag', given.ok && given.result.ok === true, JSON.stringify(given.error || given.result));
    t.check('o jogador recebe o aviso no chat', bot.chatLog.some((line) => /Dash Dada/.test(line)), bot.chatLog.slice(-3).join(' | ').slice(0, 160));
    const after = await dash.rpc('tags.player', { player: name });
    t.check('agora a tag é dele e pode ser tirada (given=true)', byId(after.result, GIVEN).state === 'owned' && byId(after.result, GIVEN).given === true, JSON.stringify(byId(after.result, GIVEN)));
    const twice = await dash.rpc('tags.give', { player: name, tag: GIVEN });
    t.check('dar de novo: already_has', !twice.ok && twice.error.code === 'already_has', JSON.stringify(twice.error || twice.result).slice(0, 100));
    const unknownTag = await dash.rpc('tags.give', { player: name, tag: 'nao_existe' });
    t.check('uma tag que não existe: not_found', !unknownTag.ok && unknownTag.error.code === 'not_found');
    const notOwned = await dash.rpc('tags.equip', { player: name, tag: PRICED });
    t.check('usar uma tag que ele não tem: not_owned', !notOwned.ok && notOwned.error.code === 'not_owned', JSON.stringify(notOwned.error || notOwned.result).slice(0, 100));
    const equipped = await dash.rpc('tags.equip', { player: name, tag: GIVEN });
    t.check('tags.equip faz o jogador usar a tag', equipped.ok && (await dash.rpc('tags.player', { player: name })).result.equipped === GIVEN, JSON.stringify(equipped.error || equipped.result));
    const takeOff = await dash.rpc('tags.equip', { player: name });
    t.check('tags.equip sem tag tira a que ele usa (ele continua com ela)', takeOff.ok && (await dash.rpc('tags.player', { player: name })).result.equipped !== GIVEN && byId((await dash.rpc('tags.player', { player: name })).result, GIVEN).given === true);
    await dash.rpc('tags.equip', { player: name, tag: GIVEN });
    const removed = await dash.rpc('tags.remove', { player: name, tag: GIVEN });
    const afterRemove = (await dash.rpc('tags.player', { player: name })).result;
    t.check('tags.remove tira a tag e, se ele a usava, tira de uso também', removed.ok && byId(afterRemove, GIVEN).state === 'locked' && afterRemove.equipped !== GIVEN, JSON.stringify(afterRemove.equipped));
    const removedTwice = await dash.rpc('tags.remove', { player: name, tag: GIVEN });
    t.check('tirar de novo: not_owned', !removedTwice.ok && removedTwice.error.code === 'not_owned');
    const free = await dash.rpc('tags.remove', { player: name, tag: FREE });
    t.check('uma tag que ele tem só pela regra (livre) não dá para tirar daqui: not_owned', !free.ok && free.error.code === 'not_owned');

    console.log('== o mesmo jogador OFFLINE');
    bot.quit();
    bot = null;
    await t.sleep(2500);
    const giveOffline = await dash.rpc('tags.give', { player: name, tag: GIVEN });
    const offline = (await dash.rpc('tags.player', { player: name })).result;
    t.check('dar a tag a quem está offline funciona e aparece na ficha dele', giveOffline.ok && offline.online === false && byId(offline, GIVEN).state === 'owned' && byId(offline, GIVEN).given === true, JSON.stringify(giveOffline.error || byId(offline, GIVEN)));
    const equipOffline = await dash.rpc('tags.equip', { player: name, tag: GIVEN });
    t.check('usar uma tag exige o jogador online (player_offline)', !equipOffline.ok && equipOffline.error.code === 'player_offline', JSON.stringify(equipOffline.error || equipOffline.result).slice(0, 100));
    const takeOffline = await dash.rpc('tags.remove', { player: name, tag: GIVEN });
    t.check('tirar de quem está offline também', takeOffline.ok && byId((await dash.rpc('tags.player', { player: name })).result, GIVEN).given === false);

    console.log('== editar e apagar');
    const edited = await dash.rpc('tags.save', { id: GIVEN, definition: definition('<red>Dash Dada', unlock('PRICE', { price: 99 }), { weight: 5 }) });
    t.check('editar diz o que mudou (peso e a forma de ganhar)', edited.result.ok === true && edited.result.created === false && edited.result.changes.some((c) => c.path === 'weight') && edited.result.changes.some((c) => c.path.startsWith('unlock')), JSON.stringify(edited.result.changes));
    const missing = await dash.rpc('tags.save', { id: 'nao_existe_' + SUFFIX, definition: definition('<red>X', unlock('GIVEN')) });
    t.check('editar uma tag que não existe: not_found', !missing.ok && missing.error.code === 'not_found');
    for (const id of [GIVEN, FREE, PRICED]) {
      const deleted = await dash.rpc('tags.delete', { id: id.toUpperCase() });
      t.check(`tags.delete apaga ${id.replace(SUFFIX, '')}`, deleted.ok && deleted.result.ok === true && !byId((await dash.rpc('tags.list')).result, id), JSON.stringify(deleted.error || deleted.result));
    }
    const deletedTwice = await dash.rpc('tags.delete', { id: GIVEN });
    t.check('apagar de novo: not_found', !deletedTwice.ok && deletedTwice.error.code === 'not_found');

    console.log('== auditoria');
    const audit = (await dash.rpc('audit.list', { limit: 100 })).result.entries;
    const mine = audit.filter((e) => { const text = e.summary.toLowerCase(); return text.includes(name.toLowerCase()) || text.includes(GIVEN) || text.includes(FREE); });
    const methods = new Set(mine.map((e) => e.method));
    t.check('a auditoria guardou dar, tirar, usar, criar/editar e apagar', ['tags.give', 'tags.remove', 'tags.equip', 'tags.save', 'tags.delete'].every((m) => methods.has(m)), [...methods].join(','));
    t.check('as ações de tag trazem o jogador e a tag (e o id e o nome das opções que mudaram)', mine.some((e) => e.method === 'tags.give' && e.summary.includes(GIVEN)) && mine.some((e) => e.method === 'tags.save' && /\[.*weight.*\]/.test(e.summary)), JSON.stringify(mine.slice(0, 3)));
  } finally {
    if (bot) { try { bot.quit(); } catch (e) { /* já saiu */ } }
    fs.writeFileSync(FILE, originalText);
    for (const n of stamped().filter((x) => !backupsBefore.has(x))) fs.unlinkSync(path.join(DIR, n));
    t.rcon('tags reload');
    dash.close();
  }
  t.check('no fim o tags.json é o texto original', fs.readFileSync(FILE, 'utf8') === originalText);
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
