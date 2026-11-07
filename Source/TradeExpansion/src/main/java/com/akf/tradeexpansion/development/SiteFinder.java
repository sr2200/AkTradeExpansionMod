package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.ConstructionMarkerBlock;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where a facility can stand (design N-5): at the oldest building site stake of the market, otherwise on the first
 * free, flat place around the market center. Nothing but air and replaceable blocks (grass, flowers, snow layers) is ever
 * built over, so player and village buildings stay untouched.
 * <p>
 * Templates face north: their front is the {@code z = 0} side with the door at {@code x = width / 2}.
 */
public final class SiteFinder {
	/** Natural ground a building may stand on (dirt, sand, stone, snow, ...; not dirt paths, ice or fluids). */
	public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, TradeExpansion.id("facility_ground"));
	/** Distance between positions tried by the automatic search. */
	private static final int STEP = 2;

	private SiteFinder() {
	}

	/**
	 * A place to build: the template origin and rotation (pivot 0), the front center at floor level, the building's box
	 * and the floor level. Columns lower than the floor get a foundation.
	 */
	public record Site(BlockPos origin, Rotation rotation, BlockPos front, BoundingBox box, int floorY, BlockPos marker) {
	}

	/** {@code site} when found; otherwise {@code reason} says why (stake blocked) or it is {@code null} (nothing found). */
	public record Result(Site site, Component reason, boolean markerUsed) {
	}

	public static Result find(ServerLevel level, Market market, StructureTemplate template) {
		// 1. Building site stakes, oldest first; stale entries (the stake is gone) are dropped.
		List<BlockPos> markers = market.markers();
		for (BlockPos marker : List.copyOf(markers)) {
			BlockState state = level.isLoaded(marker) ? level.getBlockState(marker) : null;
			if (state == null) continue;
			if (!state.is(ModBlocks.CONSTRUCTION_MARKER)) {
				markers.remove(marker);
				continue;
			}
			Direction facing = state.getValue(ConstructionMarkerBlock.FACING);
			Check check = check(level, market, template, marker.getX(), marker.getZ(), rotation(facing), marker);
			if (check.site() != null) return new Result(check.site(), null, true);
			return new Result(null, Component.translatable("message.ak_trade_expansion.facility.marker_blocked", check.reason()), true);
		}
		// 2. The nearest free place around the center, the front facing the center first.
		TradeExpansionConfig.Development c = TradeExpansionConfig.get().development;
		BlockPos center = market.center();
		for (int[] offset : DevelopmentMath.siteOffsets(c.siteRadius, STEP, c.siteSearchLimit)) {
			int x = center.getX() + offset[0];
			int z = center.getZ() + offset[1];
			for (Direction facing : facingsToward(offset[0], offset[1])) {
				Check check = check(level, market, template, x, z, rotation(facing), null);
				if (check.site() != null) return new Result(check.site(), null, false);
			}
		}
		return new Result(null, null, false);
	}

	/** The front faces the market center first, then the two sides, then away from it. */
	private static List<Direction> facingsToward(int dx, int dz) {
		Direction toCenter = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
		return List.of(toCenter, toCenter.getClockWise(), toCenter.getCounterClockWise(), toCenter.getOpposite());
	}

	/** Rotation that turns a north-facing template so that its front faces {@code facing}. */
	public static Rotation rotation(Direction facing) {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	public static Direction facing(Rotation rotation) {
		return rotation.rotate(Direction.NORTH);
	}

	private record Check(Site site, Component reason) {
		static Check fail(String key) {
			return new Check(null, Component.translatable("message.ak_trade_expansion.facility.site." + key));
		}
	}

	/** Can the template stand with its front center at ({@code fx}, {@code fz}), turned by {@code rotation}? */
	private static Check check(ServerLevel level, Market market, StructureTemplate template, int fx, int fz, Rotation rotation, BlockPos marker) {
		int w = template.getSize().getX();
		int h = template.getSize().getY();
		int d = template.getSize().getZ();
		BlockPos frontLocal = StructureTemplate.transform(new BlockPos(w / 2, 0, 0), Mirror.NONE, rotation, BlockPos.ZERO);
		int ox = fx - frontLocal.getX();
		int oz = fz - frontLocal.getZ();

		// Ground of the building's columns and of the ring around it (the ring only has to be about as high).
		int floorY = Integer.MIN_VALUE;
		int lowest = Integer.MAX_VALUE;
		List<int[]> columns = new ArrayList<>();
		for (int lx = -1; lx <= w; lx++) {
			for (int lz = -1; lz <= d; lz++) {
				BlockPos p = StructureTemplate.transform(new BlockPos(lx, 0, lz), Mirror.NONE, rotation, BlockPos.ZERO);
				int x = ox + p.getX();
				int z = oz + p.getZ();
				if (!level.hasChunk(x >> 4, z >> 4)) return Check.fail("unloaded");
				boolean inside = lx >= 0 && lx < w && lz >= 0 && lz < d;
				int y = surface(level, x, z, marker);
				if (inside) {
					BlockState ground = level.getBlockState(new BlockPos(x, y - 1, z));
					if (!ground.is(GROUND) || !ground.getFluidState().isEmpty()) return Check.fail("ground");
					columns.add(new int[] {x, y, z});
					floorY = Math.max(floorY, y);
				}
				lowest = Math.min(lowest, y);
			}
		}
		if (floorY - lowest > 1) return Check.fail("uneven");
		if (floorY + h > level.getMaxY()) return Check.fail("space");

		// Only air and replaceable blocks from the ground to the roof.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int[] col : columns) {
			for (int y = col[1]; y < floorY + h; y++) {
				pos.set(col[0], y, col[2]);
				if (!replaceable(level.getBlockState(pos), marker != null && pos.equals(marker))) return Check.fail("space");
			}
		}

		BlockPos origin = new BlockPos(ox, floorY, oz);
		BoundingBox box = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
				.setRotation(rotation), origin);
		BoundingBox around = box.inflatedBy(1);
		for (Facility other : market.facilities().values()) {
			if (other.state() != Facility.State.DESTROYED && other.box().intersects(around)) return Check.fail("overlap");
		}
		for (BlockPos stall : market.stalls()) {
			if (around.isInside(stall)) return Check.fail("overlap");
		}
		return new Check(new Site(origin, rotation, new BlockPos(fx, floorY, fz), box, floorY, marker), null);
	}

	/** First free height of a column: above the top solid block, with snow layers, grass and flowers counted as free. */
	private static int surface(ServerLevel level, int x, int z, BlockPos marker) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y - 1, z);
		while (y > level.getMinY() && replaceable(level.getBlockState(pos), marker != null && pos.equals(marker))) {
			y--;
			pos.setY(y - 1);
		}
		return y;
	}

	/** Air, grass, flowers, snow layers and the like (no fluids); the site's own stake too. */
	public static boolean replaceable(BlockState state, boolean ownMarker) {
		if (ownMarker && state.is(ModBlocks.CONSTRUCTION_MARKER)) return true;
		return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
	}
}
