package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PropagationMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Spreads a pressure change of one market to the others in the same dimension (design C-2), and between the Nether and
 * the Overworld with {@code distance.linkNetherToOverworld} (Nether coordinates ×8).
 * Only X propagates; stock does not. The formula is {@link PropagationMath}.
 */
public final class Propagation {
	private Propagation() {
	}

	/** The falloff settings of {@code distance.*}. */
	public static PropagationMath.Falloff falloff() {
		TradeExpansionConfig.Distance d = TradeExpansionConfig.get().distance;
		return new PropagationMath.Falloff(d.nearRadius, d.farRadius, d.falloffExponent);
	}

	/**
	 * @return the markets whose X changed (excluding the source)
	 */
	public static List<Market> apply(MarketRegistry registry, Market source, Identifier goodsId, double deltaX, long gameTime) {
		PropagationMath.Falloff falloff = falloff();
		List<Market> affected = new ArrayList<>();
		// C-2 MarketIndex: only markets within reach are looked at.
		for (Market other : registry.linkedWithin(source, falloff.reach())) {
			if (other == source) continue;
			GoodState state = other.good(goodsId);
			GoodParams params = other.params(goodsId);
			if (state == null || params == null) continue;

			double d = MarketRegistry.linkedDistance(source, other);
			if (!PropagationMath.reaches(d, falloff)) continue;

			other.refresh(gameTime);
			double link = GoodsCatalog.get(goodsId).link(); // C-2: × the good's link factor (D-2 `link`, default 1)
			state.setPressure(PropagationMath.spread(params, state.pressure(), deltaX, d, falloff, link));
			affected.add(other);
		}
		return affected;
	}
}
