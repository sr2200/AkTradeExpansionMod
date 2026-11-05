// Generates the village stall buildings (design stage 9): data/ak_trade_expansion/structure/village/<type>/market_stall.nbt
// Layout (7 x 5 x 7): floor at y=0, entrance jigsaw on the west edge (x=0, y=1, z=3) like vanilla desert houses,
// stall in the middle facing the entrance, four fence posts, a striped wool awning, a barrel, a hay bale and a lantern.
// A trader NPC (design A-5 ④) stands in front of the stall and links itself to it on its first tick; the vanilla
// villager spawn point is behind the stall.
const n = require('./nbt.js');
const fs = require('fs');
const path = require('path');
// Usage: node tools/structures/genstall.js src/main/resources/data/ak_trade_expansion/structure/village
const out = process.argv[2];
const DATA_VERSION = 5023;

const types = {
  plains:  { floor: 'minecraft:cobblestone',      fence: 'minecraft:oak_fence',    awning: ['minecraft:red_wool', 'minecraft:white_wool'] },
  desert:  { floor: 'minecraft:smooth_sandstone', fence: 'minecraft:birch_fence',  awning: ['minecraft:orange_wool', 'minecraft:white_wool'] },
  savanna: { floor: 'minecraft:acacia_planks',    fence: 'minecraft:acacia_fence', awning: ['minecraft:yellow_wool', 'minecraft:white_wool'] },
  snowy:   { floor: 'minecraft:spruce_planks',    fence: 'minecraft:spruce_fence', awning: ['minecraft:light_blue_wool', 'minecraft:white_wool'] },
  taiga:   { floor: 'minecraft:cobblestone',      fence: 'minecraft:spruce_fence', awning: ['minecraft:green_wool', 'minecraft:white_wool'] },
};

const S = (v) => ({ t: 8, v });
const I = (v) => ({ t: 3, v });
const C = (o) => ({ t: 10, v: o });
const L = (arr, et) => ({ t: 9, et, v: arr });

for (const [type, m] of Object.entries(types)) {
  const palette = [];
  const index = new Map();
  const state = (id, props) => {
    const key = id + JSON.stringify(props || {});
    if (!index.has(key)) {
      const entry = { id: S(id) };
      if (props) entry.properties = C(Object.fromEntries(Object.entries(props).map(([k, v]) => [k, S(v)])));
      index.set(key, palette.length);
      palette.push(C(entry));
    }
    return index.get(key);
  };
  const W = 7, H = 5, D = 7;
  const grid = new Map(); // "x,y,z" -> { state, nbt }
  const set = (x, y, z, s, nbt) => grid.set(`${x},${y},${z}`, { s, nbt });

  for (let x = 0; x < W; x++) for (let y = 0; y < H; y++) for (let z = 0; z < D; z++) {
    set(x, y, z, y === 0 ? state(m.floor) : state('minecraft:air'));
  }
  const fence = (props) => state(m.fence, { east: 'false', north: 'false', south: 'false', west: 'false', waterlogged: 'false', ...props });
  for (const [x, z] of [[1, 1], [1, 5], [5, 1], [5, 5]]) for (let y = 1; y <= 3; y++) set(x, y, z, fence({}));
  for (let x = 1; x <= 5; x++) for (let z = 1; z <= 5; z++) set(x, 4, z, state(m.awning[z % 2]));

  set(3, 1, 3, state('ak_trade_expansion:market_stall', { facing: 'west' }),
      C({ id: S('ak_trade_expansion:market_stall'), origin: S('natural') }));
  set(4, 1, 2, state('minecraft:barrel', { facing: 'up', open: 'false' }));
  set(4, 1, 4, state('minecraft:hay_block', { axis: 'y' }));
  set(3, 3, 3, state('minecraft:lantern', { hanging: 'true', waterlogged: 'false' }));

  // Entrance (connects to the street) and villager spawn point, copied from vanilla small houses.
  set(0, 1, 3, state('minecraft:jigsaw', { orientation: 'west_up' }), C({
    joint: S('aligned'), final_state: S('minecraft:structure_void'), name: S('minecraft:building_entrance'),
    pool: S('minecraft:empty'), id: S('minecraft:jigsaw'), target: S('minecraft:building_entrance'),
  }));
  set(5, 0, 3, state('minecraft:jigsaw', { orientation: 'up_north' }), C({
    joint: S('rollable'), final_state: S(m.floor), name: S('minecraft:bottom'),
    pool: S(`minecraft:village/${type}/villagers`), id: S('minecraft:jigsaw'), target: S('minecraft:bottom'),
  }));

  const blocks = [];
  for (const [key, { s, nbt }] of grid) {
    const pos = key.split(',').map(Number);
    const b = { pos: L(pos.map(I), 3), state: I(s) };
    if (nbt) b.nbt = nbt;
    blocks.push(C(b));
  }
  const root = C({
    size: L([I(W), I(H), I(D)], 3),
    entities: L([C({
      pos: L([2.5, 1.0, 3.5].map(v => ({ t: 6, v })), 6),
      blockPos: L([2, 1, 3].map(I), 3),
      nbt: C({ id: S('ak_trade_expansion:trader'), link_nearby_stall: { t: 1, v: 1 }, Rotation: L([90, 0].map(v => ({ t: 5, v })), 5) }),
    })], 10),
    blocks: L(blocks, 10),
    palette: L(palette, 10),
    DataVersion: I(DATA_VERSION),
  });
  const file = path.join(out, type, 'market_stall.nbt');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, n.write(root));
  // Round trip check.
  const back = n.plain(n.read(fs.readFileSync(file)));
  console.log(type, back.size, back.blocks.length, 'blocks', back.palette.length, 'states');
}
