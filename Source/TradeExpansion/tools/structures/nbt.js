// Minimal big-endian NBT reader / writer (gzip) for structure templates.
const zlib = require('zlib');

function read(buf) {
  buf = zlib.gunzipSync(buf);
  let p = 0;
  const u8 = () => buf[p++];
  const i16 = () => { const v = buf.readInt16BE(p); p += 2; return v; };
  const i32 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
  const i64 = () => { const v = buf.readBigInt64BE(p); p += 8; return v; };
  const f32 = () => { const v = buf.readFloatBE(p); p += 4; return v; };
  const f64 = () => { const v = buf.readDoubleBE(p); p += 8; return v; };
  const str = () => { const n = buf.readUInt16BE(p); p += 2; const s = buf.toString('utf8', p, p + n); p += n; return s; };
  function payload(t) {
    switch (t) {
      case 1: return { t, v: buf.readInt8(p++) };
      case 2: return { t, v: i16() };
      case 3: return { t, v: i32() };
      case 4: return { t, v: i64() };
      case 5: return { t, v: f32() };
      case 6: return { t, v: f64() };
      case 7: { const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(buf.readInt8(p++)); return { t, v }; }
      case 8: return { t, v: str() };
      case 9: { const et = u8(); const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(payload(et)); return { t, et, v }; }
      case 10: { const v = {}; for (;;) { const ct = u8(); if (ct === 0) break; const name = str(); v[name] = payload(ct); } return { t, v }; }
      case 11: { const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(i32()); return { t, v }; }
      case 12: { const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(i64()); return { t, v }; }
      default: throw new Error('tag ' + t + ' at ' + p);
    }
  }
  const t = u8(); str();
  return payload(t);
}

function write(root) {
  const parts = [];
  const b = (n, fn) => { const x = Buffer.alloc(n); fn(x); parts.push(x); };
  const s = (str) => { const x = Buffer.from(str, 'utf8'); b(2, y => y.writeUInt16BE(x.length)); parts.push(x); };
  function payload(tag) {
    const { t, v } = tag;
    switch (t) {
      case 1: return b(1, x => x.writeInt8(v));
      case 2: return b(2, x => x.writeInt16BE(v));
      case 3: return b(4, x => x.writeInt32BE(v));
      case 4: return b(8, x => x.writeBigInt64BE(BigInt(v)));
      case 5: return b(4, x => x.writeFloatBE(v));
      case 6: return b(8, x => x.writeDoubleBE(v));
      case 7: b(4, x => x.writeInt32BE(v.length)); return v.forEach(e => b(1, x => x.writeInt8(e)));
      case 8: return s(v);
      case 9: { const et = v.length ? v[0].t : (tag.et || 0); b(1, x => x.writeUInt8(et)); b(4, x => x.writeInt32BE(v.length)); return v.forEach(payload); }
      case 10: for (const [k, c] of Object.entries(v)) { b(1, x => x.writeUInt8(c.t)); s(k); payload(c); } return b(1, x => x.writeUInt8(0));
      case 11: b(4, x => x.writeInt32BE(v.length)); return v.forEach(e => b(4, x => x.writeInt32BE(e)));
      case 12: b(4, x => x.writeInt32BE(v.length)); return v.forEach(e => b(8, x => x.writeBigInt64BE(BigInt(e))));
    }
  }
  b(1, x => x.writeUInt8(10)); s('');
  payload(root);
  return zlib.gzipSync(Buffer.concat(parts));
}

// Plain JS view (for dumping).
function plain(tag) {
  if (tag.t === 10) { const o = {}; for (const [k, c] of Object.entries(tag.v)) o[k] = plain(c); return o; }
  if (tag.t === 9) return tag.v.map(plain);
  if (tag.t === 4) return tag.v.toString() + 'L';
  return tag.v;
}

module.exports = { read, write, plain };
