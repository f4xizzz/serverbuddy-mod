// Volta a config da Blacklist do servidor de teste ao estado do início do blacklist-test.js (default e mode_tower, sem pvp).
const fs = require('fs');
const t = require('./lib.js');

const CFG = 'D:/mods/ServerBuddy/run/config/ServerBuddy/Blacklist/config.json';
const cfg = JSON.parse(fs.readFileSync(CFG, 'utf8').split(/\r?\n/).filter((l) => !/^\s*\/\//.test(l)).join('\n'));
const ctx = (moves) => ({ when: [], when_any: [], moves, message: null });
cfg.contexts = { default: ctx(['thunderbolt', 'sheercold']), mode_tower: ctx(['explosion']), mode_raid: ctx([]) };
fs.writeFileSync(CFG, JSON.stringify(cfg, null, 2), 'utf8');
console.log(t.rcon('blacklist reload', 'blacklist add default zzzz', 'blacklist remove default zzzz'));
