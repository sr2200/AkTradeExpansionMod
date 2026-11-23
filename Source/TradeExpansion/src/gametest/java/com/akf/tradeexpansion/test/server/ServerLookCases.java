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
				"Lv3 の見た目（23種類の建物の2階建て・高い形（礼拝堂・駅舎・噴水・時計塔・見張り塔）を4つの向きで、2階なしの Lv3 を北向きで、7種類の見た目それぞれ）を、Lv1 と同じく構造物をブロックの更新つきで石の床に置くと、落ちて物になる部品が無く、30ティック進めてもブロックが変わらず、水と溶岩が建物の外に流れ出ないこと（Lv2 の飾りと方向 b の旗は Lv3 に含まれるので置いていない。見た目そのものは画像で見る）",
				ServerLookCases::standRaised));
		cases.add(partial("building_parts_by_style", List.of("29-14", "22-52", "22-53", "22-55", "22-56", "28-6"),
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
