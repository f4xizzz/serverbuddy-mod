// Os SEGREDOS das configs (senha de banco, endereço de webhook...) nunca chegam ao site. Este teste grava segredos de verdade nos arquivos do servidor
// e confere a RESPOSTA CRUA de cada função do painel (config.get/schema/search/validate/save/restore/history, a auditoria e os logs ao vivo): o valor
// não pode aparecer em nenhuma. Confere também o outro lado: salvar sem mexer no segredo deixa o arquivo idêntico, trocar e limpar funcionam, e uma marca
// não carrega um segredo para uma opção que o site enxerga. Por fim a COBERTURA: toda opção de qualquer arquivo com nome de segredo precisa estar
// protegida (ou ter o motivo escrito em NOT_SECRET). Muda os arquivos do servidor de teste e devolve o texto original no fim. SÓ contra um site local.
const fs = require('fs');
const path = require('path');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const ROOT = 'D:/mods/ServerBuddy/run/config/ServerBuddy';
const RUN = 'D:/mods/ServerBuddy/run';
const rand = () => Math.random().toString(36).slice(2, 10);
const text = (response) => JSON.stringify(response);
const clone = (value) => JSON.parse(JSON.stringify(value));

// o nome de uma opção que guarda um segredo (o teste usa o seu próprio critério, de propósito: não depende da lista do mod)
const normalize = (name) => name.toLowerCase().replace(/[_\- ]/g, '');
const SECRET_ENDINGS = ['password', 'passwd', 'webhookurl', 'urloverride', 'connectionstring', 'secret', 'apikey'];
const isSecretName = (name) => SECRET_ENDINGS.some((ending) => normalize(name).endsWith(ending));

// COBERTURA: um nome que PARECE segredo e que o mod não protege precisa de um motivo aqui (senão o teste falha, e quem criou a opção decide)
const SECRET_LIKE = /password|passwd|passphrase|secret|token|webhook|api[_-]?key|credential|\bauth|jdbc|(^|[_-])uri($|[_-])|url|conn(ection)?[_-]?string|dsn|licen[cs]e|private[_-]?key/i;
const NOT_SECRET = {
  avatar_url: 'o endereço da imagem do avatar de quem posta (público); o endereço do webhook em si é webhook_url, protegido',
  webhook: 'o objeto que agrupa as opções do webhook; o segredo está dentro dele (webhook_url, protegido)',
  webhooks: 'o objeto que agrupa os webhooks de cada evento; os segredos estão dentro (webhook_url, protegido)',
  webhook_rare: 'o texto da mensagem que vai para o Discord (Wonder Trade), não o endereço',
  discordWebhookDefaultSettings: 'um liga/desliga ("usar o webhook padrão"), não o endereço',
  unknown_token: 'uma mensagem de chat ("a ficha não existe"), não uma credencial',
  tokens: 'as fichas Pokémon do jogo (shiny, gênero...), não credenciais',
  poketokens: 'o liga/desliga do módulo das fichas Pokémon (modules.conf), não uma credencial',
};

/** Percorre as opções do esquema (também dentro de listas e mapas) chamando `visit(nó, caminho)`. */
function walk(fields, visit, base = '') {
  for (const node of fields || []) {
    const here = base ? `${base}.${node.key}` : node.key;
    visit(node, here);
    if (node.fields) walk(node.fields, visit, here);
    if (node.item?.fields) walk(node.item.fields, visit, `${here}[]`);
    if (node.value?.fields) walk(node.value.fields, visit, `${here}{}`);
  }
}

const originals = new Map(); // arquivo -> texto original
const folders = new Map(); // pasta -> nomes que já existiam (para apagar só as cópias .bak do teste)
/** Devolve o texto original dos arquivos e apaga as cópias .bak do teste (pode rodar mais de uma vez; roda também se o teste cair). */
function restoreAll() {
  for (const [file, original] of originals) fs.writeFileSync(file, original);
  for (const [folder, names] of folders) for (const name of fs.readdirSync(folder)) if (/\.bak$/.test(name) && !names.has(name)) fs.unlinkSync(path.join(folder, name));
}
process.on('exit', restoreAll);

