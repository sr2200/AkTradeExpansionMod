package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shortages (design K): every {@code demand.durationDays} a market re-rolls up to {@code demand.maxShortages} of its
 * sellable goods whose base price is multiplied. The multiplier is applied in {@link Market#params}.
 */
public final class DemandService {
	private DemandService() {
	}

	/** Re-rolls the shortages when a new period has started. @return {@code true} if they changed */
	public static boolean refresh(ServerLevel level, Market market, long now) {
		TradeExpansionConfig.Demand config = TradeExpansionConfig.get().demand;
		long period = QuestMath.period(now, config.durationDays);
		if (period == market.shortagePeriod()) return false;
		market.setShortagePeriod(period);
		roll(level, market, period);
		return true;
	}

	/** Chooses new shortages for {@code period} (also used by {@code market reroll}). */
	public static void roll(ServerLevel level, Market market, long period) {
		TradeExpansionConfig.Demand config = TradeExpansionConfig.get().demand;
		market.shortages().clear();
		List<Identifier> candidates = new ArrayList<>();
		for (Identifier goodsId : market.goods().keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			// Sellable goods only; tokens such as the emerald coin (unit > 1 / auto_add) are excluded.
			if (def != null && def.sell() && def.unit() == 1 && def.autoAdd().isEmpty()) candidates.add(goodsId);
		}
		Random random = new Random(MarketFactory.seed(level, market.center()) ^ (period * 0x632BE59BD9B4E019L));
		for (int i = 0; i < config.maxShortages && !candidates.isEmpty(); i++) {
			Identifier pick = candidates.remove(random.nextInt(candidates.size()));
			market.shortages().put(pick, QuestMath.shortageMultiplier(random.nextDouble(), config.minMultiplier, config.maxMultiplier));
		}
	}
}
