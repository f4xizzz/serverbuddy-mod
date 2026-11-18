// O editor de configs do painel, ponta a ponta contra o mod: validar (erro no campo, aviso de opção desconhecida), recusar sem gravar, salvar com
// cópia carimbada + diff do que mudou + auditoria, histórico, restaurar (e o que NÃO se pode restaurar), recarregar com resultado e a busca.
// Usa o PokeSnack/config.json (muda um número e volta ao texto original byte a byte no fim). SÓ contra um site local (veja dash-rpc.js).
const fs = require('fs');
const path = require('path');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const DIR = 'D:/mods/ServerBuddy/run/config/ServerBuddy/PokeSnack';
const FILE = DIR + '/config.json';
const target = { module: 'PokeSnack', file: 'config.json' };
const clone = (value) => JSON.parse(JSON.stringify(value));
const stamped = () => fs.readdirSync(DIR).filter((name) => /^config\.json\.\d{8}-\d{6}-\d{3}\.bak$/.test(name));

(async () => {
  const originalText = fs.readFileSync(FILE, 'utf8');
  const before = new Set(stamped());
  const legacy = fs.existsSync(FILE + '.bak') ? fs.readFileSync(FILE + '.bak') : null;
  const dash = await connect();
  try {
    const got = await dash.rpc('config.get', target);
    const original = got.result.values;
    t.check('abre o arquivo (valores e esquema)', got.ok && typeof original.max_pokemon_per_chunk === 'number' && got.result.schema.fields.some((f) => f.key === 'max_pokemon_per_chunk' && ['int', 'float'].includes(f.type)), JSON.stringify(got.error || '').slice(0, 100));

    console.log('== validar: o mod diz o que está errado e em qual campo');
    const same = await dash.rpc('config.validate', { ...target, values: original });
    t.check('os valores de agora são válidos', same.ok && same.result.ok === true && same.result.errors.filter((e) => !e.warning).length === 0, JSON.stringify(same.result).slice(0, 160));
    const bad = clone(original);
    bad.max_pokemon_per_chunk = 'muitos';
    bad.enabled = 'talvez';
    const invalid = await dash.rpc('config.validate', { ...target, values: bad });
    const errs = invalid.result.errors.filter((e) => !e.warning);
    t.check('texto onde vai número e "talvez" onde vai ligado/desligado: um erro por campo, com o caminho', invalid.result.ok === false && errs.some((e) => e.path === 'max_pokemon_per_chunk' && ['int', 'number'].includes(e.code)) && errs.some((e) => e.path === 'enabled' && e.code === 'boolean'), JSON.stringify(errs).slice(0, 220));
    const typo = clone(original);
    typo.max_pokemon_per_chunkk = 5;
    const warned = await dash.rpc('config.validate', { ...target, values: typo });
    t.check('uma opção que o mod não conhece é só um AVISO (ok continua verdadeiro)', warned.result.ok === true && warned.result.errors.some((e) => e.warning && e.path === 'max_pokemon_per_chunkk'), JSON.stringify(warned.result).slice(0, 200));
    const notObject = await dash.rpc('config.validate', { ...target, values: [1, 2] });
    t.check('um arquivo que não é um objeto é recusado', notObject.ok && notObject.result.ok === false, JSON.stringify(notObject.result || notObject.error).slice(0, 120));

    console.log('== salvar: recusado NÃO grava nada');
    const auditBefore = (await dash.rpc('audit.list', { limit: 200 })).result.entries.length;
    const refused = await dash.rpc('config.save', { ...target, values: bad });
    t.check('config.save com erro devolve ok=false e os erros', refused.ok && refused.result.ok === false && refused.result.errors.some((e) => e.path === 'enabled'), JSON.stringify(refused.result).slice(0, 160));
    t.check('o arquivo no disco continua igual e nenhuma cópia nova nasceu', fs.readFileSync(FILE, 'utf8') === originalText && stamped().every((name) => before.has(name)));
    const auditAfterRefused = (await dash.rpc('audit.list', { limit: 200 })).result.entries.length;
    t.check('uma gravação recusada não entra na auditoria (nada mudou)', auditAfterRefused === auditBefore, `${auditBefore} -> ${auditAfterRefused}`);

    console.log('== salvar de verdade: cópia carimbada, diff e auditoria');
    const edited = clone(original);
    edited.max_pokemon_per_chunk = original.max_pokemon_per_chunk + 7;
    const saved = await dash.rpc('config.save', { ...target, values: edited });
    t.check('config.save grava (ok) e diz o que mudou, com o de antes e o de depois', saved.ok && saved.result.ok === true && saved.result.changes.length === 1 && saved.result.changes[0].path === 'max_pokemon_per_chunk'
      && Number(saved.result.changes[0].from) === original.max_pokemon_per_chunk && Number(saved.result.changes[0].to) === edited.max_pokemon_per_chunk, JSON.stringify(saved.result).slice(0, 220));
    const onDisk = fs.readFileSync(FILE, 'utf8');
    t.check('o arquivo no disco tem o valor novo e continua com os comentários do mod', onDisk.includes(String(edited.max_pokemon_per_chunk)) && (!originalText.includes('//') || onDisk.includes('//')), onDisk.slice(0, 120));
    const fresh = stamped().filter((name) => !before.has(name));
    t.check('uma cópia carimbada do arquivo ANTIGO foi guardada (e o .bak único também)', fresh.length === 1 && fs.readFileSync(path.join(DIR, fresh[0]), 'utf8') === originalText && fs.readFileSync(FILE + '.bak', 'utf8') === originalText, fresh.join(','));
    const entries = (await dash.rpc('audit.list', { limit: 5 })).result.entries;
    t.check('a auditoria anotou o arquivo e o NOME da opção que mudou (nunca os valores)', entries[0] && entries[0].method === 'config.save' && /PokeSnack\/config\.json \[max_pokemon_per_chunk\]/.test(entries[0].summary) && !entries[0].summary.includes(String(edited.max_pokemon_per_chunk)), JSON.stringify(entries[0]));
    const beforeNoop = { modified: fs.statSync(FILE).mtimeMs, copies: stamped().join(',') };
    const again = await dash.rpc('config.save', { ...target, values: edited });
    t.check('salvar o mesmo valor de novo não muda nada ("no changes")', again.result.ok === true && again.result.changes.length === 0 && fs.statSync(FILE).mtimeMs === beforeNoop.modified && stamped().join(',') === beforeNoop.copies, JSON.stringify(again.result).slice(0, 120));

    const nextEdit = { ...edited, max_pokemon_per_chunk: edited.max_pokemon_per_chunk + 1 };
    const nextSave = await dash.rpc('config.save', { ...target, values: nextEdit });
    t.check('uma segunda mudança real cria outra cópia para conferir a ordem do histórico', nextSave.ok && nextSave.result.ok === true && nextSave.result.changes.length === 1);

    console.log('== histórico e restaurar');
    const history = await dash.rpc('config.history', target);
    const backups = history.result.backups;
    t.check('config.history lista as cópias, da mais nova para a mais velha', backups.length >= 2 && backups.every((b) => /^config\.json\.\d{8}-\d{6}-\d{3}\.bak$/.test(b.id) && b.size > 0 && b.modified > 0) && backups[0].id > backups[1].id, JSON.stringify(backups.slice(0, 2)));
    const oldest = backups.find((b) => fs.readFileSync(path.join(DIR, b.id), 'utf8') === originalText);
    t.check('a cópia do arquivo original está na lista', Boolean(oldest), backups.map((b) => b.id).join(','));
    for (const bad of ['../../Shout/config.json', 'config.json', '../config.json.20260101-000000-000.bak', '']) {
      const refusedId = await dash.rpc('config.restore', { ...target, backup: bad });
      t.check(`restaurar "${bad || '(vazio)'}" é recusado (só vale o id de uma cópia da lista)`, !refusedId.ok && refusedId.error.code === 'not_found', JSON.stringify(refusedId.error || refusedId.result).slice(0, 100));
    }
    const restored = await dash.rpc('config.restore', { ...target, backup: oldest.id });
    t.check('restaurar devolve os valores de antes e o arquivo volta a ser o texto original byte a byte', restored.ok && restored.result.values.max_pokemon_per_chunk === original.max_pokemon_per_chunk && fs.readFileSync(FILE, 'utf8') === originalText, JSON.stringify(restored.error || '').slice(0, 100));
    const after = (await dash.rpc('config.history', target)).result.backups;
    t.check('antes de restaurar o mod guardou o arquivo que estava lá (dá para desfazer o restaurar)', after.length === backups.length + 1 && after.some((b) => fs.readFileSync(path.join(DIR, b.id), 'utf8').includes(String(edited.max_pokemon_per_chunk))), `${backups.length} -> ${after.length}`);
    const restoreAudit = (await dash.rpc('audit.list', { limit: 3 })).result.entries[0];
    t.check('restaurar também é uma ação registrada', restoreAudit.method === 'config.restore' && restoreAudit.summary.includes(oldest.id), JSON.stringify(restoreAudit));

    console.log('== recarregar com o resultado de cada um');
    const one = await dash.rpc('config.reload', target);
    t.check('config.reload de um arquivo: ok e um resultado com o nome dele', one.ok && one.result.ok === true && one.result.results.length === 1 && one.result.results[0].label === 'PokeSnack/config.json' && one.result.results[0].ok === true, JSON.stringify(one.result || one.error).slice(0, 200));
    const noModule = await dash.rpc('config.reload', { module: 'Dashboard', file: 'config.json' });
    t.check('um arquivo sem recarga própria avisa que vale depois de reiniciar, sem fingir', noModule.ok && noModule.result.results.length === 1 && noModule.result.results[0].ok === true && noModule.result.results[0].note === 'restart', JSON.stringify(noModule.result || noModule.error).slice(0, 200));
    const unknown = await dash.rpc('config.reload', { module: 'Nada', file: 'config.json' });
    t.check('um arquivo que não existe: not_found', !unknown.ok && unknown.error.code === 'not_found', JSON.stringify(unknown.error || unknown.result).slice(0, 100));
    const everything = await dash.rpc('config.reload', {});
    t.check('config.reload de tudo devolve a lista de resultados', everything.ok && Array.isArray(everything.result.results) && everything.result.results.length >= 1 && typeof everything.result.ok === 'boolean', JSON.stringify(everything.result || everything.error).slice(0, 200));

    console.log('== busca em todos os arquivos');
    const found = await dash.rpc('config.search', { query: 'max_pokemon_per_chunk' });
    t.check('config.search acha a opção pelo nome, com o arquivo e o caminho', found.ok && found.result.hits.some((h) => h.module === 'PokeSnack' && h.file === 'config.json' && h.path === 'max_pokemon_per_chunk'), JSON.stringify(found.result || found.error).slice(0, 200));
    const wide = await dash.rpc('config.search', { query: 'enabled' });
    const modules = new Set(wide.result.hits.map((h) => h.label));
    t.check('uma palavra comum acha em VÁRIOS arquivos e respeita o limite', wide.ok && modules.size >= 5 && wide.result.hits.length <= 60, `${modules.size} arquivos, ${wide.result.hits.length} resultados`);
    const tooShort = await dash.rpc('config.search', { query: 'e' });
    t.check('uma letra só não é busca', tooShort.ok && tooShort.result.hits.length === 0);
  } finally {
    // deixa tudo como estava: o texto original de volta e sem as cópias que o teste fez
    fs.writeFileSync(FILE, originalText);
    if (legacy) fs.writeFileSync(FILE + '.bak', legacy);
    else if (fs.existsSync(FILE + '.bak')) fs.unlinkSync(FILE + '.bak');
    for (const name of stamped().filter((n) => !before.has(n))) fs.unlinkSync(path.join(DIR, name));
    dash.close();
  }
  t.check('no fim o arquivo é o texto original', fs.readFileSync(FILE, 'utf8') === originalText);
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
