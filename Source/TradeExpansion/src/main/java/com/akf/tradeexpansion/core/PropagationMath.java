package com.akf.tradeexpansion.core;

/**
 * Price propagation between markets (design C-2): a pressure change ΔX at one market moves X of another market of the
 * same goods by ΔX × w(d) × link, clamped to the other market's range. Minecraft-independent.
 */
public final class PropagationMath {
	private PropagationMath() {
	}

	/** Distance falloff settings ({@code distance.nearRadius} / {@code farRadius} / {@code falloffExponent}). */
	public record Falloff(double nearRadius, double farRadius, double exponent) {
		public double weight(double distance) {
			return DistanceModel.weight(distance, nearRadius, farRadius, exponent);
		}

		/** Markets farther than this get nothing (w = 0). */
		public double reach() {
			return Math.max(nearRadius, farRadius);
		}
	}

	/**
	 * @param params   the other market's parameters of the goods (for the X range)
	 * @param pressure the other market's X (already recovered to now)
	 * @param link     the goods' {@code link} factor (D-2, default 1)
	 * @return the other market's new X, or {@code pressure} unchanged when it is out of reach
	 */
	public static double spread(GoodParams params, double pressure, double deltaX, double distance, Falloff falloff, double link) {
		double w = falloff.weight(distance);
		if (w <= 0) return pressure;
		return params.clampPressure(pressure + deltaX * w * link);
	}

	/** Whether a market {@code distance} away is touched at all (w &gt; 0). */
	public static boolean reaches(double distance, Falloff falloff) {
		return falloff.weight(distance) > 0;
	}
}
