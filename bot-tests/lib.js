// Utilitários do bot de teste do ServerBuddy (só para o servidor local de desenvolvimento, em modo offline).
const { createRequire } = require('module');
const { spawnSync } = require('child_process');
const fs = require('fs');

const path = require('path');

// pasta dos scripts (aqui), pasta do node_modules do mineflayer (ver o README) e o servidor de teste do mod
const S = __dirname;
const LIB = process.env.SB_BOT_LIB || [
  path.join(__dirname, '..', 'bot-tests-lib'),
  path.join(__dirname, '..', '..', 'ServerBuddy-pesquisa', 'bot-tests-lib'),
].find((dir) => fs.existsSync(path.join(dir, 'node_modules', 'mineflayer'))) || path.join(__dirname, '..', 'bot-tests-lib');
const RUN = process.env.SB_RUN || 'D:/mods/ServerBuddy/run';
const lib = createRequire(path.join(LIB, 'index.js'));
const mineflayer = lib('mineflayer');

/** Lê uma chave do server.properties do servidor de teste. */
function prop(name) {
  const line = fs.readFileSync(RUN + '/server.properties', 'utf8').split(/\r?\n/).find((l) => l.startsWith(name + '='));
  return line ? line.slice(name.length + 1).trim() : '';
}
const PW = prop('rcon.password');
const RCON_PORT = prop('rcon.port') || '25575';
const GAME_PORT = Number(prop('server-port') || 25565);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Roda comandos no console do servidor por RCON e devolve a saída. */
function rcon(...cmds) {
  const r = spawnSync('node', [path.join(S, 'rcon.js'), RCON_PORT, PW, ...cmds], { encoding: 'utf8' });
  return ((r.stdout || '') + (r.stderr || '')).trim();
}

async function join(name = 'SbBot') {
  const bot = mineflayer.createBot({
    host: '127.0.0.1', port: GAME_PORT, username: name, version: '1.21.1', auth: 'offline',
  });
  // A biblioteca do bot não entende alguns pacotes de mods (receitas com componentes de item do Cobblemon) e o erro
  // mata a leitura de tudo que vem depois; então um pacote que não dá para ler vira um pacote vazio e o resto segue.
  const client = bot._client;
  let dropped = 0;
  const guard = () => {
    const d = client.deserializer;
    const orig = d.parsePacketBuffer.bind(d);
    d.parsePacketBuffer = (buf) => {
      try {
        return orig(buf);
      } catch (e) {
        if (bot.rawIgnored) bot.rawIgnored.push(buf);
        if (dropped++ === 0) console.log('  [bot] pacote que o cliente de teste não entende foi ignorado: ' + String(e.message).slice(0, 80));
        return { data: { name: 'unparsed', params: {} }, metadata: { size: buf.length }, buffer: buf, fullBuffer: buf };
      }
    };
  };
  const originalSetSerializer = client.setSerializer.bind(client);
  client.setSerializer = (state) => { originalSetSerializer(state); guard(); };
  guard();
  // Os pacotes de spawn dos Pokémon selvagens vêm em "bundle"; se um deles falha no cliente de teste, o bundle fica
  // aberto e segura os pacotes seguintes (como o do menu). Sem agrupar, cada pacote segue direto.
  Object.defineProperty(client, '_hasBundlePacket', { get: () => false, set: () => {} });

  bot.spawnPayloads = [];
  client.on('custom_payload', (d) => { if (d.channel === 'cobblemon:spawn_pokemon_entity') bot.spawnPayloads.push(d.data); });
  bot.spawned = new Map();
  client.on('spawn_entity', (d) => { bot.spawned.set(String(d.objectUUID), { id: d.entityId, type: d.type, x: d.x, y: d.y, z: d.z }); });
  bot.chatLog = [];
  let lastLine = '';
  let repeats = 0;
  bot.on('message', (m) => {
    const t = m.toString();
    bot.chatLog.push(t);
    if (t === lastLine) { repeats++; return; }
    if (repeats > 0) console.log(`  [chat] (a linha acima repetiu mais ${repeats}x)`);
    repeats = 0;
    lastLine = t;
    console.log('  [chat] ' + t);
  });
  // erros de leitura de pacote do cliente de teste (receitas com componentes de mods) não atrapalham: só avisa uma vez
  let parseErrors = 0;
  bot.on('error', (e) => {
    if (String(e.message).startsWith('Parse error')) {
      if (parseErrors++ === 0) console.log('  [bot] pacote ignorado pelo cliente de teste: ' + e.message.slice(0, 90));
      return;
    }
    console.log('  [erro do bot] ' + e.message);
  });
  bot.on('kicked', (r) => console.log('  [kick] ' + JSON.stringify(r)));
  await new Promise((res, rej) => {
    bot.once('spawn', res);
    bot.once('end', () => rej(new Error('o bot foi desconectado antes do spawn')));
    setTimeout(() => rej(new Error('timeout esperando o spawn')), 40000);
  });
  await sleep(1500);
  return bot;
}

/** Espera o servidor abrir um menu. Usa o pacote open_window (o evento windowOpen do mineflayer só sai depois de ler os itens, e ele não lê itens de mods). */
/** Ping da lista de servidores (o que o menu Multiplayer recebe). */
async function ping() {
  const mc = lib('minecraft-protocol');
  return await mc.ping({ host: '127.0.0.1', port: GAME_PORT, version: '1.21.1' });
}

