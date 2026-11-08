package com.akf.tradeexpansion.core;

/**
 * Formulas of hired staff (design Q): travel time and accidents of carriers, price limits of carriers and clerks,
 * daily wages. Minecraft-independent.
 */
public final class StaffMath {
	/** Shortest one-way trip, so that very close markets still take a little time (0.05 days = 1 minute). */
	public static final long MIN_TRAVEL_TICKS = 1200;

	private StaffMath() {
	}

	/** One-way travel time of a carrier: distance ÷ blocks per day, at least {@link #MIN_TRAVEL_TICKS}. */
	public static long travelTicks(double distance, double blocksPerDay) {
		if (blocksPerDay <= 0) return MIN_TRAVEL_TICKS;
		return Math.max(MIN_TRAVEL_TICKS, Math.round(distance / blocksPerDay * PriceModel.TICKS_PER_DAY));
	}

	/** Chance of an accident on one loaded trip: min(max, base + perThousand × distance ÷ 1000), within 0..1. */
	public static double accidentChance(double distance, double base, double perThousandBlocks, double max) {
		double chance = Math.min(max, base + perThousandBlocks * Math.max(0, distance) / 1000.0);
		return Math.clamp(chance, 0.0, 1.0);
	}

	/** Units lost in an accident: ceil(cargo × fraction), at least 1 and at most the cargo (0 without cargo). */
	public static int lostUnits(int cargo, double fraction) {
		if (cargo <= 0) return 0;
		int lost = (int) Math.ceil(cargo * Math.clamp(fraction, 0.0, 1.0) - 1e-9);
		return Math.clamp(lost, 1, cargo);
	}

	/**
	 * How many units can be bought one by one while the unit buy price stays at or below {@code maxPrice} (X falls by 1
	 * per unit, as in B-5), capped by {@code maxUnits}.
	 */
	public static int buyableWithin(GoodParams p, double pressure, int maxUnits, long maxPrice) {
		double x = pressure;
		int n = 0;
		while (n < maxUnits && PriceModel.buyPrice(p, x) <= maxPrice) {
			n++;
			x = p.clampPressure(x - 1);
		}
		return n;
	}

	/**
	 * How many units can be sold one by one while the unit sell price stays at or above {@code minPrice} (X rises by 1
	 * per unit), capped by {@code maxUnits}.
	 */
	public static int sellableWithin(GoodParams p, double pressure, int maxUnits, long minPrice) {
		double x = pressure;
		int n = 0;
		while (n < maxUnits && PriceModel.sellPrice(p, x) >= minPrice) {
			n++;
			x = p.clampPressure(x + 1);
		}
		return n;
	}

	/** Part of {@code cost} that belongs to {@code units} of {@code total} units (rounded down, 0 when nothing is held). */
	public static long shareOf(long cost, int units, int total) {
		if (total <= 0 || units <= 0) return 0;
		if (units >= total) return cost;
		return (long) Math.floor(cost * (double) units / total);
	}

	/**
	 * Price step of the limit buttons: 1% of the price (at least 1c), or 10% with Shift.
	 */
	public static long priceStep(long price, boolean large) {
		return Math.max(1, Math.round(Math.max(1, price) * (large ? 0.10 : 0.01)));
	}
}
