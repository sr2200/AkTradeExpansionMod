package com.akf.tradeexpansion.core;

/**
 * Numbers of the rival guilds (design O-3). Minecraft-independent.
 */
public final class RivalMath {
	private RivalMath() {
	}

	/** Fraction of X left after one day: f = 2^(-1 / H). */
	public static double dailyRetention(double halfLifeDays) {
		return Math.pow(2.0, -1.0 / halfLifeDays);
	}

	/**
	 * Push of X at the center market at the start of each campaign day: d = τ · ln(1 + m) · (1 − f) / (1 − fⁿ), so that
	 * after the n-th push X = τ · ln(1 + m), i.e. the price multiplier is exactly (1 + m) (buying) or 1 / (1 + m) (selling).
	 *
	 * @param impact m (≥ 0)
	 * @param days   n, the campaign length (≥ 1)
	 */
	public static double dailyPush(double elasticity, double halfLifeDays, double impact, int days) {
		if (impact <= 0) return 0;
		double f = dailyRetention(halfLifeDays);
		int n = Math.max(1, days);
		return elasticity * Math.log(1 + impact) * (1 - f) / (1 - Math.pow(f, n));
	}

	/** What is left now of a push made {@code ageDays} ago: 2^(-age / H) (B-3, X recovers linearly). */
	public static double decay(double ageDays, double halfLifeDays) {
		return Math.pow(2.0, -Math.max(0.0, ageDays) / halfLifeDays);
	}

	/**
	 * X at the end of day {@code k} (0-based, right after the (k+1)-th push and before any recovery) of a campaign of
	 * {@code days} days, as the sum of the pushes so far; for tests and the design check.
	 */
	public static double pressureAfter(double elasticity, double halfLifeDays, double impact, int days, int k) {
		double d = dailyPush(elasticity, halfLifeDays, impact, days);
		double x = 0;
		for (int j = 0; j <= k; j++) x += d * decay(k - j, halfLifeDays);
		return x;
	}
}