function waitWindow(bot, timeout = 8000) {
  return new Promise((res, rej) => {
    const onOpen = () => { clearTimeout(timer); setTimeout(() => res(bot.currentWindow), 500); };
    const timer = setTimeout(() => { bot._client.removeListener('open_window', onOpen); rej(new Error('a janela não abriu')); }, timeout);
    bot._client.once('open_window', onOpen);
  });
}

/** Abre um menu pelo comando e espera o conteúdo chegar. */
async function openByCommand(bot, command) {
  const waiting = waitWindow(bot);
  bot.chat(command);
  const w = await waiting;
  await sleep(900);
  return w;
}

function plain(x) {
  if (x == null) return '';
  try {
    if (typeof x === 'string') {
      try { return plain(JSON.parse(x)); } catch (e) { return x; }
    }
    if (x.toString && x.constructor && x.constructor.name === 'ChatMessage') return x.toString();
    if (x.value !== undefined) return plain(x.value);
    return JSON.stringify(x);
  } catch (e) { return String(x); }
}

const nbt = lib('prismarine-nbt');

/** Junta o texto de um componente de texto (o título vem como NBT de rede; strings soltas vêm como {"": "texto"}). */
function textOf(c) {
  if (c == null) return '';
  if (typeof c === 'string') return c;
  if (Array.isArray(c)) return c.map(textOf).join('');
  let s = '';
  if (c[''] !== undefined) s += textOf(c['']);
  if (c.text !== undefined) s += c.text;
  if (c.translate) s += '<' + c.translate + '>';
  if (c.extra) s += textOf(c.extra);
  return s;
}

function titleOf(w) {
  let tt = w.title;
  try {
    if (tt && tt.type) tt = nbt.simplify(tt);
    else if (typeof tt === 'string') tt = JSON.parse(tt);
  } catch (e) { /* devolve como está */ }
  return textOf(tt);
}

/** O texto simples do nome e da descrição de um item (os componentes custom_name, item_name e lore). */
function itemText(it) {
  if (!it || !it.components) return '';
  const parts = [];
  for (const c of it.components) {
    if (c.type === 'item_name' || c.type === 'custom_name') {
      try { parts.push('nome=' + textOf(nbt.simplify({ type: c.data.type, name: '', value: c.data.value }))); } catch (e) { parts.push('nome=?'); }
    }
    if (c.type === 'lore') {
      try { parts.push('lore=[' + c.data.map((l) => textOf(nbt.simplify({ type: l.type, name: '', value: l.value }))).join(' / ') + ']'); } catch (e) { parts.push('lore=?'); }
    }
  }
  return parts.join(' ');
}

function label(it) {
  let n = '';
  try { n = it.displayName; } catch (e) { /* sem nome */ }
  return n && n !== it.name ? ` "${n}"` : '';
}

/** Resumo dos slots do menu (sem o inventário do jogador). */
function slots(w) {
  const out = [];
  const end = w.inventoryStart !== undefined ? w.inventoryStart : w.slots.length;
  for (let i = 0; i < end; i++) {
    const it = w.slots[i];
    if (it) out.push(`${i}:${it.name}x${it.count}${label(it)}`);
  }
  return out;
}

/** Clica num slot do menu aberto e devolve a janela atual (o menu reabre quando o título muda, então a antiga fica velha). */
async function click(bot, slot, button = 0, mode = 0) {
  try {
    await bot.clickWindow(slot, button, mode);
  } catch (e) {
    console.log('  (clickWindow: ' + e.message + ')');
  }
  await sleep(1000);
  return bot.currentWindow;
}

/** Quantos itens o jogador tem de verdade (pergunta ao servidor; o inventário local do bot não acompanha os menus). */
function count(bot, name) {
  const id = name.includes(':') ? name : 'minecraft:' + name;
  const out = rcon(`clear ${bot.username} ${id} 0`);
  const m = out.match(/Found (\d+) matching item/);
  return m ? Number(m[1]) : 0;
}

/** Lê um JSON que pode ter linhas de comentário (as configs do ServerBuddy nascem comentadas). */
function parseJson(text) { return JSON.parse(text.split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n')); }
function readJson(file) { return parseJson(fs.readFileSync(file, 'utf8')); }

let failures = 0;
function check(label, ok, extra = '') {
  console.log(`${ok ? '  OK  ' : ' FALHA'} ${label}${extra ? ' -> ' + extra : ''}`);
  if (!ok) { failures++; process.exitCode = 1; }
  return ok;
}

/** O Cobblemon spawna o Pokémon com um payload próprio: acha o id de rede da entidade pelo UUID (o id é o varint logo antes do UUID). */
function entityIdByUuid(bot, uuid) {
  const hex = uuid.replace(/-/g, '');
  for (const buf of bot.spawnPayloads || []) {
    const idx = buf.toString('hex').indexOf(hex);
    if (idx < 0 || idx % 2) continue;
    const end = idx / 2 - 1;
    if (end < 0) continue;
    let start = end;
    while (start > 0 && (buf[start - 1] & 0x80)) start--;
    let v = 0, shift = 0;
    for (let i = start; i <= end; i++) { v |= (buf[i] & 0x7f) << shift; shift += 7; }
    return v;
  }
  return null;
}

module.exports = { parseJson, readJson, ping, entityIdByUuid, lib, rcon, join, waitWindow, openByCommand, titleOf, itemText, textOf, slots, click, count, check, sleep, plain, S };
Object.defineProperty(module.exports, 'failures', { get: () => failures });
