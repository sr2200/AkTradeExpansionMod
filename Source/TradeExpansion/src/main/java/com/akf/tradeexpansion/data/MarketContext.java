package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.market.MarketOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * What a market profile condition can look at (design D-1 step 1).
 */
public record MarketContext(ServerLevel world, BlockPos center, MarketOrigin origin) {
	public Holder<Biome> biome() {
		return world.getBiome(center);
	}
}
