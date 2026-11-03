// Builds the "商品一覧" section of README.md from the bundled data (goods, profiles, consumables tag) and the
// Japanese names in docs/goods-design.md.
// Usage (from the repository root): node Source/TradeExpansion/tools/docs/goodslist.js .
const fs = require('fs');
const root = process.argv[2];
const D = root + '/Source/TradeExpansion/src/main/resources/data/trade_expansion/';
const goodsDoc = fs.readFileSync(root + '/docs/goods-design.md', 'utf8');

// names and categories from goods-design section 2
const manualNames = { red_tulip: '赤いチューリップ', orange_tulip: '橙色のチューリップ', white_tulip: '白いチューリップ', pink_tulip: 'ピンクのチューリップ', emerald_coin: 'エメラルド貨' };
const categories = [];
let current = null;
for (const line of goodsDoc.split('\n')) {
  const h = line.match(/^### 2-(\d+)\. ([^（]+)/);
  if (h) { current = { title: h[2].trim(), ids: [] }; categories.push(current); continue; }
  if (line.startsWith('### ') || line.startsWith('## ')) current = null;
  if (!current || !line.startsWith('| `')) continue;
  const cells = line.split('|').map(c => c.trim());
  const ids = [...cells[1].matchAll(/`([a-z_]+)`/g)].map(m => m[1]);
  const names = ids.length > 1 ? cells[2].split('・') : [cells[2]];
  ids.forEach((id, i) => { if (!manualNames[id]) manualNames[id] = names.length === ids.length ? names[i].trim() : cells[2]; current.ids.push(id); });
}

const coinCat = categories.find(c => c.title.startsWith('補助コイン')); if (coinCat) { coinCat.title = '補助コイン'; coinCat.ids.push('emerald_coin'); categories.push(categories.splice(categories.indexOf(coinCat), 1)[0]); }
const goods = {};
for (const f of fs.readdirSync(D + 'trade_expansion/goods')) goods[f.replace('.json', '')] = JSON.parse(fs.readFileSync(D + 'trade_expansion/goods/' + f, 'utf8'));

// consumables
const tag = JSON.parse(fs.readFileSync(D + 'tags/item/consumables.json', 'utf8')).values;
const isConsumable = item => tag.includes(item) || (tag.includes('#minecraft:logs') && /_log$/.test(item)) || (tag.includes('#minecraft:planks') && /_planks$/.test(item));

// markets
const shortName = { plains_village: '平原', desert_village: '砂漠', savanna_village: 'サバンナ', snowy_village: '雪原', taiga_village: 'タイガ', jungle: 'ジャングル', swamp: '沼地', crafted_stall: '開拓地', default_overworld: '既定', default_the_nether: 'ネザー', default_the_end: 'エンド', default: '他の次元' };
const overworld = ['plains_village', 'desert_village', 'savanna_village', 'snowy_village', 'taiga_village', 'jungle', 'swamp', 'crafted_stall', 'default_overworld'];
const where = {}; // goods -> {profile: minLevel}
for (const f of fs.readdirSync(D + 'trade_expansion/market_profile')) {
  const id = f.replace('.json', '');
  const p = JSON.parse(fs.readFileSync(D + 'trade_expansion/market_profile/' + f, 'utf8'));
  const put = (g, lvl) => { const k = g.replace('trade_expansion:', ''); (where[k] ??= {}); where[k][id] = Math.min(where[k][id] ?? 99, lvl); };
  (p.always || []).forEach(g => put(g, 1));
  (p.pools || []).forEach(pool => pool.entries.forEach(e => put(e.goods, e.min_level || 1)));
}
function markets(id) {
  if (goods[id].auto_add) return '村の市場すべて（商品数とは別枠）';
  const w = where[id] || {};
  const keys = Object.keys(w).filter(k => k !== 'default');
  if (!keys.length) return '-';
  const lv = n => n > 1 ? '（レベル' + n + '〜）' : '';
  const parts = [];
  let rest = keys;
  if (overworld.every(k => k in w)) {
    const levels = overworld.map(k => w[k]);
    const common = Math.max(...levels);
    parts.push('オーバーワールドの全市場' + lv(common));
    // regions where it appears earlier than in the common pool (e.g. a local specialty)
    rest = keys.filter(k => !overworld.includes(k) || w[k] < common);
  }
  const order = Object.keys(shortName);
  rest.sort((x, y) => order.indexOf(x) - order.indexOf(y)).forEach(k => parts.push(shortName[k] + lv(w[k])));
  return parts.join('・');
}
const price = (b, s, up) => up ? Math.max(1, Math.ceil(b * (1 + s / 2) - 1e-9)) : Math.max(1, Math.floor(b * (1 - s / 2) + 1e-9));
const fmt = n => n.toLocaleString('en-US');

let out = '## 商品一覧\n\n';
out += '同梱の商品は80種と、補助コインのエメラルド貨です。1つの市場が扱うのはこのうち10〜20種で、どれが並ぶかは市場の種類（地域・次元）と規模（レベル）で抽選されます。\n\n';
out += '- **買値／売値** は平常時（相場のずれが0）の目安です。実際の価格は市場ごとのばらつき（±15%）、地域の価格倍率、品不足、売買による相場の動きで変わります。\n';
out += '- **在庫** は平常時の在庫（目標在庫）です。上限はその2倍（ダイヤの剣・エメラルド貨は個別に設定）。\n';
out += '- **消耗品** は在庫と相場が早く戻る商品です（「相場の動き方」を参照）。\n';
out += '- **並ぶ市場** の「オーバーワールドの全市場」は、どの地域の市場でも抽選の候補になる日用品・高級品です。「（レベルN〜）」はその規模以上の市場だけで並ぶことを表します。地元の特産として、ほかより低いレベルから並ぶ地域は個別に書いています。\n';
out += '- 値は設定の `goods` や `/tradeex goods set` で変えられます。ここに載っているのは既定値です。\n\n';
const seen = new Set();
for (const cat of categories) {
  const ids = cat.ids.filter(id => goods[id] && !seen.has(id));
  if (!ids.length) continue;
  out += `### ${cat.title}（${ids.length}種）\n\n| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |\n|---|---|---|---|---|---|---|\n`;
  for (const id of ids) {
    seen.add(id);
    const g = goods[id];
    const s = g.spread ?? 0.4;
    const unit = g.unit && g.unit > 1 ? `（${g.unit}個あたり）` : '';
    const buy = g.buy === false ? '買えない' : fmt(price(g.base_price, s, true)) + 'c';
    const sell = g.sell === false ? '売れない' : fmt(price(g.base_price, s, false)) + 'c';
    out += `| ${manualNames[id] || id} | \`${g.item}\` | ${fmt(g.base_price)}c${unit} | ${buy} / ${sell} | ${g.target_stock} | ${isConsumable(g.item) ? '○' : ''} | ${markets(id)} |\n`;
  }
  out += '\n';
}
const missing = Object.keys(goods).filter(id => !seen.has(id));
if (missing.length) throw new Error('not categorized: ' + missing.join(', '));
out += '---\n\n';

for (const file of [root + '/README.md']) {
  let s = fs.readFileSync(file, 'utf8');
  const old = s.indexOf('## 商品一覧\n');
  if (old >= 0) s = s.slice(0, old) + s.slice(s.indexOf('## コマンド', old));
  const at = s.indexOf('## コマンド');
  s = s.slice(0, at) + out + s.slice(at);
  fs.writeFileSync(file, s);
}
console.log('ok', seen.size, 'goods');
