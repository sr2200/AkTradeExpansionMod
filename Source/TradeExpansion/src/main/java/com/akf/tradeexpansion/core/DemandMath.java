package com.akf.tradeexpansion.core;

/**
 * K-2 (v0.78): fulfilling shortages makes a village grow. A shortage is fulfilled once {@code demand.fulfillCount}
 * units were sold to it in its period; the market's vigor (0〜100) goes up when one is fulfilled and down for each
 * shortage left unfulfilled at the end of its period. A low vigor slightly lowers the development points (N-2).
 */
public final class DemandMath {
	/** Vigor of a new market (and the most it can have). */
	public static final double VIGOR_MAX = 100;
	/** Below this vigor the development points are lowered. */
	public static final double VIGOR_LOW = 50;

	private DemandMath() {
	}

	/** Units still to sell to fulfill a shortage (0 = fulfilled; {@code required <= 0}: never fulfilled). */
	public static int left(int sold, int required) {
		if (required <= 0) return Integer.MAX_VALUE;
		return Math.max(0, required - sold);
	}

	/** Vigor after a change, kept within 0〜{@link #VIGOR_MAX}. */
	public static double vigor(double vigor, double delta) {
		return Math.clamp(vigor + delta, 0, VIGOR_MAX);
	}

	/**
	 * Multiplier of the development points: 1 from {@link #VIGOR_LOW} up, falling in a straight line to {@code 1 -
	 * penalty} at vigor 0.
	 */
	public static double pointsMultiplier(double vigor, double penalty) {
		if (vigor >= VIGOR_LOW || penalty <= 0) return 1;
		double low = (VIGOR_LOW - Math.max(0, vigor)) / VIGOR_LOW;
		return Math.max(0, 1 - penalty * low);
	}

	/** A villager comes with this fulfilled shortage (the {@code every}-th, 2×{@code every}-th, ...). */
	public static boolean villagerDue(int fulfilled, int every) {
		return every > 0 && fulfilled > 0 && fulfilled % every == 0;
	}

	/** Development points given for fulfilling a shortage: B without the shortage × required units × ratio. */
	public static double fulfillPoints(double basePrice, int required, double ratio) {
		return Math.max(0, basePrice * required * ratio);
	}
}
