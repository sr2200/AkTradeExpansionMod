package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Goods traded {@code unit} items at a time (design B-5, D-2 {@code unit}), e.g. the emerald coin (10 per unit): prices,
 * stock, X and quantities count units; only the item moves are unit × n.
 */
class TradeUnitTest {
	private static final int UNIT = 10;
	/** Emerald coin with the old (moving) price: B = 5 per 10, τ = 128, T = 0, Scap = 64 units. */
	private static final GoodParams EMERALD = new GoodParams(5, 128, 0.4, 0, 64, 64, 0.25, 3.0);
	/** A buyable good sold 10 at a time: B = 20 per 10. */
	private static final GoodParams BUNDLE = new GoodParams(20, 64, 0.4, 16, 32, 8, 0.25, 3.0);

	/** Only whole units count: 19 items are 1 unit, 9 items none. */
	@Test
	void remainderIsNotCounted() {
		assertEquals(1, PriceModel.units(19, UNIT));
		assertEquals(0, PriceModel.units(9, UNIT));
		assertEquals(2, PriceModel.units(20, UNIT));
		assertEquals(19, PriceModel.units(19, 1)); // unit 1 = items
	}

	/** The total of n units is the sum of the per-unit prices, X rising by 1 per unit (not per item). */
	@Test
	void totalCountsUnits() {
		assertEquals(4, PriceModel.sellPrice(EMERALD, 0)); // 4c per 10 coins
		long expected = 0;
		for (int k = 0; k < 3; k++) expected += PriceModel.sellPrice(EMERALD, k);
		assertEquals(expected, PriceModel.sumSell(EMERALD, 0, 3));
		GoodState state = new GoodState(1, 0, 0, 0);
		PriceModel.applySell(EMERALD, state, 3);
		assertEquals(3, state.pressure(), 1e-9); // 30 coins = 3 units = X + 3
		assertEquals(3, state.stock(), 1e-9);
	}

	/** Selling stops at Scap units: with 60 of 64 units in stock, 4 units (40 items) can still be sold. */
	@Test
	void capacityCountsUnits() {
		assertEquals(4, PriceModel.sellCapacity(EMERALD, new GoodState(1, 0, 60, 0)));
		assertEquals(0, PriceModel.sellCapacity(EMERALD, new GoodState(1, 0, 64, 0)));
	}

	/** The largest affordable quantity is in units; the bulk discount tiers count items (units × unit). */
	@Test
	void maxQuantityAndBulkDiscount() {
		// The price rises with each unit bought (X − 1 per unit), so 3 units cost the sum of 3 rising prices.
		long threeUnits = PriceModel.sumBuy(BUNDLE, 0, 3);
		assertEquals(3, PriceModel.maxAffordable(BUNDLE, 0, threeUnits, 64));
		assertEquals(2, PriceModel.maxAffordable(BUNDLE, 0, threeUnits - 1, 64));
		BulkDiscount bulk = new BulkDiscount(List.of(new BulkDiscount.Tier(16, 0.05), new BulkDiscount.Tier(64, 0.10)));
		assertEquals(0.0, bulk.rate(BUNDLE, 1, UNIT), 1e-9);   // 10 items
		assertEquals(0.05, bulk.rate(BUNDLE, 2, UNIT), 1e-9);  // 20 items ≥ 16
		assertEquals(0.10, bulk.rate(BUNDLE, 7, UNIT), 1e-9);  // 70 items ≥ 64
	}
}
