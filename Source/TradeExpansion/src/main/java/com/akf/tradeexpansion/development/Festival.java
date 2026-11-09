package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.Market;

/**
 * Festival days of a market with a festival plaza (design N-4, v0.51): every {@code development.festivalIntervalDays}
 * market-clock days, shifted per market so that neighbouring villages do not celebrate on the same day. Nothing is
 * saved: the day follows from the market id and the clock.
 */
public final class Festival {
	private Festival() {
	}

	/** Days until the next festival day of {@code market} (0 = today), or -1 without a completed festival plaza. */
	public static int daysUntil(Market market, long now) {
		if (!market.hasFacility(FacilityKind.FESTIVAL)) return -1;
		return DevelopmentMath.festivalIn(market.id(), Math.floorDiv(now, PriceModel.TICKS_PER_DAY), Development.config().festivalIntervalDays);
	}

	public static boolean today(Market market, long now) {
		return daysUntil(market, now) == 0;
	}

	/** Multiplier of reputation and development points gained at {@code market} now. */
	public static double multiplier(Market market, long now) {
		return today(market, now) ? Development.config().festivalMultiplier : 1.0;
	}
}
