package com.akf.tradeexpansion.core;

/**
 * Periods of the village trends (design P). Minecraft-independent.
 */
public final class TrendMath {
	private TrendMath() {
	}

	/** Length of one period in ticks (at least 1). */
	public static long length(double durationDays) {
		return Math.max(1, Math.round(durationDays * PriceModel.TICKS_PER_DAY));
	}

	/** Per-market shift of the period start, so that markets do not all change on the same day: seed mod length. */
	public static long offset(long seed, double durationDays) {
		return Math.floorMod(seed, length(durationDays));
	}

	/** Index of the period {@code gameTime} falls into: floor((time + offset) / length). */
	public static long period(long gameTime, double durationDays, long offset) {
		return Math.floorDiv(gameTime + offset, length(durationDays));
	}

	/** Multiplier in [min, max] from a random fraction in [0, 1). */
	public static double multiplier(double fraction, double min, double max) {
		return min + (max - min) * fraction;
	}
}
