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
		return DevelopmentMath.festivalIn(market.id(), Math.floorDiv(now, PriceModel.TICKS_PER_DAY), intervalDays(market));
	}

	public static boolean today(Market market, long now) {
		return daysUntil(market, now) == 0;
	}

	/** Multiplier of reputation and development points gained at {@code market} now. */
	public static double multiplier(Market market, long now) {
		return today(market, now) ? festivalMultiplier(market) : 1.0;
	}

	/** N-11 (v0.86): the multiplier of a festival day at {@code market}: {@code development.festivalMultiplier}, stronger with the level. */
	public static double festivalMultiplier(Market market) {
		return FacilityEffects.raise(market, FacilityKind.FESTIVAL, 0, Development.config().festivalMultiplier);
	}

	/** N-11: days between the festivals of {@code market}: {@code development.festivalIntervalDays}, fewer with the level (at least 2). */
	public static int intervalDays(Market market) {
		return com.akf.tradeexpansion.core.FacilityLevelMath.interval(Development.config().festivalIntervalDays,
				FacilityEffects.strength(market, FacilityKind.FESTIVAL, 1), 2);
	}
}
