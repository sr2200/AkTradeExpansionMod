package com.akf.tradeexpansion.core;

import java.util.Collection;

/**
 * Distance falloff of price propagation (design C-1 / C-2). Minecraft-independent.
 */
public final class DistanceModel {
	private DistanceModel() {
	}

	/** A neighbouring market: horizontal distance to it and its (refreshed) pressure X. */
	public record Neighbor(double distance, double pressure) {
	}

	/**
	 * w(d) = 1 (d &lt;= Rnear), ((Rfar - d) / (Rfar - Rnear))^p (Rnear &lt; d &lt; Rfar), 0 (d &gt;= Rfar)
	 */
	public static double weight(double distance, double nearRadius, double farRadius, double exponent) {
		if (distance <= nearRadius) return 1.0;
		if (distance >= farRadius) return 0.0;
		return Math.pow((farRadius - distance) / (farRadius - nearRadius), exponent);
	}

	public static double horizontalDistance(double x1, double z1, double x2, double z2) {
		double dx = x1 - x2;
		double dz = z1 - z2;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Initial X of a newly created market (C-1).
	 * Average of neighbours within Rnear if any; otherwise the w(d)-weighted average of those within Rfar; otherwise 0.
	 */
	public static double initialPressure(Collection<Neighbor> neighbors, double nearRadius, double farRadius, double exponent) {
		double nearSum = 0;
		int nearCount = 0;
		double weightedSum = 0;
		double weightTotal = 0;
		for (Neighbor n : neighbors) {
			if (n.distance() <= nearRadius) {
				nearSum += n.pressure();
				nearCount++;
			} else {
				double w = weight(n.distance(), nearRadius, farRadius, exponent);
				weightedSum += w * n.pressure();
				weightTotal += w;
			}
		}
		if (nearCount > 0) return nearSum / nearCount;
		if (weightTotal > 0) return weightedSum / weightTotal;
		return 0.0;
	}
}
