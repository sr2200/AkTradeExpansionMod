package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.TrendMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Village trends (design P): every {@code trend.durationDays} (shifted per market) a market chooses goods groups in
 * fashion and out of fashion; the multiplier of B is applied in {@link Market#params}.
 */
public final class TrendService {
	private TrendService() {
	}

	/** Chooses the trends again when a new period has started. @return {@code true} if they changed */
	public static boolean refresh(ServerLevel level, Market market, long now) {
		TradeExpansionConfig.Trend config = TradeExpansionConfig.get().trend;
		long seed = MarketFactory.seed(level, market.center());
		long period = TrendMath.period(now, config.durationDays, TrendMath.offset(seed, config.durationDays));
		if (period == market.trendPeriod()) return false;
		market.setTrendPeriod(period);
		roll(market, seed, period);
		return true;
	}

	/** Chooses the trends of {@code period} (also after {@code market reroll}). */
	public static void roll(Market market, long seed, long period) {
		TradeExpansionConfig.Trend config = TradeExpansionConfig.get().trend;
		market.trends().clear();
		List<Identifier> candidates = candidates(market, config.minGoods);
		Random random = new Random(seed ^ (period * 0x9E3779B97F4A7C15L) ^ 0x51ED27L);
		for (int i = 0; i < config.popularCount && !candidates.isEmpty(); i++) {
			Identifier pick = candidates.remove(random.nextInt(candidates.size()));
			market.trends().put(pick, TrendMath.multiplier(random.nextDouble(), config.popularMin, config.popularMax));
		}
		for (int i = 0; i < config.unpopularCount && !candidates.isEmpty(); i++) {
			Identifier pick = candidates.remove(random.nextInt(candidates.size()));
			market.trends().put(pick, TrendMath.multiplier(random.nextDouble(), config.unpopularMin, config.unpopularMax));
		}
	}

	/** Groups with at least {@code minGoods} of the market's goods (no specials, auto_add or multi-item units), by id. */
	static List<Identifier> candidates(Market market, int minGoods) {
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		for (Identifier goodsId : market.goods().keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def == null || def.autoAdd().isPresent() || def.unit() != 1 || market.isSpecial(goodsId)) continue;
			for (Identifier group : GoodsGroups.groupsOf(goodsId)) counts.merge(group, 1, Integer::sum);
		}
		List<Identifier> list = new ArrayList<>();
		counts.forEach((group, n) -> {
			if (n >= minGoods) list.add(group);
		});
		list.sort(Comparator.comparing(Identifier::toString));
		return list;
	}
}
