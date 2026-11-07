// Draws the cargo crate item texture (design J-2, v0.35): a 16x16 wooden crate with a dark frame, a cross brace and
// a red wax seal. No libraries needed (writes the PNG itself).
// Usage (in Source/TradeExpansion): node tools/icon/gencrate.js src/main/resources/assets/ak_trade_expansion/textures/item/cargo_crate.png
const fs = require('fs');
const zlib = require('zlib');

const W = 16;
const px = Array.from({ length: W }, () => Array(W).fill(null));
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const C = {
  outline: hex('#2B1D12'), frame: hex('#6B4423'), frameLight: hex('#8A5A2E'),
  plank: hex('#B07B42'), plankLight: hex('#C8925A'), plankDark: hex('#94622F'),
  seal: hex('#C8352E'), sealLight: hex('#E85A4F'), sealDark: hex('#8E211C'),
};
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < W && y < W) px[y][x] = c; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };

// body: horizontal planks (3 px each) with a darker seam
rect(1, 2, 14, 14, C.plank);
for (const y of [5, 9, 13]) rect(2, y, 13, y, C.plankDark);
for (const y of [2, 6, 10]) rect(2, y, 13, y, C.plankLight);
// frame: posts on the sides, rails on top and bottom
rect(1, 2, 2, 14, C.frame); rect(13, 2, 14, 14, C.frame);
rect(1, 2, 14, 3, C.frame); rect(1, 13, 14, 14, C.frame);
rect(2, 2, 13, 2, C.frameLight);
// diagonal brace
for (let i = 0; i <= 8; i++) { set(3 + i, 12 - i, C.frame); set(4 + i, 12 - i, C.frameLight); }
// outline
for (let x = 1; x <= 14; x++) { set(x, 1, C.outline); set(x, 15, C.outline); }
for (let y = 1; y <= 15; y++) { set(0, y, C.outline); set(15, y, C.outline); }
set(0, 1, null); set(15, 1, null); set(0, 15, null); set(15, 15, null);
// red wax seal (bottom right)
rect(10, 9, 12, 11, C.seal); set(9, 10, C.seal); set(13, 10, C.seal); set(11, 8, C.seal); set(11, 12, C.seal);
set(10, 9, C.sealLight); set(11, 9, C.sealLight); set(12, 11, C.sealDark); set(11, 12, C.sealDark); set(13, 10, C.sealDark);

const raw = Buffer.alloc(W * (W * 4 + 1));
for (let y = 0; y < W; y++) {
  raw[y * (W * 4 + 1)] = 0;
  for (let x = 0; x < W; x++) {
    const c = px[y][x] || [0, 0, 0, 0];
    for (let k = 0; k < 4; k++) raw[y * (W * 4 + 1) + 1 + x * 4 + k] = c[k];
  }
}
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = buf => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
  return Buffer.concat([len, td, c]);
};
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(W, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
fs.writeFileSync(process.argv[2], png);
console.log('wrote', process.argv[2], W + 'x' + W);
