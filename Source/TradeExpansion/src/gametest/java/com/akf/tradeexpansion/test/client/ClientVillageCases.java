package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.market.SettlementRule;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases in a normal world with generated villages (the seed of {@link PostScreenshots}), which the flat world of
 * the other cases has none of: the green box of {@code where show} (checklist 11-2) and the generated stall building
 * (6-1).
 */
final class ClientVillageCases {
	private static final int VILLAGE = 0x55FF55;

	private ClientVillageCases() {
	}

	private record Found(BlockPos center, BlockPos stand, BoundingBox box, BlockPos stall, List<String> stallBlocks, int villages) {
	}

	/**
	 * 11-2: standing in a generated village, {@code /aktradeex where show} draws green dots on the edges of the village's
	 * box, on the ground. 6-1 (part): one of the villages near spawn has the generated stall building (the stall with a
	 * barrel, a hay bale, a lantern and fences around it).
	 */
	static void naturalVillage(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		TestSingleplayerContext world = context.worldBuilder().adjustSettings(PostScreenshots::normalWorld).create();
		try {
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			Found found = world.getServer().computeOnServer(server -> find(server.overworld()));
			AutotestReport.LOGGER.info("Village at {} (box {}), stall {} of {} villages", found.center(), found.box(), found.stall(), found.villages());
			BlockPos stand = found.stand();
			world.getServer().runCommand("tp @p " + stand.getX() + " " + stand.getY() + " " + stand.getZ());
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();

			ParticleRecorder.start();
			world.getServer().runCommand("execute as @p at @p run aktradeex where show");
			context.waitTicks(25); // drawn every 10 ticks
			List<ParticleRecorder.Particle> green = ParticleRecorder.dust().stream().filter(p -> p.color() == VILLAGE).toList();
			ParticleRecorder.stop();
			BoundingBox box = found.box();
			check(green.size() >= 8, "only %d green dots (the village's range) in the village at %s", green.size(), found.center());
			List<String> off = new ArrayList<>();
			for (ParticleRecorder.Particle d : green) {
				boolean edge = Math.abs(d.x() - box.minX()) < 0.01 || Math.abs(d.x() - (box.maxX() + 1)) < 0.01
						|| Math.abs(d.z() - box.minZ()) < 0.01 || Math.abs(d.z() - (box.maxZ() + 1)) < 0.01;
				if (!edge) off.add(String.format("%.1f, %.1f", d.x(), d.z()));
			}
			check(off.isEmpty(), "green dots off the edges of the village box %s: %s", box, off.subList(0, Math.min(5, off.size())));
			List<ParticleRecorder.Particle> some = green.subList(0, Math.min(50, green.size()));
			List<String> air = world.getServer().computeOnServer(server -> {
				List<String> bad = new ArrayList<>();
				for (ParticleRecorder.Particle d : some) {
					int top = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(d.x()), (int) Math.floor(d.z()));
					if (Math.abs(d.y() - (top + 0.3)) > 0.01) bad.add(String.format("%.1f, %.2f, %.1f (top %d)", d.x(), d.y(), d.z(), top));
				}
				return bad;
			});
			check(air.isEmpty(), "green dots not on the ground: %s", air.subList(0, Math.min(5, air.size())));
			// Look along the nearest edge for the picture.
			world.getServer().runCommand("execute as @p at @p run aktradeex where show");
			BlockPos edge = world.getServer().computeOnServer(server -> ground(server, stand.getX(), box.minZ()));
			context.getInput().lookAt(edge);
			context.waitTicks(12);
			screenshot(context, result, "11-2_where_show_village");
			world.getServer().runCommand("execute as @p at @p run aktradeex where hide");

			try {
				check(found.stall() != null, "none of the %d villages near spawn has a generated stall", found.villages());
				List<String> missing = new ArrayList<>(List.of("barrel", "hay_block", "lantern", "fence"));
				missing.removeAll(found.stallBlocks());
				check(missing.isEmpty(), "the stall building at %s has no %s around the stall", found.stall(), missing);
				BlockPos stall = found.stall();
				// In front of the stall (its face), a little above.
				BlockPos front = world.getServer().computeOnServer(server -> stall.relative(
						server.overworld().getBlockState(stall).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), 4));
				world.getServer().runCommand("tp @p " + front.getX() + " " + (front.getY() + 1) + " " + front.getZ());
				context.waitTicks(20);
				world.getConnection().waitForChunksRender();
				context.getInput().lookAt(stall);
				context.waitTicks(5);
				screenshot(context, also, "6-1_generated_stall");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			ParticleRecorder.stop();
			context.setScreen(() -> null);
			world.close();
		}
	}

	/** The nearest village to spawn (where to stand in it and its box) and the first of up to seven with a generated stall. */
	private static Found find(ServerLevel level) {
		var villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureTags.VILLAGE);
		BlockPos spawn = level.getRespawnData().pos();
		int[][] from = {{0, 0}, {800, 0}, {0, 800}, {-800, 0}, {0, -800}, {800, 800}, {-800, -800}};
		List<BlockPos> seen = new ArrayList<>();
		BlockPos center = null;
		BlockPos stall = null;
		for (int[] f : from) {
			var near = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, spawn.offset(f[0], 0, f[1]), 60, false);
			if (near == null || seen.contains(near.getFirst())) continue;
			seen.add(near.getFirst());
			if (center == null) center = near.getFirst();
			stall = PostScreenshots.findStall(level, near.getFirst());
			if (stall != null) break;
		}
		check(center != null, "no village found around spawn");
		// Generates the chunks of the village center, then its box.
		BlockPos middle = ground(level.getServer(), center.getX(), center.getZ());
		BoundingBox box = SettlementRule.villageAt(level, middle);
		check(box != null, "no village structure at its center %s", middle);
		// A big village's edges are further than the dots are drawn (64 blocks): stand inside, 12 blocks from its north edge.
		BlockPos stand = ground(level.getServer(), middle.getX(), box.minZ() + 12);
		check(SettlementRule.villageAt(level, stand) != null, "%s, 12 blocks inside the north edge of %s, is not in the village", stand, box);
		List<String> blocks = new ArrayList<>();
		if (stall != null) {
			check(level.getBlockState(stall).is(ModBlocks.MARKET_STALL), "the stall found at %s is not a stall", stall);
			for (BlockPos p : BlockPos.betweenClosed(stall.offset(-5, -2, -5), stall.offset(5, 5, 5))) {
				BlockState state = level.getBlockState(p);
				if (state.is(Blocks.BARREL)) blocks.add("barrel");
				if (state.is(Blocks.HAY_BLOCK)) blocks.add("hay_block");
				if (state.is(Blocks.LANTERN)) blocks.add("lantern");
				if (state.getBlock() instanceof FenceBlock) blocks.add("fence");
			}
		}
		return new Found(center, stand, box, stall == null ? null : stall.immutable(), blocks.stream().distinct().toList(), seen.size());
	}
}
