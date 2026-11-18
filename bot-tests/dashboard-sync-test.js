// /sb dashboard sync: o código do pareamento vem clicável (copia para a área de transferência) e o link da dashboard abre o navegador;
// o convite que os admins recebem ao entrar também. Precisa do servidor de teste rodando CONTRA UM SITE LOCAL, nunca o de produção:
//   JAVA_TOOL_OPTIONS="-Dserverbuddy.api=http://localhost:3001" ./gradlew runServer
// Usa o jogador EsA (operador nível 4 no ops.json). Grava o código em SB_CODE_FILE (se definido) para o teste no navegador.
const fs = require('fs');
const t = require('./lib.js');

const SITE = process.env.SB_SITE || 'http://localhost:3001';
const nbt = t.lib('prismarine-nbt');

/** Tudo o que o servidor mandou como mensagem de sistema, já em JSON simples (texto, cliques e dicas). */
function collect(bot) {
  const packets = [];
  bot._client.on('system_chat', (packet) => {
    try {
      packets.push(nbt.simplify(packet.content));
    } catch (e) {
      packets.push({ erro: String(e.message) });
    }
  });
  return packets;
}

const flat = (value) => JSON.stringify(value);
const textOf = (value) => (value == null ? '' : typeof value === 'string' ? value : Array.isArray(value) ? value.map(textOf).join('') : `${value.text ?? ''}${textOf(value.extra)}`);
/** Todos os pedaços do componente que têm um evento de clique, com a ação e o valor. */
function clicks(value, found = []) {
  if (value && typeof value === 'object') {
    const click = value.clickEvent ?? value.click_event;
    if (click) found.push({ action: click.action, value: click.value ?? click.url ?? click.command, text: textOf(value) });
    for (const child of Object.values(value)) clicks(child, found);
  }
  return found;
}
const hovers = (value) => /hoverEvent|hover_event/.test(flat(value));

(async () => {
  const online = t.rcon('list');
  if (/EsA/.test(online)) throw new Error('EsA já está online');
  t.rcon('sb dashboard disconnect');
  const bot = await t.join('EsA');
  const packets = collect(bot);
  await t.sleep(1200);

  console.log('== o convite (quem tem a permissão da dashboard e ainda não ligou)');
  bot.chat('/sb dashboard disconnect');
  await t.sleep(800);
  bot.chat('/sb dashboard sync');
  await t.sleep(6000);
  const all = packets.map((p) => ({ text: textOf(p), clicks: clicks(p), json: p }));
  console.log(all.map((p) => '  [msg] ' + p.text).filter((l) => /dashboard|Pairing|code|Type it/i.test(l)).join('\n'));

  const codeLine = all.find((p) => /Pairing code/.test(p.text));
  t.check('o chat mostra a linha do código de pareamento', Boolean(codeLine), all.map((p) => p.text).join(' | ').slice(0, 300));
  const code = (codeLine?.text.match(/SB-[A-Z0-9]{4}-[A-Z0-9]{4}/) || [])[0];
  t.check('o código vem no formato SB-XXXX-XXXX', Boolean(code), codeLine?.text);
  const copy = codeLine?.clicks.find((c) => c.action === 'copy_to_clipboard');
  t.check('clicar no código COPIA o código para a área de transferência', copy?.value === code, flat(codeLine?.clicks));
  t.check('o código tem uma dica ao passar o mouse', Boolean(codeLine) && hovers(codeLine.json));
  t.check('o código só aparece para quem rodou o comando', t.rcon('list').includes('EsA') && packets.filter((p) => textOf(p).includes(code ?? '#')).length === 1);

  const linkLine = all.find((p) => p.clicks.some((c) => c.action === 'open_url'));
  const open = linkLine?.clicks.find((c) => c.action === 'open_url');
  t.check('o link da dashboard abre o navegador (open_url)', open?.value === `${SITE}/dashboard`, flat(linkLine?.clicks));
  t.check('o link também tem a dica', Boolean(linkLine) && hovers(linkLine.json));
  t.check('o texto do link é o endereço completo', linkLine?.text.includes(`${SITE}/dashboard`), linkLine?.text);
  if (process.env.SB_DEBUG) console.log('  [json do link] ' + flat(linkLine?.json));

  console.log('== /sb dashboard status');
  const status = t.rcon('sb dashboard status');
  t.check('o status diz que espera o código no site', /waiting for the code to be typed on the site|connecting/.test(status), status.replace(/\n/g, ' | '));

  if (process.env.SB_CODE_FILE && code) fs.writeFileSync(process.env.SB_CODE_FILE, code);
  console.log('== fim' + (code ? ` (código ${code})` : ''));
  bot.quit();
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
