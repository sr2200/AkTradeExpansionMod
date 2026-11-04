package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.quest.DemandService;
import com.akf.tradeexpansion.quest.QuestService;
import net.minecraft.server.level.ServerLevel;

/**
 * Brings a market up to date before it is shown or traded with: lazy recovery (B-3), today's specials (D-5),
 * shortages (K), requests (J).
 */
public final class MarketUpkeep {
	private MarketUpkeep() {
	}

	/** @return the market clock time used */
	public static long touch(ServerLevel level, Market market) {
		long now = MarketClock.now(level);
		market.refresh(now);
		boolean changed = MarketFactory.refreshSpecials(level, market, now);
		changed |= DemandService.refresh(level, market, now);
		changed |= QuestService.refresh(level, market, now);
		if (changed) MarketRegistry.get(level.getServer()).setDirty();
		return now;
	}
}
