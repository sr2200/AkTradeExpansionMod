// Generates the advancements of the mod (design R) and their titles / descriptions in the language files.
// Edit the ADVANCEMENTS table below, then run (in Source/TradeExpansion): node tools/advancements/genadv.js
// - writes src/main/resources/data/ak_trade_expansion/advancement/<id>.json (old files there are removed first)
// - replaces every "advancements.ak_trade_expansion.*" key in assets/ak_trade_expansion/lang/{ja_jp,en_us}.json
// Criteria use the vanilla triggers where they fit and ak_trade_expansion:trade_event otherwise
// (event names: com.akf.tradeexpansion.advancement.TradeAdvancements).
const fs = require('fs');
const path = require('path');

const NS = 'ak_trade_expansion';
const ROOT = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const OUT = path.join(ROOT, 'data', NS, 'advancement');
const LANG = path.join(ROOT, 'assets', NS, 'lang');

const COINS = ['copper_coin', 'iron_coin', 'gold_coin', 'diamond_coin', 'netherite_coin'].map(c => `${NS}:${c}`);

const event = (name, min) => ({ trigger: `${NS}:trade_event`, conditions: min === undefined ? { event: name } : { event: name, min } });
const has = items => ({ trigger: 'minecraft:inventory_changed', conditions: { items: [{ items }] } });
const placed = block => ({
  trigger: 'minecraft:placed_block',
  conditions: { location: { type: 'minecraft:match_block', blocks: block } },
});

