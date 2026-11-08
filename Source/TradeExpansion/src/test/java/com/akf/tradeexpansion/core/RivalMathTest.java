package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Rival guild pushes (design O-3). */
class RivalMathTest {
	@Test
	void lastDayReachesTheImpact() {
		for (int days = 1; days <= 6; days++) {
			for (double h : new double[] {0.5, 2.0, 5.0}) {
				double x = RivalMath.pressureAfter(32, h, 0.25, days, days - 1);
				// X = tau ln(1 + m): the price multiplier exp(X / tau) is 1.25 (applied with the sign of the campaign)
				assertEquals(32 * Math.log(1.25), x, 1e-9, "days=" + days + " H=" + h);
				assertEquals(1.25, Math.exp(x / 32), 1e-9);
			}
		}
	}

	@Test
	void pushesGrowTowardsTheLastDay() {
		double previous = 0;
		for (int k = 0; k < 4; k++) {
			double x = RivalMath.pressureAfter(16, 2.0, 0.3, 4, k);
			assertTrue(x > previous);
			previous = x;
		}
	}

	@Test
	void decayHalvesPerHalfLife() {
		assertEquals(1.0, RivalMath.decay(0, 2.0), 1e-12);
		assertEquals(0.5, RivalMath.decay(2.0, 2.0), 1e-12);
		assertEquals(0.25, RivalMath.decay(4.0, 2.0), 1e-12);
		assertEquals(1.0, RivalMath.decay(-1, 2.0), 1e-12); // never grows
	}

	@Test
	void noImpactNoPush() {
		assertEquals(0.0, RivalMath.dailyPush(32, 2.0, 0.0, 3));
	}

	/**
	 * O-4: adding a push already recovered to now gives the same X as pushing on the day and recovering since (X recovers
	 * linearly).
	 */
	@Test
	void lazyPushMatchesPushThenRecover() {
		GoodParams p = new GoodParams(100, 32, 0.4, 16, 32, 8, 0.25, 4);
		double push = RivalMath.dailyPush(p.elasticity(), p.halfLifeDays(), 0.2, 3);
		GoodState eager = new GoodState(1.0, 5.0, 16, 0);
		PriceModel.recover(p, eager, 1.0, 1.0);
		eager.setPressure(eager.pressure() - push);
		PriceModel.recover(p, eager, 1.5, 1.0);

		GoodState lazy = new GoodState(1.0, 5.0, 16, 0);
		PriceModel.recover(p, lazy, 2.5, 1.0);
		lazy.setPressure(lazy.pressure() - push * RivalMath.decay(1.5, p.halfLifeDays()));
		assertEquals(eager.pressure(), lazy.pressure(), 1e-9);
	}
}
