package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketLevelTest {
	private static final int[] BEDS = {5, 10, 20, 40};
	private static final int[] VILLAGERS = {3, 6, 12, 24};

	@Test
	void bedThresholds() {
		assertEquals(1, MarketLevel.level(0, BEDS));
		assertEquals(1, MarketLevel.level(4, BEDS));
		assertEquals(2, MarketLevel.level(5, BEDS));
		assertEquals(2, MarketLevel.level(9, BEDS));
		assertEquals(3, MarketLevel.level(10, BEDS));
		assertEquals(3, MarketLevel.level(19, BEDS));
		assertEquals(4, MarketLevel.level(39, BEDS));
		assertEquals(5, MarketLevel.level(40, BEDS));
		assertEquals(5, MarketLevel.level(1000, BEDS));
	}

	@Test
	void villagerThresholds() {
		assertEquals(1, MarketLevel.level(2, VILLAGERS));
		assertEquals(2, MarketLevel.level(3, VILLAGERS));
		assertEquals(4, MarketLevel.level(23, VILLAGERS));
		assertEquals(5, MarketLevel.level(24, VILLAGERS));
	}

	@Test
	void goodsCountTable() {
		int[] expected = {10, 13, 15, 18, 20};
		for (int level = 1; level <= 5; level++) {
			assertEquals(expected[level - 1], MarketLevel.goodsCount(level, 10, 20), "level " + level);
		}
	}

	@Test
	void goodsCountWithOtherSettings() {
		assertEquals(5, MarketLevel.goodsCount(1, 5, 5));
		assertEquals(5, MarketLevel.goodsCount(5, 5, 5));
		assertEquals(20, MarketLevel.goodsCount(3, 10, 30));
		assertEquals(10, MarketLevel.goodsCount(0, 10, 20));
		assertEquals(20, MarketLevel.goodsCount(9, 10, 20));
	}
}
