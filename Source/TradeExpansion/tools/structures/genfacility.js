// Generates the facility buildings of the village development (design N-4 / N-5):
// data/ak_trade_expansion/structure/facility/<style>/<facility>.nbt
// Every template faces north: the front is the z = 0 side and the door is at x = width / 2. Layer y = 0 is the floor.
// The facility sign (built=true) inside is the building's core; it stands at (width / 2, 1, depth - 2).
//
// Shapes (the footprints stay 5x5 / 7x7 / 9x9, everything fits inside them):
// - quest hall, warehouse, inn: a timber-framed house. The front row z = 0 is a covered porch on two posts, the front
//   wall with the door stands at z = 1. Stone base course, corner and middle posts, a beam on top of the walls, a tie
//   beam across the room (the lanterns hang from it) and a gable roof of stairs whose gable faces the front (desert: a
//   flat roof with a parapet). The inn has a chimney with a campfire on top.
// - bank: a stone house with a portico of columns at the front, a cornice, barred windows, a teller counter, a ceiling
//   and a hipped roof of stairs.
// - fountain (11x11) and park (13x13), the leisure facilities: open plazas without walls; see fountain() and park().
//   The sign stands in the front opening (fountain) or inside the gate (park).
// Styles share the shapes and only change the materials: one per village type (plains, desert, savanna, snowy, taiga)
// and one each for the Nether and the End (used when development.dimensions includes them). Beds explode there, so the
// inn of those styles has rugs instead (its effect is the villagers who arrive).
const n = require('./nbt.js');
const fs = require('fs');
const path = require('path');
// Usage: node tools/structures/genfacility.js src/main/resources/data/ak_trade_expansion/structure/facility
const out = process.argv[2];
const DATA_VERSION = 5023;

// roof / bankRoof: the stem of <stem>_stairs and <stem>_slab. flat: a flat roof of roofBlock with a parapet instead.
const styles = {
	plains: {
		found: 'minecraft:cobblestone', floor: 'minecraft:spruce_planks', pillar: 'minecraft:spruce_log', beam: 'minecraft:stripped_spruce_log',
		wall: 'minecraft:oak_planks', roof: 'minecraft:spruce', window: 'minecraft:glass_pane', door: 'minecraft:oak_door',
		bed: 'minecraft:red_bed', carpet: 'minecraft:red_carpet', lantern: 'minecraft:lantern', pot: 'minecraft:potted_poppy',
		chimney: 'minecraft:bricks', chimneyTop: 'minecraft:campfire',
		stone: 'minecraft:stone_bricks', stoneBase: 'minecraft:polished_andesite', column: 'minecraft:quartz_pillar',
		cornice: 'minecraft:chiseled_stone_bricks', bankRoof: 'minecraft:deepslate_tile', bankFloor: 'minecraft:polished_andesite',
		bankChecker: 'minecraft:smooth_stone', counter: 'minecraft:polished_andesite', ceiling: 'minecraft:spruce_planks',
	},
	desert: {
		found: 'minecraft:smooth_sandstone', floor: 'minecraft:smooth_sandstone', pillar: 'minecraft:cut_sandstone', beam: 'minecraft:chiseled_sandstone',
		wall: 'minecraft:sandstone', flat: true, roofBlock: 'minecraft:smooth_sandstone', parapet: 'minecraft:sandstone_wall',
		window: 'minecraft:glass_pane', door: 'minecraft:jungle_door',
		bed: 'minecraft:orange_bed', carpet: 'minecraft:orange_carpet', lantern: 'minecraft:lantern', pot: 'minecraft:potted_cactus',
		chimney: 'minecraft:cut_sandstone', chimneyTop: 'minecraft:campfire',
		stone: 'minecraft:smooth_sandstone', stoneBase: 'minecraft:cut_sandstone', column: 'minecraft:cut_sandstone',
		cornice: 'minecraft:chiseled_sandstone', bankRoof: 'minecraft:smooth_sandstone', bankFloor: 'minecraft:cut_sandstone',
		bankChecker: 'minecraft:smooth_sandstone', counter: 'minecraft:cut_sandstone', ceiling: 'minecraft:smooth_sandstone',
	},
	savanna: {
		found: 'minecraft:cobblestone', floor: 'minecraft:acacia_planks', pillar: 'minecraft:acacia_log', beam: 'minecraft:acacia_log',
		wall: 'minecraft:acacia_planks', roof: 'minecraft:mud_brick', window: 'minecraft:glass_pane', door: 'minecraft:acacia_door',
		bed: 'minecraft:yellow_bed', carpet: 'minecraft:yellow_carpet', lantern: 'minecraft:lantern', pot: 'minecraft:potted_acacia_sapling',
		chimney: 'minecraft:mud_bricks', chimneyTop: 'minecraft:campfire',
		stone: 'minecraft:cut_red_sandstone', stoneBase: 'minecraft:terracotta', column: 'minecraft:stripped_acacia_log',
		cornice: 'minecraft:chiseled_red_sandstone', bankRoof: 'minecraft:smooth_red_sandstone', bankFloor: 'minecraft:smooth_red_sandstone',
		bankChecker: 'minecraft:terracotta', counter: 'minecraft:terracotta', ceiling: 'minecraft:acacia_planks',
	},
	snowy: {
		found: 'minecraft:cobblestone', floor: 'minecraft:spruce_planks', pillar: 'minecraft:spruce_log', beam: 'minecraft:stripped_spruce_log',
		wall: 'minecraft:snow_block', roof: 'minecraft:spruce', window: 'minecraft:glass_pane', door: 'minecraft:spruce_door',
		bed: 'minecraft:light_blue_bed', carpet: 'minecraft:light_blue_carpet', lantern: 'minecraft:lantern', pot: 'minecraft:potted_spruce_sapling',
		chimney: 'minecraft:stone_bricks', chimneyTop: 'minecraft:campfire',
		stone: 'minecraft:polished_diorite', stoneBase: 'minecraft:stone_bricks', column: 'minecraft:stripped_spruce_log',
		cornice: 'minecraft:stone_bricks', bankRoof: 'minecraft:spruce', bankFloor: 'minecraft:polished_diorite',
		bankChecker: 'minecraft:spruce_planks', counter: 'minecraft:spruce_planks', ceiling: 'minecraft:spruce_planks',
	},
	taiga: {
		found: 'minecraft:mossy_cobblestone', floor: 'minecraft:spruce_planks', pillar: 'minecraft:spruce_log', beam: 'minecraft:spruce_log',
		wall: 'minecraft:spruce_planks', roof: 'minecraft:dark_oak', window: 'minecraft:glass_pane', door: 'minecraft:spruce_door',
		bed: 'minecraft:green_bed', carpet: 'minecraft:green_carpet', lantern: 'minecraft:lantern', pot: 'minecraft:potted_fern',
		chimney: 'minecraft:cobblestone', chimneyTop: 'minecraft:campfire',
		stone: 'minecraft:stone_bricks', stoneBase: 'minecraft:mossy_stone_bricks', column: 'minecraft:stripped_spruce_log',
		cornice: 'minecraft:mossy_stone_bricks', bankRoof: 'minecraft:dark_oak', bankFloor: 'minecraft:spruce_planks',
		bankChecker: 'minecraft:dark_oak_planks', counter: 'minecraft:dark_oak_planks', ceiling: 'minecraft:dark_oak_planks',
	},
	nether: {
		found: 'minecraft:polished_blackstone', floor: 'minecraft:polished_blackstone_bricks', pillar: 'minecraft:crimson_stem',
		beam: 'minecraft:stripped_crimson_stem', wall: 'minecraft:crimson_planks', roof: 'minecraft:nether_brick',
		window: 'minecraft:glass_pane', door: 'minecraft:crimson_door',
		bed: null, carpet: 'minecraft:red_carpet', lantern: 'minecraft:soul_lantern', pot: 'minecraft:potted_crimson_fungus',
		chimney: 'minecraft:nether_bricks', chimneyTop: 'minecraft:soul_campfire',
		stone: 'minecraft:polished_blackstone_bricks', stoneBase: 'minecraft:polished_blackstone', column: 'minecraft:polished_basalt',
		cornice: 'minecraft:chiseled_polished_blackstone', bankRoof: 'minecraft:polished_blackstone_brick', bankFloor: 'minecraft:polished_blackstone',
		bankChecker: 'minecraft:gilded_blackstone', counter: 'minecraft:polished_blackstone', ceiling: 'minecraft:crimson_planks',
	},
	end: {
		found: 'minecraft:end_stone_bricks', floor: 'minecraft:purpur_block', pillar: 'minecraft:purpur_pillar', beam: 'minecraft:purpur_pillar',
		wall: 'minecraft:end_stone_bricks', roof: 'minecraft:purpur', window: 'minecraft:glass_pane', door: 'minecraft:warped_door',
		bed: null, carpet: 'minecraft:purple_carpet', lantern: 'minecraft:lantern', pot: null,
		chimney: 'minecraft:purpur_block', chimneyTop: 'minecraft:end_rod',
		stone: 'minecraft:end_stone_bricks', stoneBase: 'minecraft:purpur_block', column: 'minecraft:purpur_pillar',
		cornice: 'minecraft:purpur_block', bankRoof: 'minecraft:purpur', bankFloor: 'minecraft:end_stone_bricks',
		bankChecker: 'minecraft:purpur_block', counter: 'minecraft:purpur_block', ceiling: 'minecraft:purpur_block',
	},
};

