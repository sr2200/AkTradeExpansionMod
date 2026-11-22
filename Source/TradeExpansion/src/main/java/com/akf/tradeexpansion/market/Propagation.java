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
 * Only X propagates; stock does not. The formula is {@link PropagationMath}. Markets on a trade route ({@link TradeRoutes},
 * C-5) move by at least {@code route.roadWeight} of the change.
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
		// C-5: markets on a trade route get at least route.roadWeight, wherever they are.
		java.util.Set<Long> partners = new java.util.HashSet<>();
		List<Market> candidates = new ArrayList<>(registry.linkedWithin(source, falloff.reach())); // C-2 MarketIndex: only markets within reach
		for (Market m : TradeRoutes.partners(registry, source)) {
			partners.add(m.id());
			if (!candidates.contains(m)) candidates.add(m);
		}
		double roadWeight = TradeRoutes.roadWeight();
		for (Market other : candidates) {
			if (other == source) continue;
			GoodState state = other.good(goodsId);
			GoodParams params = other.params(goodsId);
			if (state == null || params == null) continue;

			double d = MarketRegistry.linkedDistance(source, other);
			double minWeight = partners.contains(other.id()) ? roadWeight : 0;
			if (!PropagationMath.reaches(d, falloff) && minWeight <= 0) continue;

			other.refresh(gameTime);
			double link = GoodsCatalog.get(goodsId).link(); // C-2: × the good's link factor (D-2 `link`, default 1)
			state.setPressure(PropagationMath.spread(params, state.pressure(), deltaX, d, falloff, link, minWeight));
			affected.add(other);
		}
		return affected;
	}
}