(async () => {
  const dash = await connect();
  const injected = []; // { label, key, secret }
  try {
    const list = (await dash.rpc('config.list')).result.files;
    const fileOf = (entry) => path.join(ROOT, entry.module, entry.file);

    console.log('== grava segredos de verdade nos arquivos (o texto original volta no fim)');
    let counter = 0;
    for (const entry of list) {
      const file = fileOf(entry);
      if (!fs.existsSync(file)) continue;
      const original = fs.readFileSync(file, 'utf8');
      let changed = false;
      const out = original.split('\n').map((line) => {
        const m = line.match(/^(\s*"([^"]+)"\s*:\s*)"([^"]*)"(,?\s*\r?)$/);
        if (!m || !isSecretName(m[2])) return line;
        const secret = `SBTEST-${m[2].replace(/[^A-Za-z]/g, '')}-${++counter}-${rand()}`;
        injected.push({ label: entry.label, key: m[2], secret });
        changed = true;
        return `${m[1]}"${secret}"${m[4]}`;
      });
      if (!changed) continue;
      originals.set(file, original);
      if (!folders.has(path.dirname(file))) folders.set(path.dirname(file), new Set(fs.readdirSync(path.dirname(file))));
      fs.writeFileSync(file, out.join('\n'));
    }
    const labels = [...new Set(injected.map((i) => i.label))];
    t.check('o teste achou arquivos do painel com segredos para gravar (webhook, Shop, Kits, Crates...)', injected.length >= 8 && labels.length >= 5, `${injected.length} segredos em ${labels.length} arquivos: ${labels.join(', ')}`);
    const secrets = injected.map((i) => i.secret);
    const leaks = (response, among = secrets) => among.filter((secret) => text(response).includes(secret));

    console.log('== config.get e config.schema de cada arquivo com segredo');
    for (const label of labels) {
      const entry = list.find((e) => e.label === label);
      const own = injected.filter((i) => i.label === label);
      const got = await dash.rpc('config.get', { module: entry.module, file: entry.file });
      const schema = await dash.rpc('config.schema', { module: entry.module, file: entry.file });
      const marked = [];
      const secretNodes = [];
      const visit = (value, here) => {
        if (value && typeof value === 'object' && !Array.isArray(value) && typeof value.$secret === 'string') marked.push(here);
        else if (value && typeof value === 'object') for (const [k, v] of Object.entries(value)) visit(v, here ? `${here}.${k}` : k);
      };
      visit(got.result.values, '');
      walk(got.result.schema.fields, (node) => { if (node.secret) secretNodes.push(node); });
      t.check(`${label}: config.get e config.schema não trazem nenhum dos ${own.length} segredo(s) deste arquivo (nem os dos outros)`, got.ok && schema.ok && leaks(got).length === 0 && leaks(schema).length === 0, JSON.stringify(leaks(got).concat(leaks(schema))));
      // um objeto que pode ser nulo (o grupo de anúncios) não tem opções no esquema: ele é editado como JSON puro e a marca vai dentro dele
      const rawJson = got.result.schema.fields.some((n) => n.type === 'json');
      t.check(`${label}: no lugar de cada segredo vai uma marca "set"; o esquema o marca como secreto, sem padrão (ou o objeto é editado como JSON puro)`, marked.length >= own.length && (secretNodes.length >= 1 || rawJson) && secretNodes.every((n) => n.default === undefined), `${marked.length} marcas, ${secretNodes.length} nós`);
    }

    console.log('== todos os arquivos do painel: nada vaza, e o que está no disco com nome de segredo não aparece');
    const sweep = [];
    const unprotected = new Map(); // nome -> [onde]
    const usedExceptions = new Set();
    let flagged = 0;
    for (const entry of list) {
      const got = await dash.rpc('config.get', { module: entry.module, file: entry.file });
      if (!got.ok) { sweep.push(`${entry.label}: ${JSON.stringify(got.error)}`); continue; }
      const raw = text(got);
      const found = leaks(got);
      if (found.length) sweep.push(`${entry.label}: ${found.join(',')}`);
      // qualquer texto de uma opção com nome de segredo que o disco tenha agora (inclusive de arquivos que o teste não mexeu)
      const file = fileOf(entry);
      if (fs.existsSync(file)) {
        for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
          const m = line.match(/^\s*"([^"]+)"\s*:\s*"([^"]{6,})"/);
          if (m && isSecretName(m[1]) && raw.includes(m[2])) sweep.push(`${entry.label}: o valor de ${m[1]} está na resposta`);
        }
      }
      walk(got.result.schema.fields, (node, here) => {
        const bare = String(node.key);
        if (!SECRET_LIKE.test(bare)) return;
        if (node.secret) { flagged++; return; }
        if (NOT_SECRET[bare]) { usedExceptions.add(bare); return; }
        if (!unprotected.has(bare)) unprotected.set(bare, []);
        unprotected.get(bare).push(`${entry.label}:${here}`);
      });
    }
    t.check(`config.get dos ${list.length} arquivos não mostra nenhum segredo`, sweep.length === 0, sweep.slice(0, 4).join(' | '));
    t.check('COBERTURA: toda opção com nome de segredo está protegida ou tem o motivo escrito no teste (NOT_SECRET)', unprotected.size === 0,
      [...unprotected].map(([name, where]) => `${name} (${where.slice(0, 2).join(', ')})`).join('; '));
    t.check(`COBERTURA: nenhum motivo de NOT_SECRET ficou velho (todos aparecem em algum arquivo; ${flagged} opções protegidas no total)`, Object.keys(NOT_SECRET).every((name) => usedExceptions.has(name)) && flagged >= 8,
      Object.keys(NOT_SECRET).filter((name) => !usedExceptions.has(name)).join(','));

    console.log('== busca, validação e histórico');
    const queries = ['password', 'webhook', 'url', 'secret', 'SBTEST', 'storage'];
    const searched = [];
    for (const query of queries) searched.push(await dash.rpc('config.search', { query }));
    t.check('config.search acha pelo NOME (password, webhook...) mas nenhuma resposta traz um segredo', searched.every((r) => r.ok) && searched[0].result.hits.length > 0 && searched.every((r) => leaks(r).length === 0), JSON.stringify(searched.flatMap((r) => leaks(r))));
    t.check('buscar o próprio segredo não acha nada (o valor não é pesquisável)', (await dash.rpc('config.search', { query: secrets[0] })).result.hits.length === 0 && (await dash.rpc('config.search', { query: 'SBTEST-' })).result.hits.length === 0);
    const hooks = list.find((e) => e.file === 'webhook.conf');
    const hookTarget = { module: hooks.module, file: hooks.file };
    const hookSecret = injected.find((i) => i.label === hooks.label && i.key === 'webhook_url').secret;
    const masked = (await dash.rpc('config.get', hookTarget)).result.values;
    const valid = await dash.rpc('config.validate', { ...hookTarget, values: masked });
    t.check('config.validate aceita o que o site recebeu (as marcas) e a resposta não traz segredo', valid.ok && valid.result.ok === true && leaks(valid).length === 0, JSON.stringify(valid.result || valid.error).slice(0, 150));
    const typedSecret = `https://discord.com/api/webhooks/777/SBTEST-TYPED-${rand()}`;
    const broken = { ...clone(masked), webhook_url: typedSecret, username: { nested: typedSecret } };
    const invalid = await dash.rpc('config.validate', { ...hookTarget, values: broken });
    t.check('um valor errado com um segredo novo junto: o erro aparece e a resposta não repete o segredo digitado nem o do arquivo', invalid.ok && invalid.result.ok === false && !text(invalid).includes(typedSecret) && leaks(invalid).length === 0, JSON.stringify(invalid.result || invalid.error).slice(0, 200));
    t.check('config.history não traz segredo', leaks(await dash.rpc('config.history', hookTarget)).length === 0);

    console.log('== salvar sem mexer no segredo');
    const readHook = () => fs.readFileSync(fileOf(hooks), 'utf8');
    const first = await dash.rpc('config.save', { ...hookTarget, values: masked });
    t.check('config.save com as marcas como vieram: ok, nada mudou e a resposta não tem segredo', first.ok && first.result.ok === true && first.result.changes.length === 0 && leaks(first).length === 0, JSON.stringify(first.result || first.error).slice(0, 200));
    const afterFirst = readHook();
    t.check('o segredo continua no arquivo (o marcador nunca é gravado por cima)', afterFirst.includes(hookSecret) && !afterFirst.includes('$secret'), afterFirst.slice(0, 200));
    const again = await dash.rpc('config.save', { ...hookTarget, values: (await dash.rpc('config.get', hookTarget)).result.values });
    t.check('salvar de novo sem mexer deixa o arquivo IDÊNTICO byte a byte', again.result.ok === true && again.result.changes.length === 0 && readHook() === afterFirst);

    console.log('== mudar outra opção, trocar o segredo, limpar');
    const other = clone(masked);
    const botName = `SecretsBot-${rand()}`;
    other.username = botName;
    const renamed = await dash.rpc('config.save', { ...hookTarget, values: other });
    t.check('mudar só o nome: a única mudança é "username" e o segredo continua igual no arquivo', renamed.result.ok === true && renamed.result.changes.length === 1 && renamed.result.changes[0].path === 'username' && readHook().includes(hookSecret) && readHook().includes(botName) && leaks(renamed).length === 0, JSON.stringify(renamed.result).slice(0, 200));
    const swap = clone(other);
    swap.webhook_url = typedSecret;
    const swapped = await dash.rpc('config.save', { ...hookTarget, values: swap });
    const change = (swapped.result.changes || []).find((c) => c.path === 'webhook_url');
    t.check('trocar o segredo: o arquivo tem o novo, a resposta diz que mudou (sem valor) e não repete nenhum segredo', swapped.result.ok === true && readHook().includes(typedSecret) && !readHook().includes(hookSecret) && change && change.secret === true && change.change === 'changed' && change.from === undefined && change.to === undefined && !text(swapped).includes(typedSecret) && leaks(swapped).length === 0, JSON.stringify(swapped.result).slice(0, 220));
    const audit = (await dash.rpc('audit.list', { limit: 20 }));
    t.check('a auditoria anotou o nome da opção (webhook.conf [webhook_url]) e nenhum valor', /webhook\.conf \[[^\]]*webhook_url/.test(text(audit)) && leaks(audit).length === 0 && !text(audit).includes(typedSecret), text(audit).slice(0, 200));
    const swapMasked = (await dash.rpc('config.get', hookTarget)).result.values;
    const cleared = clone(swapMasked);
    cleared.webhook_url = { $secret: 'clear' };
    const wipe = await dash.rpc('config.save', { ...hookTarget, values: cleared });
    const wiped = (wipe.result.changes || []).find((c) => c.path === 'webhook_url');
    t.check('limpar (ação explícita): o campo fica vazio no arquivo e a resposta diz "cleared" sem valor', wipe.result.ok === true && /"webhook_url":\s*""/.test(readHook()) && wiped && wiped.change === 'cleared' && leaks(wipe).length === 0 && !text(wipe).includes(typedSecret), readHook().slice(0, 300));
    const lost = clone(masked);
    lost.webhook_url = { $secret: 'set', at: ['webhook_url'] };
    lost.username = { $secret: 'set', at: ['webhook_url'] };
    await dash.rpc('config.save', { ...hookTarget, values: swap });
    const sneaky = await dash.rpc('config.save', { ...hookTarget, values: lost });
    t.check('uma marca em uma opção que o site enxerga NÃO leva o segredo para lá (não dá para ler de volta)', sneaky.result.ok === true && !/"username":\s*"SBTEST/.test(readHook()) && !(await dash.rpc('config.get', hookTarget)).result.values.username?.startsWith?.('SBTEST') && !text(await dash.rpc('config.get', hookTarget)).includes(typedSecret), readHook().slice(0, 300));

    console.log('== restaurar e logs ao vivo');
    const backups = (await dash.rpc('config.history', hookTarget)).result.backups;
    const dir = path.dirname(fileOf(hooks));
    const withOld = [...backups].reverse().find((b) => fs.readFileSync(path.join(dir, b.id), 'utf8').includes(hookSecret)); // a mais antiga: o arquivo como o teste o encontrou
    const restored = withOld ? await dash.rpc('config.restore', { ...hookTarget, backup: withOld.id }) : null;
    t.check('config.restore devolve o arquivo exatamente como na cópia (o segredo dela volta) e responde só com marcas', Boolean(withOld) && restored.ok && readHook().includes(hookSecret) && leaks(restored).length === 0, withOld ? JSON.stringify(restored.result || restored.error).slice(0, 160) : 'nenhuma cópia com o segredo antigo');
    await dash.rpc('config.reload', { module: 'Dashboard', file: 'config.json' }); // o mod lê os segredos de novo (para esconder dos logs)
    t.rcon(`say ${hookSecret}`);
    await t.sleep(1200);
    const logs = await dash.rpc('logs.tail', { lines: 150 });
    const line = (logs.result.lines || []).find((l) => /Server\]|Rcon/.test(l.text) && l.text.includes('…'));
    t.check('uma linha do log que cita o endereço do webhook chega ao site sem ele (com "…" no lugar)', logs.ok && leaks(logs).length === 0 && Boolean(line), line ? line.text.slice(0, 120) : 'a linha não apareceu: ' + text(logs).slice(0, 160));

    dash.ws.send({ type: 'subscribe', topic: 'logs.live' });
    await t.sleep(600);
    t.rcon(`say SBSECRETS-LIVE ${hookSecret}`);
    let liveLine = null;
    let liveSafe = true;
    const deadline = Date.now() + 5000;
    while (!liveLine && Date.now() < deadline) {
      try {
        const event = await dash.ws.next(Math.max(1, deadline - Date.now()));
        liveSafe = liveSafe && leaks(event).length === 0;
        if (event.type === 'event' && event.topic === 'logs.live') liveLine = event.data?.lines?.find((item) => item.text.includes('SBSECRETS-LIVE'));
      } catch (error) { break; }
    }
    dash.ws.send({ type: 'unsubscribe', topic: 'logs.live' });
    t.check('logs.live entrega o evento real do mod com o segredo oculto', liveSafe && liveLine?.text.includes('…') && !text(liveLine).includes(hookSecret), liveLine ? liveLine.text.slice(0, 120) : 'nenhum evento de log recebido');
    const secretQuery = await dash.rpc('logs.tail', { lines: 150, query: hookSecret });
    t.check('buscar no log pelo segredo não encontra o texto cru escondido no buffer', secretQuery.ok && secretQuery.result.lines.length === 0);

    console.log('== o disco do servidor: nem a auditoria nem o log guardam um segredo');
    const auditFile = fs.readFileSync(ROOT + '/dashboard-audit.log', 'utf8');
    const logFile = fs.existsSync(RUN + '/logs/latest.log') ? fs.readFileSync(RUN + '/logs/latest.log', 'utf8') : '';
    const spoken = hookSecret; // este o teste mesmo falou no console: ele está no log do servidor e só nele
    const others = secrets.filter((s) => s !== spoken);
    t.check('dashboard-audit.log não tem nenhum segredo', secrets.concat(typedSecret).every((s) => !auditFile.includes(s)));
    t.check('o log do servidor não tem nenhum dos segredos que o teste não falou no console (nem o que foi digitado no painel)', others.every((s) => !logFile.includes(s)) && !logFile.includes(typedSecret.split('/').pop()), others.filter((s) => logFile.includes(s)).join(','));
  } finally {
    // devolve tudo como estava: o texto original dos arquivos e sem as cópias .bak que o teste fez
    restoreAll();
    dash.close();
  }
  t.check('no fim os arquivos que o teste mexeu estão com o texto original', [...originals].every(([file, original]) => fs.readFileSync(file, 'utf8') === original));
  await t.sleep(300);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
