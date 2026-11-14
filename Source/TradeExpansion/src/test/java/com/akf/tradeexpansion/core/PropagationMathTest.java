package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagationMathTest {
	private static final GoodParams BREAD = new GoodParams(20, 32, 0.4, 16, 32, 8, 0.25, 3.0);
	private static final PropagationMath.Falloff FALLOFF = new PropagationMath.Falloff(128, 1536, 2.0);

	@Test
	void nearMarketsGetTheWholeChange() {
		assertEquals(5.0, PropagationMath.spread(BREAD, 0, 5, 100, FALLOFF, 1.0), 1e-9);
		assertEquals(5.0, PropagationMath.spread(BREAD, 0, 5, 128, FALLOFF, 1.0), 1e-9);
	}

	@Test
	void weightTimesLink() {
		double w = DistanceModel.weight(500, 128, 1536, 2.0); // 0.541
		assertEquals(2 + 10 * w * 0.5, PropagationMath.spread(BREAD, 2, 10, 500, FALLOFF, 0.5), 1e-9);
		assertEquals(2 + 10 * w * 1.5, PropagationMath.spread(BREAD, 2, 10, 500, FALLOFF, 1.5), 1e-9);
		assertEquals(2.0, PropagationMath.spread(BREAD, 2, 10, 500, FALLOFF, 0.0), 1e-9); // link 0: the change does not spread
	}

	@Test
	void farMarketsAreUntouched() {
		assertEquals(3.0, PropagationMath.spread(BREAD, 3, 10, 1536, FALLOFF, 1.0));
		assertEquals(3.0, PropagationMath.spread(BREAD, 3, 10, 9999, FALLOFF, 1.0));
		assertTrue(PropagationMath.reaches(1535, FALLOFF));
		assertFalse(PropagationMath.reaches(1536, FALLOFF));
		assertEquals(1536, FALLOFF.reach());
	}

	@Test
	void clampedToTheRangeOfX() {
		assertEquals(BREAD.maxPressure(), PropagationMath.spread(BREAD, 0, 1e6, 0, FALLOFF, 1.0), 1e-9);
		assertEquals(BREAD.minPressure(), PropagationMath.spread(BREAD, 0, -1e6, 0, FALLOFF, 1.0), 1e-9);
	}

	@Test
	void reachCoversANearRadiusBeyondTheFarRadius() {
		PropagationMath.Falloff odd = new PropagationMath.Falloff(2000, 1000, 2.0);
		assertEquals(2000, odd.reach());
		assertTrue(PropagationMath.reaches(1500, odd));
	}
}
