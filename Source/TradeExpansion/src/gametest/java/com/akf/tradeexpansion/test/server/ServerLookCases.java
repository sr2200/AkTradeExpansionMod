package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the buildings' looks (22-50 to 22-56, 28-6, 29-2, 29-14): every building of every look (the five
 * village types, the Nether and the End) is placed as its construction completes it (the structure with block updates),
 * in each of the four directions, on a stone floor away from the test area; nothing may fall off (no dropped items), no
 * block may change in the ticks after (with fast random ticks: lava must not set anything on fire), and no water or
 * lava may run out. Each look has its own structure of each building, with the parts the checklist names.
 */
final class ServerLookCases {
	private ServerLookCases() {
	}

	static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga", "nether", "end");
	private static final Rotation[] ROTATIONS = Rotation.values();
	/** Distance between the buildings of one round (the largest is 15 wide). */
	private static final int SPACING = 24;
	private static final int COLUMNS = 4;
	/** Ticks the placed buildings stand before they are compared. */
	private static final int STAND = 30;

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("buildings_stand_every_way", List.of("29-2", "22-50"),
				"23種類の建物を7種類の見た目（平原・砂漠・サバンナ・雪原・タイガ・ネザー・エンド）と4つの向きで、完成の時と同じく構造物をブロックの更新つきで石の床に置くと、落ちて物になる部品（ボタン・旗・松明・はしご・吊り看板・ろうそく・ケーキなど）が無く、ランダムティックを速めて30ティック進めてもブロックが変わらず（火が付かない。時計塔の避雷針は蝋引きで錆びない。ブロックの下の草と菌糸が土とネザーラックになる・つる・サトウキビ・竹が伸びるのは除く）、水と溶岩が建物の外に流れ出ないこと（材料と形の見た目は画像で見る。村の家や木の近くでは見ていない）",
				ServerLookCases::stand));
		cases.add(partial("raised_buildings_stand", List.of("45-1"),
				"Lv3 の見た目（23種類の建物の2階建て・高い形（礼拝堂・駅舎・噴水・時計塔・見張り塔・公園・市場・港・劇場・温泉・祭りの広場）を4つの向きで、2階なしの Lv3 を北向きで、7種類の見た目それぞれ）を、Lv1 と同じく構造物をブロックの更新つきで石の床に置くと、落ちて物になる部品が無く、30ティック進めてもブロックが変わらず、水と溶岩が建物の外に流れ出ないこと（Lv2 の飾りと方向 b の旗は Lv3 に含まれるので置いていない。見た目そのものは画像で見る）",
				ServerLookCases::standRaised));
		cases.add(partial("upstairs_reachable", List.of("45-3"),
				"2階建ての Lv3（木組みの家8種・銀行・鋳造所・商人組合の会館・村役場・依頼所）を7種類の見た目と4つの向きで置き、プレイヤーと同じ大きさの箱（幅 0.6・高さ 1.8）を、段差は 0.6 まで歩いて・1.25 まで跳んで動かすと、入口から2階の床まで行けること（階段の途中で頭がつかえない。依頼所ははしご）。宿屋の2階にもベッドがあること（村の見た目。寝られるか・本当に歩いて上がれるかはゲームの中で見る）",
				ServerLookCases::upstairs));
		cases.add(partial("building_parts_by_style",List.of("29-14", "22-52", "22-53", "22-55", "22-56", "28-6"),
				"7種類の見た目それぞれに23種類の建物の構造物があり（平原に代わらない）、看板（建物の目印）が1つずつあること。宿屋は村の見た目でベッド4つ・ネザーとエンドはベッドなしでカーペット、煙突に火の付いた焚き火（エンドはエンドロッド）。依頼所と時計塔に鐘、時計塔にボタンと避雷針、見張り塔にはしご・旗・松明、酒場に吊り看板とろうそく、市場にハチの巣（bee_nest）と飾り壺、劇場と祭りの広場に旗、祭りの広場にろうそくのケーキ、銀行に鉄格子、温泉にソウルサンド、鋳造所に溶鉱炉・金床・鍛冶台と煙突の火（エンドはエンドロッド）、駅舎にレールと鐘、目利きの店にアメジストの塊・模様入りの本棚・書見台、問屋に樽・チェスト・干し草、商人組合の会館にエメラルドブロック・旗・ろうそく、礼拝堂に鐘・ろうそく・色付きガラス、村役場に旗・書見台・本棚、職人の工房に金床・作業台・石切台と煙突の火、学舎に本棚・書見台・ろうそく。噴水広場はネザーで溶岩・ほかは水、公園はネザーで真紅のキノコと溶岩・エンドでコーラスとエンドロッド・村で葉（材料の見た目、鐘を鳴らせる・寝られる・村人が来るは見ていない）",
				ServerLookCases::parts));
		cases.add(ServerCase.full("look_by_biome", List.of("22-51"), ctx -> {
			// The biome of the market's center decides the look: the village type, jungles and swamps the plains look.
			Map<String, String> expected = new LinkedHashMap<>();
			expected.put("minecraft:jungle", "plains");
			expected.put("minecraft:swamp", "plains");
			expected.put("minecraft:mangrove_swamp", "plains");
			expected.put("minecraft:plains", "plains");
			expected.put("minecraft:desert", "desert");
			expected.put("minecraft:savanna", "savanna");
			expected.put("minecraft:snowy_plains", "snowy");
			expected.put("minecraft:snowy_taiga", "snowy");
			expected.put("minecraft:taiga", "taiga");
			BlockPos at = ctx.center();
			String before = ctx.level().getBiome(at).unwrapKey().orElseThrow().identifier().toString();
			List<String> wrong = new ArrayList<>();
			try {
				for (Map.Entry<String, String> e : expected.entrySet()) {
					ctx.fillBiome(at, 4, e.getKey());
					String look = FacilityBuilder.style(ctx.level(), at);
					if (!look.equals(e.getValue())) wrong.add(e.getKey() + " -> " + look + " (expected " + e.getValue() + ")");
				}
			} finally {
				ctx.fillBiome(at, 4, before);
			}
			that(wrong.isEmpty(), "looks by biome: %s", wrong);
		}));
		return cases;
	}

	// ---- placing

	/** The structure of a building in a look; fails when the look has none of its own (the plains one would be used). */
	static StructureTemplate template(ServerCtx c, Identifier id, String style) {
		FacilityDefinition def = FacilityCatalog.get(id);
		Identifier base = TradeExpansion.id(def.structure());
		Identifier styled = Identifier.fromNamespaceAndPath(base.getNamespace(), "facility/" + style + "/" + base.getPath());
		that(c.server().getStructureTemplateManager().get(styled).isPresent(), "the %s look has no structure of its own for %s (%s)", style, id, styled);
		return FacilityBuilder.template(c.server(), def, style).orElseThrow();
	}

	private record Placed(Identifier id, BoundingBox box) {
	}

	/** Where the buildings go: south of the test area, on a stone floor laid for the case (and taken away after it). */
	private static BlockPos base(ServerCtx c) {
		return c.center().offset(0, 0, 400);
	}

	/** Lays the stone floor under the grid of buildings and keeps its chunks loaded; all of it goes back after the case. */
	private static void prepare(ServerCtx c) {
		ServerLevel level = c.level();
		BlockPos base = base(c);
		int size = COLUMNS * SPACING;
		BlockPos from = base.offset(-4, -1, -4), to = base.offset(size + 4, 26, size + 4);
		Map<BlockPos, BlockState> saved = new HashMap<>();
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				if (level.setChunkForced(cx, cz, true)) forced.add(new ChunkPos(cx, cz));
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			BlockState was = level.getBlockState(p);
			BlockState now = p.getY() == from.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (was != now) {
				saved.put(p.immutable(), was);
				level.setBlock(p, now, 2 | 16);
			}
		}
		c.atCleanUp(() -> {
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				BlockState back = saved.getOrDefault(p, null);
				if (back != null) level.setBlock(p, back, 2 | 16);
				else if (p.getY() > from.getY()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			for (Entity e : level.getEntities((Entity) null, AABB.of(BoundingBox.fromCorners(from, to)).inflate(4), e -> !(e instanceof Player))) e.discard();
			for (ChunkPos ch : forced) level.setChunkForced(ch.x(), ch.z(), false);
		});
	}

	/** Places every building of {@code style} turned by {@code rotation}, one per cell of the grid, with block updates. */
	private static List<Placed> placeAll(ServerCtx c, String style, Rotation rotation) {
		return placeAll(c, style, rotation, 1, false);
	}

	/**
	 * v0.87: the same with the look of {@code level} (N-11; {@code tall}: the tall look of level 3 where the building has
	 * one, else the plain level 3).
	 */
	private static List<Placed> placeAll(ServerCtx c, String style, Rotation rotation, int level, boolean tall) {
		List<Placed> placed = new ArrayList<>();
		int i = 0;
		for (Identifier id : FacilityCatalog.all().keySet()) {
			FacilityDefinition def = FacilityCatalog.get(id);
			StructureTemplate template = level <= 1 ? template(c, id, style)
					: FacilityBuilder.template(c.server(), def, style, level, tall, "a")
							.or(() -> FacilityBuilder.template(c.server(), def, style, level, false, "a")).orElseThrow();
			// Turned around the middle of its cell.
			BlockPos cell = base(c).offset(i % COLUMNS * SPACING + SPACING / 2, 0, i / COLUMNS * SPACING + SPACING / 2);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
			BlockPos origin = cell.subtract(StructureTemplate.transform(new BlockPos(template.getSize().getX() / 2, 0, template.getSize().getZ() / 2),
					settings.getMirror(), rotation, BlockPos.ZERO));
			template.placeInWorld(c.level(), origin, origin, settings, RandomSource.create(i), Block.UPDATE_ALL);
			placed.add(new Placed(id, template.getBoundingBox(settings, origin)));
			i++;
		}
		return placed;
	}

	private static void clear(ServerCtx c, List<Placed> placed) {
		ServerLevel level = c.level();
		for (Placed p : placed) {
			BoundingBox b = p.box();
			for (BlockPos q : BlockPos.betweenClosed(b.minX() - 3, b.minY(), b.minZ() - 3, b.maxX() + 3, b.maxY() + 2, b.maxZ() + 3)) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			for (Entity e : level.getEntities((Entity) null, AABB.of(b).inflate(4), e -> !(e instanceof Player))) e.discard();
		}
	}

	// ---- 29-2 / 22-50: standing in every direction

	private record Round(String style, Rotation rotation, int level, boolean tall) {
		@Override
		public String toString() {
			return style + " look" + (level > 1 ? " Lv" + level + (tall ? " tall" : "") : "") + " turned " + rotation;
		}
	}

	private static void stand(ServerCtx ctx) {
		List<Round> rounds = new ArrayList<>();
		for (String style : STYLES) for (Rotation rotation : ROTATIONS) rounds.add(new Round(style, rotation, 1, false));
		stand(ctx, rounds);
	}

	/** 45-1 (v0.87): the tall looks of level 3 in every direction, the plain level 3 facing north. */
	private static void standRaised(ServerCtx ctx) {
		List<Round> rounds = new ArrayList<>();
		for (String style : STYLES) {
			for (Rotation rotation : ROTATIONS) rounds.add(new Round(style, rotation, 3, true));
			rounds.add(new Round(style, Rotation.NONE, 3, false));
		}
		stand(ctx, rounds);
	}

	private static void stand(ServerCtx ctx, List<Round> rounds) {
		prepare(ctx);
		ServerLevel level = ctx.level();
		int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
		level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 200, ctx.server());
		ctx.atCleanUp(() -> level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, speed, ctx.server()));
		for (Round round : rounds) {
			{
				String style = round.toString();
				@SuppressWarnings("unchecked")
				List<Placed>[] placed = new List[1];
				@SuppressWarnings("unchecked")
				Map<BlockPos, Block>[] before = new Map[1];
				ctx.later(1, c -> {
					placed[0] = placeAll(c, round.style(), round.rotation(), round.level(), round.tall());
					// What fell off while it was placed lies around as items.
					List<String> dropped = drops(c, placed[0]);
					that(dropped.isEmpty(), "%s: parts fell off when placed: %s", style, dropped);
					before[0] = new HashMap<>();
					for (Placed p : placed[0]) {
						for (BlockPos q : BlockPos.betweenClosed(p.box().minX(), p.box().minY(), p.box().minZ(), p.box().maxX(), p.box().maxY(), p.box().maxZ())) {
							before[0].put(q.immutable(), c.level().getBlockState(q).getBlock());
						}
					}
				});
				ctx.later(STAND, c -> {
					try {
						List<String> dropped = drops(c, placed[0]);
						that(dropped.isEmpty(), "%s: parts fell off within %d ticks: %s", style, STAND, dropped);
						List<String> changed = new ArrayList<>();
						before[0].forEach((q, b) -> {
							Block now = c.level().getBlockState(q).getBlock();
							if (now == b || changed.size() >= 10) return;
							// Grass and nylium under a block turn to dirt and netherrack (not to be seen); flowing water and lava settle.
							boolean covered = !c.level().getBlockState(q.above()).isAir();
							if (covered && (b == Blocks.GRASS_BLOCK && now == Blocks.DIRT || b.defaultBlockState().is(BlockTags.NYLIUM) && now == Blocks.NETHERRACK)) return;
							if ((b == Blocks.AIR || b == Blocks.WATER || b == Blocks.LAVA) && (now == Blocks.WATER || now == Blocks.LAVA || now == Blocks.AIR)) return;
							if (b == Blocks.WATER && now == Blocks.BUBBLE_COLUMN) return; // over soul sand
							String was = BuiltInRegistries.BLOCK.getKey(b).getPath(), is = BuiltInRegistries.BLOCK.getKey(now).getPath(); // the clock tower's rod is waxed, so copper must not weather either
							if (java.util.stream.Stream.of(was, is).anyMatch(x -> x.contains("vines") || x.equals("sugar_cane") || x.contains("bamboo"))) return; // plants grow
							changed.add(name(q, c.level(), b) + " -> " + BuiltInRegistries.BLOCK.getKey(now).getPath());
						});
						that(changed.isEmpty(), "%s: blocks changed within %d ticks: %s", style, STAND, changed);
						List<String> outside = new ArrayList<>();
						for (Placed p : placed[0]) {
							BoundingBox b = p.box();
							for (BlockPos q : BlockPos.betweenClosed(b.minX() - 3, b.minY(), b.minZ() - 3, b.maxX() + 3, b.maxY() + 1, b.maxZ() + 3)) {
								if (b.isInside(q)) continue;
								BlockState s = c.level().getBlockState(q);
								if ((!c.level().getFluidState(q).isEmpty() || s.is(BlockTags.FIRE)) && outside.size() < 10) {
									outside.add(p.id().getPath() + " " + q.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
								}
							}
							for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
								if (c.level().getBlockState(q).is(BlockTags.FIRE) && outside.size() < 10) outside.add(p.id().getPath() + " fire at " + q.toShortString());
							}
						}
						that(outside.isEmpty(), "%s: water, lava or fire outside the buildings (or fire in them): %s", style, outside);
					} finally {
						clear(c, placed[0]);
					}
				});
			}
		}
	}

	private static List<String> drops(ServerCtx c, List<Placed> placed) {
		List<String> out = new ArrayList<>();
		for (Placed p : placed) {
			for (ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, AABB.of(p.box()).inflate(3))) {
				out.add(p.id().getPath() + ": " + e.getItem().getCount() + " " + BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath() + " at "
						+ e.blockPosition().toShortString());
			}
		}
		return out;
	}

	private static String name(BlockPos q, ServerLevel level, Block b) {
		return q.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(b).getPath();
	}

	// ---- 45-3: the way up to the second storey

	/** The kinds whose tall level 3 has a second storey (genfacility.js: spec.tall), the quest hall with a ladder. */
	private static final java.util.Set<FacilityKind> STOREYS = java.util.EnumSet.of(FacilityKind.QUEST_HALL, FacilityKind.BANK, FacilityKind.WAREHOUSE,
			FacilityKind.INN, FacilityKind.TAVERN, FacilityKind.MINT, FacilityKind.APPRAISER, FacilityKind.WHOLESALER, FacilityKind.GUILD_HALL,
			FacilityKind.TOWN_HALL, FacilityKind.WORKSHOP, FacilityKind.SCHOOL);
	/** Height of the second storey's floor over the ground (genfacility.js: wall). */
	private static final int UPPER_FLOOR = 4;

	private static void upstairs(ServerCtx ctx) {
		prepare(ctx);
		List<String> failed = new ArrayList<>();
		for (String style : STYLES) {
			for (Rotation rotation : ROTATIONS) {
				ctx.later(1, c -> {
					List<Placed> placed = placeAll(c, style, rotation, 3, true);
					try {
						for (Placed p : placed) {
							FacilityKind kind = FacilityCatalog.get(p.id()).kind();
							if (!STOREYS.contains(kind)) continue;
							String where = style + " " + p.id().getPath() + " turned " + rotation;
							String result = Walker.upstairs(c.level(), p.box());
							if (result != null) failed.add(where + ": " + result);
							boolean village = !style.equals("nether") && !style.equals("end");
							if (kind == FacilityKind.INN && village) {
								int upper = 0;
								BoundingBox b = p.box();
								for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY() + UPPER_FLOOR + 1, b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
									BlockState s = c.level().getBlockState(q);
									if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) upper++;
								}
								if (upper == 0) failed.add(where + ": no bed on the second storey");
							}
						}
					} finally {
						clear(c, placed);
					}
				});
			}
		}
		ctx.later(1, c -> that(failed.isEmpty(), "the way up to the second storey: %s", failed));
	}

	/**
	 * A rough walk of a player's body (0.6 wide, 1.8 high) over the block shapes, on a grid of quarter blocks: it steps up
	 * 0.6 as it walks (vanilla's step height) or 1.25 with a jump, rising in place first so that the head must be free,
	 * falls where nothing holds it and climbs ladders. A quarter block a move, the body stands across two block columns
	 * on the way, as a player's does on a staircase, and can stand off the middle of a block to pass a hanging lantern.
	 */
	static final class Walker {
		private static final double HALF = 0.3, HEIGHT = 1.8, E = 1e-3, STEP = 1.0 / 16, GRID = 0.25;
		private static final double WALK = 9 * STEP, JUMP = 20 * STEP;
		private static final int[][] DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

		private final ServerLevel level;
		private final double bottom;

		private Walker(ServerLevel level, double bottom) {
			this.level = level;
			this.bottom = bottom;
		}

		/** @return null when the second storey's floor of the building in {@code box} can be reached from a door, else why not */
		static String upstairs(ServerLevel level, BoundingBox box) {
			Walker w = new Walker(level, box.minY());
			double goal = box.minY() + UPPER_FLOOR + 1;
			java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			// Seeds: the middle of the blocks on both sides of each door on the ground (a door is open to go through).
			for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.minY() + 1, box.maxZ())) {
				if (!level.getBlockState(q).is(BlockTags.DOORS)) continue;
				for (int[] d : DIRECTIONS) {
					double x = q.getX() + d[0] + 0.5, z = q.getZ() + d[1] + 0.5;
					double feet = w.settle(x, box.minY() + 1 + 2 * STEP, z); // over a rug, under a lantern hanging low
					if (w.free(x, feet, z)) w.add(queue, seen, x, z, feet);
				}
			}
			if (queue.isEmpty()) return "no door on the ground";
			double highest = box.minY();
			while (!queue.isEmpty()) {
				long[] n = queue.poll();
				double x = n[0] * GRID, z = n[1] * GRID, feet = n[2] * STEP;
				highest = Math.max(highest, feet);
				if (feet >= goal - E && feet < goal + 0.5) return null;
				for (int[] d : DIRECTIONS) {
					double nx = x + d[0] * GRID, nz = z + d[1] * GRID;
					if (nx < box.minX() - 1 || nx > box.maxX() + 2 || nz < box.minZ() - 1 || nz > box.maxZ() + 2) continue;
					for (double lift : new double[] { WALK, JUMP }) {
						double to = w.move(x, z, feet, nx, nz, lift);
						if (!Double.isNaN(to) && to <= box.maxY()) w.add(queue, seen, nx, nz, to);
					}
				}
				// Ladders: on one, the body climbs.
				if (level.getBlockState(BlockPos.containing(x, feet + E, z)).is(BlockTags.CLIMBABLE) && w.free(x, feet + 0.5, z)) {
					w.add(queue, seen, x, z, feet + 0.5);
				}
			}
			return String.format("could not get higher than %.2f over the ground (the floor of the second storey is at %d)", highest - box.minY(), UPPER_FLOOR);
		}

		private void add(java.util.ArrayDeque<long[]> queue, java.util.Set<String> seen, double x, double z, double feet) {
			long gx = Math.round(x / GRID), gz = Math.round(z / GRID), f = Math.round(feet / STEP);
			if (seen.add(gx + "," + gz + "," + f)) queue.add(new long[] { gx, gz, f });
		}

		private AABB body(double x, double feet, double z) {
			return new AABB(x - HALF + E, feet + E, z - HALF + E, x + HALF - E, feet + HEIGHT - E, z + HALF - E);
		}

		private boolean free(double x, double feet, double z) {
			return level.noCollision(body(x, feet, z));
		}

		/** Falls from {@code feet} to what holds the body (or a few blocks under the ground, where it stops looking). */
		private double settle(double x, double feet, double z) {
			double f = feet;
			while (f > bottom - 3 && free(x, f - STEP, z) && !level.getBlockState(BlockPos.containing(x, f + E, z)).is(BlockTags.CLIMBABLE)) f -= STEP;
			return f;
		}

		/** @return the height of the feet after moving from (x, z) to (nx, nz), NaN when the body cannot */
		private double move(double x, double z, double feet, double nx, double nz, double lift) {
			double f = feet;
			if (!free(nx, f, nz)) {
				double up = Double.NaN;
				for (double l = STEP; l <= lift + E; l += STEP) {
					if (!free(x, f + l, z)) break; // the head hits something as it rises
					if (free(nx, f + l, nz)) {
						up = f + l;
						break;
					}
				}
				if (Double.isNaN(up)) return Double.NaN;
				f = up;
			}
			return settle(nx, f, nz);
		}
	}

	// ---- 29-14 and others: the parts of each look

	private static void parts(ServerCtx ctx) {
		prepare(ctx);
		List<String> missing = new ArrayList<>();
		for (String style : STYLES) {
			ctx.later(1, c -> {
				List<Placed> placed = placeAll(c, style, Rotation.NONE);
				try {
					for (Placed p : placed) {
						Map<String, Integer> n = count(c.level(), p.box());
						FacilityKind kind = FacilityCatalog.get(p.id()).kind();
						boolean village = !style.equals("nether") && !style.equals("end");
						List<String> wanted = new ArrayList<>();
						if (n.getOrDefault("@sign", 0) != 1) missing.add(style + " " + p.id().getPath() + ": " + n.getOrDefault("@sign", 0) + " building signs");
						switch (kind) {
							case INN -> {
								if (village && n.getOrDefault("@bed", 0) != 4) missing.add(style + " inn: " + n.getOrDefault("@bed", 0) + " beds, not 4");
								if (!village && n.getOrDefault("@bed", 0) != 0) missing.add(style + " inn: " + n.getOrDefault("@bed", 0) + " beds, none expected");
								if (!village) wanted.add("@carpet");
								wanted.add(style.equals("end") ? "end_rod" : "@lit_campfire"); // the End's chimney: an end rod
							}
							case QUEST_HALL -> wanted.add("bell");
							case CLOCK_TOWER -> wanted.addAll(List.of("bell", "@button", "lightning_rod"));
							case WATCHTOWER -> wanted.addAll(List.of("ladder", "@banner", "torch"));
							case TAVERN -> wanted.addAll(List.of("@hanging_sign", "@candle"));
							case MARKET_HALL -> wanted.addAll(List.of("bee_nest", "decorated_pot"));
							case THEATER -> wanted.add("@banner");
							case FESTIVAL -> wanted.addAll(List.of("@banner", "@candle_cake"));
							case BANK -> wanted.add("iron_bars");
							case HOT_SPRING -> wanted.add("soul_sand");
							case STATION -> wanted.addAll(List.of("rail", "bell"));
							case APPRAISER -> wanted.addAll(List.of("amethyst_cluster", "chiseled_bookshelf", "lectern"));
							case WHOLESALER -> wanted.addAll(List.of("barrel", "chest", "hay_block"));
							case GUILD_HALL -> wanted.addAll(List.of("emerald_block", "@banner", "@candle"));
							case CHAPEL -> wanted.addAll(List.of("bell", "@candle", "white_stained_glass_pane"));
							case TOWN_HALL -> wanted.addAll(List.of("@banner", "lectern", "bookshelf"));
							case WORKSHOP -> wanted.addAll(List.of("anvil", "crafting_table", "stonecutter", style.equals("end") ? "end_rod" : "@lit_campfire"));
							case SCHOOL -> wanted.addAll(List.of("bookshelf", "lectern", "@candle"));
							case MINT -> wanted.addAll(List.of("blast_furnace", "anvil", "smithing_table", style.equals("end") ? "end_rod" : "@lit_campfire")); // chimney top
							case FOUNTAIN -> wanted.add(style.equals("nether") ? "@lava" : "@water");
							case PARK -> wanted.addAll(style.equals("nether") ? List.of("crimson_fungus", "@lava") : style.equals("end") ? List.of("chorus", "end_rod")
									: List.of("@leaves"));
							default -> {
							}
						}
						for (String w : wanted) {
							if (n.getOrDefault(w, 0) == 0) missing.add(style + " " + p.id().getPath() + ": no " + w);
						}
					}
				} finally {
					clear(c, placed);
				}
			});
		}
		ctx.later(1, c -> that(missing.isEmpty(), "parts missing: %s", missing));
	}

	/**
	 * Counts of the blocks in {@code box} by name part ("bell", "torch", ...; every word of each block's path counts) and
	 * by kinds starting with "@" (beds by their heads, buttons, banners, ...).
	 */
	private static Map<String, Integer> count(ServerLevel level, BoundingBox box) {
		Map<String, Integer> n = new LinkedHashMap<>();
		for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(q);
			String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
			n.merge(path, 1, Integer::sum);
			for (String word : List.of("bell", "torch", "ladder", "lightning_rod", "bee_nest", "decorated_pot", "iron_bars", "soul_sand", "crimson_fungus", "chorus",
					"end_rod")) {
				if (path.contains(word) && !path.equals(word)) n.merge(word, 1, Integer::sum);
			}
			if (s.is(ModBlocks.FACILITY_SIGN)) n.merge("@sign", 1, Integer::sum);
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) n.merge("@bed", 1, Integer::sum);
			if (s.is(BlockTags.WOOL_CARPETS)) n.merge("@carpet", 1, Integer::sum);
			if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) n.merge("@lit_campfire", 1, Integer::sum);
			if (s.is(BlockTags.BUTTONS)) n.merge("@button", 1, Integer::sum);
			if (s.is(BlockTags.BANNERS)) n.merge("@banner", 1, Integer::sum);
			if (s.is(BlockTags.ALL_HANGING_SIGNS)) n.merge("@hanging_sign", 1, Integer::sum);
			if (s.is(BlockTags.CANDLES)) n.merge("@candle", 1, Integer::sum);
			if (s.is(BlockTags.CANDLE_CAKES)) n.merge("@candle_cake", 1, Integer::sum);
			if (s.is(BlockTags.LEAVES)) n.merge("@leaves", 1, Integer::sum);
			if (level.getFluidState(q).is(FluidTags.WATER)) n.merge("@water", 1, Integer::sum);
			if (level.getFluidState(q).is(FluidTags.LAVA)) n.merge("@lava", 1, Integer::sum);
		}
		return n;
	}
}
