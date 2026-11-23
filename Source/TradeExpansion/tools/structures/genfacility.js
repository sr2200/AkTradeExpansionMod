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
//   v0.84: from 7 wide the side walls stand one block in, so that the eaves hang over them (bushes and open shutters
//   under them); eave row and ridge of a darker material, upside-down stairs under the roof, a second stone in the base,
//   roof beams sticking out of flat roofs, the chimney outside the right wall, the kind's goods under the eaves.
// - bank: a stone house with a portico of columns at the front, a cornice, barred windows, a teller counter, a ceiling
//   and a hipped roof of stairs.
//   v0.87: the stone buildings (bank, mint, guild hall, town hall, station) stand their side walls one block in as the
//   houses do (stoneBody), with columns and window sills in the strip under the eaves; the bank has a gable with a
//   pediment, the mint and the town hall a hipped roof with a flat top (the town hall's with a cupola and a bell).
//   Inn, school and appraiser have their own things outside (houseOutside); tall houses and stone buildings have stairs
//   up to the second storey (staircase); chapel, station, fountain, clock tower and watchtower have a higher level 3
//   (spec.high); direction b of a raised building has banners of another colour; levelExtras adds the decorations of
//   single kinds at level 2 / 3.
// - fountain (11x11) and park (13x13), the leisure facilities: open plazas without walls; see fountain() and park().
// - v0.51: clock_tower, watchtower, market_hall, harbor (front rows on land, the rest over water), theater, hot_spring
//   and festival have their own functions; the tavern is a house with its own interior.
//   The sign stands in the front opening (fountain) or inside the gate (park).
// - mint (7x7): a stone workshop with a porch (anvil, quenching cauldron, copper), blast furnaces at the back and a
//   chimney through the hipped roof.
// - v0.79: appraiser (7x7), wholesaler, workshop and school (9x9) are houses with their own interiors; guild hall (7x9)
//   and town hall (9x9) are stone halls (stoneHall); chapel (7x9) and station (9x11) have their own functions.
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

// v0.51 buildings: the fence of the hot spring and the rocks around its bath.
const extra = {
	plains: { springFence: 'minecraft:bamboo_fence', rocks: ['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:andesite', 'minecraft:stone'] },
	desert: { springFence: 'minecraft:bamboo_fence', rocks: ['minecraft:sandstone', 'minecraft:smooth_sandstone', 'minecraft:terracotta', 'minecraft:andesite'] },
	savanna: { springFence: 'minecraft:bamboo_fence', rocks: ['minecraft:terracotta', 'minecraft:red_sandstone', 'minecraft:andesite', 'minecraft:cobblestone'] },
	snowy: { springFence: 'minecraft:spruce_fence', rocks: ['minecraft:cobblestone', 'minecraft:stone', 'minecraft:andesite', 'minecraft:diorite'] },
	taiga: { springFence: 'minecraft:spruce_fence', rocks: ['minecraft:mossy_cobblestone', 'minecraft:cobblestone', 'minecraft:mossy_stone_bricks', 'minecraft:andesite'] },
	nether: { springFence: 'minecraft:crimson_fence', rocks: ['minecraft:blackstone', 'minecraft:basalt', 'minecraft:polished_blackstone', 'minecraft:gilded_blackstone'] },
	end: { springFence: 'minecraft:end_stone_brick_wall', rocks: ['minecraft:end_stone', 'minecraft:end_stone_bricks', 'minecraft:purpur_block', 'minecraft:obsidian'] },
};
for (const [style, m] of Object.entries(styles)) Object.assign(m, extra[style]);

// v0.84 houses: open shutters beside the side windows (trapdoor), the eave row and ridge of the gable roof (stem of
// <stem>_stairs / <stem>_slab), a second stone mixed into the base (by a fixed pattern) and, for flat roofs, the roof
// beams sticking out under the eaves.
const houseLook = {
	plains: { shutter: 'minecraft:spruce_trapdoor', roofEdge: 'minecraft:dark_oak', foundMix: 'minecraft:mossy_cobblestone' },
	desert: { shutter: 'minecraft:jungle_trapdoor', foundMix: 'minecraft:sandstone', viga: 'minecraft:stripped_jungle_log' },
	savanna: { shutter: 'minecraft:acacia_trapdoor', roofEdge: 'minecraft:acacia', foundMix: 'minecraft:andesite' },
	snowy: { shutter: 'minecraft:spruce_trapdoor', roofEdge: 'minecraft:dark_oak', foundMix: 'minecraft:andesite' },
	taiga: { shutter: 'minecraft:spruce_trapdoor', roofEdge: 'minecraft:spruce', foundMix: 'minecraft:cobblestone' },
	nether: { shutter: 'minecraft:crimson_trapdoor', roofEdge: 'minecraft:red_nether_brick', foundMix: 'minecraft:blackstone' },
	end: { shutter: 'minecraft:warped_trapdoor', roofEdge: 'minecraft:end_stone_brick', foundMix: 'minecraft:end_stone' },
};
for (const [style, m] of Object.entries(styles)) Object.assign(m, houseLook[style]);

// v0.87 stone buildings: the stem of the slabs under their side windows (<stem>_slab), and the banner colour of the
// direction b of a raised building (N-11; direction a uses the carpet colour).
const stoneLook = {
	plains: { stoneStem: 'minecraft:stone_brick', branchColour: 'minecraft:blue' },
	desert: { stoneStem: 'minecraft:smooth_sandstone', branchColour: 'minecraft:cyan' },
	savanna: { stoneStem: 'minecraft:cut_red_sandstone', branchColour: 'minecraft:green' },
	snowy: { stoneStem: 'minecraft:polished_diorite', branchColour: 'minecraft:red' },
	taiga: { stoneStem: 'minecraft:stone_brick', branchColour: 'minecraft:brown' },
	nether: { stoneStem: 'minecraft:polished_blackstone_brick', branchColour: 'minecraft:black' },
	end: { stoneStem: 'minecraft:end_stone_brick', branchColour: 'minecraft:magenta' },
};
for (const [style, m] of Object.entries(styles)) Object.assign(m, stoneLook[style]);

/** Blocks with an axis property (logs, stems, pillars). */
const AXIS = /(_log|_stem|_pillar|basalt|hay_block)$/;

const S = (v) => ({ t: 8, v });
const I = (v) => ({ t: 3, v });
const C = (o) => ({ t: 10, v: o });
const L = (arr, et) => ({ t: 9, et, v: arr });

/** Footprints (width x depth) and wall heights. */
const facilities = {
	quest_hall: { w: 5, d: 5, wall: 4, tall: true },
	bank: { w: 7, d: 7, wall: 4, tall: true },
	warehouse: { w: 9, d: 9, wall: 4, tall: true },
	inn: { w: 9, d: 9, wall: 4, tall: true },
	fountain: { w: 11, d: 11, high: true },
	park: { w: 13, d: 13, high: true },
	clock_tower: { w: 7, d: 7, high: true },
	watchtower: { w: 5, d: 5, high: true },
	tavern: { w: 9, d: 9, wall: 4, tall: true },
	market_hall: { w: 11, d: 9, high: true },
	harbor: { w: 9, d: 13, high: true }, // rows z 0..5 on land, 6..12 over the water (data: "waterside": 6)
	theater: { w: 11, d: 11, high: true },
	hot_spring: { w: 9, d: 9, high: true },
	festival: { w: 11, d: 11, high: true },
	mint: { w: 7, d: 7, wall: 4, tall: true },
	// v0.79
	station: { w: 9, d: 11, wall: 4, high: true },
	appraiser: { w: 7, d: 7, wall: 4, tall: true },
	wholesaler: { w: 9, d: 9, wall: 4, tall: true },
	guild_hall: { w: 7, d: 9, wall: 4, tall: true },
	chapel: { w: 7, d: 9, wall: 5, high: true },
	town_hall: { w: 9, d: 9, wall: 4, tall: true },
	workshop: { w: 9, d: 9, wall: 4, tall: true },
	school: { w: 9, d: 9, wall: 4, tall: true },
};

/** Stairs and slabs from the stem of their block ids: minecraft:spruce -> minecraft:spruce_stairs. */
const stairs = (stem) => stem + '_stairs';
const slab = (stem) => stem + '_slab';

/**
 * One template. lv (v0.86, N-11): 1 = the building as built, 2 / 3 = the same building with decorations added (decorate);
 * tall: level 3 with a second storey (kinds with spec.tall; the walls go up 4 more, see top) or, v0.87, a higher shape
 * of its own (kinds with spec.high: chapel, station, fountain, clock tower, watchtower). branch (v0.87): the direction
 * chosen at level 2; b has banners of another colour (m.branchColour).
 */
