#!/usr/bin/env node
// Имитатор FlashForge Adventurer 4 (TCP 8899) для проверки приложения без принтера.
// Запуск:  node tools/mock-printer.js [порт]
// В эмуляторе Android указывайте IP 10.0.2.2, на телефоне — IP компьютера в той же сети.
'use strict';
const net = require('net');
const zlib = require('zlib');

const PORT = Number(process.argv[2]) || 8899;
const PACKET = 16 + 4096;

const p = {
  status: 'READY', move: 'READY', led: 1, file: '',
  nozzle: { cur: 24, target: 0 }, bed: { cur: 23, target: 0 },
  progress: 0, layer: 0, layers: 200,
  files: ['cube.gx', 'benchy.gx', 'vase.gcode'],
};

setInterval(() => {
  const approach = (t) => { t.cur += Math.sign((t.target || 22) - t.cur) * Math.min(3, Math.abs((t.target || 22) - t.cur)); };
  approach(p.nozzle); approach(p.bed);
  if (p.status === 'BUILDING_FROM_SD' && p.move !== 'PAUSED') {
    p.progress = Math.min(100, p.progress + 1);
    p.layer = Math.round(p.layers * p.progress / 100);
    if (p.progress >= 100) { p.status = 'BUILDING_COMPLETED'; p.move = 'READY'; p.nozzle.target = 0; p.bed.target = 0; }
  }
}, 1000);

function fileListBinary() {
  const parts = [Buffer.from([0x44, 0xaa, 0xaa, 0x44]), u32(p.files.length)];
  for (const f of p.files) {
    const name = Buffer.from('/data/' + f, 'utf8');
    parts.push(Buffer.from([0x3a, 0x3a, 0xa3, 0xa3]), u32(name.length), name);
  }
  return Buffer.concat(parts);
}
function u32(n) { const b = Buffer.alloc(4); b.writeUInt32BE(n); return b; }

function handle(line, sock, session) {
  const m = line.replace(/^~/, '').trim();
  const code = m.split(/\s+/)[0];
  const reply = (text = '') => sock.write(`CMD ${code} Received.\r\n${text}ok\r\n`);
  console.log('>', m);

  if (code !== 'M601' && !session.control) return reply('Control failed.\r\n');
  switch (code) {
    case 'M601': session.control = true; return reply('Control Success V2.1.\r\n');
    case 'M602': session.control = false; return reply('Control Release.\r\n');
    case 'M115': return reply('Machine Type: FlashForge Adventurer 4\r\nMachine Name: Mock Adventurer\r\nFirmware: v2.2.4-mock\r\nSN: SNMOCK0001\r\nX: 220 Y: 200 Z: 250\r\nTool Count: 1\r\nMac Address:00:11:22:33:44:55\r\n');
    case 'M119': return reply(`Endstop: X-max:0 Y-max:0 Z-max:0\r\nMachineStatus: ${p.status}\r\nMoveMode: ${p.move}\r\nStatus: S:1 L:0 J:0 F:0\r\nLED: ${p.led}\r\nCurrentFile: ${p.file}\r\n`);
    case 'M105': return reply(`T0:${p.nozzle.cur.toFixed(1)}/${p.nozzle.target.toFixed(1)} B:${p.bed.cur.toFixed(1)}/${p.bed.target.toFixed(1)}\r\n`);
    case 'M27': return reply(`SD printing byte ${p.progress}/100\r\nLayer: ${p.layer}/${p.layers}\r\n`);
    case 'M661': reply('List files.\r\n'); return sock.write(fileListBinary());
    case 'M23': {
      const name = m.slice(4).replace(/^0:\/user\//, '');
      if (!p.files.includes(name)) return reply('Open file failed.\r\n');
      Object.assign(p, { status: 'BUILDING_FROM_SD', move: 'MOVING', file: name, progress: 0, layer: 0 });
      p.nozzle.target = 210; p.bed.target = 60;
      return reply(`File opened: ${name} Size: 1000\r\nFile selected\r\n`);
    }
    case 'M24': p.move = 'MOVING'; return reply();
    case 'M25': p.move = 'PAUSED'; return reply();
    case 'M26': Object.assign(p, { status: 'READY', move: 'READY', file: '', progress: 0 }); p.nozzle.target = 0; p.bed.target = 0; return reply();
    case 'M146': p.led = /r0 g0 b0/.test(m) ? 0 : 1; return reply();
    case 'M104': p.nozzle.target = Number((m.match(/S(\d+)/) || [])[1] || 0); return reply();
    case 'M140': p.bed.target = Number((m.match(/S(\d+)/) || [])[1] || 0); return reply();
    case 'G28': case 'G90': case 'G91': case 'G1': return reply();
    case 'M28': {
      const [, size, path] = m.match(/^M28\s+(\d+)\s+(\S+)/) || [];
      session.upload = { size: Number(size), name: path.replace(/^0:\/user\//, ''), received: 0, index: 0 };
      return reply(`Writing to file: ${path}\r\n`);
    }
    case 'M29': return reply('Done saving file.\r\n');
    default: return reply();
  }
}

net.createServer((sock) => {
  console.log('client connected', sock.remoteAddress);
  const session = { control: false, upload: null };
  let buf = Buffer.alloc(0);
  sock.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    for (;;) {
      const up = session.upload;
      if (up && up.received < up.size) {
        if (buf.length < PACKET) return;
        const pkt = buf.subarray(0, PACKET); buf = buf.subarray(PACKET);
        const magic = pkt.readUInt32BE(0), index = pkt.readUInt32BE(4), len = pkt.readUInt32BE(8), crc = pkt.readUInt32BE(12);
        const data = pkt.subarray(16, 16 + len);
        const okCrc = typeof zlib.crc32 === 'function' ? zlib.crc32(data) === crc : true;
        if (magic !== 0x5a5aa5a5 || index !== up.index || !okCrc) console.warn('! bad packet', { magic: magic.toString(16), index, len, okCrc });
        up.index++; up.received += len;
        if (up.received >= up.size) {
          if (!p.files.includes(up.name)) p.files.push(up.name);
          console.log(`upload done: ${up.name} (${up.size} bytes, ${up.index} packets)`);
        }
        continue;
      }
      if (up && up.received >= up.size) session.upload = null;
      const nl = buf.indexOf(0x0a);
      if (nl < 0) return;
      const line = buf.subarray(0, nl).toString('utf8').trim(); buf = buf.subarray(nl + 1);
      if (line) handle(line, sock, session);
    }
  });
  sock.on('error', () => {});
  sock.on('close', () => console.log('client disconnected'));
}).listen(PORT, () => console.log(`Mock Adventurer 4 listening on :${PORT}`));
