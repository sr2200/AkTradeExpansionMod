package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CoinMathTest {
	private static final long[] DEFAULT = CoinMath.values(new int[]{10, 10, 10, 10});

	@Test
	void values() {
		assertArrayEquals(new long[]{1, 10, 100, 1000, 10000}, DEFAULT);
		assertArrayEquals(new long[]{1, 2, 4, 8, 16}, CoinMath.values(new int[]{2, 2, 2, 2}));
		assertArrayEquals(new long[]{1, 100, 10_000, 1_000_000, 100_000_000}, CoinMath.values(new int[]{100, 100, 100, 100}));
		assertArrayEquals(new long[]{1, 8, 64, 640, 1280}, CoinMath.values(new int[]{8, 8, 10, 2}));
	}

	@Test
	void ratioOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> CoinMath.values(new int[]{1, 10, 10, 10}));
		assertThrows(IllegalArgumentException.class, () -> CoinMath.values(new int[]{10, 10, 10, 101}));
	}

	@Test
	void decompose() {
		// 5,517c = diamond 5, gold 5, iron 1, copper 7 (design 3-1)
		assertArrayEquals(new long[]{7, 1, 5, 5, 0}, CoinMath.decompose(5517, DEFAULT));
		assertArrayEquals(new long[]{0, 0, 0, 0, 0}, CoinMath.decompose(0, DEFAULT));
		assertArrayEquals(new long[]{0, 0, 0, 0, 12}, CoinMath.decompose(120_000, DEFAULT));
	}

	@Test
	void payWithTwoDiamondCoins() {
		// 2 diamond coins pay 1,080c: change 920c = gold 9, iron 2 (design E-3)
		long[] change = CoinMath.change(new long[]{0, 0, 0, 2, 0}, 1080, DEFAULT);
		assertArrayEquals(new long[]{0, 2, 9, 0, 0}, change);
	}

	@Test
	void insufficientFunds() {
		assertNull(CoinMath.change(new long[]{9, 9, 9, 0, 0}, 1000, DEFAULT));
		assertNotNull(CoinMath.change(new long[]{10, 9, 9, 0, 0}, 1000, DEFAULT));
	}

	@Test
	void negativeAmountsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> CoinMath.decompose(-1, DEFAULT));
		assertThrows(IllegalArgumentException.class, () -> CoinMath.change(new long[5], -1, DEFAULT));
	}

	/** Totals that do not fit in a long fail loudly instead of wrapping around to a small or negative amount. */
	@Test
	void overflowIsDetected() {
		long[] huge = CoinMath.values(new int[] {100, 100, 100, 100});
		assertThrows(ArithmeticException.class, () -> CoinMath.total(new long[] {0, 0, 0, 0, Long.MAX_VALUE / 2}, huge));
		assertThrows(ArithmeticException.class, () -> CoinMath.total(new long[] {Long.MAX_VALUE, 0, 0, 0, 1}, DEFAULT));
	}

	/** Paying exactly what is held leaves no change; a price of 0 gives everything back as the fewest coins. */
	@Test
	void exactAndFreePayments() {
		assertArrayEquals(new long[5], CoinMath.change(new long[] {7, 1, 5, 5, 0}, 5517, DEFAULT));
		assertArrayEquals(new long[] {0, 0, 0, 0, 1}, CoinMath.change(new long[] {0, 0, 0, 10, 0}, 0, DEFAULT));
	}

	@Test
	void randomPaymentsConserveValue() {
		Random random = new Random(12345);
		int[][] ratioSets = {{10, 10, 10, 10}, {2, 2, 2, 2}, {100, 100, 100, 100}, {8, 8, 10, 2}, {64, 3, 50, 7}};
		for (int i = 0; i < 10_000; i++) {
			long[] values = CoinMath.values(ratioSets[i % ratioSets.length]);
			long[] held = new long[values.length];
			for (int c = 0; c < held.length; c++) held[c] = random.nextInt(200);
			long funds = CoinMath.total(held, values);
			long price = funds == 0 ? 0 : (long) (random.nextDouble() * funds * 1.2);
			long[] change = CoinMath.change(held, price, values);
			if (price > funds) {
				assertNull(change);
				continue;
			}
			assertNotNull(change);
			assertEquals(price, funds - CoinMath.total(change, values));
			// Greedy change keeps every non-top tier below its ratio.
			for (int c = 0; c < values.length - 1; c++) {
				assertTrue(change[c] < values[c + 1] / values[c]);
			}
		}
	}
}
