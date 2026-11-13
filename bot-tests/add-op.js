// Acrescenta um jogador (UUID offline, como o servidor de teste em modo offline calcula) ao ops.json com um nível dado.
// uso: node add-op.js <nome> <nivel>      (só para o servidor local de desenvolvimento, com o servidor PARADO)
const fs = require('fs');
const crypto = require('crypto');

const FILE = 'D:/mods/ServerBuddy/run/ops.json';
const [name, levelText] = process.argv.slice(2);
const level = Number(levelText);

function offlineUuid(n) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + n, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

const ops = JSON.parse(fs.readFileSync(FILE, 'utf8').replace(/^\uFEFF/, ''));
// confere a conta com um jogador que já está no arquivo (se o nome dele estiver lá)
const known = ops.find((o) => o.name === 'SbBot');
if (known && offlineUuid('SbBot') !== known.uuid) {
  console.log('UUID calculado para SbBot não bate com o do arquivo: ' + offlineUuid('SbBot') + ' vs ' + known.uuid);
  process.exit(1);
}
const uuid = offlineUuid(name);
const existing = ops.find((o) => o.uuid === uuid || o.name === name);
if (existing) {
  existing.level = level;
  existing.uuid = uuid;
} else {
  ops.push({ uuid, name, level, bypassesPlayerLimit: false });
}
fs.writeFileSync(FILE, JSON.stringify(ops, null, 2) + '\n', 'utf8');
console.log(`${name} (${uuid}) agora é op de nível ${level}`);
