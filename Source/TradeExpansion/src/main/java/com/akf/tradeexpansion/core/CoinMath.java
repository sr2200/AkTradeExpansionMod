package com.akf.tradeexpansion.core;

/**
 * Coin arithmetic of design E-3. Minecraft-independent.
 * Coins are indexed from the smallest (0 = copper) to the largest.
 */
public final class CoinMath {
	public static final int MIN_RATIO = 2;
	public static final int MAX_RATIO = 100;

	private CoinMath() {
	}

	/**
	 * Copper value of every coin tier: value[0] = 1, value[i] = ratios[0] * ... * ratios[i-1].
	 *
	 * @param ratios conversion ratio between neighbouring tiers, each in [2, 100]
	 */
	public static long[] values(int[] ratios) {
		long[] values = new long[ratios.length + 1];
		values[0] = 1;
		for (int i = 0; i < ratios.length; i++) {
			if (ratios[i] < MIN_RATIO || ratios[i] > MAX_RATIO) {
				throw new IllegalArgumentException("ratio[" + i + "] must be in [" + MIN_RATIO + ", " + MAX_RATIO + "]: " + ratios[i]);
			}
			values[i + 1] = values[i] * ratios[i];
		}
		return values;
	}

	/**
	 * Minting (E-2, Q20): coins made from a recipe worth {@code value} copper = value ÷ coin value, rounded down, at least 1
	 * and at most one stack (64).
	 */
	public static int mintCount(long value, long coinValue) {
		return (int) Math.clamp(value / Math.max(1, coinValue), 1, 64);
	}

	/** Total copper value of the given coin counts. */
	public static long total(long[] counts, long[] values) {
		long sum = 0;
		for (int i = 0; i < values.length; i++) {
			sum = Math.addExact(sum, Math.multiplyExact(counts[i], values[i]));
		}
		return sum;
	}

	/**
	 * Splits an amount into coins, largest first. Since every value is an integer multiple of the next
	 * smaller one, this yields the minimum number of coins.
	 */
	public static long[] decompose(long amount, long[] values) {
		if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
		long[] counts = new long[values.length];
		long rest = amount;
		for (int i = values.length - 1; i >= 0; i--) {
			counts[i] = rest / values[i];
			rest -= counts[i] * values[i];
		}
		return counts;
	}

	/**
	 * Payment by "collect all coins, return the change" (E-3).
	 *
	 * @return coin counts to give back after removing every held coin, or {@code null} if the funds are insufficient
	 */
	public static long[] change(long[] held, long price, long[] values) {
		if (price < 0) throw new IllegalArgumentException("price must be >= 0");
		long funds = total(held, values);
		if (funds < price) return null;
		return decompose(funds - price, values);
	}
}