// Materials of the leisure facilities (fountain plaza, park), merged into the style. bowl / bench / gzRoof: stems of
// <stem>_stairs and <stem>_slab. fluid: water, lava in the Nether (water would be odd there and lava glows).
const leisure = {
	plains: {
		pave: 'minecraft:stone_bricks', paveRing: 'minecraft:polished_andesite', paveAxis: 'minecraft:smooth_stone',
		rim: 'minecraft:smooth_quartz', rimCorner: 'minecraft:chiseled_quartz_block', bowl: 'minecraft:smooth_quartz',
		shaft: 'minecraft:quartz_pillar', shaftBase: 'minecraft:chiseled_quartz_block', basin: 'minecraft:prismarine_bricks',
		glow: 'minecraft:sea_lantern', fluid: 'minecraft:water', postBase: 'minecraft:chiseled_stone_bricks', post: 'minecraft:stone_brick_wall',
		ground: 'minecraft:grass_block', path: 'minecraft:dirt_path', hedge: 'minecraft:oak_leaves', bench: 'minecraft:oak',
		tree: { shape: 'round', log: 'minecraft:cherry_log', leaves: 'minecraft:cherry_leaves' },
		flowers: ['minecraft:poppy', 'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:cornflower', 'minecraft:allium', 'minecraft:azure_bluet'],
		tall: 'minecraft:rose_bush', bush: 'minecraft:flowering_azalea', reed: 'minecraft:sugar_cane',
		deck: 'minecraft:oak_planks', gzPost: 'minecraft:stripped_oak_log', fence: 'minecraft:oak_fence', gzRoof: 'minecraft:dark_oak',
		gzTop: 'minecraft:dark_oak_planks',
	},
	desert: {
		pave: 'minecraft:smooth_sandstone', paveRing: 'minecraft:cut_sandstone', paveAxis: 'minecraft:orange_terracotta',
		rim: 'minecraft:smooth_sandstone', rimCorner: 'minecraft:chiseled_sandstone', bowl: 'minecraft:smooth_sandstone',
		shaft: 'minecraft:cut_sandstone', shaftBase: 'minecraft:chiseled_sandstone', basin: 'minecraft:prismarine_bricks',
		glow: 'minecraft:ochre_froglight', fluid: 'minecraft:water', postBase: 'minecraft:chiseled_sandstone', post: 'minecraft:sandstone_wall',
		ground: 'minecraft:grass_block', path: 'minecraft:smooth_sandstone', hedge: 'minecraft:azalea_leaves', bench: 'minecraft:jungle',
		tree: { shape: 'palm', log: 'minecraft:jungle_log', leaves: 'minecraft:jungle_leaves' },
		flowers: ['minecraft:orange_tulip', 'minecraft:red_tulip', 'minecraft:dandelion', 'minecraft:poppy', 'minecraft:torchflower'],
		tall: 'minecraft:sunflower', bush: 'minecraft:flowering_azalea', reed: 'minecraft:sugar_cane',
		deck: 'minecraft:smooth_sandstone', gzPost: 'minecraft:cut_sandstone', fence: 'minecraft:jungle_fence', gzRoof: 'minecraft:jungle',
		gzTop: 'minecraft:chiseled_sandstone',
	},
	savanna: {
		pave: 'minecraft:packed_mud', paveRing: 'minecraft:mud_bricks', paveAxis: 'minecraft:smooth_red_sandstone',
		rim: 'minecraft:smooth_red_sandstone', rimCorner: 'minecraft:chiseled_red_sandstone', bowl: 'minecraft:smooth_red_sandstone',
		shaft: 'minecraft:cut_red_sandstone', shaftBase: 'minecraft:chiseled_red_sandstone', basin: 'minecraft:prismarine_bricks',
		glow: 'minecraft:ochre_froglight', fluid: 'minecraft:water', postBase: 'minecraft:chiseled_red_sandstone', post: 'minecraft:red_sandstone_wall',
		ground: 'minecraft:grass_block', path: 'minecraft:dirt_path', hedge: 'minecraft:acacia_leaves', bench: 'minecraft:acacia',
		tree: { shape: 'flat', log: 'minecraft:acacia_log', leaves: 'minecraft:acacia_leaves' },
		flowers: ['minecraft:red_tulip', 'minecraft:orange_tulip', 'minecraft:dandelion', 'minecraft:allium', 'minecraft:poppy'],
		tall: 'minecraft:sunflower', bush: 'minecraft:flowering_azalea', reed: 'minecraft:sugar_cane',
		deck: 'minecraft:acacia_planks', gzPost: 'minecraft:stripped_acacia_log', fence: 'minecraft:acacia_fence', gzRoof: 'minecraft:acacia',
		gzTop: 'minecraft:acacia_planks',
	},
	snowy: {
		pave: 'minecraft:stone_bricks', paveRing: 'minecraft:polished_diorite', paveAxis: 'minecraft:spruce_planks',
		rim: 'minecraft:polished_diorite', rimCorner: 'minecraft:chiseled_quartz_block', bowl: 'minecraft:polished_diorite',
		shaft: 'minecraft:quartz_pillar', shaftBase: 'minecraft:chiseled_quartz_block', basin: 'minecraft:prismarine_bricks',
		glow: 'minecraft:sea_lantern', fluid: 'minecraft:water', postBase: 'minecraft:chiseled_stone_bricks', post: 'minecraft:stone_brick_wall',
		ground: 'minecraft:grass_block', path: 'minecraft:dirt_path', hedge: 'minecraft:spruce_leaves', bench: 'minecraft:spruce',
		tree: { shape: 'cone', log: 'minecraft:spruce_log', leaves: 'minecraft:spruce_leaves' },
		flowers: ['minecraft:lily_of_the_valley', 'minecraft:blue_orchid', 'minecraft:azure_bluet', 'minecraft:white_tulip', 'minecraft:cornflower'],
		tall: 'minecraft:lilac', bush: 'minecraft:flowering_azalea', reed: null, // the pond freezes; sugar cane would break
		deck: 'minecraft:spruce_planks', gzPost: 'minecraft:stripped_spruce_log', fence: 'minecraft:spruce_fence', gzRoof: 'minecraft:spruce',
		gzTop: 'minecraft:spruce_planks',
	},
	taiga: {
		pave: 'minecraft:stone_bricks', paveRing: 'minecraft:mossy_stone_bricks', paveAxis: 'minecraft:polished_andesite',
		rim: 'minecraft:mossy_stone_bricks', rimCorner: 'minecraft:chiseled_stone_bricks', bowl: 'minecraft:mossy_stone_brick',
		shaft: 'minecraft:stripped_spruce_log', shaftBase: 'minecraft:chiseled_stone_bricks', basin: 'minecraft:prismarine_bricks',
		glow: 'minecraft:sea_lantern', fluid: 'minecraft:water', postBase: 'minecraft:mossy_cobblestone', post: 'minecraft:mossy_stone_brick_wall',
		ground: 'minecraft:grass_block', path: 'minecraft:dirt_path', hedge: 'minecraft:spruce_leaves', bench: 'minecraft:spruce',
		tree: { shape: 'cone', log: 'minecraft:spruce_log', leaves: 'minecraft:spruce_leaves' },
		flowers: ['minecraft:cornflower', 'minecraft:lily_of_the_valley', 'minecraft:fern', 'minecraft:allium', 'minecraft:poppy'],
		tall: 'minecraft:peony', bush: 'minecraft:flowering_azalea', reed: 'minecraft:sugar_cane',
		deck: 'minecraft:spruce_planks', gzPost: 'minecraft:spruce_log', fence: 'minecraft:spruce_fence', gzRoof: 'minecraft:dark_oak',
		gzTop: 'minecraft:dark_oak_planks',
	},
	nether: {
		pave: 'minecraft:polished_blackstone_bricks', paveRing: 'minecraft:polished_blackstone', paveAxis: 'minecraft:red_nether_bricks',
		rim: 'minecraft:polished_blackstone_bricks', rimCorner: 'minecraft:chiseled_polished_blackstone', bowl: 'minecraft:polished_blackstone',
		shaft: 'minecraft:polished_basalt', shaftBase: 'minecraft:chiseled_polished_blackstone', basin: 'minecraft:magma_block',
		glow: 'minecraft:shroomlight', fluid: 'minecraft:lava', postBase: 'minecraft:gilded_blackstone', post: 'minecraft:polished_blackstone_wall',
		ground: 'minecraft:crimson_nylium', path: 'minecraft:polished_blackstone_bricks', hedge: 'minecraft:nether_wart_block', bench: 'minecraft:crimson',
		tree: { shape: 'fungus', log: 'minecraft:crimson_stem', leaves: 'minecraft:nether_wart_block' },
		flowers: ['minecraft:crimson_roots', 'minecraft:crimson_fungus', 'minecraft:warped_roots', 'minecraft:warped_fungus'],
		tall: null, bush: null, reed: null,
		deck: 'minecraft:crimson_planks', gzPost: 'minecraft:stripped_crimson_stem', fence: 'minecraft:crimson_fence', gzRoof: 'minecraft:nether_brick',
		gzTop: 'minecraft:nether_bricks',
	},
	end: {
		pave: 'minecraft:end_stone_bricks', paveRing: 'minecraft:end_stone', paveAxis: 'minecraft:purpur_block',
		rim: 'minecraft:purpur_block', rimCorner: 'minecraft:purpur_pillar', bowl: 'minecraft:purpur',
		shaft: 'minecraft:purpur_pillar', shaftBase: 'minecraft:end_stone_bricks', basin: 'minecraft:purpur_block',
		glow: 'minecraft:sea_lantern', fluid: 'minecraft:water', postBase: 'minecraft:purpur_block', post: 'minecraft:end_stone_brick_wall',
		ground: 'minecraft:end_stone', path: 'minecraft:end_stone_bricks', hedge: 'minecraft:end_stone_brick_wall', bench: 'minecraft:purpur',
		tree: { shape: 'chorus' },
		flowers: ['minecraft:end_rod'], // nothing grows on end stone: glowing rods instead
		tall: null, bush: null, reed: null,
		deck: 'minecraft:purpur_block', gzPost: 'minecraft:purpur_pillar', fence: 'minecraft:end_stone_brick_wall', gzRoof: 'minecraft:purpur',
		gzTop: 'minecraft:purpur_block',
	},
};
for (const [style, m] of Object.entries(styles)) Object.assign(m, leisure[style]);

