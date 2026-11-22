// Draws the merchant's notebook item texture (design T, v0.81): a 16x16 green leather-bound book with cream page
// edges, a strap and a gold coin on the cover. No libraries needed (writes the PNG itself).
// Usage (in Source/TradeExpansion): node tools/icon/gennotebook.js src/main/resources/assets/ak_trade_expansion/textures/item/merchant_notebook.png
const fs = require('fs');
const zlib = require('zlib');

const W = 16;
const px = Array.from({ length: W }, () => Array(W).fill(null));
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const C = {
  outline: hex('#1E2A1A'), cover: hex('#3F6B3A'), coverLight: hex('#548A4C'), coverDark: hex('#2F5029'),
  page: hex('#EDE3C8'), pageDark: hex('#C9BC98'),
  strap: hex('#6B4423'), strapLight: hex('#8A5A2E'),
  coin: hex('#E8B530'), coinLight: hex('#FFE07A'), coinDark: hex('#A87A12'),
};
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < W && y < W) px[y][x] = c; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };

// page edges (right and bottom, under the cover)
rect(4, 3, 14, 14, C.page);
for (const y of [5, 8, 11]) rect(13, y, 14, y, C.pageDark);
rect(4, 14, 14, 14, C.pageDark);
// cover
rect(2, 2, 12, 13, C.cover);
rect(2, 2, 12, 2, C.coverLight); rect(2, 2, 2, 13, C.coverLight);
rect(3, 13, 12, 13, C.coverDark); rect(12, 3, 12, 13, C.coverDark);
// spine
rect(2, 2, 3, 13, C.coverDark); rect(2, 2, 2, 13, C.cover);
// strap across the right side
rect(10, 7, 14, 8, C.strap); rect(10, 7, 14, 7, C.strapLight);
// gold coin on the cover
rect(6, 4, 8, 8, C.coin); rect(5, 5, 9, 7, C.coin);
set(6, 4, C.coinLight); set(5, 5, C.coinLight); set(7, 4, C.coinLight);
set(9, 7, C.coinDark); set(8, 8, C.coinDark); set(7, 8, C.coinDark);
set(7, 6, C.coinDark);
// outline
for (let x = 2; x <= 14; x++) { set(x, 1, C.outline); set(x, 15, C.outline); }
for (let y = 1; y <= 15; y++) { set(1, y, C.outline); set(15, y, C.outline); }
set(14, 1, null); set(15, 1, null); set(15, 2, C.outline); set(14, 2, null); set(13, 2, null);
rect(13, 1, 15, 2, null); set(13, 1, C.outline); set(14, 3, C.page); set(15, 3, C.outline); set(13, 2, C.outline); set(14, 2, C.outline);
set(1, 1, null); set(1, 15, null); set(15, 15, null);
rect(2, 14, 3, 14, C.outline);

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