// frame: task / goal / challenge. xp: experience reward.
const ADVANCEMENTS = [
  { id: 'root', icon: `${NS}:gold_coin`, criterion: has(COINS), root: true,
    ja: ['商いの始まり', 'コインを手に入れる'], en: ['The Start of Trade', 'Get a coin'] },
  { id: 'open_shop', parent: 'root', icon: `${NS}:market_stall`, criterion: placed(`${NS}:market_stall`), xp: 10,
    ja: ['店開き', '交易所を置く'], en: ['Open for Business', 'Place a market stall'] },
  { id: 'first_trade', parent: 'root', icon: `${NS}:copper_coin`, criterion: event('trade'), xp: 10,
    ja: ['初めての商い', '市場で品物を売るか買う'], en: ['First Deal', 'Buy or sell something at a market'] },
  { id: 'big_deal', parent: 'first_trade', icon: `${NS}:gold_coin`, criterion: event('trade', 1000), xp: 20,
    ja: ['大口取引', '1回の取引で1,000c以上の売り買いをする'], en: ['Big Deal', 'Buy or sell for 1,000c or more in one trade'] },
  { id: 'bulk_buy', parent: 'first_trade', icon: 'minecraft:chest', criterion: event('bulk_buy', 64), xp: 20,
    ja: ['まとめ買い', '1回に64個以上まとめて買う'], en: ['Buying in Bulk', 'Buy 64 or more at once'] },
  { id: 'market_flood', parent: 'first_trade', icon: 'minecraft:barrel', criterion: event('sell_stopped'), frame: 'goal', hidden: true, xp: 30,
    ja: ['市場をあふれさせる', '売りすぎて、市場の買取を止めてしまう'], en: ['Flooding the Market', 'Sell so much that the market stops buying'] },
  { id: 'shortage', parent: 'first_trade', icon: 'minecraft:hay_block', criterion: event('shortage_sold'), xp: 20,
    ja: ['渡りに船', '品不足の商品を売る'], en: ['Just What We Needed', 'Sell a good the market is short of'] },
  { id: 'rival', parent: 'first_trade', icon: 'minecraft:iron_ingot', criterion: event('rival_sold'), frame: 'goal', xp: 30,
    ja: ['商会の裏をかく', '商会が買い集めている商品を、値上がりしている市場で売る'], en: ['Outfoxing the Guild', 'Sell a good a guild is buying up, where its price is up'] },
  { id: 'peddler', parent: 'first_trade', icon: 'minecraft:map', criterion: event('markets', 3), xp: 20,
    ja: ['行商', '3つの市場で取引する'], en: ['Peddler', 'Trade at 3 markets'] },
  { id: 'traveling_merchant', parent: 'peddler', icon: 'minecraft:compass', criterion: event('markets', 10), frame: 'challenge', xp: 100,
    ja: ['旅商人', '10の市場で取引する'], en: ['Traveling Merchant', 'Trade at 10 markets'] },
  { id: 'nether_market', parent: 'peddler', icon: 'minecraft:gold_ingot', criterion: event('trade_nether'), frame: 'goal', xp: 30,
    ja: ['地底の商談', 'ネザーの市場で取引する'], en: ['Business Below', 'Trade at a market in the Nether'] },
  { id: 'end_market', parent: 'peddler', icon: 'minecraft:chorus_fruit', criterion: event('trade_end'), frame: 'goal', xp: 50,
    ja: ['果ての商談', 'エンドの市場で取引する'], en: ['Business at the End', 'Trade at a market in the End'] },
  { id: 'first_quest', parent: 'first_trade', icon: 'minecraft:writable_book', criterion: event('quest_delivered', 1), xp: 20,
    ja: ['頼れる人', '市場の依頼を納める'], en: ['Someone to Count On', 'Deliver a market request'] },
  { id: 'hard_quest', parent: 'first_quest', icon: 'minecraft:book', criterion: event('quest_delivered', 3), frame: 'goal', xp: 50,
    ja: ['腕利き', '★3の依頼を納める'], en: ['Seasoned Hand', 'Deliver a 3-star request'] },
  { id: 'cargo', parent: 'first_quest', icon: `${NS}:cargo_crate`, criterion: event('cargo_delivered'), xp: 30,
    ja: ['長い旅路', '輸送依頼の荷箱を届ける'], en: ['The Long Road', 'Deliver the crate of a transport request'] },
  { id: 'acquaintance', parent: 'first_trade', icon: 'minecraft:emerald', criterion: event('reputation', 1), xp: 20,
    ja: ['顔見知り', 'どこかの村で評判を「顔見知り」にする'], en: ['Familiar Face', 'Become an Acquaintance in a village'] },
  { id: 'valued_customer', parent: 'acquaintance', icon: 'minecraft:emerald_block', criterion: event('reputation', 3), frame: 'goal', xp: 50,
    ja: ['お得意様', 'どこかの村で評判を「お得意様」にする'], en: ['Valued Customer', 'Become a Valued customer in a village'] },
  { id: 'notable', parent: 'valued_customer', icon: 'minecraft:bell', criterion: event('reputation', 5), frame: 'challenge', xp: 100,
    ja: ['名士', 'どこかの村で評判を「名士」にする'], en: ['Pillar of the Community', 'Become a Notable in a village'] },
  { id: 'employer', parent: 'acquaintance', icon: 'minecraft:lead', criterion: event('staff_hired'), xp: 30,
    ja: ['人を使う身', '運び屋か番頭を雇う'], en: ['Employer', 'Hire a carrier or a clerk'] },
  { id: 'away_profit', parent: 'employer', icon: 'minecraft:saddle', criterion: event('staff_profit', 1), frame: 'goal', xp: 50,
    ja: ['留守を任せて', '運び屋の往復で利益を出す'], en: ['Minding the Store', 'Make a profit on a carrier\'s round trip'] },
  { id: 'investor', parent: 'first_trade', icon: 'minecraft:oak_planks', criterion: event('donated'), xp: 20,
    ja: ['村への投資', '交易画面の「発展」タブで寄付する'], en: ['Investing in the Village', 'Donate in the Development tab of a trade screen'] },
  { id: 'builder', parent: 'investor', icon: 'minecraft:scaffolding', criterion: event('facilities', 1), frame: 'goal', xp: 50,
    ja: ['槌音', '発展度を入れた村に、建物が完成する'], en: ['Sound of Hammers', 'See a building completed in a village you helped develop'] },
  { id: 'village_revival', parent: 'builder', icon: 'minecraft:lectern', criterion: event('facility_kinds', 8), frame: 'challenge', xp: 100,
    ja: ['村おこし', '発展度を入れた1つの村に、8種類以上の建物がそろう'],
    en: ['Village Revival', 'Have eight or more kinds of buildings in one village you helped develop'] },
  { id: 'wallet', parent: 'root', icon: `${NS}:wallet`, criterion: has([`${NS}:wallet`]), xp: 10,
    ja: ['財布の紐', '財布を手に入れる'], en: ['Purse Strings', 'Get a wallet'] },
  { id: 'savings', parent: 'wallet', icon: 'minecraft:gold_block', criterion: event('bank_deposit', 1), xp: 20,
    ja: ['堅実な蓄え', '銀行に預け入れる'], en: ['Nest Egg', 'Deposit money in a bank'] },
  { id: 'card', parent: 'savings', icon: `${NS}:bank_card`, criterion: event('card_issued'), xp: 20,
    ja: ['顔パス', 'クレジットカードを発行する'], en: ['Card Carrier', 'Get a credit card issued'] },
  { id: 'well_off', parent: 'wallet', icon: `${NS}:diamond_coin`, criterion: event('wealth', 10000), frame: 'goal', xp: 50,
    ja: ['小金持ち', '現金と銀行の合計で 10,000c を持つ'], en: ['Well-Off', 'Have 10,000c in cash and the bank together'] },
  { id: 'merchant_prince', parent: 'well_off', icon: `${NS}:netherite_coin`, criterion: event('wealth', 100000), frame: 'challenge', xp: 100,
    ja: ['豪商', '現金と銀行の合計で 100,000c を持つ'], en: ['Merchant Prince', 'Have 100,000c in cash and the bank together'] },
  { id: 'fireproof', parent: 'wallet', icon: `${NS}:netherite_coin`, criterion: has([`${NS}:netherite_coin`]), frame: 'goal', xp: 30,
    ja: ['燃えない財産', 'ネザライト貨を手に入れる'], en: ['Fireproof Fortune', 'Get a netherite coin'] },
];

