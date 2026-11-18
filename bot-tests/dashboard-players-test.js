// Dashboard, jogadores ONLINE e OFFLINE: o que o painel vê de um jogador com ele conectado tem que ser o mesmo depois que ele sai (lido do
// disco: playerdata/<uuid>.dat e o storage do Cobblemon). Também confere a lista (filtro online/offline, última vez visto), o perfil,
// os erros (jogador desconhecido, arquivo corrompido) e que consultar um UUID qualquer não cria dados do Cobblemon.
// SÓ contra um site local (veja dash-rpc.js). Usa um jogador novo a cada rodada e o operador EsA (para o código do sync).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const t = require('./lib.js');
const { connect } = require('./dash-rpc.js');

const WORLD = 'D:/mods/ServerBuddy/run/world';
const NAME = 'Pl' + (Date.now() % 1000000);
const strip = (response) => (response.ok ? response.result : { __error: response.error });
const without = (obj, ...keys) => { const copy = JSON.parse(JSON.stringify(obj)); for (const key of keys) delete copy[key]; return copy; };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const nbt = t.lib('prismarine-nbt');
/** O UUID que o servidor em modo offline dá a um nome (MD5 de "OfflinePlayer:nome", como o Java). */
function offlineUuid(name) {
  const hash = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
/** Os arquivos que o Cobblemon guarda de um jogador (Pokédex, time, PC, dados gerais e TMs) que existem no disco. */
function cobblemonFiles(uuid) {
  const dirs = [['pokedex'], ['pokemon', 'pcstore'], ['pokemon', 'playerpartystore'], ['cobblemonplayerdata'], ['tm_moves']];
  // o time e o PC ficam em .dat; os outros em .nbt ou .json (cada um com a cópia .old)
  return dirs.flatMap((dir) => ['.nbt', '.json', '.dat'].flatMap((ext) => [ext, ext + '.old']).map((ext) => path.join(WORLD, ...dir, uuid.slice(0, 2), uuid + ext))).filter((file) => fs.existsSync(file));
}

(async () => {
  console.log('== ligando a dashboard ao servidor de teste (site local)');
  const dash = await connect();
  t.check('a conta de teste está no plano Pro (teste grátis): todos os métodos liberados', dash.state.plan === 'pro' && dash.state.methods.includes('players.enderchest') && dash.state.methods.includes('players.pc'), JSON.stringify(dash.state).slice(0, 200));
  const call = async (method, params) => strip(await dash.rpc(method, { player: NAME, ...params }));

  console.log('== preparando ' + NAME + ' (itens, baú do fim e Pokémon)');
  const bot = await t.join(NAME);
  t.rcon('clear ' + NAME);
  t.rcon(
    `give ${NAME} minecraft:diamond_sword[minecraft:enchantments={levels:{"minecraft:sharpness":5}},minecraft:custom_name='"Excalibur"',minecraft:lore=['"Pulled from a stone"']] 1`,
    `give ${NAME} minecraft:bread 17`,
    `item replace entity ${NAME} armor.head with minecraft:iron_helmet`,
    `item replace entity ${NAME} weapon.offhand with minecraft:shield`,
    `item replace entity ${NAME} enderchest.0 with minecraft:emerald 5`,
    `item replace entity ${NAME} enderchest.26 with minecraft:book`,
    `xp set ${NAME} 12 levels`,
  );
  for (const species of ['pikachu level=50 shiny=true', 'bulbasaur level=5', 'charmander level=7', 'squirtle level=9', 'eevee level=11', 'snorlax level=30', 'mew level=40']) t.rcon(`givepokemonother ${NAME} ${species}`);
  await t.sleep(1500);

  console.log('== com o jogador ONLINE');
  const online = {
    profile: await call('players.get'),
    inventory: await call('players.inventory'),
    ender: await call('players.enderchest'),
    party: await call('players.party'),
    pc: await call('players.pc'),
    dex: await call('players.dex'),
  };
  const slotsOf = (inv) => inv.items.map((i) => i.slot).sort((a, b) => a - b);
  t.check('perfil online: online, nível de XP e dimensão', online.profile.online === true && online.profile.xpLevel === 12 && /overworld/.test(online.profile.dimension || ''), JSON.stringify(online.profile).slice(0, 220));
  const sword = (online.inventory.items || []).find((i) => i.id === 'minecraft:diamond_sword');
  t.check('inventário: a espada tem nome, encantamento e descrição', sword && sword.name === 'Excalibur' && sword.customName && sword.enchants?.[0]?.id === 'minecraft:sharpness' && sword.enchants[0].level === 5 && sword.lore?.[0] === 'Pulled from a stone', JSON.stringify(sword));
  t.check('inventário: elmo na vaga 103 e escudo na mão esquerda (-106), como no arquivo do jogador', slotsOf(online.inventory).includes(103) && slotsOf(online.inventory).includes(-106), String(slotsOf(online.inventory)));
  t.check('baú do fim: 5 esmeraldas na vaga 0 e um livro na 26', online.ender.items?.some((i) => i.slot === 0 && i.id === 'minecraft:emerald' && i.count === 5) && online.ender.items.some((i) => i.slot === 26 && i.id === 'minecraft:book'), JSON.stringify(online.ender).slice(0, 200));
  t.check('time com 6 Pokémon (o Pikachu brilhante em primeiro, com número da Pokédex e IVs)', online.party.pokemon?.length === 6 && online.party.pokemon[0].showdownId === 'pikachu' && online.party.pokemon[0].shiny === true && online.party.pokemon[0].dex === 25 && online.party.pokemon[0].ivs && online.party.pokemon[0].moves?.length > 0, JSON.stringify(online.party.pokemon?.[0]).slice(0, 260));
  t.check('o sétimo Pokémon (Mew) foi para o PC, em formato compacto', online.pc.total === 1 && online.pc.boxes?.[0]?.pokemon?.[0]?.showdownId === 'mew' && !('moves' in online.pc.boxes[0].pokemon[0]), JSON.stringify(online.pc).slice(0, 220));
  t.check('Pokédex responde com números', typeof online.dex.caught === 'number' && typeof online.dex.percent === 'number', JSON.stringify(online.dex));

  console.log('== o jogador sai (os arquivos são gravados) e os mesmos pedidos agora leem do DISCO');
  bot.quit();
  await t.sleep(3500);
  t.check('o servidor já não o tem online', !new RegExp('\\b' + NAME + '\\b').test(t.rcon('list')));
  const offline = {
    profile: await call('players.get'),
    inventory: await call('players.inventory'),
    ender: await call('players.enderchest'),
    party: await call('players.party'),
    pc: await call('players.pc'),
    dex: await call('players.dex'),
  };
  t.check('perfil offline: offline, com última vez visto recente, nível de XP, dimensão e posição', offline.profile.online === false && Date.now() - offline.profile.lastSeen < 120000 && offline.profile.xpLevel === 12 && /overworld/.test(offline.profile.dimension) && typeof offline.profile.x === 'number', JSON.stringify(offline.profile).slice(0, 260));
  t.check('o inventário offline é IGUAL ao online (itens, nomes, encantos, vagas)', same(online.inventory.items, offline.inventory.items) && offline.inventory.online === false, JSON.stringify(offline.inventory).slice(0, 200));
  t.check('o baú do fim offline é IGUAL ao online (e não é um erro)', Array.isArray(offline.ender.items) && offline.ender.items.length === 2 && same(online.ender.items, offline.ender.items), JSON.stringify(offline.ender).slice(0, 200));
  t.check('o time offline é IGUAL ao online (carregou do disco sem o jogador)', same(online.party.pokemon, offline.party.pokemon) && offline.party.online === false, JSON.stringify(offline.party).slice(0, 200));
  t.check('o PC offline é IGUAL ao online', same(online.pc.boxes, offline.pc.boxes) && offline.pc.total === 1, JSON.stringify(offline.pc).slice(0, 200));
  t.check('a Pokédex offline é IGUAL à online', same(without(online.dex, 'online'), without(offline.dex, 'online', 'lastSeen')), JSON.stringify(offline.dex));
  t.check('a resposta offline traz quando o jogador saiu', offline.party.lastSeen > 0 && offline.pc.lastSeen > 0 && offline.dex.lastSeen > 0);

  console.log('== a lista');
  const all = strip(await dash.rpc('players.list', { query: NAME }));
  const mine = all.players?.find((p) => p.name === NAME);
  t.check('a lista acha o jogador offline pelo nome, com uuid e última vez visto', mine && mine.online === false && /^[0-9a-f-]{36}$/.test(mine.uuid) && mine.lastSeen > 0, JSON.stringify(all).slice(0, 240));
  t.check('a lista traz a contagem de online e offline', typeof all.counts?.online === 'number' && all.counts.offline > 1, JSON.stringify(all.counts));
  const byUuid = strip(await dash.rpc('players.list', { query: mine.uuid.slice(0, 8) }));
  t.check('a busca também acha pelo começo do UUID', byUuid.players?.some((p) => p.uuid === mine.uuid));
  const onlyOnline = strip(await dash.rpc('players.list', { status: 'online', limit: 200 }));
  t.check('o filtro "online" não traz quem está offline', !onlyOnline.players.some((p) => p.name === NAME) && onlyOnline.players.every((p) => p.online));
  const onlyOffline = strip(await dash.rpc('players.list', { status: 'offline', limit: 200 }));
  t.check('o filtro "offline" traz o jogador e só offline', onlyOffline.players.some((p) => p.name === NAME) && onlyOffline.players.every((p) => !p.online));
  const sorted = onlyOffline.players.map((p) => p.lastSeen);
  t.check('os offline vêm do mais recente para o mais antigo (o jogador novo em primeiro)', onlyOffline.players[0].name === NAME && sorted.every((v, i) => i === 0 || sorted[i - 1] >= v));
  const page = strip(await dash.rpc('players.list', { status: 'offline', offset: 1, limit: 2 }));
  t.check('paginação: offset e limite', page.players.length === 2 && page.players[0].uuid === onlyOffline.players[1].uuid && page.total === onlyOffline.total);
  const byUuidProfile = strip(await dash.rpc('players.get', { player: mine.uuid }));
  t.check('o perfil também aceita o UUID no lugar do nome', byUuidProfile.name === NAME && byUuidProfile.online === false);

  console.log('== erros que dizem o que houve');
  const unknown = await dash.rpc('players.get', { player: 'NoSuchPlayerXyz' });
  t.check('jogador que o servidor nunca viu: player_not_found', unknown.ok === false && unknown.error.code === 'player_not_found', JSON.stringify(unknown));
  const ghost = crypto.randomUUID();
  const ghostParty = await dash.rpc('players.party', { player: ghost });
  t.check('UUID sem arquivo de jogador: player_not_found', ghostParty.ok === false && ghostParty.error.code === 'player_not_found', JSON.stringify(ghostParty));
  const created = ['playerpartystore', 'pcstore'].some((dir) => fs.existsSync(path.join(WORLD, 'pokemon', dir, ghost.slice(0, 2), ghost + '.nbt')));
  t.check('consultar esse UUID NÃO criou dados do Cobblemon para ele', !created);
  const corrupt = crypto.randomUUID();
  const file = path.join(WORLD, 'playerdata', corrupt + '.dat');
  fs.writeFileSync(file, Buffer.from('isto não é um arquivo NBT de jogador'));
  try {
    const bad = await dash.rpc('players.inventory', { player: corrupt });
    t.check('arquivo de jogador corrompido: player_data_unreadable, com mensagem', bad.ok === false && bad.error.code === 'player_data_unreadable' && /corrupted|unreadable/.test(bad.error.message || ''), JSON.stringify(bad));
    const badParty = await dash.rpc('players.party', { player: corrupt });
    t.check('o mesmo UUID no time do Cobblemon também responde sem derrubar nada', badParty.ok === false || badParty.result?.pokemon?.length === 0, JSON.stringify(badParty).slice(0, 200));
    // quem o servidor conhece mas nunca teve dados do Cobblemon: o painel responde vazio e NÃO cria nada (o carregador do próprio Cobblemon criaria)
    const badDex = await dash.rpc('players.dex', { player: corrupt });
    t.check('Pokédex de quem não tem dados do Cobblemon: zeros, sem erro', badDex.ok && badDex.result.caught === 0 && badDex.result.seen === 0, JSON.stringify(badDex).slice(0, 200));
    const badPc = await dash.rpc('players.pc', { player: corrupt });
    t.check('PC de quem não tem dados do Cobblemon: vazio, sem erro', badPc.ok === false || badPc.result?.total === 0, JSON.stringify(badPc).slice(0, 200));
    await t.sleep(2000); // o Cobblemon grava em segundo plano: se fosse criar algo, já teria criado
    const made = cobblemonFiles(corrupt);
    t.check('consultar quem não tem dados do Cobblemon NÃO criou nenhum arquivo dele (Pokédex, time, PC, dados gerais, TMs)', made.length === 0, made.join(', '));
  } finally {
    fs.rmSync(file, { force: true });
  }
  const after = await call('players.get');
  t.check('depois dos erros o servidor segue respondendo', after.name === NAME);

  console.log('== o jogador entra de novo: volta a ser lido ao vivo');
  const again = await t.join(NAME);
  t.rcon(`give ${NAME} minecraft:apple 3`);
  await t.sleep(800);
  const live = await call('players.inventory');
  t.check('online outra vez: o item novo aparece na hora (não vem de cache nem do arquivo velho)', live.online === true && live.items.some((i) => i.id === 'minecraft:apple' && i.count === 3), JSON.stringify(live.items?.slice(-2)));
  const liveDex = await call('players.dex');
  t.check('online outra vez: a Pokédex é a de antes (a leitura offline não deixou dado vazio no Cobblemon)', liveDex.online === true && same(without(online.dex, 'online'), without(liveDex, 'online')), JSON.stringify(liveDex));
  const liveParty = await call('players.party');
  t.check('online outra vez: o time é o de antes', same(online.party.pokemon, liveParty.pokemon), JSON.stringify(liveParty).slice(0, 160));
  again.quit();
  await t.sleep(3000);
  const afterLeave = await call('players.inventory');
  t.check('ao sair de novo, o disco traz o item novo (o cache offline foi esquecido)', afterLeave.online === false && afterLeave.items.some((i) => i.id === 'minecraft:apple'), JSON.stringify(afterLeave.items?.slice(-2)));

  // o que ficou no disco do Cobblemon depois de tudo (a leitura do painel não pode ter estragado nem apagado nada)
  await t.sleep(2500);
  const dexFile = path.join(WORLD, 'pokedex', offlineUuid(NAME).slice(0, 2), offlineUuid(NAME) + '.nbt');
  let species = [];
  try { species = Object.keys(nbt.simplify((await nbt.parse(fs.readFileSync(dexFile))).parsed).speciesRecords || {}); } catch (e) { species = ['ERRO: ' + e.message]; }
  t.check('o arquivo da Pokédex no disco está inteiro (Pikachu e Mew) depois das leituras offline', species.includes('cobblemon:pikachu') && species.includes('cobblemon:mew'), species.join(', '));

  t.rcon('sb storage clear ' + NAME);
  dash.close();
  console.log('== fim (jogador ' + NAME + ')');
  await t.sleep(500);
  process.exit(process.exitCode || 0);
})().catch((e) => { console.log('ERRO NO TESTE: ' + (e && e.stack || e)); process.exit(2); });
