package com.akf.tradeexpansion.core;

/**
 * Definition values of one good in one market (design B-1). Minecraft-independent.
 *
 * @param basePrice     B: base price in copper (already multiplied by profile / jitter / global factors)
 * @param elasticity    tau: pressure at which value drops to 1/e
 * @param spread        s: buy/sell spread ratio
 * @param targetStock   T: stock level the market returns to
 * @param maxStock      Scap: stock above which the market stops buying
 * @param restockPerDay r: stock recovery speed (items per in-game day)
 * @param minMultiplier Vmin
 * @param maxMultiplier Vmax
 */
public record GoodParams(
		double basePrice,
		double elasticity,
		double spread,
		double targetStock,
		double maxStock,
		double restockPerDay,
		double minMultiplier,
		double maxMultiplier
) {
	public GoodParams {
		if (basePrice <= 0) throw new IllegalArgumentException("basePrice must be > 0");
		if (elasticity <= 0) throw new IllegalArgumentException("elasticity must be > 0");
		if (spread < 0 || spread >= 2) throw new IllegalArgumentException("spread must be in [0, 2)");
		if (minMultiplier <= 0 || minMultiplier > maxMultiplier) throw new IllegalArgumentException("invalid multiplier bounds");
	}

	/** Lowest allowed pressure (value is at Vmax). */
	public double minPressure() {
		return -elasticity * Math.log(maxMultiplier);
	}

	/** Highest allowed pressure (value is at Vmin). */
	public double maxPressure() {
		return elasticity * Math.log(1.0 / minMultiplier);
	}

	public double clampPressure(double x) {
		return Math.clamp(x, minPressure(), maxPressure());
	}

	public GoodParams withElasticity(double newElasticity) {
		return new GoodParams(basePrice, newElasticity, spread, targetStock, maxStock, restockPerDay, minMultiplier, maxMultiplier);
	}
}
