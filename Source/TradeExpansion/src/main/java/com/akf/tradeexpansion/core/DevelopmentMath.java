package com.akf.tradeexpansion.core;

/**
 * Village development of design N: development points, the level bonus of completed facilities and the materials a
 * facility still wants. Minecraft-independent.
 */
public final class DevelopmentMath {
	private DevelopmentMath() {
	}

	/** Points after {@code days} with half-life {@code halfLifeDays} (0 or less = no decay, the default). */
	public static double decay(double points, double days, double halfLifeDays) {
		if (days <= 0 || halfLifeDays <= 0) return points;
		return points * Math.pow(2.0, -days / halfLifeDays);
	}

	/** N-3: level bonus = how many of the ascending thresholds the number of completed facilities reaches. */
	public static int levelBonus(int completed, int[] thresholds) {
		int bonus = 0;
		for (int threshold : thresholds) {
			if (completed >= threshold) bonus++;
		}
		return bonus;
	}

	/** N-3: market level = measured level (D-4) + bonus, at most {@link MarketLevel#MAX_LEVEL}. */
	public static int effectiveLevel(int measured, int bonus) {
		return Math.clamp((long) measured + bonus, MarketLevel.MIN_LEVEL, MarketLevel.MAX_LEVEL);
	}

	/** N-2: points of a donation of materials worth base price {@code basePrice} each. */
	public static double materialPoints(double basePrice, int count, double multiplier) {
		return Math.max(0, basePrice) * Math.max(0, count) * multiplier;
	}

	/** N-2: how many of {@code offered} items a facility takes when it wants {@code required} and has {@code donated}. */
	public static int acceptable(int required, int donated, int offered) {
		return Math.max(0, Math.min(offered, required - donated));
	}

	/**
	 * N-5: offsets {dx, dz} from the market center tried by the automatic site search: every {@code step} blocks within
	 * {@code radius} (horizontal), nearest first (ties: by angle, then x, z), at most {@code limit}. The center itself is
	 * left out (the stall stands there).
	 */
	public static java.util.List<int[]> siteOffsets(int radius, int step, int limit) {
		java.util.List<int[]> list = new java.util.ArrayList<>();
		int s = Math.max(1, step);
		int r = radius / s * s;
		for (int dx = -r; dx <= r; dx += s) {
			for (int dz = -r; dz <= r; dz += s) {
				if (dx == 0 && dz == 0 || (long) dx * dx + (long) dz * dz > (long) radius * radius) continue;
				list.add(new int[] {dx, dz});
			}
		}
		list.sort(java.util.Comparator.<int[]>comparingLong(o -> (long) o[0] * o[0] + (long) o[1] * o[1])
				.thenComparingDouble(o -> Math.atan2(o[1], o[0]))
				.thenComparingInt(o -> o[0])
				.thenComparingInt(o -> o[1]));
		return list.subList(0, Math.min(Math.max(0, limit), list.size()));
	}

	/** N-2 (v0.42): wanted count of the data × development.materialCountMultiplier, rounded up, at least 1. */
	public static long materialCount(int count, double multiplier) {
		return Math.max(1, (long) Math.ceil(count * multiplier - 1e-9));
	}

	/** N-4: development points a facility costs; rebuilding a destroyed one costs {@code rebuildRatio} of it (rounded up). */
	public static long cost(long baseCost, boolean rebuild, double rebuildRatio) {
		if (!rebuild) return baseCost;
		return (long) Math.ceil(baseCost * rebuildRatio - 1e-9);
	}
}
