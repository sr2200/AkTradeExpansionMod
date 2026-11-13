package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffMathTest {
	/** B = 100c, tau = 16, s = 0.4: buy 120c and sell 80c at X = 0. */
	private static final GoodParams P = new GoodParams(100, 16, 0.4, 16, 32, 4, 0.25, 4.0);

	@Test
	void travelTimeFollowsTheDistance() {
		assertEquals(12_000, StaffMath.travelTicks(1000, 2000)); // half a day
		assertEquals(24_000, StaffMath.travelTicks(2000, 2000));
		assertEquals(StaffMath.MIN_TRAVEL_TICKS, StaffMath.travelTicks(10, 2000)); // at least one minute
		assertEquals(StaffMath.MIN_TRAVEL_TICKS, StaffMath.travelTicks(1000, 0));
	}

	@Test
	void harborMultiplierShortensTheTripButNotBelowTheMinimum() {
		assertEquals(18000, StaffMath.travelTicks(1000, 1000, 0.75)); // 24000 × 0.75
		assertEquals(24000, StaffMath.travelTicks(1000, 1000, 1.0));
		assertEquals(24000, StaffMath.travelTicks(1000, 1000, 1.5)); // never slower
		assertEquals(StaffMath.MIN_TRAVEL_TICKS, StaffMath.travelTicks(60, 1000, 0.1));
	}

	@Test
	void accidentChanceGrowsWithDistanceUpToTheCap() {
		assertEquals(0.03, StaffMath.accidentChance(0, 0.03, 0.04, 0.2), 1e-12);
		assertEquals(0.07, StaffMath.accidentChance(1000, 0.03, 0.04, 0.2), 1e-12);
		assertEquals(0.2, StaffMath.accidentChance(10_000, 0.03, 0.04, 0.2), 1e-12);
		assertEquals(0.0, StaffMath.accidentChance(1000, 0, 0, 0.2), 1e-12);
		assertEquals(1.0, StaffMath.accidentChance(1000, 2, 0, 5), 1e-12); // never above 100%
	}

	@Test
	void lostUnitsRoundUpAndStayWithinTheCargo() {
		assertEquals(0, StaffMath.lostUnits(0, 0.5));
		assertEquals(1, StaffMath.lostUnits(10, 0.01)); // something is always lost
		assertEquals(3, StaffMath.lostUnits(10, 0.25)); // 2.5 → 3
		assertEquals(10, StaffMath.lostUnits(10, 1.0));
		assertEquals(10, StaffMath.lostUnits(10, 1.7));
	}

	@Test
	void buyingStopsAtThePriceLimit() {
		long first = PriceModel.buyPrice(P, 0);
		assertEquals(120, first);
		assertEquals(0, StaffMath.buyableWithin(P, 0, 64, first - 1));
		// Each unit bought makes the next one dearer; the count matches the prices one by one.
		int n = StaffMath.buyableWithin(P, 0, 64, 130);
		assertTrue(n > 0 && n < 64);
		assertTrue(PriceModel.buyPrice(P, -(n - 1)) <= 130);
		assertTrue(PriceModel.buyPrice(P, -n) > 130);
		assertEquals(5, StaffMath.buyableWithin(P, 0, 5, 1_000_000)); // capped by the count
	}

	@Test
	void sellingStopsAtThePriceLimit() {
		long first = PriceModel.sellPrice(P, 0);
		assertEquals(80, first);
		assertEquals(0, StaffMath.sellableWithin(P, 0, 64, first + 1));
		int n = StaffMath.sellableWithin(P, 0, 64, 75);
		assertTrue(n > 0 && n < 64);
		assertTrue(PriceModel.sellPrice(P, n - 1) >= 75);
		assertTrue(PriceModel.sellPrice(P, n) < 75);
		assertEquals(64, StaffMath.sellableWithin(P, 0, 64, 1)); // any price
	}

	@Test
	void wholesaleSpread() {
		GoodParams half = StaffMath.withSpread(P, 0.5);
		assertEquals(0.2, half.spread(), 1e-12);
		assertEquals(110, PriceModel.buyPrice(half, 0));
		assertEquals(90, PriceModel.sellPrice(half, 0));
	}

	@Test
	void carrierBuysOnlyWhatShouldSellForMore() {
		GoodParams home = StaffMath.withSpread(P, 0.5);
		GoodParams same = StaffMath.withSpread(P, 0.5);
		// Same base price at both ends: buying at 110 to sell at 90 never pays.
		assertEquals(0, StaffMath.planBuy(home, 0, same, 0, 64, 1000, 1_000_000, 0.25, 1.0).units());
		// Twice the base price at the destination: buys until the margin is gone, cheaper units first.
		GoodParams rich = StaffMath.withSpread(new GoodParams(200, 16, 0.4, 16, 32, 4, 0.25, 4.0), 0.5);
		StaffMath.BuyPlan plan = StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 0.25, 1.0);
		assertTrue(plan.units() > 0 && plan.units() < 64);
		assertTrue(plan.expectedSales() > plan.total());
		assertEquals(110, StaffMath.planBuy(home, 0, rich, 0, 1, 1000, 1_000_000, 0.25, 1.0).highestPrice());
		// Limits: the count, the price limit and the purse.
		assertEquals(3, StaffMath.planBuy(home, 0, rich, 0, 3, 1000, 1_000_000, 0.25, 1.0).units());
		assertEquals(0, StaffMath.planBuy(home, 0, rich, 0, 64, 109, 1_000_000, 0.25, 1.0).units());
		assertEquals(2, StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 225, 0.25, 1.0).units());
		// A smaller step lets more units through before the margin is gone.
		assertTrue(StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 1.0, 1.0).units() < plan.units());
	}

	/** Expected losses on the road (keep &lt; 1) make the carrier stop earlier; nothing worth carrying when nothing arrives. */
	@Test
	void expectedLossesBuyLess() {
		GoodParams home = StaffMath.withSpread(P, 0.5);
		GoodParams rich = StaffMath.withSpread(new GoodParams(200, 16, 0.4, 16, 32, 4, 0.25, 4.0), 0.5);
		int safe = StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 0.25, 1.0).units();
		int risky = StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 0.25, 0.8).units();
		assertTrue(risky > 0 && risky < safe, "safe " + safe + ", risky " + risky);
		assertEquals(0, StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 0.25, 0.0).units());
		// The first unit: buy 110, sell 180 at the destination; 180 × 0.61 = 109.8 is not more than 110.
		assertEquals(0, StaffMath.planBuy(home, 0, rich, 0, 64, 1000, 1_000_000, 0.25, 0.61).units());
		assertEquals(StaffMath.BuyPlan.NONE, StaffMath.planBuy(home, 0, rich, 0, 0, 1000, 1_000_000, 0.25, 1.0));
	}

	@Test
	void sellPlanStopsAtTheLimit() {
		StaffMath.SellPlan all = StaffMath.planSell(P, 0, 10, 1, 1.0);
		assertEquals(10, all.units());
		assertEquals(PriceModel.sumSell(P, 0, 10), all.total());
		assertEquals(0, StaffMath.planSell(P, 0, 10, 81, 1.0).units());
		assertTrue(StaffMath.planSell(P, 0, 64, 75, 0.25).units() > StaffMath.planSell(P, 0, 64, 75, 1.0).units());
	}

	@Test
	void profitAndCommission() {
		StaffMath.BuyPlan plan = new StaffMath.BuyPlan(4, 400, 110, 600);
		assertEquals(160, StaffMath.expectedProfit(plan, 0, 0, 0.2)); // 200 × 0.8
		assertEquals(Math.round((600 * 0.98 - 400) * 0.8), StaffMath.expectedProfit(plan, 0.1, 0.2, 0.2));
		assertEquals(-100, StaffMath.expectedProfit(new StaffMath.BuyPlan(1, 200, 200, 100), 0, 0, 0.2)); // no commission on a loss
		assertEquals(0, StaffMath.expectedProfit(StaffMath.BuyPlan.NONE, 0.1, 0.2, 0.2));
		assertEquals(20, StaffMath.commission(100, 0.2));
		assertEquals(0, StaffMath.commission(-50, 0.2));
	}

	@Test
	void costShare() {
		assertEquals(0, StaffMath.shareOf(1000, 0, 10));
		assertEquals(250, StaffMath.shareOf(1000, 5, 20));
		assertEquals(1000, StaffMath.shareOf(1000, 20, 20));
		assertEquals(333, StaffMath.shareOf(1000, 1, 3));
		assertEquals(0, StaffMath.shareOf(1000, 5, 0));
	}

	@Test
	void priceStep() {
		assertEquals(1, StaffMath.priceStep(20, false));
		assertEquals(2, StaffMath.priceStep(20, true));
		assertEquals(9, StaffMath.priceStep(900, false));
		assertEquals(90, StaffMath.priceStep(900, true));
		assertEquals(1, StaffMath.priceStep(0, true));
	}
}
