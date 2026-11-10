package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterestMathTest {
	private static final double RATE = 0.0003; // C案: 0.03 % a day

	/** 10 days at 0.03 % compounded: about 0.3 % (10,000c → 10,030c). */
	@Test
	void tenDaysCompounded() {
		assertEquals(10_000 * Math.pow(1 + RATE, 10), InterestMath.accrue(10_000, 10, RATE, 100_000), 1e-6);
		assertEquals(10_030, Math.floor(InterestMath.accrue(10_000, 10, RATE, 100_000)));
	}

	/** Above the cap only the cap earns interest: 200,000c gets 100,000 × 0.03 % = 30c a day. */
	@Test
	void capLimitsInterest() {
		assertEquals(200_030, InterestMath.accrue(200_000, 1, RATE, 100_000), 1e-6);
		// Crossing the cap: compounding up to 100,000c, then 30c a day.
		double start = 99_990;
		double days = 100;
		double daysToCap = Math.log(100_000 / start) / Math.log1p(RATE);
		assertEquals(100_000 + 30 * (days - daysToCap), InterestMath.accrue(start, days, RATE, 100_000), 1e-6);
	}

	/** cap 0 or less: no cap, the whole balance compounds. */
	@Test
	void noCapCompoundsEverything() {
		assertEquals(200_000 * Math.pow(1 + RATE, 30), InterestMath.accrue(200_000, 30, RATE, 0), 1e-6);
		assertEquals(200_000 * Math.pow(1 + RATE, 30), InterestMath.accrue(200_000, 30, RATE, -1), 1e-6);
	}

	/** Exactly at the cap: the interest of the cap per day, without compounding. */
	@Test
	void exactlyAtTheCap() {
		assertEquals(100_000 + 30 * 10, InterestMath.accrue(100_000, 10, RATE, 100_000), 1e-6);
	}

	/** Fractions of a day and negative inputs. */
	@Test
	void fractionsAndNegatives() {
		assertEquals(10_000 * Math.pow(1 + RATE, 0.5), InterestMath.accrue(10_000, 0.5, RATE, 100_000), 1e-6);
		assertEquals(500, InterestMath.accrue(500, -3, RATE, 100_000));
		assertEquals(500, InterestMath.accrue(500, 10, -RATE, 100_000));
		assertEquals(-20, InterestMath.accrue(-20, 10, RATE, 100_000));
	}

	@Test
	void nothingWithoutRateTimeOrMoney() {
		assertEquals(500, InterestMath.accrue(500, 10, 0, 100_000));
		assertEquals(500, InterestMath.accrue(500, 0, RATE, 100_000));
		assertEquals(0, InterestMath.accrue(0, 10, RATE, 100_000));
	}
}
