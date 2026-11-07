package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * N-5 (v0.44, {@code development.constructionEffects}): a site under construction shows it to the players nearby: an
 * orange dotted outline of the building's area at floor level (like construction tape), wood chips flying inside it and,
 * now and then, a quiet hammering sound.
 */
public final class ConstructionEffects {
	private static final int INTERVAL = 10;
	/** The outline is redrawn every this many intervals. */
	private static final int OUTLINE_EVERY = 2;
	/** Players within this horizontal distance of the site see and hear it. */
	private static final double RANGE = 48;
	private static final DustParticleOptions TAPE = new DustParticleOptions(0xFF9900, 1.0f);
	private static final BlockParticleOption CHIPS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState());
	private static final RandomSource RANDOM = RandomSource.create();

	private ConstructionEffects() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ConstructionEffects::tick);
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % INTERVAL != 0 || !Development.config().constructionEffects) return;
		boolean outline = tick / INTERVAL % OUTLINE_EVERY == 0;
		for (Market market : MarketRegistry.get(server).all()) {
			if (market.facilities().isEmpty()) continue;
			ServerLevel level = server.getLevel(market.dimension());
			if (level == null) continue;
			for (Facility facility : market.facilities().values()) {
				if (facility.state() != Facility.State.CONSTRUCTING) continue;
				BoundingBox box = facility.box();
				List<ServerPlayer> viewers = viewers(level, box);
				if (viewers.isEmpty()) continue;
				if (outline) viewers.forEach(p -> tape(level, p, box));
				chips(level, viewers, box);
				if (RANDOM.nextInt(6) == 0) {
					double x = box.minX() + RANDOM.nextDouble() * box.getXSpan();
					double z = box.minZ() + RANDOM.nextDouble() * box.getZSpan();
					level.playSound(null, x, box.minY() + 1, z, RANDOM.nextBoolean() ? SoundEvents.WOOD_HIT : SoundEvents.SCAFFOLDING_HIT,
							SoundSource.BLOCKS, 0.4f, 0.8f + RANDOM.nextFloat() * 0.4f);
				}
			}
		}
	}

	private static List<ServerPlayer> viewers(ServerLevel level, BoundingBox box) {
		double cx = (box.minX() + box.maxX() + 1) / 2.0;
		double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - cx;
			double dz = player.getZ() - cz;
			if (dx * dx + dz * dz <= RANGE * RANGE) list.add(player);
		}
		return list;
	}

	/** Dotted rectangle around the area, a little above the floor. */
	private static void tape(ServerLevel level, ServerPlayer player, BoundingBox box) {
		double x0 = box.minX(), x1 = box.maxX() + 1, z0 = box.minZ(), z1 = box.maxZ() + 1;
		double y = box.minY() + 0.15;
		for (double x = x0; x <= x1 + 1e-6; x += 1) {
			level.sendParticles(player, TAPE, true, false, x, y, z0, 1, 0, 0, 0, 0);
			level.sendParticles(player, TAPE, true, false, x, y, z1, 1, 0, 0, 0, 0);
		}
		for (double z = z0 + 1; z < z1 - 1e-6; z += 1) {
			level.sendParticles(player, TAPE, true, false, x0, y, z, 1, 0, 0, 0, 0);
			level.sendParticles(player, TAPE, true, false, x1, y, z, 1, 0, 0, 0, 0);
		}
	}

	/** A few wood chips at random spots of the area, up to the height of the walls. */
	private static void chips(ServerLevel level, List<ServerPlayer> viewers, BoundingBox box) {
		for (int i = 0; i < 2; i++) {
			double x = box.minX() + 0.5 + RANDOM.nextDouble() * (box.getXSpan() - 1);
			double z = box.minZ() + 0.5 + RANDOM.nextDouble() * (box.getZSpan() - 1);
			double y = box.minY() + 0.5 + RANDOM.nextDouble() * Math.max(1, box.getYSpan() - 2);
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.getBlockState(pos).isAir()) continue; // only in the open
			for (ServerPlayer player : viewers) level.sendParticles(player, CHIPS, false, false, x, y, z, 4, 0.3, 0.3, 0.3, 0.05);
		}
	}
}
