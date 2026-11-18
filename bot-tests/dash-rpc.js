// Cliente da dashboard para os testes: pega o código do /sb dashboard sync com um bot, liga uma conta no site LOCAL e abre o WebSocket como o
// navegador faria. Assim os testes passam por tudo (hub, plano, mod) sem abrir o navegador. SÓ contra um site local:
//   JAVA_TOOL_OPTIONS="-Dserverbuddy.api=http://localhost:3001" ./gradlew runServer   (e o site em localhost:3001)
// Variáveis: SB_SITE (padrão http://localhost:3001), SB_SITE_DIR (a pasta do site, de onde vem o cliente de WebSocket dos testes dele),
// SB_ACCOUNT e SB_PASSWORD (uma conta que já existe nesse site; padrão qa_real).
const { pathToFileURL } = require('url');
const t = require('./lib.js');

const SITE = process.env.SB_SITE || 'http://localhost:3001';
const SITE_DIR = process.env.SB_SITE_DIR || 'D:/sites/serverbuddy/.claude/worktrees/mod-serverbuddy-3626d3';
const nbt = t.lib('prismarine-nbt');

const textOf = (value) => (value == null ? '' : typeof value === 'string' ? value : Array.isArray(value) ? value.map(textOf).join('') : `${value.text ?? ''}${value[''] ?? ''}${textOf(value.extra)}`);

/** Um operador (EsA, nível 4) entra, roda /sb dashboard sync e devolve o código que o mod mostrou só para ele. */
async function syncCode() {
  t.rcon('sb dashboard disconnect');
  const bot = await t.join('EsA');
  const lines = [];
  bot._client.on('system_chat', (packet) => {
    try { lines.push(textOf(nbt.simplify(packet.content))); } catch (e) { /* mensagem que o teste não lê */ }
  });
  bot.chat('/sb dashboard sync');
  let code = null;
  for (let i = 0; i < 40 && !code; i++) {
    await t.sleep(500);
    code = (lines.join('\n').match(/SB-[A-Z0-9]{4}-[A-Z0-9]{4}/) || [])[0] || null;
  }
  bot.quit();
  await t.sleep(600);
  if (!code) throw new Error('o mod não mostrou o código do pareamento: ' + lines.slice(-4).join(' | '));
  return code;
}

/** Liga a conta ao servidor de teste e devolve `{ rpc(método, parâmetros), state, close() }`. */
async function connect({ username = process.env.SB_ACCOUNT || 'qa_real', password = process.env.SB_PASSWORD || 'senha-segura-123' } = {}) {
  const { connectWs } = await import(pathToFileURL(SITE_DIR + '/server/tests/wsClient.js').href);
  const code = await syncCode();
  const login = await fetch(SITE + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
  if (login.status !== 200) throw new Error(`login de ${username} no site local falhou: ${login.status}`);
  const cookie = (login.headers.get('set-cookie') || '').split(';')[0];
  const linked = await fetch(SITE + '/api/me/dashboard/link', { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: cookie }, body: JSON.stringify({ code }) });
  const body = await linked.json();
  if (linked.status !== 201) throw new Error(`ligar o servidor falhou: ${linked.status} ${JSON.stringify(body)}`);
  const ws = await connectWs(SITE, `/ws/dashboard?link=${body.link.id}`, { Cookie: cookie });
  if (ws.status !== 101) throw new Error('o WebSocket da dashboard foi recusado: ' + ws.status);
  const state = await ws.next(5000);
  let counter = 0;
  /** Roda uma função do mod e devolve a resposta inteira do hub: `{ ok, result }` ou `{ ok: false, error: { code, message? } }`. */
  async function rpc(method, params = {}, ms = 30000) {
    const id = 'r' + (++counter);
    ws.send({ type: 'rpc', id, method, params });
    for (;;) {
      const message = await ws.next(ms);
      if (message.type === 'rpc' && message.id === id) return message;
    }
  }
  return { rpc, state, ws, link: body.link, close: () => ws.close() };
}

module.exports = { connect, syncCode, SITE };
