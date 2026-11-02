package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Spreads a pressure change of one market to the others in the same dimension (design C-2).
 * Only X propagates; stock does not.
 */
public final class Propagation {
	private Propagation() {
	}

	/**
	 * @return the markets whose X changed (excluding the source)
	 */
	public static List<Market> apply(MarketRegistry registry, Market source, Identifier item, double deltaX, long gameTime) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		List<Market> affected = new ArrayList<>();
		// Linear scan is fine for a few hundred markets; a spatial index can replace it if needed.
		for (Market other : registry.inDimension(source.dimension())) {
			if (other == source) continue;
			GoodState state = other.good(item);
			GoodParams params = other.params(item);
			if (state == null || params == null) continue;

			double d = DistanceModel.horizontalDistance(source.center().getX(), source.center().getZ(), other.center().getX(), other.center().getZ());
			double w = DistanceModel.weight(d, config.nearRadius(), config.farRadius(), config.falloffExponent());
			if (w <= 0) continue;

			other.refresh(gameTime);
			state.setPressure(params.clampPressure(state.pressure() + deltaX * w));
			affected.add(other);
		}
		return affected;
	}
}
