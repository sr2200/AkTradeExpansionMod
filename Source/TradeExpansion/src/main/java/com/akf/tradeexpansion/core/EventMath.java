package com.akf.tradeexpansion.core;

/**
 * Numbers of the market events (design V). Minecraft-independent.
 */
public final class EventMath {
	/** V-4: days an event takes to reach its full strength (1/3, 2/3, then 1). */
	public static final int RAMP_DAYS = 3;
	/**
	 * V-4: bounds of the product of the multipliers of B from what happens at a market (shortage, trends, rain, event,
	 * today's special); the market profile's own multiplier is not part of it.
	 */
	public static final double MIN_COMBINED = 0.4;
	public static final double MAX_COMBINED = 2.5;

	private EventMath() {
	}

	/** V-4: share of the full strength on {@code day} of an event starting on {@code startDay}: 0 before, then 1/3, 2/3, 1. */
	public static double ramp(long day, long startDay) {
		if (day < startDay) return 0.0;
		return Math.min(1.0, (day - startDay + 1) / (double) RAMP_DAYS);
	}

	/**
	 * V-4: multiplier of B at a market: 1 + (m − 1) × strength × weight × ramp, never below 0.05.
	 *
	 * @param multiplier m, the event's multiplier at its center at full strength
	 * @param weight     share by distance from the center (1 at the center, 0 beyond the far radius)
	 */
	public static double multiplier(double multiplier, double strength, double weight, double ramp) {
		double k = Math.max(0.0, strength) * Math.clamp(weight, 0.0, 1.0) * Math.clamp(ramp, 0.0, 1.0);
		return Math.max(0.05, 1.0 + (multiplier - 1.0) * k);
	}

	/** V-4: the product of the market's own multipliers of B, kept within {@link #MIN_COMBINED}..{@link #MAX_COMBINED}. */
	public static double clampCombined(double product) {
		return Math.clamp(product, MIN_COMBINED, MAX_COMBINED);
	}

	/** A value in [min, max] from a random fraction in [0, 1). */
	public static double between(double fraction, double min, double max) {
		return min + (max - min) * fraction;
	}
}
