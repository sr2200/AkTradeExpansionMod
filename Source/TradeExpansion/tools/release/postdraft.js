// Updates the mechanical parts of the download page draft (docs/release/post-draft.md) for a release:
//   - the version in the zip and jar names (AKTradeExpansion-<version>.zip, ak_trade_expansion-<version>.jar)
//   - the numbers of goods (data .../goods, without auto_add ones), village buildings (.../facility) and advancements
// The written parts (the change history line, "できること") are left to a person. release.bat runs this.
// Usage (in Source/TradeExpansion): node tools/release/postdraft.js
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const draftPath = path.resolve(root, '..', '..', 'docs', 'release', 'post-draft.md');
const data = path.join(root, 'src', 'main', 'resources', 'data', 'ak_trade_expansion');

const version = /^version\s*=\s*(\S+)/m.exec(fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8'))[1];
const jsonFiles = (dir) => fs.readdirSync(dir).filter(f => f.endsWith('.json'));
const goodsDir = path.join(data, 'ak_trade_expansion', 'goods');
const goods = jsonFiles(goodsDir).filter(f => !JSON.parse(fs.readFileSync(path.join(goodsDir, f), 'utf8')).auto_add).length;
const facilities = jsonFiles(path.join(data, 'ak_trade_expansion', 'facility')).length;
const advancements = jsonFiles(path.join(data, 'advancement')).length;

let text = fs.readFileSync(draftPath, 'utf8');
const before = text;
const replace = (pattern, to, what) => {
  if (!pattern.test(text)) throw new Error('post-draft.md: ' + what + ' not found (' + pattern + ')');
  text = text.replace(pattern, to);
};
replace(/AKTradeExpansion-(?:【バージョン】|[0-9][0-9.]*)\.zip/g, `AKTradeExpansion-${version}.zip`, 'zip name');
replace(/ak_trade_expansion-(?:【バージョン】|[0-9][0-9.]*)\.jar/g, `ak_trade_expansion-${version}.jar`, 'jar name');
replace(/\*\*\d+種類の商品\*\*/g, `**${goods}種類の商品**`, 'number of goods');
replace(/の\*\*\d+種類\*\*。建物ごとに/g, `の**${facilities}種類**。建物ごとに`, 'number of buildings');
replace(/「商いの始まり」（\d+個）/g, `「商いの始まり」（${advancements}個）`, 'number of advancements');

if (text !== before) fs.writeFileSync(draftPath, text);
console.log(`post-draft.md: version ${version}, ${goods} goods, ${facilities} buildings, ${advancements} advancements${text === before ? ' (no change)' : ''}`);
