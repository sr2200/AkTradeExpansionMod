package com.akf.tradeexpansion.core;

import java.util.List;

/**
 * Levels of the village's buildings (design N-11, v0.86): how strong a building's effects are at level 1..3 and in the
 * direction chosen at level 2, and what raising it costs. Minecraft-independent.
 *
 * <p>Each effect of a building has a strength: 1 at level 1, {@code steps[level - 2]} above it (1.5 and 2 by default).
 * The direction chosen at level 2 makes one effect grow faster ({@link Role#STRONG}: 1.5 times the growth) and the other
 * slower ({@link Role#WEAK}: half the growth). A multiplier m of the effect becomes {@link #raise}: an increase keeps
 * {@code strength} times its bonus (1.5 → 1.75 → 2), a decrease is raised to the power of the strength (0.75 → 0.65 →
 * 0.56), so it never reaches 0. An added count is multiplied and rounded ({@link #count}).
 */
public final class FacilityLevelMath {
	/** Highest level of a building. */
	public static final int MAX_LEVEL = 3;

	private FacilityLevelMath() {
	}

	/** How the chosen direction treats one effect. */
	public enum Role {
		/** No direction (level 1, or a building without directions). */
		NEUTRAL,
		/** The effect the direction is about: it grows 1.5 times as fast. */
		STRONG,
		/** The other effect: it grows half as fast. */
		WEAK
	}

	/** Strength of an effect at {@code level} (clamped to 1..3) with growth {@code steps} (levels 2 and 3). */
	public static double strength(int level, Role role, List<Double> steps) {
		if (level <= 1) return 1.0;
		int i = Math.min(level, MAX_LEVEL) - 2;
		double s = steps == null || steps.size() <= i ? 1.0 + 0.5 * (i + 1) : steps.get(i);
		double growth = Math.max(0, s - 1);
		return switch (role) {
			case NEUTRAL -> 1 + growth;
			case STRONG -> 1 + growth * 1.5;
			case WEAK -> 1 + growth * 0.5;
		};
	}

	/**
	 * A multiplier at {@code strength}: above 1 it keeps {@code strength} times its bonus ({@code 1 + (m - 1) × s}), below 1
	 * it is raised to the power of the strength ({@code m^s}); 1 stays 1.
	 */
	public static double raise(double multiplier, double strength) {
		if (!(strength > 1) || multiplier == 1) return multiplier;
		if (multiplier > 1) return 1 + (multiplier - 1) * strength;
		if (multiplier <= 0) return multiplier;
		return Math.pow(multiplier, strength);
	}

	/** An added count at {@code strength}: {@code count × s}, rounded half up; never below {@code count}. */
	public static int count(int count, double strength) {
		if (count <= 0 || !(strength > 1)) return count;
		return (int) Math.max(count, Math.min(Integer.MAX_VALUE, Math.round(count * strength)));
	}

	/** Days between two events at {@code strength} (the festival): {@code days / s}, rounded, at least {@code min}. */
	public static int interval(int days, double strength, int min) {
		if (!(strength > 1)) return days;
		return (int) Math.max(min, Math.round(days / strength));
	}

	/**
	 * Multiplier of a side bonus (more reputation / more development points) a direction adds: 1 at level 1, {@code 1 +
	 * bonus × (level - 1)} above it.
	 */
	public static double perk(int level, double bonus) {
		if (level <= 1 || !(bonus > 0)) return 1.0;
		return 1 + bonus * (Math.min(level, MAX_LEVEL) - 1);
	}

	/**
	 * Factor of the building's cost for raising it from {@code level} to the next: {@code costs[level - 1]} (1 and 1.5 by
	 * default); 0 at the top level.
	 */
	public static double upgradeFactor(int level, List<Double> costs) {
		if (level < 1 || level >= MAX_LEVEL) return 0;
		int i = level - 1;
		return Math.max(0, costs == null || costs.size() <= i ? 1.0 + 0.5 * i : costs.get(i));
	}

	/** Factor of the building's cost for everything up to {@code level}: 1 for building it plus each raise. */
	public static double totalFactor(int level, List<Double> costs) {
		double total = 1;
		for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) total += upgradeFactor(l, costs);
		return total;
	}

	/** {@code base × factor}, rounded up, at least 1 when the factor is above 0. */
	public static long scaled(long base, double factor) {
		if (base <= 0 || !(factor > 0)) return 0;
		return Math.max(1, (long) Math.ceil(base * factor - 1e-9));
	}
}
