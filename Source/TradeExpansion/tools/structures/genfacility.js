// Generates the facility buildings of the village development (design N-4 / N-5):
// data/ak_trade_expansion/structure/facility/<style>/<facility>.nbt
// Every template faces north: the front is the z = 0 side with the door at x = width / 2. Layer y = 0 is the floor,
// the walls stand on it, a stepped roof covers them. The facility sign (built=true) inside is the building's core.
// Styles share the shapes and only change the materials: one per village type (plains, desert, savanna, snowy, taiga)
// and one each for the Nether and the End (used when development.dimensions includes them). Beds explode there, so the
// inn of those styles has none (its effect is the villagers who arrive).
const n = require('./nbt.js');
const fs = require('fs');
const path = require('path');
// Usage: node tools/structures/genfacility.js src/main/resources/data/ak_trade_expansion/structure/facility
const out = process.argv[2];
const DATA_VERSION = 5023;

const styles = {
	plains: {
		floor: 'minecraft:cobblestone', pillar: 'minecraft:oak_log', wall: 'minecraft:oak_planks', roof: 'minecraft:spruce_planks',
		window: 'minecraft:glass', door: 'minecraft:oak_door', bed: 'minecraft:red_bed', stone: 'minecraft:stone_bricks',
		stonePillar: 'minecraft:polished_andesite', carpet: 'minecraft:red_carpet', lantern: 'minecraft:lantern',
	},
	desert: {
		floor: 'minecraft:smooth_sandstone', pillar: 'minecraft:chiseled_sandstone', wall: 'minecraft:sandstone', roof: 'minecraft:cut_sandstone',
		window: 'minecraft:glass', door: 'minecraft:birch_door', bed: 'minecraft:orange_bed', stone: 'minecraft:cut_sandstone',
		stonePillar: 'minecraft:chiseled_sandstone', carpet: 'minecraft:orange_carpet', lantern: 'minecraft:lantern',
	},
	savanna: {
		floor: 'minecraft:coarse_dirt', pillar: 'minecraft:acacia_log', wall: 'minecraft:acacia_planks', roof: 'minecraft:orange_terracotta',
		window: 'minecraft:glass', door: 'minecraft:acacia_door', bed: 'minecraft:yellow_bed', stone: 'minecraft:terracotta',
		stonePillar: 'minecraft:stripped_acacia_log', carpet: 'minecraft:yellow_carpet', lantern: 'minecraft:lantern',
	},
	snowy: {
		floor: 'minecraft:spruce_planks', pillar: 'minecraft:spruce_log', wall: 'minecraft:snow_block', roof: 'minecraft:spruce_planks',
		window: 'minecraft:glass', door: 'minecraft:spruce_door', bed: 'minecraft:light_blue_bed', stone: 'minecraft:polished_diorite',
		stonePillar: 'minecraft:stripped_spruce_log', carpet: 'minecraft:light_blue_carpet', lantern: 'minecraft:lantern',
	},
	taiga: {
		floor: 'minecraft:cobblestone', pillar: 'minecraft:spruce_log', wall: 'minecraft:spruce_planks', roof: 'minecraft:dark_oak_planks',
		window: 'minecraft:glass', door: 'minecraft:spruce_door', bed: 'minecraft:green_bed', stone: 'minecraft:mossy_stone_bricks',
		stonePillar: 'minecraft:cobblestone', carpet: 'minecraft:green_carpet', lantern: 'minecraft:lantern',
	},
	nether: {
		floor: 'minecraft:polished_blackstone', pillar: 'minecraft:crimson_stem', wall: 'minecraft:crimson_planks', roof: 'minecraft:nether_bricks',
		window: 'minecraft:glass', door: 'minecraft:crimson_door', bed: null, stone: 'minecraft:polished_blackstone_bricks',
		stonePillar: 'minecraft:gilded_blackstone', carpet: 'minecraft:red_carpet', lantern: 'minecraft:soul_lantern',
	},
	end: {
		floor: 'minecraft:end_stone_bricks', pillar: 'minecraft:purpur_pillar', wall: 'minecraft:purpur_block', roof: 'minecraft:end_stone_bricks',
		window: 'minecraft:glass', door: 'minecraft:warped_door', bed: null, stone: 'minecraft:end_stone_bricks',
		stonePillar: 'minecraft:purpur_pillar', carpet: 'minecraft:purple_carpet', lantern: 'minecraft:lantern',
	},
};

/** Blocks with an axis property (logs, stems, pillars) stand upright. */
const AXIS = /(_log|_stem|_pillar|basalt)$/;

const S = (v) => ({ t: 8, v });
const I = (v) => ({ t: 3, v });
const C = (o) => ({ t: 10, v: o });
const L = (arr, et) => ({ t: 9, et, v: arr });

/** Sizes (width x depth) and wall heights; the template height is wall + 3 (two roof layers + the floor). */
const facilities = {
	quest_hall: { w: 5, d: 5, wall: 3 },
	bank: { w: 7, d: 7, wall: 4 },
	warehouse: { w: 9, d: 9, wall: 4 },
	inn: { w: 9, d: 9, wall: 4 },
};

