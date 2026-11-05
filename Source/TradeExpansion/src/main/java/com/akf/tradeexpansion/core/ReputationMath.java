package com.akf.tradeexpansion.core;

/**
 * Reputation of a player with a market (design L-2 / M, Q39). Minecraft-independent.
 */
public final class ReputationMath {
	/** The bonus keeps the round trip "buy, then sell straight back" at least this much short of break-even (B-6). */
	private static final double MARGIN = 1.02;

	private ReputationMath() {
	}

	/** Points after {@code days} of decay with half-life {@code halfLifeDays} (0 or less = no decay). */
	public static double decay(double points, double days, double halfLifeDays) {
		if (days <= 0 || halfLifeDays <= 0) return points;
		return points * Math.pow(2.0, -days / halfLifeDays);
	}

	/** Level 0..thresholds.length: the number of thresholds reached. */
	public static int level(double points, int[] thresholds) {
		int level = 0;
		for (int threshold : thresholds) {
			if (points >= threshold) level++;
		}
		return level;
	}

	/** Points needed for the next level, or -1 at the top. */
	public static int nextThreshold(int level, int[] thresholds) {
		return level < thresholds.length ? thresholds[level] : -1;
	}

	/**
	 * Largest price bonus d (buy × (1 − d), sell × (1 + d)) that keeps the good exploit-safe (B-6): the ratio of buy to
	 * sell price must stay above exp(1 / tau) with a small margin.
	 */
	public static double safeBonus(GoodParams p) {
		double ratio = (1 + p.spread() / 2) / (1 - p.spread() / 2);
		double needed = Math.exp(1 / p.elasticity()) * MARGIN;
		if (ratio <= needed) return 0;
		double k = needed / ratio;
		return (1 - k) / (1 + k);
	}

	/**
	 * The good's params as seen by a player with price bonus {@code bonus}: buy prices × (1 − d), sell prices × (1 + d),
	 * d capped by {@link #safeBonus}. Expressed as a new base price B' and spread s' so every price formula (sums, bulk
	 * discount, maximum quantity) works unchanged: B'(1 + s'/2) = B(1 + s/2)(1 − d), B'(1 − s'/2) = B(1 − s/2)(1 + d).
	 */
	public static GoodParams withBonus(GoodParams p, double bonus) {
		double d = Math.min(bonus, safeBonus(p));
		if (d <= 0) return p;
		double up = (1 + p.spread() / 2) * (1 - d);
		double down = (1 - p.spread() / 2) * (1 + d);
		double mid = (up + down) / 2;
		return new GoodParams(p.basePrice() * mid, p.elasticity(), (up - down) / mid, p.targetStock(), p.maxStock(), p.restockPerDay(),
				p.minMultiplier(), p.maxMultiplier(), p.consumePerDay(), p.halfLifeDays());
	}
}
