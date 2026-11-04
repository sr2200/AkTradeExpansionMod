package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriceModelTest {
	/** Diamond sword of design 3-1: B=900, tau=16, T=8, max_stock=24. */
	private static final GoodParams SWORD = new GoodParams(900, 16, 0.4, 8, 24, 4, 0.25, 3.0);
	/** Bread of design 3-2: B=20, tau=32, T=16, r=8. */
	private static final GoodParams BREAD = new GoodParams(20, 32, 0.4, 16, 32, 8, 0.25, 3.0);
	/** H: recovery half-life of design B-7 (2 in-game days). */
	private static final double HALF_LIFE = 2.0;

	@Test
	void swordNormalPrices() {
		assertEquals(1080, PriceModel.buyPrice(SWORD, 0));
		assertEquals(720, PriceModel.sellPrice(SWORD, 0));
	}

	@Test
	void swordSellTenMatchesTable() {
		long[] expected = {720, 676, 635, 596, 560, 526, 494, 464, 436, 410};
		for (int k = 0; k < expected.length; k++) {
			assertEquals(expected[k], PriceModel.sellPrice(SWORD, k), "item " + (k + 1));
		}
		assertEquals(5517, PriceModel.sumSell(SWORD, 0, 10));
	}

	@Test
	void bulkEqualsOneByOne() {
		GoodState bulk = new GoodState(1, 0, 8, 0);
		GoodState single = new GoodState(1, 0, 8, 0);
		long bulkTotal = PriceModel.sumSell(SWORD, bulk.pressure(), 10);
		PriceModel.applySell(SWORD, bulk, 10);
		long singleTotal = 0;
		for (int i = 0; i < 10; i++) {
			singleTotal += PriceModel.sumSell(SWORD, single.pressure(), 1);
			PriceModel.applySell(SWORD, single, 1);
		}
		assertEquals(bulkTotal, singleTotal);
		assertEquals(bulk.pressure(), single.pressure(), 1e-12);
		assertEquals(18, bulk.stock(), 1e-12);
	}

	@Test
	void swordAfterSellingTen() {
		assertEquals(385, PriceModel.sellPrice(SWORD, 10));
		assertEquals(579, PriceModel.buyPrice(SWORD, 10));
	}

	@Test
	void recoveryHalfLife() {
		GoodState start = new GoodState(1, 10, 18, 0);
		PriceModel.recover(SWORD, start, 0, HALF_LIFE, 1.0);
		assertEquals(10, start.pressure(), 1e-12);

		double[][] table = {{0.5, 425}, {1, 462}, {2, 526}, {3, 577}, {4, 615}}; // {days, sell price}
		for (double[] row : table) {
			GoodState s = new GoodState(1, 10, 18, 0);
			PriceModel.recover(SWORD, s, row[0], HALF_LIFE, 1.0);
			assertEquals((long) row[1], PriceModel.sellPrice(SWORD, s.pressure()), "after " + row[0] + " days");
		}
		GoodState twoDays = new GoodState(1, 10, 18, 0);
		PriceModel.recover(SWORD, twoDays, 2, HALF_LIFE, 1.0);
		assertEquals(5, twoDays.pressure(), 1e-12);
	}

	@Test
	void negativeElapsedTimeIsIgnored() {
		GoodState s = new GoodState(1, 10, 18, 0);
		PriceModel.recover(SWORD, s, -3, HALF_LIFE, 1.0);
		assertEquals(10, s.pressure());
		assertEquals(18, s.stock());
	}

	@Test
	void stockRecovery() {
		GoodState empty = new GoodState(1, 0, 0, 0);
		PriceModel.recover(BREAD, empty, 0.125, HALF_LIFE, 1.0);
		assertEquals(1, empty.stock(), 1e-12);

		GoodState full = new GoodState(1, 0, 0, 0);
		PriceModel.recover(BREAD, full, 5, HALF_LIFE, 1.0);
		assertEquals(16, full.stock(), 1e-12);

		GoodState excess = new GoodState(1, 0, 24, 0);
		PriceModel.recover(BREAD, excess, 1, HALF_LIFE, 1.0); // drains at r/2 = 4/day
		assertEquals(20, excess.stock(), 1e-12);
	}

	/** B-3: consumables drain T per day above the target and use their own half-life. */
	@Test
	void consumablesRecoverQuickly() {
		GoodParams food = new GoodParams(20, 32, 0.4, 16, 32, 8, 0.25, 3.0, 16, 1.0);
		GoodState full = new GoodState(1, 10, 32, 0);
		PriceModel.recover(food, full, 0.5, 1.0);
		assertEquals(24, full.stock(), 1e-12); // 16/day for half a day
		assertEquals(10 * Math.pow(2, -0.5), full.pressure(), 1e-12); // half-life 1 day
		PriceModel.recover(food, full, 1, 1.0);
		assertEquals(16, full.stock(), 1e-12); // stops at T

		// The short constructor keeps the old behaviour: drain r / 2, half-life 2 days.
		assertEquals(4, BREAD.consumePerDay(), 1e-12);
		assertEquals(2.0, BREAD.halfLifeDays(), 1e-12);
	}

	@Test
	void breadBuyout() {
		assertEquals(24, PriceModel.buyPrice(BREAD, 0));
		assertEquals(39, PriceModel.buyPrice(BREAD, -15));
		assertEquals(497, PriceModel.sumBuy(BREAD, 0, 16));

		GoodState s = new GoodState(1, 0, 16, 0);
		PriceModel.applyBuy(BREAD, s, 16);
		assertFalse(PriceModel.canBuy(s));
		assertEquals(0.125, PriceModel.daysUntilRestock(BREAD, s, 1.0), 1e-12);

		PriceModel.recover(BREAD, s, 1, HALF_LIFE, 1.0);
		assertEquals(-16 / Math.sqrt(2), s.pressure(), 1e-9);
		assertEquals(35, PriceModel.buyPrice(BREAD, s.pressure()));

		PriceModel.recover(BREAD, s, 1, HALF_LIFE, 1.0);
		assertEquals(-8, s.pressure(), 1e-9);
		assertEquals(31, PriceModel.buyPrice(BREAD, s.pressure()));
	}

	@Test
	void multiplierAndPressureAreClamped() {
		assertEquals(0.25, PriceModel.multiplier(SWORD, 1e6), 1e-12);
		assertEquals(3.0, PriceModel.multiplier(SWORD, -1e6), 1e-12);
		GoodState s = new GoodState(1, 0, 8, 0);
		PriceModel.applySell(SWORD, s, 100_000);
		assertEquals(SWORD.maxPressure(), s.pressure(), 1e-9);
	}

	@Test
	void buyNeverBelowSell() {
		GoodParams cheap = new GoodParams(1, 32, 0.4, 16, 32, 8, 0.25, 3.0);
		for (double x = -60; x <= 60; x += 0.37) {
			assertTrue(PriceModel.buyPrice(SWORD, x) >= PriceModel.sellPrice(SWORD, x));
			assertTrue(PriceModel.buyPrice(cheap, x) >= PriceModel.sellPrice(cheap, x));
			assertTrue(PriceModel.sellPrice(cheap, x) >= 1);
		}
	}

	@Test
	void sellThenBuyBackNeverProfits() {
		for (GoodParams p : new GoodParams[]{SWORD, BREAD, PriceModel.ensureExploitSafe(new GoodParams(500, 3, 0.05, 8, 16, 4, 0.25, 3.0))}) {
			assertTrue(PriceModel.isExploitSafe(p));
			for (double x = p.minPressure(); x <= p.maxPressure(); x += 0.5) {
				for (int n = 1; n <= 20; n++) {
					long earned = PriceModel.sumSell(p, x, n);
					long spent = PriceModel.sumBuy(p, p.clampPressure(x + n), n);
					assertTrue(spent >= earned, "profit at x=" + x + " n=" + n);
				}
			}
		}
	}

	@Test
	void minSafeElasticity() {
		assertEquals(2.466, PriceModel.minSafeElasticity(0.4), 1e-3);
		assertEquals(20.0, PriceModel.minSafeElasticity(0.05), 0.1);
		GoodParams unsafe = new GoodParams(100, 5, 0.05, 8, 16, 4, 0.25, 3.0);
		assertFalse(PriceModel.isExploitSafe(unsafe));
		assertTrue(PriceModel.isExploitSafe(PriceModel.ensureExploitSafe(unsafe)));
	}

	@Test
	void damagedItemsSellCheaper() {
		// Full price for plain items equals the normal sum.
		assertEquals(PriceModel.sumSell(SWORD, 0, 3), PriceModel.sumSell(SWORD, 0, new double[]{1, 1, 1}));
		// 720 + floor(676 × 0.5) = 720 + 338
		assertEquals(1058, PriceModel.sumSell(SWORD, 0, new double[]{1, 0.5}));
		// Never below 1c per item.
		GoodParams cheap = new GoodParams(2, 128, 0.4, 64, 128, 32, 0.25, 3.0);
		assertEquals(1, PriceModel.sumSell(cheap, 0, new double[]{0.01}));
	}

	@Test
	void maxAffordable() {
		assertEquals(0, PriceModel.maxAffordable(BREAD, 0, 23, 64));
		assertEquals(1, PriceModel.maxAffordable(BREAD, 0, 24, 64));
		assertEquals(16, PriceModel.maxAffordable(BREAD, 0, 497, 16));
		assertEquals(15, PriceModel.maxAffordable(BREAD, 0, 496, 64));
	}

	/** A-4: a full market (S = Scap = 24) takes items again once consumption (c = r/2 = 2 per day) brings S to 23. */
	@Test
	void daysUntilBuyingAgain() {
		assertEquals(0.5, PriceModel.daysUntilBuying(SWORD, new GoodState(1, 0, 24, 0), 1.0), 1e-9);
		assertEquals(0.25, PriceModel.daysUntilBuying(SWORD, new GoodState(1, 0, 23.5, 0), 1.0), 1e-9);
		assertEquals(0.0, PriceModel.daysUntilBuying(SWORD, new GoodState(1, 0, 20, 0), 1.0));
	}

	private static final BulkDiscount BULK = new BulkDiscount(java.util.List.of(new BulkDiscount.Tier(64, 0.10), new BulkDiscount.Tier(16, 0.05)));

	/** B-5 (v0.27): 16 items or more 5% off, 64 or more 10% off; fewer items pay the plain total. */
	@Test
	void bulkDiscountTiers() {
		assertEquals(PriceModel.sumBuy(BREAD, 0, 15), PriceModel.sumBuy(BREAD, 0, 15, 1, BULK));
		assertEquals((long) Math.ceil(PriceModel.sumBuy(BREAD, 0, 16) * 0.95 - 1e-9), PriceModel.sumBuy(BREAD, 0, 16, 1, BULK));
		assertEquals((long) Math.ceil(PriceModel.sumBuy(BREAD, 0, 64) * 0.90 - 1e-9), PriceModel.sumBuy(BREAD, 0, 64, 1, BULK));
		// Tiers count items, not trading units: 4 units of 16 reach the 64-item tier.
		assertEquals(0.10, BULK.rate(BREAD, 4, 16), 1e-9);
	}

	/** Buying with the largest discount and selling straight back never profits (B-6). */
	@Test
	void bulkDiscountIsCappedAgainstRoundTrips() {
		GoodParams thin = new GoodParams(20, 200, 0.05, 64, 128, 8, 0.25, 3.0); // small spread: break-even below 10%
		assertTrue(BulkDiscount.safeRate(thin) < 0.10);
		for (GoodParams p : new GoodParams[] {thin, BREAD, SWORD}) {
			long paid = PriceModel.sumBuy(p, 0, 64, 1, BULK);
			long back = PriceModel.sumSell(p, p.clampPressure(-64), 64);
			assertTrue(back < paid, "round trip profits for " + p);
		}
	}

	/** maxAffordable with a discount = the largest n whose discounted total fits, checked by brute force. */
	@Test
	void maxAffordableWithDiscount() {
		for (GoodParams p : new GoodParams[] {BREAD, SWORD}) {
			for (long funds : new long[] {0, 100, 500, 1_000, 5_000, 20_000, 100_000}) {
				int expected = 0;
				for (int n = 1; n <= 64; n++) {
					if (PriceModel.sumBuy(p, 0, n, 1, BULK) <= funds) expected = n;
				}
				assertEquals(expected, PriceModel.maxAffordable(p, 0, funds, 64, 1, BULK), "funds " + funds);
			}
		}
	}
}