const key = (id, part) => `advancements.${NS}.${id}.${part}`;

function advancement(a) {
  const display = {
    icon: { id: a.icon },
    title: { translate: key(a.id, 'title') },
    description: { translate: key(a.id, 'description') },
  };
  if (a.frame && a.frame !== 'task') display.frame = a.frame;
  if (a.root) {
    display.background = 'minecraft:block/spruce_planks';
    display.announce_to_chat = false;
  }
  if (a.hidden) display.hidden = true;
  const json = {};
  if (a.parent) json.parent = `${NS}:${a.parent}`;
  json.criteria = { done: a.criterion };
  json.display = display;
  json.requirements = [['done']];
  if (a.xp) json.rewards = { experience: a.xp };
  return json;
}

// sanity checks: unique ids, parents defined before children
const seen = new Set();
for (const a of ADVANCEMENTS) {
  if (seen.has(a.id)) throw new Error(`duplicate id ${a.id}`);
  if (a.parent && !seen.has(a.parent)) throw new Error(`${a.id}: parent ${a.parent} must come first`);
  seen.add(a.id);
}

fs.mkdirSync(OUT, { recursive: true });
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json')) fs.unlinkSync(path.join(OUT, f));
for (const a of ADVANCEMENTS) {
  fs.writeFileSync(path.join(OUT, `${a.id}.json`), JSON.stringify(advancement(a), null, 2) + '\n');
}

for (const [file, lang] of [['ja_jp.json', 'ja'], ['en_us.json', 'en']]) {
  const p = path.join(LANG, file);
  const entries = JSON.parse(fs.readFileSync(p, 'utf8'));
  for (const k of Object.keys(entries)) if (k.startsWith(`advancements.${NS}.`)) delete entries[k];
  for (const a of ADVANCEMENTS) {
    entries[key(a.id, 'title')] = a[lang][0];
    entries[key(a.id, 'description')] = a[lang][1];
  }
  fs.writeFileSync(p, JSON.stringify(entries, null, 2) + '\n');
}
console.log(`${ADVANCEMENTS.length} advancements written to ${OUT}`);
