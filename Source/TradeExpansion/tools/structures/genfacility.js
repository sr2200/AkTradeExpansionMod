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

	if (kind === 'bank') bank();
	else house();

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