function build(style, m, kind, spec) {
	const { w: W, d: D, wall } = spec;
	const H = wall + 3;
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
	const grid = new Map();
	const set = (x, y, z, s) => grid.set(`${x},${y},${z}`, s);
	const mid = Math.floor(W / 2);
	const stoneHouse = kind === 'bank';
	const wallBlock = stoneHouse ? m.stone : m.wall;
	const upright = (id) => AXIS.test(id) ? state(id, { axis: 'y' }) : state(id);
	const pillar = upright(stoneHouse ? m.stonePillar : m.pillar);

	for (let x = 0; x < W; x++) for (let y = 0; y < H; y++) for (let z = 0; z < D; z++) set(x, y, z, state('minecraft:air'));
	for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) set(x, 0, z, stoneHouse ? upright(m.stonePillar) : state(m.floor));
	// Walls with corner pillars and windows on the sides and the back.
	for (let y = 1; y <= wall; y++) {
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const edgeX = x === 0 || x === W - 1;
			const edgeZ = z === 0 || z === D - 1;
			if (!edgeX && !edgeZ) continue;
			if (edgeX && edgeZ) { set(x, y, z, pillar); continue; }
			const along = edgeZ ? x : z;
			const window = y === 2 && along % 2 === 0 && along > 0 && along < (edgeZ ? W : D) - 1 && !(z === 0 && Math.abs(x - mid) <= 1);
			set(x, y, z, state(window ? m.window : wallBlock));
		}
	}
	// Door in the middle of the front.
	const door = (half) => state(m.door, { facing: 'south', half, hinge: 'left', open: 'false', powered: 'false' });
	set(mid, 1, 0, door('lower'));
	set(mid, 2, 0, door('upper'));
	// Stepped roof.
	for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) set(x, wall + 1, z, state(m.roof));
	for (let x = 1; x < W - 1; x++) for (let z = 1; z < D - 1; z++) set(x, wall + 2, z, state(m.roof));
	set(mid, wall, Math.floor(D / 2), state(m.lantern, { hanging: 'true', waterlogged: 'false' }));
	// The core: the facility sign at the back, its board facing the door.
	set(mid, 1, D - 2, state('ak_trade_expansion:facility_sign', { facing: 'north', kind, built: 'true' }));

	switch (kind) {
		case 'quest_hall':
			set(1, 1, D - 2, state('minecraft:bookshelf'));
			set(W - 2, 1, D - 2, state('minecraft:bookshelf'));
			break;
		case 'warehouse':
			for (let z = 1; z <= D - 3; z++) {
				set(1, 1, z, state('minecraft:barrel', { facing: 'up', open: 'false' }));
				set(1, 2, z, state('minecraft:barrel', { facing: 'up', open: 'false' }));
				set(W - 2, 1, z, state('minecraft:chest', { facing: 'west', type: 'single', waterlogged: 'false' }));
			}
			set(mid - 1, 1, D - 2, state('minecraft:hay_block', { axis: 'y' }));
			break;
		case 'inn':
			// Four beds along the side walls (head at the wall); rugs instead where beds explode.
			for (const z of [2, D - 4]) {
				if (m.bed) {
					set(1, 1, z, state(m.bed, { facing: 'west', part: 'head', occupied: 'false' }));
					set(2, 1, z, state(m.bed, { facing: 'west', part: 'foot', occupied: 'false' }));
					set(W - 2, 1, z, state(m.bed, { facing: 'east', part: 'head', occupied: 'false' }));
					set(W - 3, 1, z, state(m.bed, { facing: 'east', part: 'foot', occupied: 'false' }));
				} else {
					for (const x of [1, 2, W - 3, W - 2]) set(x, 1, z, state(m.carpet));
				}
			}
			for (let z = 1; z < D - 2; z++) set(mid, 1, z, state(m.carpet));
			break;
		case 'bank':
			// A counter in front of the sign, with a gold block on it.
			for (let x = 1; x < W - 1; x++) if (x !== mid) set(x, 1, D - 3, state('minecraft:smooth_stone_slab', { type: 'double', waterlogged: 'false' }));
			set(1, 2, D - 3, state('minecraft:gold_block'));
			break;
	}

	const blocks = [];
	for (const [key, s] of grid) blocks.push(C({ pos: L(key.split(',').map(Number).map(I), 3), state: I(s) }));
	const root = C({
		size: L([I(W), I(H), I(D)], 3),
		entities: L([], 10),
		blocks: L(blocks, 10),
		palette: L(palette, 10),
		DataVersion: I(DATA_VERSION),
	});
	const file = path.join(out, style, `${kind}.nbt`);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, n.write(root));
	const back = n.plain(n.read(fs.readFileSync(file)));
	console.log(style, kind, back.size, back.blocks.length, 'blocks', back.palette.length, 'states');
}

for (const [style, m] of Object.entries(styles)) {
	for (const [kind, spec] of Object.entries(facilities)) build(style, m, kind, spec);
}
