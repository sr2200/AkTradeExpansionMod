package com.akf.tradeexpansion.core;

/**
 * Price formulas of design chapter B. Minecraft-independent, all prices are copper units (long).
 */
public final class PriceModel {
	/** One in-game day in ticks. */
	public static final long TICKS_PER_DAY = 24000L;

	/** Guards ceil/floor against binary floating point error (e.g. 20 * 1.2 = 24.000000000000004). */
	private static final double ROUNDING_EPSILON = 1e-9;

	private PriceModel() {
	}

	/** V = clamp(exp(-X / tau), Vmin, Vmax) */
	public static double multiplier(GoodParams p, double pressure) {
		return Math.clamp(Math.exp(-pressure / p.elasticity()), p.minMultiplier(), p.maxMultiplier());
	}

	/** Pbuy = max(1, ceil(B * V * (1 + s/2))): the price the player pays. */
	public static long buyPrice(GoodParams p, double pressure) {
		double raw = p.basePrice() * multiplier(p, pressure) * (1 + p.spread() / 2);
		return Math.max(1L, (long) Math.ceil(raw - ROUNDING_EPSILON));
	}

	/** Psell = max(1, floor(B * V * (1 - s/2))): the price the player receives. */
	public static long sellPrice(GoodParams p, double pressure) {
		double raw = p.basePrice() * multiplier(p, pressure) * (1 - p.spread() / 2);
		return Math.max(1L, (long) Math.floor(raw + ROUNDING_EPSILON));
	}

	/** Total for selling n items one by one; X rises by 1 per item (B-5). */
	public static long sumSell(GoodParams p, double pressure, int n) {
		long total = 0;
		double x = pressure;
		for (int k = 0; k < n; k++) {
			total += sellPrice(p, x);
			x = p.clampPressure(x + 1);
		}
		return total;
	}

	/**
	 * Selling with reduced prices for damaged items (A-4): item k is paid floor(Psell(X + k) × ratios[k]), at least 1c.
	 * Ratios are in selling order (plain items first = 1.0, then by remaining durability).
	 */
	public static long sumSell(GoodParams p, double pressure, double[] ratios) {
		long total = 0;
		double x = pressure;
		for (double ratio : ratios) {
			long full = sellPrice(p, x);
			total += ratio >= 1.0 ? full : Math.max(1L, (long) Math.floor(full * ratio + ROUNDING_EPSILON));
			x = p.clampPressure(x + 1);
		}
		return total;
	}

	/** Total for buying n items one by one; X falls by 1 per item (B-5). */
	public static long sumBuy(GoodParams p, double pressure, int n) {
		long total = 0;
		double x = pressure;
		for (int k = 0; k < n; k++) {
			total += buyPrice(p, x);
			x = p.clampPressure(x - 1);
		}
		return total;
	}

	/** Largest n with sumBuy(n) &lt;= funds, capped by maxCount. */
	public static int maxAffordable(GoodParams p, double pressure, long funds, int maxCount) {
		long total = 0;
		double x = pressure;
		for (int k = 0; k < maxCount; k++) {
			total += buyPrice(p, x);
			if (total > funds) return k;
			x = p.clampPressure(x - 1);
		}
		return maxCount;
	}

	/** Applies the result of selling n items to the state (X += n, S += n). */
	public static void applySell(GoodParams p, GoodState s, int n) {
		s.setPressure(p.clampPressure(s.pressure() + n));
		s.setStock(s.stock() + n);
	}

	/** Applies the result of buying n items to the state (X -= n, S -= n). */
	public static void applyBuy(GoodParams p, GoodState s, int n) {
		s.setPressure(p.clampPressure(s.pressure() - n));
		s.setStock(s.stock() - n);
	}

	/**
	 * Lazy recovery (B-3).
	 *
	 * @param elapsedDays  in-game days since the last update; negative values (DAY_TIME clock going back) count as 0
	 * @param halfLifeDays H
	 * @param restockMultiplier global multiplier of r and c
	 */
	public static void recover(GoodParams p, GoodState s, double elapsedDays, double restockMultiplier) {
		recover(p, s, elapsedDays, p.halfLifeDays(), restockMultiplier);
	}

	/** As {@link #recover(GoodParams, GoodState, double, double)} with an explicit half-life. */
	public static void recover(GoodParams p, GoodState s, double elapsedDays, double halfLifeDays, double restockMultiplier) {
		double dt = Math.max(0.0, elapsedDays);
		if (dt == 0.0) return;

		s.setPressure(s.pressure() * Math.pow(2.0, -dt / halfLifeDays));

		double target = p.targetStock();
		double stock = s.stock();
		if (stock < target) {
			s.setStock(Math.min(target, stock + p.restockPerDay() * restockMultiplier * dt));
		} else {
			// Excess stock is consumed by the market (r / 2 by default, faster for consumables).
			s.setStock(Math.max(target, stock - p.consumePerDay() * restockMultiplier * dt));
		}
	}

	public static boolean canBuy(GoodState s) {
		return s.stock() >= 1.0;
	}

	public static boolean canSell(GoodParams p, GoodState s) {
		return s.stock() < p.maxStock();
	}

	/** Number of items the market can still take before reaching Scap. */
	public static int sellCapacity(GoodParams p, GoodState s) {
		return (int) Math.max(0, Math.floor(p.maxStock() - s.stock()));
	}

	/** In-game days until the next item is in stock: (1 - S) / r. 0 if already in stock. */
	public static double daysUntilRestock(GoodParams p, GoodState s, double restockMultiplier) {
		if (canBuy(s)) return 0.0;
		double r = p.restockPerDay() * restockMultiplier;
		return r <= 0 ? Double.POSITIVE_INFINITY : (1.0 - s.stock()) / r;
	}

	/**
	 * Smallest tau for which an immediate sell-then-buy round trip never profits (B-6):
	 * (1 + s/2) / (1 - s/2) &gt; exp(1 / tau).
	 */
	public static double minSafeElasticity(double spread) {
		double ratio = (1 + spread / 2) / (1 - spread / 2);
		if (ratio <= 1.0) return Double.POSITIVE_INFINITY;
		return 1.0 / Math.log(ratio);
	}

	public static boolean isExploitSafe(GoodParams p) {
		return p.elasticity() > minSafeElasticity(p.spread());
	}

	/** Returns params whose tau satisfies B-6 (unchanged if already safe). */
	public static GoodParams ensureExploitSafe(GoodParams p) {
		if (isExploitSafe(p)) return p;
		return p.withElasticity(minSafeElasticity(p.spread()) * 1.01);
	}
}
