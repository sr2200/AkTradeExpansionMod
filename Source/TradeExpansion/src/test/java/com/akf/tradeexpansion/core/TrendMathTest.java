package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Village trend periods (design P). */
class TrendMathTest {
	@Test
	void periodsAreShiftedPerMarket() {
		long day = PriceModel.TICKS_PER_DAY;
		long offset = TrendMath.offset(123456789L, 5.0);
		assertTrue(offset >= 0 && offset < 5 * day);
		// A period lasts exactly durationDays and starts at (k × length − offset).
		long start = 5 * day - offset;
		assertEquals(TrendMath.period(start - 1, 5.0, offset) + 1, TrendMath.period(start, 5.0, offset));
		assertEquals(TrendMath.period(start, 5.0, offset), TrendMath.period(start + 5 * day - 1, 5.0, offset));
		assertEquals(TrendMath.period(start, 5.0, offset) + 1, TrendMath.period(start + 5 * day, 5.0, offset));
	}

	@Test
	void negativeSeedsGiveAPositiveOffset() {
		assertTrue(TrendMath.offset(-987654321L, 3.0) >= 0);
	}

	@Test
	void multiplierRange() {
		assertEquals(1.15, TrendMath.multiplier(0.0, 1.15, 1.30), 1e-12);
		assertEquals(1.225, TrendMath.multiplier(0.5, 1.15, 1.30), 1e-12);
	}

	/** v0.51 theater: bonuses grow k times, losses shrink to 1 / k; neutral and k below 1 change nothing. */
	@Test
	void theater() {
		assertEquals(1.3, TrendMath.theater(1.2, 1.5), 1e-9);
		assertEquals(0.9, TrendMath.theater(0.85, 1.5), 1e-9);
		assertEquals(1.0, TrendMath.theater(1.0, 3.0), 1e-9);
		assertEquals(1.2, TrendMath.theater(1.2, 0.5), 1e-9);
	}
}