function build(style, m, kind, spec, lv = 1, tall = false, branch = 'a') {
	const { w: W, d: D, wall } = spec;
	// Height of the walls under the roof: the second storey of a tall level 3 adds 4 (its floor is at y = wall).
	const storey = tall && !!spec.tall;
	const top = storey ? wall + 4 : wall;
	/** Window rows of the ground floor (rows), repeated on the second storey. */
	const winRow = (y, rows) => rows.includes(y) || storey && rows.includes(y - wall);
	const palette = [];
	const index = new Map();
	const ids = [];
	const state = (id, props) => {
		const key = id + JSON.stringify(props || {});
		if (!index.has(key)) {
			const entry = { id: S(id) };
			if (props) entry.properties = C(Object.fromEntries(Object.entries(props).map(([k, v]) => [k, S(v)])));
			index.set(key, palette.length);
			ids.push(id);
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
	 * on top whose water runs over the bowl in two streams (left and right) back into the basin. Paved ring with a compass pattern, lamp
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
		// Column with the bowl, a glowing block and the spout: the water runs over the top in two streams onto the bowl,
		// over its edge and down into the basin (in the game; the template only holds the source blocks).
		// v0.87 level 3 (tall): the column is 4 higher with a second bowl at y 7; the water then runs over that bowl
		// straight down into the basin.
		const rise = tall ? 4 : 0;
		set(c, 1, c, block(m.shaftBase));
		for (let y = 2; y <= 5 + rise; y++) set(c, y, c, up(m.shaft));
		for (const by of rise ? [3, 7] : [3]) {
			for (const [dx, dz, facing] of [[1, 0, 'west'], [-1, 0, 'east'], [0, 1, 'north'], [0, -1, 'south']]) {
				set(c + dx, by, c + dz, stair(m.bowl, facing, 'top'));
			}
			for (const dx of [-1, 1]) for (const dz of [-1, 1]) set(c + dx, by, c + dz, state(slab(m.bowl), { type: 'top' }));
		}
		set(c, 6 + rise, c, up(m.glow));
		set(c, 7 + rise, c, fluid());
		// v0.88: two cheeks at the front and the back of the spout, so that the water runs over the bowls only to the left
		// and the right; from the front both bowls stay in sight (four streams hid them, the lower one most of all).
		set(c, 7 + rise, c - 1, stair(m.bowl, 'south'));
		set(c, 7 + rise, c + 1, stair(m.bowl, 'north'));
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
		if (tall) {
			// v0.88 level 3 (high): an open lantern turret on the gazebo roof (fence posts, a small hipped roof, a rod).
			for (const x of [c - 1, c + 1]) for (const z of [gc - 1, gc + 1]) for (const y of [7, 8]) set(x, y, z, block(m.fence));
			hipRoof(c - 1, gc - 1, c + 1, gc + 1, 9, m.gzRoof, m.gzTop);
			set(c, 9, gc, block(m.gzTop));
			set(c, 11, gc, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
		}
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
			case 'chorus': { // a chorus plant with three branches and (grown-out) flowers
				// A stem with a side branch must have air above or below it (vanilla ChorusPlantBlock.canSurvive), or the
				// block updates of the completion break it: the branches leave the top of the trunk, which ends there.
				const plant = (sides) => state('minecraft:chorus_plant', Object.fromEntries(
					['north', 'east', 'south', 'west', 'up', 'down'].map((d) => [d, String(sides.includes(d))])));
				const flowerTop = state('minecraft:chorus_flower', { age: '5' });
				set(x, 1, z, plant(['up', 'down']));
				set(x, 2, z, plant(['up', 'down']));
				set(x, 3, z, plant(['down', 'east', 'west', 'south']));
				for (const [a, toTrunk] of [[-1, 'east'], [1, 'west']]) {
					set(x + a, 3, z, plant([toTrunk, 'up']));
					set(x + a, 4, z, plant(['up', 'down']));
					set(x + a, 5, z, flowerTop);
				}
				set(x, 3, z + 1, plant(['north', 'up']));
				set(x, 4, z + 1, flowerTop);
				break;
			}
		}
	}

	/**
	 * Timber-framed house (quest hall 5x5, appraiser 7x7, the others 9x9) with a porch at z = 0 and the front wall at
	 * z = 1. From 7 wide the side walls stand one block in from the edge (x0 / x1), so that the eaves hang over them; that
	 * strip is a stone drip edge with bushes under the windows and open shutters beside them (or the kind's goods).
	 */
	function house() {
		const fz = 1;
		const bz = D - 1;
		const ins = W >= 7 ? 1 : 0;
		const x0 = ins, x1 = W - 1 - ins; // the side walls
		const L = x0 + 1, R = x1 - 1; // the room
		const span = x1 - x0 + 1;
		// Stone with a few blocks of a second stone, by a fixed pattern.
		const found = (x, z) => block(m.foundMix && (x * 7 + z * 3) % 5 === 0 ? m.foundMix : m.found);
		// Floor: boards in the room, stone under the walls, on the porch and along the sides.
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const inside = x > x0 && x < x1 && z > fz && z < bz;
			set(x, 0, z, inside ? block(m.floor) : found(x, z));
		}
		// Porch: two posts and a beam carrying the front of the roof. Tall (v0.86): the posts go up to the roof, the beam
		// over the porch carries a balcony with a railing, and a second beam carries the roof.
		for (let y = 1; y <= top; y++) {
			set(x0, y, 0, up(m.pillar));
			set(x1, y, 0, up(m.pillar));
		}
		for (let x = x0 + 1; x < x1; x++) set(x, wall, 0, lying(m.beam, 'x'));
		if (tall) {
			for (let x = x0 + 1; x < x1; x++) {
				set(x, wall + 1, 0, block(m.fence));
				set(x, top, 0, lying(m.beam, 'x'));
			}
		}

		// Posts, windows (2 high) and plain wall along each side; base course of stone, beam on top.
		const rel = (list) => list.map((r) => x0 + r);
		const posts = rel({ 5: [0, 4], 7: [0, 2, 4, 6] }[span]);
		const frontWindows = rel({ 5: [1, 3], 7: [1, 5] }[span]);
		const backPosts = rel({ 5: [0, 4], 7: [0, 3, 6] }[span]);
		const backWindows = rel({ 5: [2], 7: [1, 5] }[span]);
		const sidePosts = { 5: [1, 4], 7: [1, 6], 9: [1, 4, 8] }[D];
		const sideWindows = { 5: [2, 3], 7: [3, 4], 9: [2, 3, 6, 7] }[D];
		const windowRows = W === 5 ? [2] : [2, 3];
		const cell = (x, y, z, post, win, axis) => {
			if (post) return set(x, y, z, up(m.pillar));
			if (y === wall || y === top) return set(x, y, z, lying(m.beam, axis));
			if (win && winRow(y, windowRows)) return set(x, y, z, block(m.window));
			set(x, y, z, y === 1 ? found(x, z) : block(m.wall));
		};
		for (let y = 1; y <= top; y++) {
			for (let x = x0; x <= x1; x++) {
				cell(x, y, fz, posts.includes(x), frontWindows.includes(x), 'x');
				cell(x, y, bz, backPosts.includes(x), backWindows.includes(x), 'x');
			}
			for (let z = fz + 1; z < bz; z++) {
				cell(x0, y, z, sidePosts.includes(z), sideWindows.includes(z), 'z');
				cell(x1, y, z, sidePosts.includes(z), sideWindows.includes(z), 'z');
			}
		}
		door(mid, fz);
		if (tall) {
			// The second storey: boards over the room, a door out to the balcony, a rug and a lantern under the upper
			// tie beam.
			for (let x = x0 + 1; x < x1; x++) for (let z = fz + 1; z < bz; z++) set(x, wall, z, block(m.floor));
			set(mid, wall + 1, fz, state(m.door, { facing: 'south', half: 'lower', hinge: 'left' }));
			set(mid, wall + 2, fz, state(m.door, { facing: 'south', half: 'upper', hinge: 'left' }));
			for (let z = fz + 1; z < bz; z++) set(mid, wall + 1, z, block(m.carpet));
		}
		// Under the eaves: a bush under each side window and open shutters on both sides of each pair of windows.
		const sides = ins ? [[0, 'west'], [W - 1, 'east']] : [];
		for (const [xo, facing] of sides) {
			for (const z of sideWindows) {
				set(xo, 0, z, block(m.ground));
				set(xo, 1, z, leaves(m.hedge));
				for (const dz of [-1, 1]) {
					const sz = z + dz;
					if (sideWindows.includes(sz) || sz < 1 || sz >= D) continue;
					for (const y of windowRows) {
						set(xo, y, sz, state(m.shutter, { facing, half: 'bottom', open: 'true' }));
						if (tall) set(xo, y + wall, sz, state(m.shutter, { facing, half: 'bottom', open: 'true' }));
					}
				}
			}
		}
		// Tie beam across the room; the lanterns hang from it (tall: on both storeys).
		const tz = sidePosts.length > 2 ? sidePosts[1] : Math.floor((fz + bz) / 2);
		for (const ty of tall ? [wall, top] : [wall]) {
			for (let x = x0 + 1; x < x1; x++) set(x, ty, tz, lying(m.beam, 'x'));
			for (const x of span === 7 ? [L + 1, R - 1] : [mid]) set(x, ty - 1, tz, lantern(true));
		}

		roof(fz, bz, ins);
		sign();
		const porchSides = [x0 + 1, x1 - 1];
		/** Goods along one outside side instead of the bushes (z from..to at x). */
		const sideRow = (x, from, to, at) => {
			for (let z = from; z <= to; z++) {
				set(x, 0, z, found(x, z));
				set(x, 1, z, at(z, 1));
			}
		};
		switch (kind) {
			case 'quest_hall':
				// Bell under the porch, bookshelves at the back, a lectern and a map table.
				set(porchSides[0], wall - 1, 0, state('minecraft:bell', { attachment: 'ceiling', facing: 'east' }));
				set(porchSides[1], wall - 1, 0, lantern(true));
				if (m.pot) set(porchSides[1], 1, 0, block(m.pot));
				for (const x of [L, R]) for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
				set(L, 1, 2, state('minecraft:lectern', { facing: 'east' }));
				set(R, 1, 2, block('minecraft:cartography_table'));
				break;
			case 'warehouse':
				// Goods on the porch, barrels stacked along one wall, chests along the other; firewood stacked under both
				// eaves and a hoist over a loft door in the front gable.
				set(L, 1, 0, state('minecraft:barrel', { facing: 'up' }));
				set(L, 2, 0, state('minecraft:barrel', { facing: 'up' }));
				set(L + 1, 1, 0, state('minecraft:barrel', { facing: 'north' }));
				set(R, 1, 0, lying('minecraft:hay_block', 'x'));
				set(R - 1, 1, 0, lying('minecraft:hay_block', 'z'));
				set(R, 2, 0, lying('minecraft:hay_block', 'x'));
				set(mid - 2, wall - 1, 0, lantern(true));
				set(mid + 2, wall - 1, 0, lantern(true));
				for (let z = fz + 1; z < bz; z++) {
					set(L, 1, z, state('minecraft:barrel', { facing: 'up' }));
					set(L, 2, z, state('minecraft:barrel', { facing: z % 2 ? 'east' : 'up' }));
					if (z <= D - 3) set(R, 1, z, state('minecraft:chest', { facing: 'west' }));
				}
				set(R, 1, D - 2, lying('minecraft:hay_block', 'y'));
				set(R, 2, D - 2, lying('minecraft:hay_block', 'y'));
				set(R - 1, 1, D - 2, lying('minecraft:hay_block', 'z'));
				set(L + 1, 1, D - 2, block('minecraft:crafting_table'));
				for (const x of [0, W - 1]) sideRow(x, 2, D - 2, () => lying(m.pillar, 'z'));
				if (!m.flat) {
					grid.delete(`${mid},${top + 2},${fz}`);
					set(mid, top + 1, fz, state(m.door, { facing: 'south', half: 'lower', hinge: 'left' }));
					set(mid, top + 2, fz, state(m.door, { facing: 'south', half: 'upper', hinge: 'left' }));
					set(mid, top + 3, 0, lying(m.beam, 'z'));
					set(mid, top + 2, 0, state('minecraft:iron_chain', { axis: 'y' }));
					set(mid, top + 1, 0, lying('minecraft:hay_block', 'y'));
				}
				break;
			case 'inn':
				// Four beds along the side walls (head at the wall), a rug down the middle, a fireplace with a chimney.
				for (const z of [3, D - 3]) {
					if (m.bed) {
						set(L, 1, z, state(m.bed, { facing: 'west', part: 'head' }));
						set(L + 1, 1, z, state(m.bed, { facing: 'west', part: 'foot' }));
						set(R, 1, z, state(m.bed, { facing: 'east', part: 'head' }));
						set(R - 1, 1, z, state(m.bed, { facing: 'east', part: 'foot' }));
					} else {
						for (const x of [L, L + 1, R - 1, R]) set(x, 1, z, block(m.carpet));
					}
				}
				for (let z = fz + 1; z < D - 2; z++) set(mid, 1, z, block(m.carpet));
				set(L, 1, D - 2, state('minecraft:barrel', { facing: 'up' }));
				set(L, 1, 4, block(m.pot || m.carpet));
				set(R, 1, D - 2, block('minecraft:crafting_table'));
				chimney(5);
				for (const x of porchSides) {
					set(x, wall - 1, 0, lantern(true));
					if (m.pot) set(x, 1, 0, block(m.pot));
				}
				break;
			case 'tavern': {
				// v0.51: a hanging sign and a table with stools on the porch; inside the bar (a counter in front of
				// stacked casks), a table with candles and chairs against each side wall, and a fireplace with a chimney.
				// Casks stand under the eaves on the other side.
				const wood = m.bench;
				set(mid - 2, wall - 1, 0, state((wood === 'minecraft:purpur' ? 'minecraft:warped' : wood) + '_hanging_sign', { rotation: '0', attached: 'false' }));
				set(mid + 2, wall - 1, 0, lantern(true));
				set(L, 1, 0, state('minecraft:barrel', { facing: 'up' }));
				set(R, 1, 0, block(m.fence));
				set(R, 2, 0, state('minecraft:candle', { candles: '2', lit: 'true' }));
				set(R - 1, 1, 0, stair(m.bench, 'west')); // back away from the table
				for (let x = L; x <= R; x++) {
					if (x === mid) continue; // the sign stands in the gap
					set(x, 1, D - 3, stair(m.bench, 'south', 'top')); // counter
					set(x, 1, D - 2, state('minecraft:barrel', { facing: 'north' })); // casks behind it
					if (x !== mid - 1 && x !== mid + 1) set(x, 2, D - 2, state('minecraft:barrel', { facing: 'north' }));
				}
				set(L, 2, D - 3, state('minecraft:brewing_stand', {}));
				set(R, 2, D - 3, state('minecraft:candle', { candles: '3', lit: 'true' }));
				for (const tx of [L, R]) {
					set(tx, 1, 3, block(m.fence));
					set(tx, 2, 3, state('minecraft:candle', { candles: '1', lit: 'true' }));
					set(tx, 1, 2, stair(m.bench, 'north'));
					set(tx, 1, 4, stair(m.bench, 'south'));
				}
				for (let z = 2; z < D - 3; z++) set(mid, 1, z, block(m.carpet));
				chimney(5);
				sideRow(0, 4, 5, () => state('minecraft:barrel', { facing: 'up' }));
				break;
			}
			case 'appraiser':
				// v0.79: a small shop: a display counter with amethyst and a candle, a lectern and a map table, bookshelves
				// at the back.
				set(porchSides[0], wall - 1, 0, lantern(true));
				set(porchSides[1], wall - 1, 0, lantern(true));
				if (m.pot) set(porchSides[0], 1, 0, block(m.pot));
				set(porchSides[1], 1, 0, state('minecraft:barrel', { facing: 'up' }));
				for (const x of [L, R]) for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
				set(L, 1, D - 3, state('minecraft:chiseled_bookshelf', { facing: 'east' }));
				for (const z of [2, 3]) set(R, 1, z, stair(m.bench, 'west', 'top')); // display counter
				set(R, 2, 2, state('minecraft:amethyst_cluster', { facing: 'up' }));
				set(R, 2, 3, state('minecraft:candle', { candles: '3', lit: 'true' }));
				set(L, 1, 2, state('minecraft:lectern', { facing: 'east' }));
				set(L, 1, 3, block('minecraft:cartography_table'));
				for (let z = fz + 1; z < D - 2; z++) set(mid, 1, z, block(m.carpet));
				break;
			case 'wholesaler':
				// v0.79: casks stacked high on one side, chests and casks on the other, hay, a ledger on a lectern; more
				// casks and chests under the eaves.
				set(L, 1, 0, state('minecraft:barrel', { facing: 'up' }));
				set(L, 2, 0, state('minecraft:barrel', { facing: 'up' }));
				set(L + 1, 1, 0, state('minecraft:chest', { facing: 'south' }));
				set(R, 1, 0, lying('minecraft:hay_block', 'x'));
				set(R - 1, 1, 0, state('minecraft:barrel', { facing: 'north' }));
				set(mid - 2, wall - 1, 0, lantern(true));
				set(mid + 2, wall - 1, 0, lantern(true));
				for (let z = fz + 1; z < bz - 1; z++) {
					for (const y of [1, 2, 3]) set(L, y, z, state('minecraft:barrel', { facing: (z + y) % 2 ? 'east' : 'up' }));
					set(R, 1, z, state('minecraft:chest', { facing: 'west' }));
					set(R, 2, z, state('minecraft:barrel', { facing: 'west' }));
				}
				set(L + 1, 1, 4, lying('minecraft:hay_block', 'z'));
				set(L + 1, 1, 5, lying('minecraft:hay_block', 'z'));
				set(L + 1, 2, 4, lying('minecraft:hay_block', 'x'));
				set(R - 1, 1, 3, state('minecraft:barrel', { facing: 'up' }));
				set(L + 1, 1, D - 2, state('minecraft:lectern', { facing: 'north' }));
				set(R - 1, 1, D - 2, block('minecraft:cartography_table'));
				for (const x of [0, W - 1]) {
					sideRow(x, 4, 5, (z) => state('minecraft:barrel', { facing: z % 2 ? 'up' : 'north' }));
				}
				break;
			case 'workshop':
				// v0.79: crafting tables, a stonecutter, a loom and a fletching table on one side; a fireplace with the
				// chimney, a smoker, a smithing table and an anvil on the other; an anvil and a grindstone on the porch;
				// firewood under the eaves.
				set(L, 1, 0, state('minecraft:anvil', { facing: 'east' }));
				set(R, 1, 0, state('minecraft:grindstone', { face: 'floor', facing: 'north' }));
				set(R - 1, 1, 0, state('minecraft:barrel', { facing: 'up' }));
				set(mid - 2, wall - 1, 0, lantern(true));
				set(mid + 2, wall - 1, 0, lantern(true));
				set(L, 1, 2, block('minecraft:crafting_table'));
				set(L, 1, 3, block('minecraft:crafting_table'));
				set(L, 1, 5, state('minecraft:stonecutter', { facing: 'east' }));
				set(L, 1, 6, state('minecraft:loom', { facing: 'east' }));
				set(L, 1, D - 2, block('minecraft:fletching_table'));
				chimney(5);
				set(R, 1, 2, state('minecraft:anvil', { facing: 'north' }));
				set(R, 1, 3, state('minecraft:smoker', { facing: 'west', lit: 'false' }));
				set(R, 1, 6, block('minecraft:smithing_table'));
				set(R, 1, D - 2, state('minecraft:barrel', { facing: 'up' }));
				set(L + 1, 1, 5, block('minecraft:crafting_table'));
				set(R - 1, 1, 3, block('minecraft:cauldron'));
				for (const x of [mid - 1, mid + 1]) set(x, 1, D - 2, state('minecraft:chest', { facing: 'north' }));
				sideRow(0, 4, 5, () => lying(m.pillar, 'z'));
				break;
			case 'school': {
				// v0.79: rows of desks (slabs) and chairs facing the bookshelves at the back, a lectern for the teacher.
				if (m.pot) set(porchSides[0], 1, 0, block(m.pot));
				set(porchSides[1], 1, 0, stair(m.bench, 'north'));
				set(mid - 2, wall - 1, 0, lantern(true));
				set(mid + 2, wall - 1, 0, lantern(true));
				for (let x = L; x <= R; x++) {
					if (x === mid) continue;
					for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
				}
				set(mid, 2, D - 2, block('minecraft:bookshelf'));
				for (const z of [2, 4]) for (let x = L; x <= R; x++) {
					if (x === mid) continue;
					set(x, 1, z, stair(m.bench, 'north')); // chair, its back to the door
					set(x, 1, z + 1, state(slab(m.bench), { type: 'top' })); // desk
				}
				set(L, 2, 3, state('minecraft:candle', { candles: '1', lit: 'true' }));
				set(R, 2, 5, state('minecraft:candle', { candles: '1', lit: 'true' }));
				set(L, 1, D - 3, state('minecraft:lectern', { facing: 'north' }));
				break;
			}
		}
		houseOutside();
		if (tall) {
			// v0.87: the way up to the second storey. The quest hall (5x5) is too small for stairs: a ladder against the
			// back wall instead of the right bookshelves.
			if (W === 5) {
				for (const y of [1, 2]) grid.delete(`${R},${y},${D - 2}`);
				for (let y = 1; y <= wall; y++) set(R, y, D - 2, state('minecraft:ladder', { facing: 'north' }));
			} else staircase(R, fz + 1);
			if (kind === 'inn' && m.bed) {
				// beds on the second storey too, along the left wall
				for (const z of [3, D - 3]) {
					set(L, wall + 1, z, state(m.bed, { facing: 'west', part: 'head' }));
					set(L + 1, wall + 1, z, state(m.bed, { facing: 'west', part: 'foot' }));
				}
			}
		}

		/**
		 * v0.87 the outside of single kinds: the inn has a hanging sign on the porch and a door with a railing in its
		 * front gable, the school a bell-cote on the front of its roof, the appraiser display shelves with goods in front
		 * of its windows.
		 */
		function houseOutside() {
			const wood = m.bench === 'minecraft:purpur' ? 'minecraft:warped' : m.bench;
			if (kind === 'inn') {
				set(mid + 1, wall - 1, 0, state(wood + '_hanging_sign', { rotation: '0', attached: 'false' }));
				if (!m.flat && W >= 9) {
					grid.delete(`${mid},${top + 2},${fz}`);
					set(mid, top + 1, fz, state(m.door, { facing: 'south', half: 'lower', hinge: 'left' }));
					set(mid, top + 2, fz, state(m.door, { facing: 'south', half: 'upper', hinge: 'left' }));
					for (const x of [mid - 1, mid + 1]) set(x, top + 1, 0, block(m.fence));
				}
			}
			if (kind === 'school') {
				// on the ridge (flat roofs: on the parapet over the porch)
				const z = m.flat ? 0 : fz;
				const ry = m.flat ? top + 2 : top + 1 - ins + mid;
				set(mid, ry, z, up(m.flat ? m.pillar : m.beam));
				for (const x of [mid - 1, mid + 1]) set(x, ry + 1, z, block(m.fence));
				if (!m.flat) for (const x of [mid - 1, mid + 1]) set(x, ry, z, block(m.fence));
				set(mid, ry + 1, z, state('minecraft:bell', { attachment: 'floor', facing: 'east' }));
				const cap = m.flat ? m.roofBlock : m.roofEdge || m.roof;
				for (let x = mid - 1; x <= mid + 1; x++) set(x, ry + 2, z, state(slab(cap), { type: 'bottom' }));
			}
			if (kind === 'appraiser') {
				const goods = [state('minecraft:amethyst_cluster', { facing: 'up' }), state('minecraft:candle', { candles: '3', lit: 'true' })];
				porchSides.forEach((x, i) => {
					set(x, 1, 0, state(slab(m.bench), { type: 'top' }));
					set(x, 2, 0, goods[i]);
				});
			}
		}
	}

	/**
	 * Gable roof (ridge from front to back): with the walls set in (ins = 1) the lowest row of stairs hangs over them as
	 * eaves. The eave row and the ridge are of a darker material (roofEdge). The gables at the front and back walls have
	 * windows, and from 9 wide a collar beam. Flat styles: a flat roof with a parapet over the walls instead, and roof
	 * beams (viga) sticking out under the eaves.
	 */
	function roof(fz, bz, ins) {
		if (m.flat) {
			const x0 = ins, x1 = W - 1 - ins;
			for (let x = x0; x <= x1; x++) for (let z = 0; z < D; z++) {
				set(x, top + 1, z, block(m.roofBlock));
				const edge = x === x0 || x === x1 || z === 0 || z === D - 1;
				if (edge) set(x, top + 2, z, (x === x0 || x === x1) && (z === 0 || z === D - 1) ? up(m.pillar) : block(m.parapet));
			}
			if (ins && m.viga) for (let z = 1; z < D - 1; z += 2) for (const x of [0, W - 1]) set(x, top, z, lying(m.viga, 'x'));
			return;
		}
		const y0 = top + 1 - ins;
		const edge = m.roofEdge || m.roof;
		for (let k = 0; k < mid; k++) {
			const y = y0 + k;
			const stem = k === 0 ? edge : m.roof;
			for (let z = 0; z < D; z++) {
				set(k, y, z, stair(stem, 'east'));
				set(W - 1 - k, y, z, stair(stem, 'west'));
			}
			// Gables at the front and back wall; the porch side stays open.
			if (y > top) for (let x = k + 1; x < W - 1 - k; x++) for (const z of [fz, bz]) set(x, y, z, block(m.wall));
		}
		for (let z = 0; z < D; z++) set(mid, y0 + mid, z, state(slab(edge), { type: 'bottom' }));
		// Upside-down stairs under the roof where nothing else is: the roof seen from the porch and the room is a smooth
		// slope instead of steps.
		for (let k = 1; k < mid; k++) for (let z = 0; z < D; z++) {
			if (get(k, y0 + k - 1, z) === undefined) set(k, y0 + k - 1, z, stair(m.roof, 'west', 'top'));
			if (get(W - 1 - k, y0 + k - 1, z) === undefined) set(W - 1 - k, y0 + k - 1, z, stair(m.roof, 'east', 'top'));
		}
		for (const z of [fz, bz]) {
			if (W >= 9) {
				// Two small windows over the wall beam and a collar beam above them.
				for (const x of [mid - 1, mid + 1]) set(x, top + 1, z, block(m.window));
				for (let x = mid - 1; x <= mid + 1; x++) set(x, top + 2, z, lying(m.beam, 'x'));
			} else {
				set(mid, top + 1, z, block(m.window));
			}
		}
	}

	/**
	 * Fireplace in the right side wall at z (houses with the walls set in): a furnace in the wall facing the room, the
	 * wall above it of the chimney's material, and the chimney stack outside under the eaves up past the roof with a
	 * fire (or rod) on top.
	 */
	function chimney(z) {
		const xw = W - 2, xo = W - 1;
		set(xw, 1, z, state('minecraft:furnace', { facing: 'west' }));
		for (let y = 2; y < wall; y++) set(xw, y, z, block(m.chimney));
		const stack = top + 4;
		for (let y = 1; y < stack; y++) set(xo, y, z, block(m.chimney));
		set(xo, 0, z, block(m.found));
		set(xo, stack, z, m.chimneyTop.endsWith('campfire') ? state(m.chimneyTop, { lit: 'true', signal_fire: 'false' })
				: state(m.chimneyTop, { facing: 'up' }));
	}

	/**
	 * v0.87 stone building (bank, mint, guild hall, town hall): as in the timber houses the side walls stand one block in
	 * (x0 = 1, x1 = W - 2), so that the eaves hang over them; in that strip stand columns at the corners (and in the
	 * middle from 9 deep) and stone sills under the side windows. A porch of columns (cfg.porch) at z = 0 carries the
	 * front of the roof, the front wall with the door and a keystone (cfg.accent) over it stands at z = 1, and a step
	 * leads up to the door. Corner columns, a base course, windows (cfg.win), the cornice on top (and between the
	 * storeys), a ceiling. @return the room (L..R, fz < z < bz)
	 */
	function stoneBody(cfg) {
		const fz = 1, bz = D - 1, x0 = 1, x1 = W - 2;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const inside = x > x0 && x < x1 && z > fz && z < bz;
			set(x, 0, z, block(inside ? cfg.floor(x, z) : m.stoneBase));
		}
		const stripPosts = D >= 9 ? [fz, Math.floor((fz + bz) / 2), bz] : [fz, bz];
		for (const x of [0, W - 1]) {
			for (const z of stripPosts) for (let y = 1; y < top; y++) set(x, y, z, up(m.column));
			for (const z of cfg.sideWindows) {
				set(x, 1, z, state(slab(m.stoneStem), { type: 'top' }));
				if (storey) set(x, wall + 1, z, state(slab(m.stoneStem), { type: 'top' }));
			}
		}
		for (const x of cfg.porch) for (let y = 1; y < top; y++) set(x, y, 0, up(m.column));
		const cell = (x, y, z, corner, win) => {
			if (y === wall || y === top) return set(x, y, z, block(m.cornice));
			if (corner) return set(x, y, z, up(m.column));
			if (win && winRow(y, [2, 3])) return set(x, y, z, block(cfg.win));
			set(x, y, z, block(y === 1 ? m.stoneBase : m.stone));
		};
		for (let y = 1; y <= top; y++) {
			for (let x = x0; x <= x1; x++) {
				const corner = x === x0 || x === x1;
				cell(x, y, fz, corner, cfg.frontWindows.includes(x));
				cell(x, y, bz, corner, cfg.backWindows.includes(x));
			}
			for (let z = fz + 1; z < bz; z++) {
				cell(x0, y, z, false, cfg.sideWindows.includes(z));
				cell(x1, y, z, false, cfg.sideWindows.includes(z));
			}
		}
		door(mid, fz);
		set(mid, wall - 1, fz, block(cfg.accent));
		set(mid, 1, 0, stair(m.bankRoof, 'south')); // a step up to the door
		for (const cy of storey ? [wall, top] : [wall]) for (let x = x0 + 1; x < x1; x++) for (let z = fz + 1; z < bz; z++) set(x, cy, z, block(m.ceiling));
		if (storey) upperStorey(fz, bz);
		// Lanterns under the front of the roof, beside the outer columns.
		set(1, top - 1, 0, lantern(true));
		set(W - 2, top - 1, 0, lantern(true));
		return { fz, bz, L: x0 + 1, R: x1 - 1 };
	}

	/**
	 * v0.87 roof of the bank: a gable with its ridge from front to back and eaves over the side strips, a pediment of
	 * stone with the accent in it over the porch (and a plain gable at the back), an entablature of the cornice on the
	 * porch columns.
	 */
	function templeRoof(accent) {
		for (let k = 0; k < mid; k++) {
			const y = top + k;
			for (let z = 0; z < D; z++) {
				set(k, y, z, stair(m.bankRoof, 'east'));
				set(W - 1 - k, y, z, stair(m.bankRoof, 'west'));
			}
			if (k > 0) for (let x = k + 1; x < W - 1 - k; x++) for (const z of [0, D - 1]) set(x, y, z, block(m.stone));
		}
		for (let z = 0; z < D; z++) set(mid, top + mid, z, state(slab(m.bankRoof), { type: 'bottom' }));
		for (let x = 1; x < W - 1; x++) set(x, top, 0, block(m.cornice));
		set(mid, top + 1, 0, block(accent));
	}

	/** Bank: a stone building (stoneBody) with a portico of four columns under a pediment, barred windows, a teller counter. */
	function bank() {
		const r = stoneBody({
			porch: [0, 2, W - 3, W - 1], win: 'minecraft:iron_bars', accent: 'minecraft:gold_block',
			frontWindows: [2, W - 3], backWindows: [2, W - 3], sideWindows: [3, 4],
			floor: (x, z) => (x + z) % 2 ? m.bankChecker : m.bankFloor,
		});
		templeRoof('minecraft:gold_block');
		set(mid, wall - 1, Math.floor((r.fz + 1 + D - 3) / 2), lantern(true));
		// Rug from the door, the counter with teller bars, the vault (a chest and stacked gold) behind it.
		for (let z = r.fz + 1; z < D - 3; z++) set(mid, 1, z, block(m.carpet));
		for (const x of [r.L, r.R]) {
			set(x, 1, D - 3, block(m.counter));
			set(x, 2, D - 3, block('minecraft:iron_bars'));
		}
		set(r.L, 1, D - 2, state('minecraft:chest', { facing: 'east' }));
		set(r.R, 1, D - 2, block('minecraft:gold_block'));
		set(r.R, 2, D - 2, block('minecraft:gold_block'));
		sign();
		if (storey) staircase(r.R, r.fz + 1);
	}

	// ---- v0.51 buildings ----

	/** v0.86 the second storey of a tall stone building: a rug and a lantern under its ceiling. */
	function upperStorey(fz, bz) {
		const cz = Math.floor((fz + bz) / 2);
		for (let z = fz + 1; z < bz; z++) set(mid, wall + 1, z, block(m.carpet));
		set(mid, top - 1, cz, lantern(true));
	}

	/**
	 * Rings of stairs facing inward from the rectangle x0..x1 / z0..z1 at height y up; the last line or cell gets top.
	 * rings (v0.87): after that many rings the rest of the rectangle is a flat top of the block top.
	 */
	function hipRoof(x0, z0, x1, z1, y, stem, top, rings = Infinity) {
		for (let k = 0; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
			const a0 = x0 + k, a1 = x1 - k, b0 = z0 + k, b1 = z1 - k;
			if (k === rings) {
				for (let x = a0; x <= a1; x++) for (let z = b0; z <= b1; z++) set(x, y + k, z, block(top));
				return;
			}
			const line = a0 === a1 || b0 === b1;
			for (let x = a0; x <= a1; x++) for (let z = b0; z <= b1; z++) {
				if (x !== a0 && x !== a1 && z !== b0 && z !== b1) continue;
				if (line) { set(x, y + k, z, block(top)); continue; }
				const facing = z === b0 ? 'south' : z === b1 ? 'north' : x === a0 ? 'east' : 'west';
				set(x, y + k, z, stair(stem, facing));
			}
		}
	}

	/**
	 * v0.87 stairs up to the second storey (tall houses and stone buildings): steps in column x from row z0 toward the
	 * back, the last one in the floor of the second storey (y = wall), on a wedge of the floor's block. Whatever stood in
	 * the way goes (beds as a whole); the floor over the lower steps stays open for the head.
	 */
	function staircase(x, z0) {
		const clear = (cx, cy, cz) => {
			const id = ids[get(cx, cy, cz)];
			if (id && id.endsWith('_bed')) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				if (ids[get(cx + dx, cy, cz + dz)] === id) grid.delete(`${cx + dx},${cy},${cz + dz}`);
			}
			grid.delete(`${cx},${cy},${cz}`);
		};
		for (let i = 0; i < wall; i++) {
			const y = 1 + i, z = z0 + i;
			for (let yy = 1; yy <= y; yy++) clear(x, yy, z);
			for (let yy = 1; yy < y; yy++) set(x, yy, z, block(m.floor));
			set(x, y, z, stair(m.bench, 'south'));
			for (const hy of [y + 1, y + 2]) if (hy < top) clear(x, hy, z);
		}
	}

	/**
	 * Clock tower (7x7): a stone tower on a small paved square with lamps, a clock face with gold hub and hands on each
	 * side, an open belfry with a bell, and a hipped roof with a lightning rod on the spire.
	 */
	function clockTower() {
		const t0 = 1, t1 = W - 2, c = mid;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const ring = x === 0 || z === 0 || x === W - 1 || z === D - 1;
			set(x, 0, z, block(ring ? m.pave : m.floor));
		}
		for (const x of [0, W - 1]) for (const z of [0, D - 1]) {
			set(x, 1, z, block(m.postBase));
			set(x, 2, z, lantern(false));
		}
		const onWall = (x, z) => x === t0 || x === t1 || z === t0 || z === t1;
		const corner = (x, z) => (x === t0 || x === t1) && (z === t0 || z === t1);
		for (let y = 1; y <= 16; y++) for (let x = t0; x <= t1; x++) for (let z = t0; z <= t1; z++) {
			if (!onWall(x, z)) continue;
			const sideMid = x === c || z === c;
			if (y === 6 || y === 12 || y === 16) { set(x, y, z, block(m.cornice)); continue; }
			if (corner(x, z)) { set(x, y, z, up(m.column)); continue; }
			if (y >= 13) continue; // open belfry
			if (y === 1) { set(x, y, z, block(m.stoneBase)); continue; }
			if (sideMid && (y === 4 || y === 5 || y === 8)) { set(x, y, z, block(m.window)); continue; }
			set(x, y, z, block(m.stone));
		}
		door(c, t0);
		// Clock faces (3x3) at y 9..11 with a gold hub and two hands (buttons) on the outside.
		const faces = [
			{ cell: (a, y) => [c + a, y, t0], hand: (a, y) => [c + a, y, t0 - 1], facing: 'north' },
			{ cell: (a, y) => [c + a, y, t1], hand: (a, y) => [c + a, y, t1 + 1], facing: 'south' },
			{ cell: (a, y) => [t0, y, c + a], hand: (a, y) => [t0 - 1, y, c + a], facing: 'west' },
			{ cell: (a, y) => [t1, y, c + a], hand: (a, y) => [t1 + 1, y, c + a], facing: 'east' },
		];
		for (const f of faces) {
			for (let a = -1; a <= 1; a++) for (let y = 9; y <= 11; y++) {
				const [x, yy, z] = f.cell(a, y);
				set(x, yy, z, block(a === 0 && y === 10 ? 'minecraft:gold_block' : 'minecraft:smooth_quartz'));
			}
			for (const [a, y] of [[0, 11], [1, 10]]) {
				const [x, yy, z] = f.hand(a, y);
				set(x, yy, z, state('minecraft:polished_blackstone_button', { face: 'wall', facing: f.facing }));
			}
		}
		// Floors inside (for the lanterns), the belfry with arches and the bell.
		for (const y of [6, 12]) for (let x = t0 + 1; x < t1; x++) for (let z = t0 + 1; z < t1; z++) set(x, y, z, block(m.floor));
		set(c, 5, c, lantern(true));
		set(c, 11, c, lantern(true));
		for (let x = t0 + 1; x < t1; x++) for (let z = t0 + 1; z < t1; z++) set(x, 16, z, block(m.cornice));
		for (const [x, z, a, b] of [[t0 + 1, t0, 'east', 'west'], [t0 + 1, t1, 'east', 'west']]) {
			set(x, 15, z, stair(m.bowl, a, 'top'));
			set(t1 - 1, 15, z, stair(m.bowl, b, 'top'));
		}
		for (const x of [t0, t1]) {
			set(x, 15, t0 + 1, stair(m.bowl, 'south', 'top'));
			set(x, 15, t1 - 1, stair(m.bowl, 'north', 'top'));
		}
		set(c, 15, c, state('minecraft:bell', { attachment: 'ceiling', facing: 'north' }));
		if (!tall) {
			hipRoof(0, 0, W - 1, D - 1, 17, m.bankRoof, m.cornice);
			set(c, 21, c, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
		} else {
			// v0.87 level 3: the eaves stay, behind them a gallery with a balustrade and pinnacles at the corners, an open
			// lantern stage in the middle and a higher spire; gold corners on the clock faces.
			for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
				if (x !== 0 && x !== W - 1 && z !== 0 && z !== D - 1) continue;
				const facing = z === 0 ? 'south' : z === D - 1 ? 'north' : x === 0 ? 'east' : 'west';
				set(x, 17, z, stair(m.bankRoof, facing));
			}
			for (let x = t0; x <= t1; x++) for (let z = t0; z <= t1; z++) {
				if (!onWall(x, z)) continue;
				if (corner(x, z)) {
					set(x, 17, z, up(m.column));
					set(x, 18, z, block(m.cornice));
					set(x, 19, z, lantern(false));
				} else set(x, 17, z, block(m.post));
			}
			const s0 = c - 1, s1 = c + 1;
			for (const x of [s0, s1]) for (const z of [s0, s1]) for (let y = 17; y <= 19; y++) set(x, y, z, up(m.column));
			set(c, 20, c, block(m.cornice));
			hipRoof(s0, s0, s1, s1, 20, m.bankRoof, m.cornice);
			set(c, 19, c, lantern(true));
			set(c, 22, c, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
			for (const f of faces) for (const a of [-1, 1]) for (const y of [9, 11]) {
				const [x, yy, z] = f.cell(a, y);
				set(x, yy, z, block('minecraft:gold_block'));
			}
		}
		coreSign(c, t1 - 1, 'north');
	}

	/**
	 * Watchtower (5x5): a stone base and a timber shaft with arrow slits and a ladder inside, a lookout platform with a
	 * railing, a roof on four posts with a lantern, a signal fire on top and a banner over the door.
	 */
	function watchtower() {
		const c = mid;
		// v0.87 level 3 (tall): the shaft is 4 higher (a third row of slits) and banners hang under the platform.
		const rise = tall ? 4 : 0;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) set(x, 0, z, block(m.found));
		for (let y = 1; y <= 12 + rise; y++) for (let x = 1; x <= 3; x++) for (let z = 1; z <= 3; z++) {
			if (x === c && z === c) continue;
			const corner = x !== c && z !== c;
			if (y <= 3) set(x, y, z, block(corner ? m.stoneBase : m.found));
			else if (corner) set(x, y, z, up(m.pillar));
			else if ((y === 6 || y === 9 || rise && y === 12) && !(x === c && z === c + 1)) set(x, y, z, block('minecraft:iron_bars')); // not behind the ladder (it needs a solid wall)
			else set(x, y, z, block(m.wall));
		}
		door(c, 1);
		for (let y = 1; y <= 13 + rise; y++) set(c, y, c, state('minecraft:ladder', { facing: 'north' }));
		// A skirt of stairs around the base.
		for (const [x, z, f] of [[0, 1, 'east'], [0, 2, 'east'], [0, 3, 'east'], [4, 1, 'west'], [4, 2, 'west'], [4, 3, 'west'], [1, 4, 'north'], [2, 4, 'north'], [3, 4, 'north'], [3, 0, 'south']]) {
			set(x, 1, z, stair(m.gzRoof, f));
		}
		// Platform, railing, posts, roof, lanterns, fire.
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (!(x === c && z === c)) set(x, 13 + rise, z, block(m.floor));
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const edge = x === 0 || z === 0 || x === W - 1 || z === D - 1;
			const corner = (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
			if (corner) for (let y = 14; y <= 16; y++) set(x, y + rise, z, up(m.pillar));
			else if (edge) set(x, 14 + rise, z, block(m.fence));
		}
		hipRoof(0, 0, W - 1, D - 1, 17 + rise, m.gzRoof, m.gzTop);
		set(c, 18 + rise, c, lantern(true));
		set(c, 20 + rise, c, state(m.chimneyTop.endsWith('campfire') ? m.chimneyTop : 'minecraft:campfire', { lit: 'true', signal_fire: 'false' }));
		set(c, 11 + rise, 0, state('minecraft:red_wall_banner', { facing: 'north' }));
		if (rise) {
			for (const [x, z, facing] of [[0, c, 'west'], [W - 1, c, 'east'], [c, D - 1, 'south']]) {
				const bx = x === 0 ? 1 : x === W - 1 ? W - 2 : x, bz = z === D - 1 ? D - 2 : z;
				if (get(bx, 11 + rise, bz) !== undefined) set(x, 11 + rise, z, state('minecraft:red_wall_banner', { facing }));
			}
		}
		set(1, 3, 0, state('minecraft:wall_torch', { facing: 'north' }));
		set(3, 3, 0, state('minecraft:wall_torch', { facing: 'north' }));
		coreSign(1, 0, 'north');
	}

	/**
	 * Market hall (11x9): a great gabled roof on posts and beams over a paved aisle, with stalls on both sides
	 * (counters with goods, casks and chests behind) and lanterns hanging from the beams.
	 */
	function marketHall() {
		const c = mid;
		const postsX = [0, 3, W - 4, W - 1], postsZ = [0, 4, D - 1];
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const aisle = x >= 3 && x <= W - 4;
			set(x, 0, z, block(!aisle ? m.floor : x === c ? m.paveAxis : (x + z) % 2 ? m.pave : m.paveRing));
		}
		for (const x of postsX) for (const z of postsZ) for (let y = 1; y <= 3; y++) set(x, y, z, up(m.pillar));
		for (const z of postsZ) for (let x = 0; x < W; x++) set(x, 4, z, lying(m.beam, 'x'));
		for (const x of [0, W - 1]) for (let z = 0; z < D; z++) if (!postsZ.includes(z)) set(x, 4, z, lying(m.beam, 'z'));
		for (const z of postsZ) for (const x of [c - 3, c, c + 3]) set(x, 3, z, lantern(true));
		// Gable roof with its ridge from front to back; gable ends filled, with a round window.
		for (let k = 0; k < c; k++) {
			const y = 5 + k;
			for (let z = 0; z < D; z++) {
				set(k, y, z, stair(m.gzRoof, 'east'));
				set(W - 1 - k, y, z, stair(m.gzRoof, 'west'));
			}
			for (let x = k + 1; x < W - 1 - k; x++) for (const z of [0, D - 1]) set(x, y, z, block(m.wall));
		}
		for (let z = 0; z < D; z++) set(c, 5 + c, z, state(slab(m.gzRoof), { type: 'bottom' }));
		for (const z of [0, D - 1]) for (const y of [7, 8]) set(c, y, z, block(m.window));
		if (tall) {
			// v0.88 level 3 (high): an open lantern turret in the middle of the ridge (posts, a hipped roof, a rod).
			const zm = Math.floor(D / 2);
			for (const x of [c - 1, c + 1]) for (const z of [zm - 1, zm + 1]) for (const y of [5 + c, 6 + c]) set(x, y, z, up(m.pillar));
			hipRoof(c - 1, zm - 1, c + 1, zm + 1, 7 + c, m.gzRoof, m.gzTop);
			set(c, 7 + c, zm, block(m.gzTop));
			set(c, 6 + c, zm, lantern(true));
			set(c, 9 + c, zm, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
		}
		// Stalls: counter facing the aisle, goods on it, casks and chests behind; two per side.
		const goods = ['minecraft:melon', 'minecraft:pumpkin', 'minecraft:hay_block', 'minecraft:cake', 'minecraft:decorated_pot', 'minecraft:bee_nest',
			'minecraft:dried_kelp_block', 'minecraft:honey_block'];
		let g = 0;
		for (const side of [{ counter: 2, back: 1, facing: 'west' }, { counter: W - 3, back: W - 2, facing: 'east' }]) {
			for (const [z0, z1] of [[1, 3], [5, D - 2]]) {
				for (let z = z0; z <= z1; z++) {
					set(side.counter, 1, z, stair(m.bench, side.facing, 'top'));
					const item = goods[g++ % goods.length];
					set(side.counter, 2, z, item === 'minecraft:bee_nest' ? state(item, { facing: side.facing === 'west' ? 'east' : 'west', honey_level: '0' }) : block(item));
					set(side.back, 1, z, state('minecraft:barrel', { facing: 'up' }));
					if (z === z0) set(side.back, 2, z, state('minecraft:chest', { facing: side.facing === 'west' ? 'east' : 'west' }));
				}
			}
		}
		coreSign(c - 1, 0, 'north');
		set(c + 1, 1, 0, block(m.pot || 'minecraft:barrel'));
	}

	/**
	 * Harbor (9x13): on land (z 0..5) the harbor office, crates and a crane; over the water a quay across the whole width
	 * and a pier with log posts (they reach down to the bottom when it is built), mooring posts and lamps at its end.
	 */
	function harbor() {
		const land = 6, c = mid;
		for (let x = 0; x < W; x++) for (let z = 0; z < land; z++) set(x, 0, z, block(z === land - 1 ? m.found : m.pave));
		for (let x = 0; x < W; x++) set(x, 0, land, [0, W - 1, c - 1, c + 1].includes(x) ? up(m.pillar) : block(m.deck));
		for (let z = land + 1; z < D; z++) for (let x = c - 1; x <= c + 1; x++) {
			const post = x !== c && (z - land) % 3 === 0;
			set(x, 0, z, post ? up(m.pillar) : block(m.deck));
		}
		for (let z = land + 1; z < D; z++) for (const x of [c - 1, c + 1]) if ((z - land) % 3 === 0) set(x, 1, z, block(m.fence));
		for (const x of [c - 1, c + 1]) {
			set(x, 1, D - 1, block(m.fence));
			set(x, 2, D - 1, block(m.fence));
			set(x, 3, D - 1, lantern(false));
		}
		// Office: 4x4 with a door to the front, windows and a small hipped roof.
		// v0.88 level 3 (high): the office is 3 higher (a second row of windows, all round), with a lamp room on its roof.
		const o0 = 0, o1 = 3, oz0 = 1, oz1 = 4, oh = tall ? 3 : 0;
		for (let y = 1; y <= 3 + oh; y++) for (let x = o0; x <= o1; x++) for (let z = oz0; z <= oz1; z++) {
			if (x !== o0 && x !== o1 && z !== oz0 && z !== oz1) continue;
			const corner = (x === o0 || x === o1) && (z === oz0 || z === oz1);
			if (corner) set(x, y, z, up(m.pillar));
			else if (y === 2 && (x === o0 || z === oz1) || oh && y === 5) set(x, y, z, block(m.window));
			else set(x, y, z, block(y === 1 ? m.found : m.wall));
		}
		set(2, 1, oz0, state(m.door, { facing: 'north', half: 'lower', hinge: 'left' }));
		set(2, 2, oz0, state(m.door, { facing: 'north', half: 'upper', hinge: 'left' }));
		if (oh) {
			for (let x = o0 + 1; x < o1; x++) for (let z = oz0 + 1; z < oz1; z++) set(x, 7, z, block(m.gzTop));
			for (const x of [o0, o1]) for (const z of [oz0, oz1]) set(x, 7, z, up(m.pillar));
			for (let x = o0 + 1; x < o1; x++) for (const z of [oz0, oz1]) set(x, 7, z, block(m.fence));
			for (let z = oz0 + 1; z < oz1; z++) for (const x of [o0, o1]) set(x, 7, z, block(m.fence));
			// lamp room: glass around a glowing block, a small roof
			for (const x of [1, 2]) for (const z of [2, 3]) set(x, 8, z, block('minecraft:glass'));
			set(1, 8, 2, block(m.glow || 'minecraft:glowstone'));
			hipRoof(1, 2, 2, 3, 9, m.gzRoof, m.gzTop);
			set(2, 6, 3, lantern(true));
		} else hipRoof(o0, oz0, o1, oz1, 4, m.gzRoof, m.gzTop);
		set(1, 1, 3, state('minecraft:cartography_table', {}));
		set(1, 1, 2, lantern(false));
		// Crates, a crane with a cask on a chain over the water, lamps at the edge of the quay.
		for (const [x, z, y] of [[5, 1, 1], [6, 1, 1], [5, 1, 2], [7, 2, 1], [6, 3, 1]]) set(x, y, z, state('minecraft:barrel', { facing: 'up' }));
		set(7, 1, 1, state('minecraft:chest', { facing: 'west' }));
		set(5, 1, 3, lying('minecraft:hay_block', 'x'));
		for (let y = 1; y <= 6; y++) set(W - 2, y, land - 2, up(m.pillar));
		for (let z = land - 2; z <= land + 2; z++) set(W - 2, 7, z, lying(m.beam, 'z'));
		for (const y of [6, 5]) set(W - 2, y, land + 2, state('minecraft:iron_chain', { axis: 'y' }));
		set(W - 2, 4, land + 2, state('minecraft:barrel', { facing: 'up' }));
		for (const x of [1, W - 3]) lampPost(x, land - 1, 1);
		coreSign(c, 0, 'north');
	}

	/**
	 * Open-air theater (11x11): tiers of seats facing a raised stage at the back; the stage has a red curtain, side
	 * drapes, banners, a stepped roof, note blocks and lanterns. Walkways at both sides lead past the tiers.
	 */
	function theater() {
		const c = mid;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) set(x, 0, z, block(z === 5 || z === 6 ? m.paveRing : m.pave));
		// Tiers z 1..4 (the back row is the highest).
		for (let z = 1; z <= 4; z++) {
			const tier = 4 - z;
			const x0 = z === 4 ? 2 : 1, x1 = z === 4 ? W - 3 : W - 2;
			for (let x = x0; x <= x1; x++) {
				for (let y = 1; y <= tier; y++) set(x, y, z, block(m.stone));
				set(x, tier + 1, z, stair(m.bench, 'north'));
			}
		}
		for (const x of [1, W - 2]) {
			for (let y = 1; y <= 4; y++) set(x, y, 1, block(m.stone));
			set(x, 5, 1, lantern(false));
		}
		for (const x of [1, W - 2]) lampPost(x, 5, 1);
		// Stage: platform, steps, frame, curtain, drapes, valance, roof.
		// v0.88 level 3 (high): the frame and the curtain 3 higher (a taller stage house), standing banners and lanterns on
		// the front corners of the roof, a second pair of banners on the curtain.
		const s0 = 7, s1 = D - 2, h = tall ? 3 : 0;
		for (let x = 1; x < W - 1; x++) for (let z = s0; z <= s1; z++) set(x, 1, z, block(m.deck));
		set(c, 1, s0 - 1, stair(m.bench, 'south')); // a step up to the stage
		for (const x of [0, W - 1]) for (let z = s0; z < D; z++) for (let y = 1; y <= 6 + h; y++) set(x, y, z, z === s0 || z === D - 1 ? up(m.pillar) : block(m.wall));
		for (let x = 1; x < W - 1; x++) for (let y = 1; y <= 6 + h; y++) set(x, y, D - 1, block('minecraft:red_wool'));
		for (let x = 0; x < W; x++) set(x, 7 + h, s0, lying(m.beam, 'x'));
		for (let x = 0; x < W; x++) set(x, 7 + h, D - 1, lying(m.beam, 'x'));
		for (const x of [0, W - 1]) for (let z = s0 + 1; z < D - 1; z++) set(x, 7 + h, z, lying(m.beam, 'z'));
		for (const x of [1, W - 2]) for (let y = 3; y <= 6 + h; y++) set(x, y, s0, block('minecraft:red_wool'));
		for (let x = 2; x < W - 2; x++) set(x, 6 + h, s0, block(x % 2 ? 'minecraft:yellow_wool' : 'minecraft:red_wool'));
		for (let x = 0; x < W; x++) {
			set(x, 8 + h, s0, stair(m.gzRoof, 'south'));
			set(x, 8 + h, s0 + 1, block(m.gzTop));
			set(x, 9 + h, s0 + 1, stair(m.gzRoof, 'south'));
			set(x, 8 + h, s1, block(m.gzTop));
			set(x, 9 + h, s1, block(m.gzTop));
			set(x, 10 + h, s1, stair(m.gzRoof, 'south'));
			set(x, 8 + h, D - 1, block(m.gzTop));
			set(x, 9 + h, D - 1, block(m.gzTop));
			set(x, 10 + h, D - 1, state(slab(m.gzRoof), { type: 'bottom' }));
		}
		for (const x of [3, W - 4]) set(x, 7 + h, s0 + 1, lantern(true));
		for (const x of [3, W - 4]) set(x, 5, D - 2, state('minecraft:yellow_wall_banner', { facing: 'north' }));
		if (h) {
			for (const x of [3, W - 4]) set(x, 8, D - 2, state('minecraft:red_wall_banner', { facing: 'north' }));
			for (const x of [0, W - 1]) {
				set(x, 9 + h, s0 + 1, block(m.gzTop));
				set(x, 10 + h, s0 + 1, lantern(false));
			}
			for (const x of [2, W - 3]) {
				set(x, 9 + h, s0 + 1, block(m.gzTop));
				set(x, 10 + h, s0 + 1, state('minecraft:yellow_banner', { rotation: '0' }));
			}
		}
		set(2, 2, s1, state('minecraft:note_block', {}));
		set(W - 3, 2, s1, state('minecraft:note_block', {}));
		set(c, 2, s1, state('minecraft:jukebox', {}));
		coreSign(c, 0, 'north');
	}

	/**
	 * Hot spring (9x9): a fence with a gate and stone lanterns at the corners, a bath of water with rough rocks around
	 * it, soul sand under part of it (bubbles rise), lights under the water, a bench and potted plants.
	 */
	function hotSpring() {
		const c = mid;
		const cx = c, cz = 5;
		const inBath = (x, z) => ((x - cx) / 2.6) ** 2 + ((z - cz) / 2.1) ** 2 <= 1;
		const nearBath = (x, z) => !inBath(x, z) && [-1, 0, 1].some((a) => [-1, 0, 1].some((b) => inBath(x + a, z + b)));
		const rock = (x, z) => m.rocks[(x * 5 + z * 3 + (x * z) % 7) % m.rocks.length];
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			if (inBath(x, z)) {
				const d = (x - cx) + (z - cz);
				set(x, 0, z, block(Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 0 ? 'minecraft:soul_sand' : d % 2 === 0 ? m.glow : 'minecraft:smooth_stone'));
				set(x, 1, z, state('minecraft:water', { level: '0' }));
			} else if (nearBath(x, z)) {
				set(x, 0, z, block(rock(x, z)));
				set(x, 1, z, block(rock(z, x)));
			} else {
				const path = x === c && z <= 1;
				set(x, 0, z, block(path ? m.pave : m.ground));
			}
		}
		// A few taller rocks and lanterns on the rim.
		const rim = [];
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (nearBath(x, z)) rim.push([x, z]);
		rim.forEach(([x, z], i) => {
			if (i % 5 === 0) set(x, 2, z, block(rock(x + 1, z)));
			else if (i % 7 === 3) set(x, 2, z, lantern(false));
		});
		// Fence with a gate, stone lanterns at the corners.
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const edge = x === 0 || z === 0 || x === W - 1 || z === D - 1;
			if (!edge || get(x, 1, z) !== undefined) continue;
			if (z === 0 && x === c) continue;
			set(x, 1, z, block(m.springFence));
		}
		for (const x of [0, W - 1]) for (const z of [0, D - 1]) lampPost(x, z, 1);
		set(1, 1, 1, stair(m.bench, 'west'));
		set(1, 1, 2, stair(m.bench, 'west'));
		set(W - 2, 1, 1, block(m.pot || 'minecraft:potted_bamboo'));
		set(W - 2, 1, 2, block('minecraft:potted_bamboo'));
		if (tall) {
			// v0.88 level 3 (high): a roof on four posts over the bath (a hipped roof of the gazebo material), with lanterns.
			const r0 = 1, r1 = W - 2, rz0 = 3, rz1 = D - 2; // behind the bench and the pots by the gate
			for (const x of [r0, r1]) for (const z of [rz0, rz1]) {
				for (let y = 1; y <= 4; y++) set(x, y, z, up(m.pillar));
			}
			for (let x = r0; x <= r1; x++) for (const z of [rz0, rz1]) set(x, 5, z, lying(m.beam, 'x'));
			for (let z = rz0 + 1; z < rz1; z++) for (const x of [r0, r1]) set(x, 5, z, lying(m.beam, 'z'));
			hipRoof(r0 - 1, rz0 - 1, r1 + 1, rz1 + 1, 6, m.gzRoof, m.gzTop);
			for (const x of [r0 + 2, r1 - 2]) set(x, 4, rz0, lantern(true));
			set(c, 4, rz1, lantern(true));
		}
		coreSign(c - 1, 0, 'north');
	}

	/**
	 * Festival plaza (11x11): a two-storey yagura (posts, red and white skirt, railing, a small roof, a bell and a note
	 * block) in a dance ring, ropes of lanterns between poles around the square, four stalls with striped awnings and
	 * goods, and coloured banners.
	 */
	function festival() {
		const c = mid;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const r = Math.max(Math.abs(x - c), Math.abs(z - c));
			set(x, 0, z, block(r === 3 ? m.paveAxis : r === 2 ? m.paveRing : m.pave));
		}
		// Yagura. v0.88 level 3 (high): a second stage 4 higher (skirt, deck, railing), the roof, bell and lantern on top of it.
		const rise = tall ? 4 : 0;
		for (const x of [c - 1, c + 1]) for (const z of [c - 1, c + 1]) for (let y = 1; y <= 6 + rise; y++) set(x, y, z, up(m.pillar));
		for (const [x, z] of [[c, c - 1], [c - 1, c], [c + 1, c], [c, c + 1]]) {
			for (const r of rise ? [0, rise] : [0]) {
				set(x, 3 + r, z, block('minecraft:red_wool'));
				set(x, 2 + r, z, block('minecraft:white_wool'));
				set(x, 5 + r, z, block(m.fence));
			}
		}
		for (const r of rise ? [0, rise] : [0]) {
			for (let x = c - 1; x <= c + 1; x++) for (let z = c - 1; z <= c + 1; z++) if (x === c || z === c) set(x, 4 + r, z, block(m.deck));
		}
		if (rise) {
			// the lower stage keeps a lantern and banners in the festival colours under the upper skirt
			grid.delete(`${c},5,${c}`);
			set(c, 5, c, lantern(false));
			for (const [x, z, facing] of [[c, c - 2, 'north'], [c - 2, c, 'west'], [c + 2, c, 'east'], [c, c + 2, 'south']]) {
				const bx = x === c ? x : x < c ? x + 1 : x - 1, bz = z === c ? z : z < c ? z + 1 : z - 1;
				if (get(x, 7, z) === undefined && get(bx, 7, bz) !== undefined) set(x, 7, z, state('minecraft:red_wall_banner', { facing }));
			}
		}
		set(c, 5 + rise, c, state('minecraft:note_block', {}));
		hipRoof(c - 1, c - 1, c + 1, c + 1, 7 + rise, m.gzRoof, m.gzTop);
		set(c, 6 + rise, c, state('minecraft:bell', { attachment: 'ceiling', facing: 'north' }));
		set(c, 7 + rise, c, block(m.gzTop));
		set(c, 8 + rise, c, lantern(false));
		for (const x of [c - 2, c + 2]) for (const z of [c - 2, c + 2]) set(x, 1, z, state('minecraft:red_candle_cake', { lit: 'true' }));
		// Poles and ropes of lanterns around the square.
		for (const x of [0, W - 1]) for (const z of [0, D - 1]) for (let y = 1; y <= 5; y++) set(x, y, z, up(m.pillar));
		for (let i = 1; i < W - 1; i++) {
			for (const [x, z] of [[i, 0], [i, D - 1], [0, i], [W - 1, i]]) {
				set(x, 5, z, block(m.fence));
				if (i % 2 === 0) set(x, 4, z, lantern(true));
			}
		}
		// Stalls in the corners: counter facing the square, goods, a cask, striped awning.
		const stalls = [
			{ x0: 1, z0: 1, dx: 1, dz: 1, colors: ['red', 'white'], goods: ['minecraft:cake', 'minecraft:melon', 'minecraft:pumpkin'] },
			{ x0: W - 2, z0: 1, dx: -1, dz: 1, colors: ['blue', 'white'], goods: ['minecraft:honey_block', 'minecraft:decorated_pot', 'minecraft:cake'] },
			{ x0: 1, z0: D - 2, dx: 1, dz: -1, colors: ['yellow', 'white'], goods: ['minecraft:hay_block', 'minecraft:cake', 'minecraft:melon'] },
			{ x0: W - 2, z0: D - 2, dx: -1, dz: -1, colors: ['lime', 'white'], goods: ['minecraft:pumpkin', 'minecraft:decorated_pot', 'minecraft:honey_block'] },
		];
		for (const s of stalls) {
			const facing = s.dz > 0 ? 'north' : 'south'; // counter: the low part toward the vendor
			for (let i = 0; i < 3; i++) {
				const x = s.x0 + i * s.dx;
				set(x, 1, s.z0, i === 1 ? state('minecraft:barrel', { facing: 'up' }) : block(m.fence));
				set(x, 2, s.z0, i === 1 ? block(m.fence) : block(m.fence));
				set(x, 1, s.z0 + s.dz, stair(m.bench, facing, 'top'));
				set(x, 2, s.z0 + s.dz, block(s.goods[i]));
				for (const z of [s.z0, s.z0 + s.dz]) set(x, 3, z, block(`minecraft:${s.colors[i % 2]}_wool`));
			}
		}
		const banners = ['minecraft:red_banner', 'minecraft:yellow_banner', 'minecraft:blue_banner', 'minecraft:lime_banner'];
		[[c - 3, c], [c + 3, c], [c, c + 3]].forEach(([x, z], i) => set(x, 1, z, state(banners[i], { rotation: String((i * 4) % 16) })));
		coreSign(c, 0, 'north');
	}

	/**
	 * Mint (7x7): a stone building (stoneBody) with a porch of two columns (an anvil, a quenching cauldron and stacked
	 * copper under it), barred windows, blast furnaces at the back with a chimney through the hipped roof, which ends in a
	 * flat top (v0.87).
	 */
	function mint() {
		const r = stoneBody({
			porch: [0, W - 1], win: 'minecraft:iron_bars', accent: 'minecraft:waxed_chiseled_copper',
			frontWindows: [2, W - 3], backWindows: [], sideWindows: [3],
			floor: () => m.bankFloor,
		});
		set(1, 1, 0, state('minecraft:anvil', { facing: 'east' }));
		set(W - 2, 1, 0, m.fluid === 'minecraft:lava' ? block('minecraft:lava_cauldron') : state('minecraft:water_cauldron', { level: '3' }));
		set(W - 3, 1, 0, block('minecraft:waxed_copper_block'));
		set(mid, wall - 1, 3, lantern(true));
		hipRoof(0, 0, W - 1, D - 1, top, m.bankRoof, m.cornice, 2);
		// Back: two blast furnaces with the sign between them, a smithing table and a chest before them; a barrel with
		// copper, a cauldron and a grindstone at the front.
		set(r.L, 1, D - 2, state('minecraft:blast_furnace', { facing: 'north', lit: 'true' }));
		set(r.R, 1, D - 2, state('minecraft:blast_furnace', { facing: 'north', lit: 'true' }));
		set(r.L, 1, D - 3, block('minecraft:smithing_table'));
		set(r.R, 1, D - 3, state('minecraft:chest', { facing: 'west' }));
		set(r.L, 1, 2, state('minecraft:barrel', { facing: 'up' }));
		set(r.L, 2, 2, block('minecraft:waxed_copper_block'));
		set(r.R, 1, 2, state('minecraft:grindstone', { face: 'floor', facing: 'north' }));
		set(r.L, 1, 3, block('minecraft:cauldron'));
		if (storey) staircase(r.R, r.fz + 1);
		// Chimney from the left furnace up through the ceiling and the roof, a fire on top.
		let roofY = top;
		for (let y = top; y < 64; y++) if (get(r.L, y, D - 2) !== undefined) roofY = y;
		for (let y = 2; y <= roofY + 1; y++) set(r.L, y, D - 2, block(m.chimney));
		set(r.L, roofY + 2, D - 2, m.chimneyTop.endsWith('campfire') ? state(m.chimneyTop, { lit: 'true', signal_fire: 'false' })
				: state(m.chimneyTop, { facing: 'up' }));
		sign();
	}

	// ---- v0.79 buildings ----

	/**
	 * Stone hall (guild hall 7x9, town hall 9x9): a stone building (stoneBody) with glass windows, a porch of columns and
	 * a hipped roof; the town hall's ends in a flat top with a cupola and a bell (v0.87). The interior is the kind's.
	 */
	function stoneHall() {
		const guild = kind === 'guild_hall';
		const r = stoneBody({
			porch: W >= 9 ? [0, 2, W - 3, W - 1] : [0, W - 1], win: m.window,
			accent: guild ? 'minecraft:emerald_block' : 'minecraft:gold_block',
			frontWindows: [2, W - 3], backWindows: [2, W - 3], sideWindows: [3, D - 3],
			floor: (x, z) => (x + z) % 2 ? m.bankChecker : m.bankFloor,
		});
		if (guild) hipRoof(0, 0, W - 1, D - 1, top, m.bankRoof, m.cornice);
		else {
			hipRoof(0, 0, W - 1, D - 1, top, m.bankRoof, m.cornice, 3);
			// Cupola on the flat top: posts at the corners, the bell, a roof of slabs and a lantern on it.
			const fy = top + 3, c = mid;
			for (const x of [c - 1, c + 1]) for (const z of [c - 1, c + 1]) set(x, fy + 1, z, block(m.post));
			set(c, fy + 1, c, state('minecraft:bell', { attachment: 'floor', facing: 'north' }));
			for (let x = c - 1; x <= c + 1; x++) for (let z = c - 1; z <= c + 1; z++) set(x, fy + 2, z, state(slab(m.bankRoof), { type: 'bottom' }));
			set(c, fy + 2, c, block(m.cornice));
			set(c, fy + 3, c, lantern(false));
		}
		sign();
		if (guild) {
			// A long table with chairs and candles, green banners, bookshelves, a chest and a cask at the back.
			for (let z = 3; z <= 5; z++) {
				set(mid, 1, z, block(m.counter));
				set(r.L, 1, z, stair(m.bench, 'west'));
				set(r.R, 1, z, stair(m.bench, 'east'));
			}
			set(mid, 2, 3, state('minecraft:candle', { candles: '2', lit: 'true' }));
			set(mid, 2, 5, state('minecraft:candle', { candles: '3', lit: 'true' }));
			set(mid, 2, 4, block('minecraft:emerald_block'));
			set(mid, wall - 1, 4, lantern(true));
			set(r.L, wall - 1, 4, state('minecraft:green_wall_banner', { facing: 'east' }));
			set(r.R, wall - 1, 4, state('minecraft:green_wall_banner', { facing: 'west' }));
			for (const x of [r.L, r.R]) for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
			set(r.L, 1, D - 3, state('minecraft:chest', { facing: 'east' }));
			set(r.R, 1, D - 3, state('minecraft:barrel', { facing: 'up' }));
		} else {
			// Town hall: banners on the porch and the side walls, a reception counter, bookshelves and a lectern.
			// (banners hang on the stone between the windows and the door, never on glass)
			set(mid - 1, wall - 1, 0, state('minecraft:white_wall_banner', { facing: 'north' }));
			set(mid + 1, wall - 1, 0, state('minecraft:white_wall_banner', { facing: 'north' }));
			for (let x = r.L; x <= r.R; x++) if (x !== mid) set(x, 1, D - 4, block(m.counter));
			set(r.L, 2, D - 4, state('minecraft:candle', { candles: '2', lit: 'true' }));
			if (m.pot) set(r.R, 2, D - 4, block(m.pot));
			for (const x of [r.L, r.R]) for (const y of [1, 2]) set(x, y, D - 2, block('minecraft:bookshelf'));
			set(mid - 1, 1, D - 2, state('minecraft:lectern', { facing: 'north' }));
			set(mid + 1, 1, D - 2, state('minecraft:chest', { facing: 'north' }));
			set(r.L, wall - 1, 4, state('minecraft:blue_wall_banner', { facing: 'east' }));
			set(r.R, wall - 1, 4, state('minecraft:blue_wall_banner', { facing: 'west' }));
			for (let z = r.fz + 1; z < D - 4; z++) set(mid, 1, z, block(m.carpet));
			set(mid, wall - 1, 3, lantern(true));
		}
		if (storey) staircase(r.R, r.fz + 1);
	}

	/**
	 * Chapel (7x9): stone walls with tall stained glass windows, a steep gable roof with its ridge from front to back, a
	 * bell-cote on the front gable, pews along the aisle and an altar with candles at the back.
	 */
	function chapel() {
		const fz = 1;
		const bz = D - 1;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			const inside = x > 0 && x < W - 1 && z > fz && z < bz;
			set(x, 0, z, block(inside ? m.bankFloor : m.stoneBase));
		}
		const sideWindows = [3, 5];
		for (let y = 1; y <= wall; y++) {
			for (let x = 0; x < W; x++) {
				const corner = x === 0 || x === W - 1;
				for (const z of [fz, bz]) {
					if (corner) { set(x, y, z, up(m.column)); continue; }
					const win = z === bz && x === mid && y >= 2 && y <= 4;
					set(x, y, z, block(win ? 'minecraft:yellow_stained_glass_pane' : y === 1 ? m.stoneBase : m.stone));
				}
			}
			for (let z = fz + 1; z < bz; z++) for (const x of [0, W - 1]) {
				const win = sideWindows.includes(z) && y >= 2 && y <= 4;
				set(x, y, z, block(win ? 'minecraft:white_stained_glass_pane' : y === 1 ? m.stoneBase : m.stone));
			}
		}
		door(mid, fz);
		set(mid, 1, 0, stair(m.bankRoof, 'south')); // a step up to the door
		// Gable roof (ridge from front to back), gables of stone with a small window at the front.
		for (let k = 0; k < mid; k++) {
			const y = wall + 1 + k;
			for (let z = 0; z < D; z++) {
				set(k, y, z, stair(m.bankRoof, 'east'));
				set(W - 1 - k, y, z, stair(m.bankRoof, 'west'));
			}
			for (let x = k + 1; x < W - 1 - k; x++) for (const z of [fz, bz]) set(x, y, z, block(m.stone));
		}
		const top = wall + 1 + mid;
		for (let z = 0; z < D; z++) set(mid, top, z, state(slab(m.bankRoof), { type: 'bottom' }));
		set(mid, wall + 2, fz, block('minecraft:white_stained_glass_pane'));
		// Bell-cote on the front gable: a block, two posts, the bell and a little roof.
		set(mid, top, fz, block(m.cornice));
		for (const x of [mid - 1, mid + 1]) for (const y of [top, top + 1]) set(x, y, fz, block(m.post));
		set(mid, top + 1, fz, state('minecraft:bell', { attachment: 'floor', facing: 'east' }));
		for (let x = mid - 1; x <= mid + 1; x++) set(x, top + 2, fz, state(slab(m.bankRoof), { type: 'bottom' }));
		// Pews along the aisle, a carpet to the altar, lanterns under the ridge.
		for (const z of [3, 5]) for (const x of [1, 2, W - 3, W - 2]) set(x, 1, z, stair(m.bench, 'north'));
		for (let z = fz + 1; z < D - 2; z++) set(mid, 1, z, block(m.carpet));
		for (const z of [3, 6]) set(mid, top - 1, z, lantern(true));
		if (tall) {
			// v0.87 level 3: a bell tower (3x3) rises from the front of the roof in place of the bell-cote: stone walls with
			// a window to the front, a cornice, an open belfry on corner columns with the bell, and a spire with a
			// lightning rod.
			const t0 = mid - 1, t1 = mid + 1, z0 = fz, z1 = fz + 2, by = wall + 2;
			const ring = (x, z) => x === t0 || x === t1 || z === z0 || z === z1;
			for (let y = by; y <= top + 1; y++) for (let x = t0; x <= t1; x++) for (let z = z0; z <= z1; z++) {
				if (ring(x, z)) set(x, y, z, y === by ? block(m.stoneBase) : block(m.stone));
				else if (y === by) set(x, y, z, block(m.stone));
				else grid.delete(`${x},${y},${z}`);
			}
			set(mid, wall - 1 + 2, z0 + 1, lantern(true));
			set(mid, top, z0, block('minecraft:white_stained_glass_pane'));
			for (let x = t0; x <= t1; x++) for (let z = z0; z <= z1; z++) set(x, top + 2, z, block(m.cornice));
			for (const x of [t0, t1]) for (const z of [z0, z1]) for (const y of [top + 3, top + 4]) set(x, y, z, up(m.column));
			set(mid, top + 3, z0 + 1, state('minecraft:bell', { attachment: 'floor', facing: 'north' }));
			for (let x = t0; x <= t1; x++) for (let z = z0; z <= z1; z++) set(x, top + 5, z, block(m.cornice));
			hipRoof(t0, z0, t1, z1, top + 6, m.bankRoof, m.cornice);
			set(mid, top + 8, z0 + 1, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
		}
		// Altar: stone blocks with candles on either side of the sign.
		for (const x of [mid - 1, mid + 1]) {
			set(x, 1, D - 2, block(m.counter));
			set(x, 2, D - 2, state('minecraft:candle', { candles: '3', lit: 'true' }));
		}
		if (m.pot) for (const x of [1, W - 2]) set(x, 1, D - 2, block(m.pot));
		sign();
	}

	/**
	 * Station (9x11): a station house at the front (z 1..4) with a door to the platform, the platform under a canopy
	 * (z 5..8) with benches, a bell and lanterns, the track (rails across the whole width, z 9) and the far edge with
	 * buffers and lamps. The sign stands on the square in front. v0.87: as the stone buildings, the side walls of the
	 * house stand one block in under the eaves of its hipped roof, with columns and window sills in the strip and a step
	 * up to the door; tall (level 3): a clock turret on the ridge.
	 */
	function station() {
		const fz = 1, bz = 4, x0 = 1, x1 = W - 2;
		for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
			let floor = m.pave;
			if (z >= fz && z <= bz) floor = x > x0 && x < x1 && z > fz && z < bz ? m.floor : m.found;
			else if (z === 8) floor = m.paveRing;
			else if (z === 9) floor = m.stoneBase;
			set(x, 0, z, block(floor));
		}
		const cell = (x, y, z, corner, win) => {
			if (y === wall) return set(x, y, z, block(m.cornice));
			if (corner) return set(x, y, z, up(m.column));
			if (win && (y === 2 || y === 3)) return set(x, y, z, block(m.window));
			set(x, y, z, block(y === 1 ? m.stoneBase : m.stone));
		};
		for (let y = 1; y <= wall; y++) {
			for (let x = x0; x <= x1; x++) {
				const corner = x === x0 || x === x1;
				cell(x, y, fz, corner, [2, 3, W - 4, W - 3].includes(x));
				cell(x, y, bz, corner, [2, W - 3].includes(x));
			}
			for (let z = fz + 1; z < bz; z++) {
				cell(x0, y, z, false, true);
				cell(x1, y, z, false, true);
			}
		}
		for (const x of [0, W - 1]) {
			for (const z of [fz, bz]) for (let y = 1; y < wall; y++) set(x, y, z, up(m.column));
			for (let z = fz + 1; z < bz; z++) set(x, 1, z, state(slab(m.stoneStem), { type: 'top' }));
		}
		door(mid, fz);
		door(mid, bz);
		set(mid, 1, 0, stair(m.bankRoof, 'south')); // a step up to the door
		for (let x = x0 + 1; x < x1; x++) for (let z = fz + 1; z < bz; z++) set(x, wall, z, block(m.ceiling));
		hipRoof(0, 0, W - 1, bz, wall, m.bankRoof, m.cornice);
		// Inside: benches along the left wall, the ticket counter on the right.
		for (const z of [2, 3]) set(x0 + 1, 1, z, stair(m.bench, 'west'));
		for (const z of [2, 3]) set(x1 - 1, 1, z, block(m.counter));
		set(x1 - 2, 1, 3, block(m.counter));
		set(x1 - 1, 2, 2, block('minecraft:iron_bars'));
		set(x0 + 1, wall - 1, 2, lantern(true));
		set(x1 - 1, wall - 1, 2, lantern(true));
		// Platform canopy on posts at the edge, a beam and a roof of slabs; benches against the house.
		for (const x of [0, mid, W - 1]) for (let y = 1; y < wall; y++) set(x, y, 8, up(m.pillar));
		for (let x = 0; x < W; x++) {
			for (let z = 5; z <= 7; z++) set(x, wall, z, block(m.ceiling));
			set(x, wall, 8, lying(m.beam, 'x'));
			for (let z = 5; z <= 8; z++) set(x, wall + 1, z, state(slab(m.bankRoof), { type: 'bottom' }));
		}
		for (const x of [1, 2, W - 3, W - 2]) set(x, 1, 5, stair(m.bench, 'north'));
		for (const x of [2, W - 3]) set(x, wall - 1, 6, lantern(true));
		set(mid, wall - 1, 6, state('minecraft:bell', { attachment: 'ceiling', facing: 'north' }));
		// Track and the far edge.
		for (let x = 0; x < W; x++) set(x, 1, 9, state('minecraft:rail', { shape: 'east_west', waterlogged: 'false' }));
		for (const x of [0, W - 1]) set(x, 1, 10, block(m.fence));
		for (const x of [2, W - 3]) lampPost(x, 10, 1);
		// The square in front: lamps and the sign.
		for (const x of [0, W - 1]) lampPost(x, 0, 1);
		coreSign(mid - 2, 0, 'north');
		if (tall) {
			// Clock turret (3x3) on the ridge: a clock face with a gold hub and hands to the front and the back, a cornice,
			// a little hipped roof and a lightning rod.
			const ry = wall + 2; // the ridge
			const t0 = mid - 1, t1 = mid + 1;
			for (let y = ry; y <= ry + 2; y++) for (let x = t0; x <= t1; x++) for (let z = fz; z <= bz - 1; z++) {
				const face = z === fz || z === bz - 1;
				if (!face && x !== t0 && x !== t1) { set(x, y, z, block(m.stone)); continue; }
				set(x, y, z, block(face ? (x === mid && y === ry + 1 ? 'minecraft:gold_block' : 'minecraft:smooth_quartz') : m.stone));
			}
			for (const [z, hz, facing] of [[fz, fz - 1, 'north'], [bz - 1, bz, 'south']]) {
				set(mid, ry + 2, hz, state('minecraft:polished_blackstone_button', { face: 'wall', facing }));
				set(mid + 1, ry + 1, hz, state('minecraft:polished_blackstone_button', { face: 'wall', facing }));
			}
			for (let x = t0; x <= t1; x++) for (let z = fz; z <= bz - 1; z++) set(x, ry + 3, z, block(m.cornice));
			hipRoof(t0, fz, t1, bz - 1, ry + 4, m.bankRoof, m.cornice);
			set(mid, ry + 6, fz + 1, state('minecraft:waxed_lightning_rod', { facing: 'up' }));
		}
	}

	const shapes = { bank, mint, fountain, park, clock_tower: clockTower, watchtower, market_hall: marketHall, harbor, theater, hot_spring: hotSpring, festival,
		station, guild_hall: stoneHall, town_hall: stoneHall, chapel };
	(shapes[kind] || house)();
	if (lv >= 2) levelExtras();
	if (lv >= 2) decorate();

	/**
	 * v0.87 decorations of single kinds at level 2 / 3 where the common rule (decorate) finds little room:
	 * - quest hall: a hanging sign over the way in and candles on the map table; level 3 a banner on the front gable.
	 * - fountain: level 3 four lamp pillars on the paving around the basin.
	 * - market hall: banners inside the front gable; level 3 also inside the back one.
	 * - harbor: more casks on the quay; level 3 lanterns on the mooring posts of the pier.
	 * - hot spring: level 3 a roofed gate of posts and a beam at the entrance.
	 */
	function levelExtras() {
		const free = (x, y, z) => get(x, y, z) === undefined;
		const colour = branch === 'b' ? m.branchColour : (m.carpet || 'minecraft:red_carpet').replace('_carpet', '');
		switch (kind) {
			case 'quest_hall': {
				const wood = m.bench === 'minecraft:purpur' ? 'minecraft:warped' : m.bench;
				if (free(mid, wall - 1, 0)) set(mid, wall - 1, 0, state(wood + '_hanging_sign', { rotation: '0', attached: 'false' }));
				const R = W - 2;
				if (free(R, 2, 2)) set(R, 2, 2, state('minecraft:candle', { candles: '3', lit: 'true' }));
				if (lv >= 3 && !m.flat && free(mid, top + 2, 0) && free(mid, top + 1, 0)) {
					set(mid, top + 2, 0, state(colour + '_wall_banner', { facing: 'north' }));
				}
				break;
			}
			case 'fountain':
				if (lv < 3) break;
				for (const dx of [-3, 3]) for (const dz of [-3, 3]) {
					const x = mid + dx, z = mid + dz;
					if (!free(x, 1, z)) continue;
					set(x, 1, z, block(m.shaftBase));
					set(x, 2, z, block(m.post));
					set(x, 3, z, lantern(false));
				}
				break;
			case 'market_hall':
				for (const [z, facing, from] of [[1, 'south', 2], [D - 2, 'north', 3]]) {
					if (lv < from) continue;
					for (const x of [mid - 2, mid + 2]) if (free(x, 6, z) && free(x, 5, z)) set(x, 6, z, state(colour + '_wall_banner', { facing }));
				}
				break;
			case 'harbor':
				for (const [x, z] of [[6, 2], [7, 3]]) if (free(x, 1, z)) set(x, 1, z, state('minecraft:barrel', { facing: 'up' }));
				if (lv >= 3) for (let z = 7; z < D - 1; z++) for (const x of [mid - 1, mid + 1]) {
					if (/_fence$|_wall$/.test(ids[get(x, 1, z)] || '') && free(x, 2, z)) set(x, 2, z, lantern(false));
				}
				break;
			case 'hot_spring': {
				if (lv < 3) break;
				for (const x of [mid - 2, mid + 2]) for (let y = 1; y <= 3; y++) set(x, y, 0, up(m.pillar));
				for (let x = mid - 2; x <= mid + 2; x++) {
					set(x, 4, 0, lying(m.beam, 'x'));
					set(x, 5, 0, state(slab(m.gzRoof), { type: 'bottom' }));
				}
				set(mid, 3, 0, lantern(true));
				break;
			}
		}
	}

	/**
	 * v0.86 (N-11) decorations of a building raised to level 2 / 3. They go only where the cell is empty, so every kind
	 * keeps its own shape, and never into the way to the door or the sign:
	 * - level 2: two banners of the style's colour on the front wall (hung on solid blocks, never on glass); buildings
	 *   without a front wall (the plazas) get two standing banners at the entrance instead. Flower pots at the front
	 *   corners and beside the door, lanterns on the low posts and fences at the corners.
	 * - level 3: a second pair of banners in a second colour, banners on the side walls, lanterns at the back corners and
	 *   on the low posts and fences in the middle of each side.
	 */
	function decorate() {
		const NOT_SOLID = /(_slab|_stairs|_wall|_fence|fence_gate|pane|_bars|glass|door|lantern|torch|sign|banner|carpet|chain|candle|bell|rail|button|_bed|potted|leaves|sapling|_rod|campfire|cauldron|anvil|ladder|bush|grass$|fern|roots|fungus|tulip|poppy|dandelion|daisy|orchid|allium|bluet|lily|rose|lilac|peony|sunflower|cane|water|lava|cactus|chest|lectern|grindstone|stonecutter|brewing|amethyst|scaffolding|dirt_path|snow$|azalea|_head|skull|decorated_pot|bamboo|cake|kelp$|vine|torchflower|wheat|crop|pressure_plate|lever|note_block|chorus)/;
		const idAt = (x, y, z) => { const st = get(x, y, z); return st === undefined ? null : ids[st]; };
		const solid = (x, y, z) => { const id = idAt(x, y, z); return id !== null && id !== 'minecraft:air' && !NOT_SOLID.test(id); };
		const inside = (x, y, z) => x >= 0 && x < W && z >= 0 && z < D && y >= 0;
		const free = (x, y, z) => inside(x, y, z) && get(x, y, z) === undefined;
		const colours = [branch === 'b' ? m.branchColour : (m.carpet || 'minecraft:red_carpet').replace('_carpet', '')];
		colours.push(colours[0] === 'minecraft:yellow' ? 'minecraft:white' : 'minecraft:yellow');
		/** A wall banner at (x, y, z) on the solid block behind it (bx, bz), with room below it for the cloth. */
		const canHang = (x, y, z, bx, bz) => free(x, y, z) && free(x, y - 1, z) && y - 1 >= 1 && solid(bx, y, bz);
		/** Row of the front banner of column x at height y: in front of the first block from the front (z 1..2). */
		const frontRow = (x, y) => {
			if (x < 0 || x >= W) return -1;
			for (let z = 1; z <= 2; z++) {
				if (get(x, y, z) === undefined) continue;
				return canHang(x, y, z - 1, x, z) ? z - 1 : -1;
			}
			return -1;
		};
		/** One symmetric pair of front banners, the nearest to the door first. @return placed */
		const frontBanners = (colour) => {
			for (let dx = 1; dx < mid; dx++) for (const y of [3, 2, 4]) {
				const a = frontRow(mid - dx, y), b = frontRow(mid + dx, y);
				if (a < 0 || b < 0) continue;
				set(mid - dx, y, a, state(colour + '_wall_banner', { facing: 'north' }));
				set(mid + dx, y, b, state(colour + '_wall_banner', { facing: 'north' }));
				return true;
			}
			return false;
		};
		/** A spot on the floor of the front row: free with a solid floor, and not in front of the way in. */
		const floorSpot = (x, z) => free(x, 1, z) && solid(x, 0, z);
		const clearBehind = (x) => get(x, 1, 1) === undefined;
		/** Plazas: one symmetric pair of standing banners in the front row, facing out. */
		const standingBanners = (colour) => {
			for (let dx = 1; dx < mid; dx++) {
				const a = mid - dx, b = mid + dx;
				if (!floorSpot(a, 0) || !floorSpot(b, 0) || !free(a, 2, 0) || !free(b, 2, 0) || !clearBehind(a) || !clearBehind(b)) continue;
				// the way in stays open: the cells between the banners and the middle are free or the sign
				set(a, 1, 0, state(colour + '_banner', { rotation: '8' }));
				set(b, 1, 0, state(colour + '_banner', { rotation: '8' }));
				return true;
			}
			return false;
		};
		const pot = () => m.pot ? block(m.pot) : lantern(false);
		/** Lantern on the top of a low post or fence at (x, z) (at most 3 high), where nothing stands on it. */
		const POST = /(_fence|_wall|_log|_stem|pillar|chiseled_|_bricks)$/;
		const postLantern = (x, z) => {
			let y = -1;
			for (let yy = 0; yy < 8; yy++) if (get(x, yy, z) !== undefined) y = yy;
			if (y < 1 || y > 3 || !POST.test(idAt(x, y, z) || '') || !free(x, y + 1, z)) return;
			for (let yy = 1; yy <= y; yy++) if (get(x, yy, z) === undefined) return; // a solid column, not a roof edge
			set(x, y + 1, z, lantern(false));
		};

		/** Lanterns on the low fences of the front row (the hot spring), one symmetric pair, skipping {@code skip} pairs. */
		const fenceLanterns = (skip) => {
			for (let dx = 1; dx <= mid; dx++) {
				const a = mid - dx, b = mid + dx;
				const low = (x) => /(_fence|_wall)$/.test(idAt(x, 1, 0) || '') && free(x, 2, 0);
				if (!low(a) || !low(b) || skip-- > 0) continue;
				set(a, 2, 0, lantern(false));
				set(b, 2, 0, lantern(false));
				return;
			}
		};
		/** Lanterns hanging under the beam or roof over the front row, one symmetric pair where nothing hangs yet. */
		const hangingLanterns = () => {
			for (let dx = 1; dx <= mid; dx++) {
				const spot = (x) => {
					for (let y = 2; y < 8; y++) if (get(x, y, 0) !== undefined) return solid(x, y, 0) && free(x, y - 1, 0) && free(x, y - 2, 0) && y - 1 >= 2 ? y - 1 : -1;
					return -1;
				};
				const a = spot(mid - dx), b = spot(mid + dx);
				if (a < 0 || a !== b) continue;
				set(mid - dx, a, 0, lantern(true));
				set(mid + dx, a, 0, lantern(true));
				return;
			}
		};

		// ---- level 2 ----
		const walled = frontBanners(colours[0]);
		if (!walled) standingBanners(colours[0]);
		for (const x of [0, W - 1]) if (floorSpot(x, 0)) set(x, 1, 0, pot());
		for (let dx = 1; dx <= 2; dx++) {
			const a = mid - dx, b = mid + dx;
			const potted = (x) => /potted_/.test(idAt(x, 1, 0) || '');
			if (floorSpot(a, 0) && floorSpot(b, 0) && clearBehind(a) === clearBehind(b)) {
				set(a, 1, 0, pot());
				set(b, 1, 0, pot());
				break;
			}
			// one side has a pot already (the quest hall's porch): the other gets one too
			if (floorSpot(a, 0) && potted(b) || floorSpot(b, 0) && potted(a)) {
				set(floorSpot(a, 0) ? a : b, 1, 0, pot());
				break;
			}
		}
		for (const [x, z] of [[0, 0], [W - 1, 0], [0, D - 1], [W - 1, D - 1]]) postLantern(x, z);
		fenceLanterns(0);
		if (lv < 3) return;

		// ---- level 3 ----
		if (walled) frontBanners(colours[1]);
		else standingBanners(colours[1]);
		const zs = [Math.floor(D / 2), Math.floor(D / 2) - 1, Math.floor(D / 2) + 1, 2, D - 3];
		sideBanners: for (const z of zs) {
			for (const y of [3, 2, 4]) {
				let a = -1, b = -1;
				for (let x = 1; x <= 2; x++) if (get(x, y, z) !== undefined) { a = canHang(x - 1, y, z, x, z) ? x - 1 : -1; break; }
				for (let x = W - 2; x >= W - 3; x--) if (get(x, y, z) !== undefined) { b = canHang(x + 1, y, z, x, z) ? x + 1 : -1; break; }
				if (a < 0 || b < 0) continue;
				set(a, y, z, state(colours[0] + '_wall_banner', { facing: 'west' }));
				set(b, y, z, state(colours[0] + '_wall_banner', { facing: 'east' }));
				break sideBanners;
			}
		}
		for (const x of [0, W - 1]) if (floorSpot(x, D - 1)) set(x, 1, D - 1, lantern(false));
		hangingLanterns();
		fenceLanterns(1);
		// Plazas: lanterns at the inner corners, where all four are open paving.
		const corners = [[1, 1], [W - 2, 1], [1, D - 2], [W - 2, D - 2]];
		if (corners.every(([x, z]) => floorSpot(x, z) && free(x, 2, z))) for (const [x, z] of corners) set(x, 1, z, lantern(false));
		for (const [x, z] of [[mid, D - 1], [0, Math.floor(D / 2)], [W - 1, Math.floor(D / 2)]]) postLantern(x, z);
		// Stone buildings: the block over their door at the ends of the porch.
		const accent = { bank: 'minecraft:gold_block', town_hall: 'minecraft:gold_block', guild_hall: 'minecraft:emerald_block',
			mint: 'minecraft:waxed_chiseled_copper' }[kind];
		// v0.87: the porch columns carry the roof; the top of the outer ones is the accent
		if (accent) for (const x of [0, W - 1]) if (idAt(x, top - 1, 0) === m.column) set(x, top - 1, 0, block(accent));
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
	const suffix = lv === 1 ? '' : `_lv${lv}` + (branch === 'b' ? '_b' : '') + (tall ? '_tall' : '');
	const file = path.join(out, style, `${kind}${suffix}.nbt`);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, n.write(root));
	const back = n.plain(n.read(fs.readFileSync(file)));
	console.log(style, kind + suffix, back.size, back.blocks.length, 'blocks', back.palette.length, 'states');
}

for (const [style, m] of Object.entries(styles)) {
	for (const [kind, spec] of Object.entries(facilities)) {
		build(style, m, kind, spec);
		// v0.86 (N-11): the looks of levels 2 and 3, and level 3 with a second storey (or higher shape) where the kind has
		// one; v0.87: each in both directions
		for (const branch of ['a', 'b']) {
			build(style, m, kind, spec, 2, false, branch);
			build(style, m, kind, spec, 3, false, branch);
			if (spec.tall || spec.high) build(style, m, kind, spec, 3, true, branch);
		}
	}
}
