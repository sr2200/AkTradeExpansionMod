package com.akf.tradeexpansion.core;

/**
 * Numbers of delivery requests and shortages (design J / K). Minecraft-independent.
 */
public final class QuestMath {
	private QuestMath() {
	}

	/** J: ★1..3 from the request value B × quantity; thresholds are the values needed for ★2 and ★3. */
	public static int difficulty(double value, int[] thresholds) {
		int stars = 1;
		for (int t : thresholds) {
			if (value >= t) stars++;
		}
		return Math.min(stars, 3);
	}

	/** J: reward = ceil(value × multiplier of the difficulty). */
	public static long reward(double value, int difficulty, double[] multipliers) {
		double m = multipliers[Math.clamp(difficulty, 1, multipliers.length) - 1];
		return (long) Math.ceil(value * m - 1e-9);
	}

	/** K: index of the shortage period that {@code gameTime} falls into. */
	public static long period(long gameTime, double durationDays) {
		long length = Math.max(1, Math.round(durationDays * PriceModel.TICKS_PER_DAY));
		return Math.floorDiv(gameTime, length);
	}

	/** K: multiplier in [min, max] from a random fraction in [0, 1). */
	public static double shortageMultiplier(double fraction, double min, double max) {
		return min + (max - min) * fraction;
	}

	/**
	 * J-2 (v0.35): reward multiplier of a transport request: {@code base + perThousand × distance / 1000}, at most {@code max}
	 * (never below 0). The reward is the goods' value × this; the deposit (the goods' value) is paid back on top.
	 */
	public static double transportMultiplier(double distance, double base, double perThousand, double max) {
		return Math.max(0, Math.min(max, base + perThousand * Math.max(0, distance) / 1000.0));
	}
}
