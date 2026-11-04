package com.akf.tradeexpansion.core;

import java.util.Comparator;
import java.util.List;

/**
 * Bulk discount on buying (design B-5, v0.27): buying at least {@code minQuantity} items in one trade takes {@code rate}
 * off the total. Minecraft-independent. Selling gets no bonus.
 *
 * @param tiers sorted by {@code minQuantity}; the largest tier reached applies
 */
public record BulkDiscount(List<Tier> tiers) {
	public static final BulkDiscount NONE = new BulkDiscount(List.of());

	/** Safety margin below the break-even discount (rounding of the per-item prices). */
	private static final double SAFETY = 0.9;

	public record Tier(int minQuantity, double rate) {
	}

	public BulkDiscount {
		tiers = tiers.stream().sorted(Comparator.comparingInt(Tier::minQuantity)).toList();
	}

	/** Discount for buying {@code items} items (not trading units) in one trade, before the safety cap. */
	public double rate(int items) {
		double rate = 0;
		for (Tier tier : tiers) {
			if (items >= tier.minQuantity()) rate = tier.rate();
		}
		return rate;
	}

	/**
	 * Largest discount that keeps "buy n, then sell them straight back" from profiting: the sell-back gets the buy prices
	 * shifted by one step of X, i.e. the total times (1 - s/2) / (1 + s/2) * exp(1 / tau) (B-6).
	 */
	public static double safeRate(GoodParams p) {
		double ratio = (1 - p.spread() / 2) / (1 + p.spread() / 2) * Math.exp(1 / p.elasticity());
		return Math.max(0, (1 - ratio) * SAFETY);
	}

	/** The discount applied to buying {@code units} trading units of {@code unit} items each of good {@code p}. */
	public double rate(GoodParams p, int units, int unit) {
		return Math.min(rate(units * unit), safeRate(p));
	}
}
