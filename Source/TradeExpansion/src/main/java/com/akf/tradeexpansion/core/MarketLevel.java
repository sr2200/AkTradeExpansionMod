package com.akf.tradeexpansion.core;

/**
 * Market size levels of design D-4. Minecraft-independent.
 * Level 1 (hamlet) .. 5 (city), measured from bed count (villager count in the Nether / End).
 */
public final class MarketLevel {
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 5;

	private MarketLevel() {
	}

	/**
	 * @param count      beds or villagers around the market center
	 * @param thresholds ascending counts needed for levels 2..5 (e.g. {5, 10, 20, 40})
	 */
	public static int level(long count, int[] thresholds) {
		int level = MIN_LEVEL;
		for (int threshold : thresholds) {
			if (count >= threshold) level++;
		}
		return Math.min(level, MAX_LEVEL);
	}

	/** N = round(minGoods + (maxGoods - minGoods) * (level - 1) / 4) */
	public static int goodsCount(int level, int minGoods, int maxGoods) {
		int clamped = Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
		return (int) Math.round(minGoods + (maxGoods - minGoods) * (clamped - MIN_LEVEL) / (double) (MAX_LEVEL - MIN_LEVEL));
	}
}
