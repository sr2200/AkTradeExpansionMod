package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.buildAndComplete;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.builder;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.town;

/**
 * Server cases of what stands around the leisure buildings with water (28, 29): after they are built, hundreds of ticks
 * with fast random ticks must not let water run out of them, leaves decay or blocks change, and in a snowy biome the
 * game's own freezing (the precipitation tick on every column) must not turn their water to ice.
 */
final class ServerSurroundingsCases {
	private ServerSurroundingsCases() {
	}

	private static final Identifier FOUNTAIN = TradeExpansion.id("fountain");
	private static final Identifier PARK = TradeExpansion.id("park");
	private static final Identifier HOT_SPRING = TradeExpansion.id("hot_spring");
	private static final Identifier FESTIVAL = TradeExpansion.id("festival");

	/** Ticks for the water of a new building to settle before it is looked at. */
	private static final int SETTLE = 60;
	/** Ticks the buildings stand with fast random ticks. */
	private static final int STAND = 600;
	/** Random ticks per chunk section and tick while they stand (vanilla 3). */
	private static final int RANDOM_TICKS = 200;

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("fountain_water_stays", List.of("28-2"),
				"平原の村で噴水広場を建てると、水盤と柱の上に水（水源と流れ）が張り、水盤の底に明るさ10以上の光るブロックがあり、ランダムティックを速めて600ティック進めても、水が建物の外に出ず、建物のブロックが変わらないこと（ブロックの下の草が土になるのは除く。左右2本の流れの形・夜の見え方は画像で見る）",
				ctx -> stands(ctx, "plains", List.of(FOUNTAIN), (c, market) -> {
					Facility f = market.facilities().get(FOUNTAIN);
					Counts n = count(c.level(), f.box());
					that(n.sources >= 20, "the fountain holds only %d water sources", n.sources);
					that(n.flowing > 0, "no water flows from the top of the fountain");
					that(n.bright > 0, "no block of light level 10 or more in the fountain");
				})));
		cases.add(partial("park_stays", List.of("28-4"),
				"公園を建てると、生け垣と木の葉・池・サトウキビ・スイレンの葉・柵・ベンチ（階段）があり、ランダムティックを速めて600ティック進めても、葉が消えず、池の水が外に流れ出ず、サトウキビ・スイレンの葉も含めて建物のブロックが変わらないこと（ブロックの下の草が土になる・サトウキビが伸びるのは除く。池の下が低い地面の時は見ていない。見た目は画像で見る）",
				ctx -> stands(ctx, "plains", List.of(PARK), (c, market) -> {
					BoundingBox box = market.facilities().get(PARK).box();
					Counts n = count(c.level(), box);
					that(n.leaves >= 20, "the park has only %d leaves", n.leaves);
					that(n.sources > 0, "the park has no pond");
					that(n.has(Blocks.SUGAR_CANE), "the park has no sugar cane: %s", n.blocks.keySet());
					that(n.has(Blocks.LILY_PAD), "the park has no lily pad: %s", n.blocks.keySet());
					that(n.tagged(c.level(), box, BlockTags.FENCES), "the park has no fence: %s", n.blocks.keySet());
					that(n.tagged(c.level(), box, BlockTags.STAIRS), "the park has no bench (stairs): %s", n.blocks.keySet());
				})));
		cases.add(partial("hot_spring_and_festival_water_stay", List.of("29-5"),
				"温泉の湯船の底にソウルサンドがあって泡（泡の柱）が上がり、温泉と祭りの広場をランダムティックを速めて600ティック進めても、水が建物の外に出ず、建物のブロックが変わらないこと（ブロックの下の草が土になるのは除く。見た目は画像で見る）",
				ctx -> stands(ctx, "plains", List.of(HOT_SPRING, FESTIVAL), (c, market) -> {
					Counts n = count(c.level(), market.facilities().get(HOT_SPRING).box());
					that(n.has(Blocks.SOUL_SAND), "the hot spring has no soul sand: %s", n.blocks.keySet());
					that(n.has(Blocks.BUBBLE_COLUMN), "no bubbles rise in the hot spring: %s", n.blocks.keySet());
				})));
		cases.add(full("snow_does_not_freeze", List.of("28-3"), ctx -> freezes(ctx, List.of(FOUNTAIN), false)));
		cases.add(partial("snow_hot_spring_does_not_freeze", List.of("29-5"),
				"雪原の村の温泉（雪原の見た目）の水が、ゲームの凍らせる処理（雪の降る時と同じく、どの列にも降水の処理を何度も呼ぶ）でも凍らず、同じ場所の村の外の水は凍ること",
				ctx -> freezes(ctx, List.of(HOT_SPRING), false)));
		cases.add(partial("snow_park_pond_may_freeze", List.of("28-5"),
				"雪原の村の公園は雪原の見た目（トウヒ）で建ち、池が凍ってもスイレンの葉が残ること（ほかの見た目と材料は画像で見る）",
				ctx -> freezes(ctx, List.of(PARK), true)));
		return cases;
	}

	// ---- standing

	@FunctionalInterface
	private interface TownCheck {
		void run(ServerCtx c, Market market) throws Exception;
	}

	/**
	 * Builds {@code ids} in a town of the {@code biome} look, checks them with {@code check} once their water settled,
	 * then lets them stand {@link #STAND} ticks with fast random ticks and compares every block in and around them.
	 */
	private static void stands(ServerCtx ctx, String style, List<Identifier> ids, TownCheck check) {
		ctx.fillBiome(ctx.center(), 44, "minecraft:" + style);
		town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			for (Identifier id : ids) buildAndComplete(c, player, market, id);
			for (Identifier id : ids) that(style.equals(market.facilities().get(id).style()), "%s has the style %s, not %s", id, market.facilities().get(id).style(), style);
			c.later(SETTLE, c2 -> {
				check.run(c2, market);
				List<BoundingBox> boxes = ids.stream().map(id -> market.facilities().get(id).box()).toList();
				Map<BlockPos, Block> before = blocks(c2.level(), boxes);
				outsideDry(c2.level(), boxes, "after the water settled");
				fastRandomTicks(c2);
				c2.later(STAND, c3 -> {
					outsideDry(c3.level(), boxes, "after " + STAND + " ticks");
					unchanged(c3.level(), before, "after " + STAND + " ticks with fast random ticks");
				});
			});
		});
	}

	/** Random ticks {@link #RANDOM_TICKS} times as fast until the case ends. */
	private static void fastRandomTicks(ServerCtx c) {
		ServerLevel level = c.level();
		int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
		level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, RANDOM_TICKS, c.server());
		c.atCleanUp(() -> level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, speed, c.server()));
	}

	/** The blocks of each box and of 3 blocks around it (one layer below the floor to two above the top). */
	private static Map<BlockPos, Block> blocks(ServerLevel level, List<BoundingBox> boxes) {
		Map<BlockPos, Block> out = new HashMap<>();
		for (BoundingBox box : boxes) {
			for (BlockPos p : BlockPos.betweenClosed(box.minX() - 3, box.minY() - 1, box.minZ() - 3, box.maxX() + 3, box.maxY() + 2, box.maxZ() + 3)) {
				out.put(p.immutable(), level.getBlockState(p).getBlock());
			}
		}
		return out;
	}

	private static void unchanged(ServerLevel level, Map<BlockPos, Block> before, String when) {
		List<String> changed = new ArrayList<>();
		before.forEach((p, b) -> {
			Block now = level.getBlockState(p).getBlock();
			// Grass under a block (a lantern, a rock) turns to dirt: not to be seen.
			if (b == Blocks.GRASS_BLOCK && now == Blocks.DIRT && !level.getBlockState(p.above()).isAir()) return;
			if (b == Blocks.AIR && now == Blocks.SUGAR_CANE) return; // sugar cane grows
			if (now != b && changed.size() < 12) changed.add(p.toShortString() + " " + name(b) + " -> " + name(now));
		});
		that(changed.isEmpty(), "blocks changed %s: %s", when, changed);
	}

	/** No water or lava in the 3 blocks around the boxes (from one layer below the floor). */
	private static void outsideDry(ServerLevel level, List<BoundingBox> boxes, String when) {
		List<String> wet = new ArrayList<>();
		for (BoundingBox box : boxes) {
			for (BlockPos p : BlockPos.betweenClosed(box.minX() - 3, box.minY() - 1, box.minZ() - 3, box.maxX() + 3, box.maxY() + 2, box.maxZ() + 3)) {
				if (boxes.stream().anyMatch(b -> b.isInside(p))) continue;
				FluidState fluid = level.getFluidState(p);
				if (!fluid.isEmpty() && wet.size() < 12) wet.add(p.toShortString() + " " + name(level.getBlockState(p).getBlock()));
			}
		}
		that(wet.isEmpty(), "water ran out of the building %s: %s", when, wet);
	}

	// ---- freezing

	/**
	 * Builds {@code ids} in a snowy village and runs the precipitation tick (what freezes water and lays snow) on every
	 * column of them many times, at night in the snow. A water source dug into the floor outside them freezes (the
	 * check works); the buildings' water does not, unless {@code mayFreeze} (then their lily pads stay).
	 */
	private static void freezes(ServerCtx ctx, List<Identifier> ids, boolean mayFreeze) {
		ctx.fillBiome(ctx.center(), 44, "minecraft:snowy_plains");
		town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			for (Identifier id : ids) buildAndComplete(c, player, market, id);
			for (Identifier id : ids) that("snowy".equals(market.facilities().get(id).style()), "%s in a snowy village has the style %s", id, market.facilities().get(id).style());
			c.commandResult(player, "time set midnight");
			c.commandResult(player, "weather thunder");
			c.atCleanUp(() -> {
				c.commandResult(player, "weather clear");
				c.commandResult(player, "time set day");
			});
			c.later(SETTLE, c2 -> {
				ServerLevel level = c2.level();
				List<BoundingBox> boxes = ids.stream().map(id -> market.facilities().get(id).box()).toList();
				BlockPos control = control(c2, boxes);
				Map<Identifier, Counts> first = new HashMap<>();
				for (Identifier id : ids) first.put(id, count(level, market.facilities().get(id).box()));
				for (int round = 0; round < 40; round++) {
					for (BoundingBox box : boxes) {
						for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
							for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) level.tickPrecipitation(new BlockPos(x, box.minY(), z));
						}
					}
					level.tickPrecipitation(control);
				}
				that(level.getBlockState(control.below()).is(Blocks.ICE), "a water source outside the buildings in the snowy biome did not freeze (%s)",
						name(level.getBlockState(control.below()).getBlock()));
				for (Identifier id : ids) {
					Counts n = count(level, market.facilities().get(id).box());
					if (mayFreeze) {
						that(n.has(Blocks.LILY_PAD) || !first.get(id).has(Blocks.LILY_PAD), "the lily pads of %s went when its pond froze", id);
						that(n.leaves >= first.get(id).leaves, "leaves of %s went in the snow: %d -> %d", id, first.get(id).leaves, n.leaves);
					} else {
						that(first.get(id).sources > 0, "%s holds no water", id);
						equal(0, n.count(Blocks.ICE), "ice in " + id + " after the snowy night (water sources " + first.get(id).sources + " -> " + n.sources + ")");
						equal(first.get(id).sources, n.sources, "water sources of " + id + " after the snowy night");
					}
				}
			});
		});
	}

	/**
	 * A water source dug into the floor away from the buildings and their lights (the farthest corner of the floor);
	 * @return the column's top (the precipitation tick looks at the block below it)
	 */
	private static BlockPos control(ServerCtx c, List<BoundingBox> boxes) {
		BlockPos best = null;
		double far = -1;
		int y = c.center().getY() - 1;
		for (int dx : new int[] {-34, 34, -10, 10}) {
			for (int dz : new int[] {-34, 34, -10, 10}) {
				BlockPos p = c.center().offset(dx, -1, dz);
				double d = boxes.stream().mapToDouble(b -> distance(b, p)).min().orElse(99);
				if (d > far) {
					far = d;
					best = p;
				}
			}
		}
		BlockPos water = best.immutable();
		ServerLevel level = c.level();
		BlockState was = level.getBlockState(water);
		level.setBlock(water, Blocks.WATER.defaultBlockState(), 2 | 16);
		c.atCleanUp(() -> level.setBlock(water, was, 2 | 16));
		that(far >= 6, "no place for the water source away from the buildings (%.1f blocks)", far);
		return water.above();
	}

	private static double distance(BoundingBox b, BlockPos p) {
		double dx = Math.max(0, Math.max(b.minX() - p.getX(), p.getX() - b.maxX()));
		double dz = Math.max(0, Math.max(b.minZ() - p.getZ(), p.getZ() - b.maxZ()));
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- counting

	private static final class Counts {
		final Map<Block, Integer> blocks = new HashMap<>();
		int sources, flowing, bright, leaves;

		boolean has(Block block) {
			return count(block) > 0;
		}

		int count(Block block) {
			return blocks.getOrDefault(block, 0);
		}

		boolean tagged(ServerLevel level, BoundingBox box, net.minecraft.tags.TagKey<Block> tag) {
			return blocks.keySet().stream().anyMatch(b -> b.defaultBlockState().is(tag));
		}
	}

	private static Counts count(ServerLevel level, BoundingBox box) {
		Counts n = new Counts();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			n.blocks.merge(s.getBlock(), 1, Integer::sum);
			FluidState fluid = level.getFluidState(p);
			if (fluid.is(FluidTags.WATER)) {
				if (fluid.isSource()) n.sources++;
				else n.flowing++;
			}
			if (s.getLightEmission() >= 10) n.bright++;
			if (s.getBlock() instanceof LeavesBlock) n.leaves++;
		}
		return n;
	}

	private static String name(Block b) {
		return BuiltInRegistries.BLOCK.getKey(b).getPath();
	}
}
