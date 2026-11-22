package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.quest.DemandService;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.TrendService;
import com.akf.tradeexpansion.rival.RivalService;
import net.minecraft.server.level.ServerLevel;

/**
 * Brings a market up to date before it is shown or traded with: lazy recovery (B-3), rival guilds (O-4), today's
 * specials (D-5), shortages (K), trends (P), requests (J), the weather (U).
 */
public final class MarketUpkeep {
	private MarketUpkeep() {
	}

	/** @return the market clock time used */
	public static long touch(ServerLevel level, Market market) {
		long now = MarketClock.now(level);
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		boolean changed = com.akf.tradeexpansion.weather.WeatherService.apply(level.getServer(), market); // U: before prices are used
		market.refresh(now);
		changed |= RivalService.advance(level, registry, now);
		changed |= RivalService.apply(registry, market, now); // after refresh: pushes are added already recovered to now
		changed |= MarketFactory.refreshSpecials(level, market, now);
		changed |= DemandService.refresh(level, market, now);
		changed |= TrendService.refresh(level, market, now);
		changed |= QuestService.refresh(level, market, now);
		if (changed) registry.setDirty();
		return now;
	}

	/**
	 * O-3 (v0.82): {@link #touch} for a player who opens the screen of / trades with the market: also remembers the visit,
	 * so that rival campaigns start near the places players are.
	 */
	public static long visit(ServerLevel level, Market market) {
		long now = MarketClock.now(level);
		market.setLastVisit(now); // before touch: today's roll already counts this market
		MarketRegistry.get(level.getServer()).setDirty();
		return touch(level, market);
	}
}
