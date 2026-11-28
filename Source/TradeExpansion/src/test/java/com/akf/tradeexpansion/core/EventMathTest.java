package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMathTest {
	@Test
	void rampGrowsOverThreeDays() {
		assertEquals(0.0, EventMath.ramp(9, 10));
		assertEquals(1.0 / 3, EventMath.ramp(10, 10), 1e-12);
		assertEquals(2.0 / 3, EventMath.ramp(11, 10), 1e-12);
		assertEquals(1.0, EventMath.ramp(12, 10));
		assertEquals(1.0, EventMath.ramp(20, 10));
	}

	@Test
	void multiplierScalesTheDeviation() {
		assertEquals(1.6, EventMath.multiplier(1.6, 1, 1, 1), 1e-12);
		assertEquals(0.7, EventMath.multiplier(0.7, 1, 1, 1), 1e-12);
		assertEquals(1.3, EventMath.multiplier(1.6, 0.5, 1, 1), 1e-12); // events.strength 0.5: half the effect
		assertEquals(1.15, EventMath.multiplier(1.6, 1, 0.25, 1), 1e-12);
		assertEquals(1.0, EventMath.multiplier(1.6, 1, 0, 1), 1e-12);
		assertEquals(1.0, EventMath.multiplier(0.7, 0, 1, 1), 1e-12);
		assertTrue(EventMath.multiplier(0.1, 3, 1, 1) >= 0.05); // never zero or negative
	}

	/** V-4 / B-6: on the first day the price moves less than the buy / sell gap, so buying on a forecast cannot profit at once. */
	@Test
	void firstDayIsSmallerThanTheSpreadGap() {
		double firstDay = EventMath.multiplier(1.6, 1, 1, EventMath.ramp(0, 0));
		assertEquals(1.2, firstDay, 1e-12);
		double spread = 0.4; // default s: buy = B·V·(1 + s/2), sell = B·V·(1 − s/2)
		assertTrue(firstDay < (1 + spread / 2) / (1 - spread / 2));
	}

	@Test
	void combinedProductIsClamped() {
		assertEquals(2.5, EventMath.clampCombined(1.3 * 1.6 * 1.2 * 1.5));
		assertEquals(0.4, EventMath.clampCombined(0.6 * 0.65 * 0.85));
		assertEquals(1.1, EventMath.clampCombined(1.1), 1e-12);
	}

	@Test
	void between() {
		assertEquals(0.65, EventMath.between(0, 0.65, 0.8), 1e-12);
		assertEquals(0.725, EventMath.between(0.5, 0.65, 0.8), 1e-12);
	}
}
