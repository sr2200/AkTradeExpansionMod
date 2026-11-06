package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Minting (design E-2, Q20): the recipe is defined by copper value, the coin count follows the ratios. */
class MintingCountTest {
	/** Iron block × 9 = 640c, copper block × 9 = 64c (bundled recipes). */
	private static final long IRON_VALUE = 640;
	private static final long COPPER_VALUE = 64;

	private static long ironCoin(int ratio) {
		return CoinMath.values(new int[] {ratio, 10, 10, 10})[1];
	}

	@Test
	void ratio10Gives64() {
		assertEquals(64, CoinMath.mintCount(IRON_VALUE, ironCoin(10)));
		assertEquals(64, CoinMath.mintCount(COPPER_VALUE, 1));
	}

	@Test
	void ratio20Gives32() {
		assertEquals(32, CoinMath.mintCount(IRON_VALUE, ironCoin(20)));
	}

	/** A smaller ratio would give more than a stack: capped at 64 (the extra value is lost). */
	@Test
	void cappedAtOneStack() {
		assertEquals(64, CoinMath.mintCount(IRON_VALUE, ironCoin(5)));   // 128 → 64
		assertEquals(64, CoinMath.mintCount(IRON_VALUE, ironCoin(2)));   // 320 → 64
	}

	/** Rounded down, and never less than one coin. */
	@Test
	void roundedDownAtLeastOne() {
		assertEquals(21, CoinMath.mintCount(IRON_VALUE, ironCoin(30)));  // 640 / 30 = 21.3
		assertEquals(6, CoinMath.mintCount(IRON_VALUE, ironCoin(100)));  // 640 / 100 = 6.4
		assertEquals(1, CoinMath.mintCount(COPPER_VALUE, ironCoin(100))); // 64 / 100 = 0.64 → still 1 coin
	}
}
