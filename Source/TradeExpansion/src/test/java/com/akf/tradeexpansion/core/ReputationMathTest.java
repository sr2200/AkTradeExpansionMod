package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReputationMathTest {
	private static final GoodParams SWORD = new GoodParams(900, 16, 0.4, 8, 24, 4, 0.25, 3.0);
	private static final GoodParams BREAD = new GoodParams(20, 32, 0.4, 16, 32, 8, 0.25, 3.0);
	private static final int[] THRESHOLDS = {50, 150, 400, 1000, 2500};

	@Test
	void decayHalvesPerHalfLife() {
		assertEquals(50, ReputationMath.decay(100, 30, 30), 1e-9);
		assertEquals(25, ReputationMath.decay(100, 60, 30), 1e-9);
		assertEquals(100, ReputationMath.decay(100, 30, 0), 1e-9); // 0 = no decay
	}

	@Test
	void levelsAndNextThreshold() {
		assertEquals(0, ReputationMath.level(49, THRESHOLDS));
		assertEquals(1, ReputationMath.level(50, THRESHOLDS));
		assertEquals(5, ReputationMath.level(9999, THRESHOLDS));
		assertEquals(150, ReputationMath.nextThreshold(1, THRESHOLDS));
		assertEquals(-1, ReputationMath.nextThreshold(5, THRESHOLDS));
	}

	/** Buy prices × (1 − d), sell prices × (1 + d). */
	@Test
	void bonusMovesBothPrices() {
		GoodParams p = ReputationMath.withBonus(SWORD, 0.03);
		assertEquals(900 * 1.2 * 0.97, p.basePrice() * (1 + p.spread() / 2), 1e-6);
		assertEquals(900 * 0.8 * 1.03, p.basePrice() * (1 - p.spread() / 2), 1e-6);
		assertSame(SWORD, ReputationMath.withBonus(SWORD, 0));
	}

	/** The bonus never makes "buy, then sell straight back" profitable, even with the largest bulk discount. */
	@Test
	void bonusStaysExploitSafe() {
		GoodParams thin = new GoodParams(20, 200, 0.05, 64, 128, 8, 0.25, 3.0);
		BulkDiscount bulk = new BulkDiscount(List.of(new BulkDiscount.Tier(16, 0.05), new BulkDiscount.Tier(64, 0.10)));
		for (GoodParams base : new GoodParams[] {SWORD, BREAD, thin}) {
			GoodParams p = ReputationMath.withBonus(base, 0.05);
			assertTrue(PriceModel.isExploitSafe(p), "B-6 for " + base);
			for (int n : new int[] {1, 16, 64}) {
				long paid = PriceModel.sumBuy(p, 0, n, 1, bulk);
				long back = PriceModel.sumSell(p, p.clampPressure(-n), n);
				assertTrue(back < paid, "round trip of " + n + " profits for " + base);
			}
		}
		assertTrue(ReputationMath.safeBonus(thin) < 0.05);
	}
}