/** Blocks with an axis property (logs, stems, pillars). */
const AXIS = /(_log|_stem|_pillar|basalt|hay_block)$/;

const S = (v) => ({ t: 8, v });
const I = (v) => ({ t: 3, v });
const C = (o) => ({ t: 10, v: o });
const L = (arr, et) => ({ t: 9, et, v: arr });

/** Footprints (width x depth) and wall heights. */
const facilities = {
	quest_hall: { w: 5, d: 5, wall: 4 },
	bank: { w: 7, d: 7, wall: 4 },
	warehouse: { w: 9, d: 9, wall: 4 },
	inn: { w: 9, d: 9, wall: 4 },
	fountain: { w: 11, d: 11 },
	park: { w: 13, d: 13 },
};

/** Stairs and slabs from the stem of their block ids: minecraft:spruce -> minecraft:spruce_stairs. */
const stairs = (stem) => stem + '_stairs';
const slab = (stem) => stem + '_slab';

function build(style, m, kind, spec) {
	const { w: W, d: D, wall } = spec;
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
	const set = (x, y, z, s) => {
		if (x < 0 || x >= W || z < 0 || z >= D || y < 0) throw new Error(`${style} ${kind}: ${x},${y},${z} outside`);
		grid.set(`${x},${y},${z}`, s);
	};
	const get = (x, y, z) => grid.get(`${x},${y},${z}`);
	const block = (id) => state(id);
	const up = (id) => AXIS.test(id) ? state(id, { axis: 'y' }) : state(id);
	const lying = (id, axis) => AXIS.test(id) ? state(id, { axis }) : state(id);
	const stair = (stem, facing, half = 'bottom') => state(stairs(stem), { facing, half });
	const lantern = (hanging) => state(m.lantern, { hanging: String(hanging) });
	const mid = Math.floor(W / 2);
	const door = (x, z) => {
		set(x, 1, z, state(m.door, { facing: 'south', half: 'lower', hinge: 'left' }));
		set(x, 2, z, state(m.door, { facing: 'south', half: 'upper', hinge: 'left' }));
	};
	const sign = () => set(mid, 1, D - 2, state('ak_trade_expansion:facility_sign', { facing: 'north', kind, built: 'true' }));

	// ---- leisure facilities ----

	const leaves = (id) => id.endsWith('_leaves') ? state(id, { persistent: 'true' }) : block(id);
	/** A small plant on the ground: end rods stand up, everything else is a plain block. */
	const plant = (id) => id === 'minecraft:end_rod' ? state(id, { facing: 'up' }) : block(id);
	const tallPlant = (x, z, id) => {
		set(x, 1, z, state(id, { half: 'lower' }));
		set(x, 2, z, state(id, { half: 'upper' }));
	};
	const fluid = () => state(m.fluid, { level: '0' });
	const coreSign = (x, z, facing) => set(x, 1, z, state('ak_trade_expansion:facility_sign', { facing, kind, built: 'true' }));
	/** Lamp post: a base, a wall shaft and a lantern on top. */
	const lampPost = (x, z, shaft) => {
		set(x, 1, z, block(m.postBase));
		for (let y = 2; y < 2 + shaft; y++) set(x, y, z, block(m.post));
		set(x, 2 + shaft, z, lantern(false));
	};
	/** Flowers picked by a fixed pattern so that neighbours differ. */
	const flower = (x, z) => m.flowers[(x * 7 + z * 3) % m.flowers.length];

	/**
	 * Fountain plaza: a rounded basin of water (lava in the Nether) lit from below, a column carrying a bowl, and a spout
	 * on top whose water runs over the bowl in four streams back into the basin. Paved ring with a compass pattern, lamp
	 * posts at the corners, and benches, hedges and flower beds along the edge with an opening in the middle of each side.
	 * The sign stands in the front opening.
	 */
	function fountain() {
		const c = mid;
		const R = mid; // edge ring
		const inBasin = (dx, dz) => Math.max(Math.abs(dx), Math.abs(dz)) <= 2 && !(Math.abs(dx) === 2 && Math.abs(dz) === 2);
		const onRim = (dx, dz) => {
			if (inBasin(dx, dz)) return false;
			for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) if (inBasin(dx + a, dz + b)) return true;
			return false;
		};
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const dx = x - c, dz = z - c;
			const r = Math.max(Math.abs(dx), Math.abs(dz));
			let floor;
			if (inBasin(dx, dz)) {
				// Light under the water (star pattern): keeps it from freezing in snowy villages and glows at night.
				floor = dx === 0 || dz === 0 || Math.abs(dx) === Math.abs(dz) ? m.glow : m.basin;
				if (dx === 0 && dz === 0) floor = m.basin;
			} else if (r >= 4 && (dx === 0 || dz === 0)) floor = m.paveAxis;
			else if (r === 4) floor = m.paveRing;
			else if (r === 3 && Math.abs(dx) === Math.abs(dz)) floor = m.paveAxis;
			else floor = m.pave;
			set(x, 0, z, block(floor));
			if (inBasin(dx, dz) && (dx !== 0 || dz !== 0)) set(x, 1, z, fluid());
			if (onRim(dx, dz)) {
				const corner = Math.abs(dx) === 2 && Math.abs(dz) === 2;
				set(x, 1, z, block(corner ? m.rimCorner : m.rim));
				if (corner) set(x, 2, z, lantern(false));
			}
		}
		// Column with the bowl, a glowing block and the spout: the water runs over the top in four streams onto the bowl,
		// over its edge and down into the basin (in the game; the template only holds the source blocks).
		set(c, 1, c, block(m.shaftBase));
		for (const y of [2, 3, 4, 5]) set(c, y, c, up(m.shaft));
		for (const [dx, dz, facing] of [[1, 0, 'west'], [-1, 0, 'east'], [0, 1, 'north'], [0, -1, 'south']]) {
			set(c + dx, 3, c + dz, stair(m.bowl, facing, 'top'));
		}
		for (const dx of [-1, 1]) for (const dz of [-1, 1]) set(c + dx, 3, c + dz, state(slab(m.bowl), { type: 'top' }));
		set(c, 6, c, up(m.glow));
		set(c, 7, c, fluid());
		if (m.fluid === 'minecraft:water') {
			for (const [dx, dz] of [[1, -2], [-2, -1], [-1, 2], [2, 1]]) set(c + dx, 2, c + dz, block('minecraft:lily_pad'));
		}
		// Edge: lamp posts at the corners; along each side hedge, bench, flower bed, then the opening.
		const sides = [
			{ at: (t) => [c + t, 0], out: 'north' },
			{ at: (t) => [c + t, D - 1], out: 'south' },
			{ at: (t) => [0, c + t], out: 'west' },
			{ at: (t) => [W - 1, c + t], out: 'east' },
		];
		for (const side of sides) {
			for (let t = -R; t <= R; t++) {
				const [x, z] = side.at(t);
				const a = Math.abs(t);
				if (a === R) continue; // corners below
				if (a === R - 1) {
					set(x, 0, z, block(m.ground));
					set(x, 1, z, leaves(m.hedge));
				} else if (a === R - 2) {
					set(x, 1, z, stair(m.bench, side.out)); // back to the outside, the seat faces the fountain
				} else if (a === R - 3) {
					// Tall flowers frame each opening (single flowers where there are none).
					set(x, 0, z, block(m.ground));
					if (m.tall) tallPlant(x, z, m.tall);
					else set(x, 1, z, plant(flower(x, z)));
				}
			}
		}
		for (const x of [0, W - 1]) for (const z of [0, D - 1]) lampPost(x, z, 2);
		coreSign(c, 0, 'north');
	}

	/**
	 * Park: a hedge with a roofed gate at the front, a path to a gazebo (railing, benches, a lantern on a chain, a hipped
	 * roof with a lantern on top) and a walk around it, two trees with benches and flowers in front, a pond with lily
	 * pads (lava in the Nether) on one side and a flower garden with a bench on the other. Lamp posts at the corners and
	 * along the path. The sign stands inside the gate.
	 */
	function park() {
		const c = mid;
		// Gazebo: deck 5x5 at x c-2..c+2, z 6..10; roof eaves one block wider.
		const g0 = c - 2, g1 = c + 2, gz0 = 6, gz1 = 10;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const onPath = x >= c - 1 && x <= c + 1 && z <= gz0 - 1;
			const aroundGazebo = x >= g0 - 1 && x <= g1 + 1 && z >= gz0 - 1 && z <= gz1 + 1;
			const onDeck = x >= g0 && x <= g1 && z >= gz0 && z <= gz1;
			set(x, 0, z, block(onDeck ? m.deck : onPath || aroundGazebo ? m.path : m.ground));
		}
		// Hedge around, the gate in the middle of the front, lamp posts at the corners.
		for (let x = 0; x < W; x++) for (const z of [0, D - 1]) set(x, 1, z, leaves(m.hedge));
		for (let z = 1; z < D - 1; z++) for (const x of [0, W - 1]) set(x, 1, z, leaves(m.hedge));
		for (let x = c - 1; x <= c + 1; x++) grid.delete(`${x},1,0`);
		for (const x of [0, W - 1]) for (const z of [0, D - 1]) lampPost(x, z, 1);
		// Gate: two posts, a beam with hanging lanterns and a small gabled cap.
		for (const x of [c - 2, c + 2]) for (let y = 1; y <= 3; y++) set(x, y, 0, up(m.gzPost));
		for (let x = c - 2; x <= c + 2; x++) set(x, 4, 0, lying(m.gzPost, 'x'));
		set(c - 2, 5, 0, stair(m.gzRoof, 'east'));
		set(c + 2, 5, 0, stair(m.gzRoof, 'west'));
		for (let x = c - 1; x <= c + 1; x++) set(x, 5, 0, state(slab(m.gzRoof), { type: 'bottom' }));
		set(c - 1, 3, 0, lantern(true));
		set(c + 1, 3, 0, lantern(true));

		// Gazebo: corner posts, railing on three sides, a bench at the back, beams, hipped roof, lanterns.
		for (const x of [g0, g1]) for (const z of [gz0, gz1]) for (let y = 1; y <= 3; y++) set(x, y, z, up(m.gzPost));
		for (let z = gz0 + 1; z < gz1; z++) for (const x of [g0, g1]) set(x, 1, z, block(m.fence));
		for (let x = g0 + 1; x < g1; x++) {
			set(x, 1, gz1, block(m.fence));
			set(x, 1, gz1 - 1, stair(m.bench, 'south'));
		}
		for (let x = g0; x <= g1; x++) for (const z of [gz0, gz1]) set(x, 4, z, lying(m.gzPost, 'x'));
		for (let z = gz0 + 1; z < gz1; z++) for (const x of [g0, g1]) set(x, 4, z, lying(m.gzPost, 'z'));
		const rx0 = g0 - 1, rx1 = g1 + 1, rz0 = gz0 - 1, rz1 = gz1 + 1;
		for (let k = 0; k <= 3; k++) {
			const y = 4 + k;
			for (let x = rx0 + k; x <= rx1 - k; x++) for (let z = rz0 + k; z <= rz1 - k; z++) {
				const onRing = x === rx0 + k || x === rx1 - k || z === rz0 + k || z === rz1 - k;
				if (!onRing) continue;
				if (k === 3) { set(x, y, z, block(m.gzTop)); continue; }
				const facing = z === rz0 + k ? 'south' : z === rz1 - k ? 'north' : x === rx0 + k ? 'east' : 'west';
				if (k === 0 || get(x, y, z) === undefined) set(x, y, z, stair(m.gzRoof, facing));
			}
		}
		const gc = (gz0 + gz1) / 2;
		set(c, 8, gc, lantern(false));
		set(c, 6, gc, state('minecraft:iron_chain', { axis: 'y' }));
		set(c, 5, gc, lantern(true));
		set(c, 3, rz0, lantern(true)); // under the front eave
		// Lamp posts where the path meets the walk.
		for (const x of [c - 2, c + 2]) lampPost(x, gz0 - 2, 1);

		// Front: a tree on each side with a bench by the path and flowers around.
		for (const tx of [2, W - 3]) tree(tx, 2);
		set(c - 2, 1, 2, stair(m.bench, 'west'));
		set(c - 2, 1, 3, stair(m.bench, 'west'));
		set(c + 2, 1, 2, stair(m.bench, 'east'));
		set(c + 2, 1, 3, stair(m.bench, 'east'));
		coreSign(c - 2, 1, 'east');
		if (m.tall) tallPlant(c + 2, 1, m.tall);
		else set(c + 2, 1, 1, plant(flower(c + 2, 1)));
		for (let x = 1; x <= W - 2; x++) for (let z = 1; z <= 4; z++) {
			if (x >= c - 2 && x <= c + 2) continue;
			if (get(x, 1, z) !== undefined || (x + z) % 2) continue;
			set(x, 1, z, plant(flower(x, z)));
		}

		// Left: the pond. Right: a flower garden with a bench against the hedge.
		const pond = [[1, 6], [2, 6], [1, 7], [2, 7], [1, 8], [2, 8], [1, 9], [2, 9], [1, 10]];
		for (const [x, z] of pond) set(x, 0, z, fluid());
		if (m.fluid === 'minecraft:water') for (const [x, z] of [[1, 7], [2, 9]]) set(x, 1, z, block('minecraft:lily_pad'));
		if (m.reed) for (const y of [1, 2]) set(2, y, 10, block(m.reed));
		if (m.bush) {
			set(1, 1, 5, block(m.bush));
			set(1, 1, 11, block(m.bush));
		} else {
			set(1, 1, 5, plant(flower(1, 5)));
			set(1, 1, 11, plant(flower(1, 11)));
		}
		set(2, 1, 5, plant(flower(2, 5)));
		set(2, 1, 11, plant(flower(2, 11)));
		const fx = W - 2, fx2 = W - 3;
		for (let z = gz0 + 1; z <= gz1 - 1; z++) set(fx, 1, z, stair(m.bench, 'east'));
		for (const z of [gz0 - 1, gz0, gz1, gz1 + 1]) {
			set(fx2, 1, z, plant(flower(fx2, z)));
			if (m.bush && (z === gz0 || z === gz1)) set(fx, 1, z, block(m.bush));
			else set(fx, 1, z, plant(flower(fx, z + 1)));
		}
		if (m.tall) {
			tallPlant(fx2, gz0 + 1, m.tall);
			tallPlant(fx2, gz1 - 1, m.tall);
		}
	}

	/** A tree whose trunk stands at (x, z); the crown stays within two blocks of it (four for nothing). */
	function tree(x, z) {
		const t = m.tree;
		/** Fixed pseudo-random 0..99 of a position (same output on every run). */
		const noise = (a, y, b) => ((a * 73856093) ^ (y * 19349663) ^ (b * 83492791)) >>> 0 & 0x7fffffff;
		// sparse: share (0..100) of the outer cells left out, so that the crown is not a box.
		const disc = (y, r, trim, id = t.leaves, sparse = 0) => {
			for (let a = -r; a <= r; a++) for (let b = -r; b <= r; b++) {
				if (trim && Math.abs(a) === r && Math.abs(b) === r) continue;
				const outer = Math.max(Math.abs(a), Math.abs(b)) === r;
				if (outer && sparse && noise(x + a, y, z + b) % 100 < sparse) continue;
				if (get(x + a, y, z + b) === undefined) set(x + a, y, z + b, leaves(id));
			}
		};
		/** Leaves hanging one block under some cells of the outer ring of layer y. */
		const droop = (y, r) => {
			for (let a = -r; a <= r; a++) for (let b = -r; b <= r; b++) {
				if (Math.max(Math.abs(a), Math.abs(b)) !== r || Math.abs(a) === r && Math.abs(b) === r) continue;
				if (get(x + a, y, z + b) !== undefined && noise(x + a, y, z + b) % 100 < 40 && get(x + a, y - 1, z + b) === undefined) {
					set(x + a, y - 1, z + b, leaves(t.leaves));
				}
			}
		};
		const plus = (y, id = t.leaves) => {
			for (const [a, b] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) if (get(x + a, y, z + b) === undefined) set(x + a, y, z + b, leaves(id));
		};
		const trunk = (top) => {
			for (let y = 1; y <= top; y++) set(x, y, z, up(t.log));
		};
		switch (t.shape) {
			case 'round':
				trunk(5);
				disc(4, 2, true, t.leaves, 35);
				disc(5, 2, true, t.leaves, 10);
				disc(6, 2, true, t.leaves, 65);
				plus(7);
				droop(4, 2);
				break;
			case 'flat': // acacia: a thin trunk and a wide, flat crown
				trunk(5);
				disc(6, 2, true, t.leaves, 15);
				disc(7, 1, false);
				droop(6, 2);
				break;
			case 'palm':
				trunk(6);
				disc(7, 1, false);
				for (const [a, b] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) {
					set(x + a, 7, z + b, leaves(t.leaves));
					set(x + a, 6, z + b, leaves(t.leaves));
				}
				set(x, 8, z, leaves(t.leaves));
				break;
			case 'cone': // spruce: layers that get smaller to the top
				trunk(6);
				disc(3, 2, true, t.leaves, 20);
				disc(4, 1, false);
				disc(5, 2, true, t.leaves, 55);
				disc(6, 1, false);
				plus(7);
				set(x, 8, z, leaves(t.leaves));
				break;
			case 'fungus': // huge crimson fungus: a cap of wart blocks with shroomlights, weeping vines under the rim
				trunk(4);
				disc(5, 2, true, t.leaves, 20);
				disc(6, 1, false, t.leaves, 30);
				set(x, 7, z, block(t.leaves));
				for (const [a, b] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) {
					set(x + a, 4, z + b, block(t.leaves));
					set(x + a, 3, z + b, block('minecraft:weeping_vines'));
				}
				set(x + 1, 4, z + 1, block('minecraft:shroomlight'));
				set(x - 1, 4, z - 1, block('minecraft:shroomlight'));
				break;
			case 'chorus': { // a chorus plant with two branches and (grown-out) flowers
				for (let y = 1; y <= 4; y++) set(x, y, z, block('minecraft:chorus_plant'));
				const flowerTop = state('minecraft:chorus_flower', { age: '5' });
				set(x, 5, z, flowerTop);
				for (const a of [-1, 1]) {
					set(x + a, 3, z, block('minecraft:chorus_plant'));
					set(x + a, 4, z, flowerTop);
				}
				set(x, 2, z + 1, block('minecraft:chorus_plant'));
				set(x, 3, z + 1, flowerTop);
				break;
			}
		}
	}

	/** Timber-framed house with a porch at z = 0 and the front wall at z = 1. */
	function house() {
		const fz = 1;
		const bz = D - 1;
		// Floor: stone along the walls and on the porch, boards inside.
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const inside = x > 0 && x < W - 1 && z > fz && z < bz;
			set(x, 0, z, block(inside ? m.floor : m.found));
		}
		// Porch: two posts and a beam carrying the front of the roof.
		for (let y = 1; y <= wall; y++) {
			set(0, y, 0, up(m.pillar));
			set(W - 1, y, 0, up(m.pillar));
		}
		for (let x = 1; x < W - 1; x++) set(x, wall, 0, lying(m.beam, 'x'));

		// Posts, windows (2 high) and plain wall along each side; base course of stone, beam on top.
		const posts = { 5: [0, 4], 9: [0, 3, 5, 8] }[W];
		const frontWindows = { 5: [1, 3], 9: [1, 2, 6, 7] }[W];
		const backPosts = { 5: [0, 4], 9: [0, 4, 8] }[W];
		const backWindows = { 5: [2], 9: [2, 6] }[W];
		const sidePosts = { 5: [1, 4], 9: [1, 4, 8] }[D];
		const sideWindows = { 5: [2, 3], 9: [2, 3, 6] }[D];
		const windowRows = W === 5 ? [2] : [2, 3];
		const cell = (x, y, z, post, win, axis) => {
			if (post) return set(x, y, z, up(m.pillar));
			if (y === wall) return set(x, y, z, lying(m.beam, axis));
			if (win && windowRows.includes(y)) return set(x, y, z, block(m.window));
			set(x, y, z, block(y === 1 ? m.found : m.wall));
		};
		for (let y = 1; y <= wall; y++) {
			for (let x = 0; x < W; x++) {
				cell(x, y, fz, posts.includes(x), frontWindows.includes(x), 'x');
				cell(x, y, bz, backPosts.includes(x), backWindows.includes(x), 'x');
			}
			for (let z = fz + 1; z < bz; z++) {
				cell(0, y, z, sidePosts.includes(z), sideWindows.includes(z), 'z');
				cell(W - 1, y, z, sidePosts.includes(z), sideWindows.includes(z), 'z');
			}
		}
		door(mid, fz);
		// Tie beam across the room; the lanterns hang from it.
		const tz = sidePosts.length > 2 ? sidePosts[1] : Math.floor((fz + bz) / 2);
		for (let x = 1; x < W - 1; x++) set(x, wall, tz, lying(m.beam, 'x'));
		if (W === 5) set(mid, wall - 1, tz, lantern(true));
		else for (const x of [2, W - 3]) set(x, wall - 1, tz, lantern(true));

		roof(fz, bz);
		sign();
		const porchSides = [1, W - 2];
		switch (kind) {
			case 'quest_hall':
				// Bell under the porch, bookshelves at the back, a lectern and a map table.
				set(1, wall - 1, 0, state('minecraft:bell', { attachment: 'ceiling', facing: 'east' }));
				set(W - 2, wall - 1, 0, lantern(true));
				if (m.pot) set(W - 2, 1, 0, block(m.pot));
				for (const x of [1, W - 2]) for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
				set(1, 1, 2, state('minecraft:lectern', { facing: 'east' }));
				set(W - 2, 1, 2, block('minecraft:cartography_table'));
				break;
			case 'warehouse':
				// Goods on the porch, barrels stacked along one wall, chests along the other.
				set(1, 1, 0, state('minecraft:barrel', { facing: 'up' }));
				set(1, 2, 0, state('minecraft:barrel', { facing: 'up' }));
				set(2, 1, 0, state('minecraft:barrel', { facing: 'north' }));
				set(W - 2, 1, 0, lying('minecraft:hay_block', 'x'));
				set(W - 3, 1, 0, lying('minecraft:hay_block', 'z'));
				set(W - 2, 2, 0, lying('minecraft:hay_block', 'x'));
				set(mid - 2, wall - 1, 0, lantern(true));
				set(mid + 2, wall - 1, 0, lantern(true));
				for (let z = fz + 1; z < bz; z++) {
					set(1, 1, z, state('minecraft:barrel', { facing: 'up' }));
					set(1, 2, z, state('minecraft:barrel', { facing: z % 2 ? 'east' : 'up' }));
					if (z <= D - 3) set(W - 2, 1, z, state('minecraft:chest', { facing: 'west' }));
				}
				set(W - 2, 1, D - 2, lying('minecraft:hay_block', 'y'));
				set(W - 2, 2, D - 2, lying('minecraft:hay_block', 'y'));
				set(W - 3, 1, D - 2, lying('minecraft:hay_block', 'z'));
				set(2, 1, D - 2, block('minecraft:crafting_table'));
				break;
			case 'inn':
				// Four beds along the side walls (head at the wall), a rug down the middle, a fireplace with a chimney.
				for (const z of [3, D - 3]) {
					if (m.bed) {
						set(1, 1, z, state(m.bed, { facing: 'west', part: 'head' }));
						set(2, 1, z, state(m.bed, { facing: 'west', part: 'foot' }));
						set(W - 2, 1, z, state(m.bed, { facing: 'east', part: 'head' }));
						set(W - 3, 1, z, state(m.bed, { facing: 'east', part: 'foot' }));
					} else {
						for (const x of [1, 2, W - 3, W - 2]) set(x, 1, z, block(m.carpet));
					}
				}
				for (let z = fz + 1; z < D - 2; z++) set(mid, 1, z, block(m.carpet));
				set(1, 1, D - 2, state('minecraft:barrel', { facing: 'up' }));
				set(1, 1, 4, block(m.pot || m.carpet));
				set(W - 2, 1, 4, block('minecraft:crafting_table'));
				chimney(W - 2, D - 2);
				for (const x of porchSides) {
					set(x, wall - 1, 0, lantern(true));
					if (m.pot) set(x, 1, 0, block(m.pot));
				}
				break;
		}
	}

	/** Gable roof (ridge from front to back) or, for flat styles, a flat roof with a parapet. */
	function roof(fz, bz) {
		if (m.flat) {
			for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
				set(x, wall + 1, z, block(m.roofBlock));
				const edge = x === 0 || x === W - 1 || z === 0 || z === D - 1;
				if (edge) set(x, wall + 2, z, (x === 0 || x === W - 1) && (z === 0 || z === D - 1) ? up(m.pillar) : block(m.parapet));
			}
			return;
		}
		for (let k = 0; k < mid; k++) {
			const y = wall + 1 + k;
			for (let z = 0; z < D; z++) {
				set(k, y, z, stair(m.roof, 'east'));
				set(W - 1 - k, y, z, stair(m.roof, 'west'));
			}
			// Gables at the front and back wall; the porch side stays open.
			for (let x = k + 1; x < W - 1 - k; x++) for (const z of [fz, bz]) set(x, y, z, block(m.wall));
		}
		for (let z = 0; z < D; z++) set(mid, wall + 1 + mid, z, state(slab(m.roof), { type: 'bottom' }));
		// A small window in each gable.
		for (const z of [fz, bz]) set(mid, wall + 2, z, block(m.window));
		if (W >= 9) for (const z of [fz, bz]) set(mid, wall + 3, z, block(m.window));
	}

	/** Chimney column at (x, z) from a furnace on the floor up through the roof, a fire or rod on top. */
	function chimney(x, z) {
		let roofY = wall;
		for (let y = wall; y < 64; y++) if (get(x, y, z) !== undefined) roofY = y; // the roof over the column
		const top = roofY + 2;
		set(x, 1, z, state('minecraft:furnace', { facing: 'west' }));
		for (let y = 2; y < top; y++) set(x, y, z, block(m.chimney));
		set(x, top, z, m.chimneyTop.endsWith('campfire') ? state(m.chimneyTop, { lit: 'true', signal_fire: 'false' })
				: state(m.chimneyTop, { facing: 'up' }));
	}

	/** Stone house with a portico of columns, cornice, barred windows, teller counter, ceiling and hipped roof. */
	function bank() {
		const fz = 1;
		const bz = D - 1;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const inside = x > 0 && x < W - 1 && z > fz && z < bz;
			set(x, 0, z, block(!inside ? m.stoneBase : (x + z) % 2 ? m.bankChecker : m.bankFloor));
		}
		// Portico: columns on bases, the cornice on top with a gold block over the door.
		for (const x of [0, 2, W - 3, W - 1]) for (let y = 1; y < wall; y++) set(x, y, 0, up(m.column));
		for (let x = 0; x < W; x++) set(x, wall, 0, block(m.cornice));
		set(mid, wall, 0, block('minecraft:gold_block'));
		set(1, wall - 1, 0, lantern(true));
		set(W - 2, wall - 1, 0, lantern(true));
		// Walls: columns at the corners, a base course, barred windows, the cornice on top.
		const frontWindows = [1, W - 2];
		const backWindows = [2, W - 3];
		const sideWindows = [3, 4];
		const cell = (x, y, z, corner, win) => {
			if (y === wall) return set(x, y, z, block(m.cornice));
			if (corner) return set(x, y, z, up(m.column));
			if (win && (y === 2 || y === 3)) return set(x, y, z, block('minecraft:iron_bars'));
			set(x, y, z, block(y === 1 ? m.stoneBase : m.stone));
		};
		for (let y = 1; y <= wall; y++) {
			for (let x = 0; x < W; x++) {
				const corner = x === 0 || x === W - 1;
				cell(x, y, fz, corner, frontWindows.includes(x));
				cell(x, y, bz, corner, backWindows.includes(x));
			}
			for (let z = fz + 1; z < bz; z++) {
				cell(0, y, z, false, sideWindows.includes(z));
				cell(W - 1, y, z, false, sideWindows.includes(z));
			}
		}
		door(mid, fz);
		// Ceiling with a lantern in the middle of the hall.
		for (let x = 1; x < W - 1; x++) for (let z = fz + 1; z < bz; z++) set(x, wall, z, block(m.ceiling));
		set(mid, wall - 1, Math.floor((fz + 1 + D - 3) / 2), lantern(true));
		// Hipped roof: rings of stairs facing inward, a slab on the top.
		for (let k = 0; k <= mid; k++) {
			const y = wall + 1 + k;
			for (let x = k; x < W - k; x++) for (let z = k; z < D - k; z++) {
				const onRing = x === k || x === W - 1 - k || z === k || z === D - 1 - k;
				if (!onRing) continue;
				if (k === mid) { set(x, y, z, state(slab(m.bankRoof), { type: 'bottom' })); continue; }
				const facing = z === k ? 'south' : z === D - 1 - k ? 'north' : x === k ? 'east' : 'west';
				set(x, y, z, stair(m.bankRoof, facing));
			}
		}
		// Rug from the door, the counter with teller bars and gold, the vault behind it.
		for (let z = fz + 1; z < D - 3; z++) set(mid, 1, z, block(m.carpet));
		for (let x = 1; x < W - 1; x++) if (x !== mid) set(x, 1, D - 3, block(m.counter));
		set(1, 2, D - 3, block('minecraft:gold_block'));
		set(W - 2, 2, D - 3, block('minecraft:gold_block'));
		set(mid - 1, 2, D - 3, block('minecraft:iron_bars'));
		set(mid + 1, 2, D - 3, block('minecraft:iron_bars'));
		set(1, 1, D - 2, state('minecraft:chest', { facing: 'east' }));
		set(W - 2, 1, D - 2, state('minecraft:chest', { facing: 'west' }));
		sign();
	}

	if (kind === 'bank') bank();
	else if (kind === 'fountain') fountain();
	else if (kind === 'park') park();
	else house();

	// Air everywhere else in the box (the site is checked to be free up to the top).
	let H = 0;
	for (const key of grid.keys()) H = Math.max(H, Number(key.split(',')[1]) + 1);
	const air = state('minecraft:air');
	for (let x = 0; x < W; x++) for (let y = 0; y < H; y++) for (let z = 0; z < D; z++) {
		if (!grid.has(`${x},${y},${z}`)) grid.set(`${x},${y},${z}`, air);
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
