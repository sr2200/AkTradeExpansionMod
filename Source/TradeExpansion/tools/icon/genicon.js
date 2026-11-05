// Draws the mod icon (assets/ak_trade_expansion/icon.png): a market stall with a striped awning and a gold coin,
// as 32x32 pixel art scaled up to 128x128 without smoothing. No libraries needed (writes the PNG itself).
// Usage (in Source/TradeExpansion): node tools/icon/genicon.js src/main/resources/assets/ak_trade_expansion/icon.png
const fs = require('fs');
const zlib = require('zlib');

const N = 32;
const SCALE = 4;
const px = Array.from({ length: N }, () => Array(N).fill(null));
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const C = {
  outline: hex('#2B1D12'), red: hex('#D8443C'), redDark: hex('#A82E29'), white: hex('#F4EFE6'), whiteDark: hex('#D6CCBC'),
  post: hex('#7A4E2D'), postDark: hex('#5A3820'), wood: hex('#A0703C'), woodLight: hex('#C8925A'), woodDark: hex('#7C5229'),
  gold: hex('#F6C443'), goldLight: hex('#FFE48A'), goldDark: hex('#C98D1C'), goldDeep: hex('#9A6510'),
  green: hex('#5DBB4C'), greenDark: hex('#3C8A33'), orange: hex('#E8862A'),
};
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < N && y < N) px[y][x] = c; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };

// posts
rect(4, 10, 5, 22, C.post); rect(5, 10, 5, 22, C.postDark);
rect(26, 10, 27, 22, C.post); rect(27, 10, 27, 22, C.postDark);
// awning: stripes 4 px wide, slight shading on the lower rows, scalloped edge
for (let y = 3; y <= 10; y++) {
  for (let x = 2; x <= 29; x++) {
    const red = Math.floor((x - 2) / 4) % 2 === 0;
    const shade = y >= 9;
    set(x, y, red ? (shade ? C.redDark : C.red) : (shade ? C.whiteDark : C.white));
  }
}
for (let x = 2; x <= 29; x++) {
  const red = Math.floor((x - 2) / 4) % 2 === 0;
  const k = (x - 2) % 4;
  if (k === 1 || k === 2) set(x, 11, red ? C.redDark : C.whiteDark);
}
// awning outline
for (let x = 2; x <= 29; x++) set(x, 2, C.outline);
for (let y = 2; y <= 10; y++) { set(1, y, C.outline); set(30, y, C.outline); }
for (let x = 2; x <= 29; x++) {
  const k = (x - 2) % 4;
  set(x, k === 1 || k === 2 ? 12 : 11, C.outline);
}
// goods on the counter: a melon slice and an orange-ish fruit
rect(7, 16, 10, 18, C.green); rect(7, 18, 10, 18, C.greenDark);
rect(21, 16, 24, 18, C.orange); set(22, 15, C.greenDark);
// counter
rect(3, 19, 28, 20, C.woodLight);
rect(3, 21, 28, 27, C.wood);
for (let x = 3; x <= 28; x += 5) rect(x, 21, x, 27, C.woodDark);
rect(3, 27, 28, 27, C.woodDark);
for (let x = 2; x <= 29; x++) { set(x, 18, C.outline); set(x, 28, C.outline); }
for (let y = 18; y <= 28; y++) { set(2, y, C.outline); set(29, y, C.outline); }
// gold coin in front, overlapping the counter
const cx = 16, cy = 21, r = 7.2;
for (let y = 0; y < N; y++) {
  for (let x = 0; x < N; x++) {
    const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
    if (d <= r + 0.6 && d > r - 0.4) set(x, y, C.outline);
    else if (d <= r - 0.4 && d > r - 1.6) set(x, y, C.goldDark);
    else if (d <= r - 1.6) set(x, y, C.gold);
  }
}
// coin face: a "c" (copper unit) mark and a highlight
const c = [[0, 1, 1, 1], [1, 0, 0, 0], [1, 0, 0, 0], [1, 0, 0, 0], [0, 1, 1, 1]];
c.forEach((row, j) => row.forEach((v, i) => { if (v) set(cx - 2 + i, cy - 2 + j, C.goldDeep); }));
set(cx - 4, cy - 4, C.goldLight); set(cx - 3, cy - 5, C.goldLight); set(cx - 5, cy - 3, C.goldLight); set(cx - 4, cy - 3, C.goldLight);

// ---- PNG ----
const W = N * SCALE;
const raw = Buffer.alloc((W * 4 + 1) * W);
for (let y = 0; y < W; y++) {
  raw[y * (W * 4 + 1)] = 0;
  for (let x = 0; x < W; x++) {
    const p = px[Math.floor(y / SCALE)][Math.floor(x / SCALE)] || [0, 0, 0, 0];
    raw.set(p, y * (W * 4 + 1) + 1 + x * 4);
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
