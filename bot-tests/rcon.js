// Cliente RCON minimo (so para o teste local do ServerBuddy). uso: node rcon.js <porta> <senha> <cmd1> <cmd2> ...
const net = require('net');
const [, , port, password, ...commands] = process.argv;
function packet(id, type, body) {
  const b = Buffer.from(body, 'utf8');
  const buf = Buffer.alloc(14 + b.length);
  buf.writeInt32LE(10 + b.length, 0); buf.writeInt32LE(id, 4); buf.writeInt32LE(type, 8);
  b.copy(buf, 12); buf.writeInt16LE(0, 12 + b.length);
  return buf;
}
const sock = net.connect(Number(port), '127.0.0.1');
let buffer = Buffer.alloc(0), authed = false, idx = 0;
function next() { if (idx >= commands.length) { sock.end(); return; } sock.write(packet(2 + idx, 2, commands[idx++])); }
sock.on('connect', () => sock.write(packet(1, 3, password)));
sock.on('data', d => {
  buffer = Buffer.concat([buffer, d]);
  while (buffer.length >= 4) {
    const len = buffer.readInt32LE(0);
    if (buffer.length < len + 4) break;
    const id = buffer.readInt32LE(4);
    const body = buffer.toString('utf8', 12, 4 + len - 2);
    buffer = buffer.subarray(len + 4);
    if (!authed) { if (id === -1) { console.log('AUTH FALHOU'); process.exit(2); } authed = true; next(); }
    else { console.log('> ' + commands[idx - 1] + '\n' + body.trim() + '\n'); next(); }
  }
});
sock.on('error', e => { console.log('ERRO: ' + e.message); process.exit(1); });
sock.setTimeout(60000, () => { console.log('TIMEOUT'); process.exit(3); });
