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

	/**
	 * v0.51: the travel time times {@code multiplier} (a harbor at either end, below 1 = faster), still at least
	 * {@link #MIN_TRAVEL_TICKS}. A multiplier of 1 or more leaves it as it is.
	 */
	public static long travelTicks(double distance, double blocksPerDay, double multiplier) {
		long ticks = travelTicks(distance, blocksPerDay);
		if (!(multiplier < 1.0)) return ticks;
		return Math.max(MIN_TRAVEL_TICKS, Math.round(ticks * multiplier));
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

	/** A carrier's prices (Q, v0.48.1): the spread s × {@code factor} (wholesale prices between merchants). */
	public static GoodParams withSpread(GoodParams p, double factor) {
		return new GoodParams(p.basePrice(), p.elasticity(), p.spread() * Math.clamp(factor, 0.0, 1.0), p.targetStock(), p.maxStock(),
				p.restockPerDay(), p.minMultiplier(), p.maxMultiplier(), p.consumePerDay(), p.halfLifeDays());
	}

	/**
	 * A carrier's purchase.
	 *
	 * @param highestPrice  the dearest unit bought (0 when none)
	 * @param expectedSales what the units would fetch at the destination now, one after another
	 */
	public record BuyPlan(int units, long total, long highestPrice, long expectedSales) {
		public static final BuyPlan NONE = new BuyPlan(0, 0, 0, 0);
	}

	/**
	 * Buys at home one unit at a time (X falls by {@code step} per unit) while: fewer than {@code max} units, the unit price
	 * is at most {@code maxPrice}, the total stays within {@code funds}, and the unit is expected to sell for more at the
	 * destination: sellPrice(dest, destX + k × step) × {@code keep} &gt; price, where {@code keep} is the share expected to
	 * arrive (1 − accident chance × lost share).
	 */
	public static BuyPlan planBuy(GoodParams home, double homeX, GoodParams dest, double destX, int max, long maxPrice, long funds,
			double step, double keep) {
		double x = homeX;
		double y = destX;
		int n = 0;
		long total = 0;
		long highest = 0;
		long sales = 0;
		while (n < max) {
			long price = PriceModel.buyPrice(home, x);
			long sale = PriceModel.sellPrice(dest, y);
			if (price > maxPrice || total + price > funds || sale * keep <= price) break;
			total += price;
			highest = price;
			sales += sale;
			n++;
			x = home.clampPressure(x - step);
			y = dest.clampPressure(y + step);
		}
		return new BuyPlan(n, total, highest, sales);
	}

	/** A sale of units one at a time (X rises by {@code step} per unit) while the unit price is at least {@code minPrice}. */
	public record SellPlan(int units, long total) {
	}

	public static SellPlan planSell(GoodParams p, double x, int max, long minPrice, double step) {
		int n = 0;
		long total = 0;
		while (n < max) {
			long price = PriceModel.sellPrice(p, x);
			if (price < minPrice) break;
			total += price;
			n++;
			x = p.clampPressure(x + step);
		}
		return new SellPlan(n, total);
	}

	/**
	 * Expected profit of one round trip with {@code plan}: the sales that arrive (accidents lose {@code lostShare} of the
	 * cargo with {@code accidentChance}) minus the purchase, minus the commission on a positive result.
	 */
	public static long expectedProfit(BuyPlan plan, double accidentChance, double lostShare, double commission) {
		if (plan.units() <= 0) return 0;
		double gross = plan.expectedSales() * (1 - accidentChance * lostShare) - plan.total();
		return Math.round(gross > 0 ? gross * (1 - commission) : gross);
	}

	/** Commission of a round trip: {@code rate} of a positive profit, nothing otherwise. */
	public static long commission(long profit, double rate) {
		return profit <= 0 ? 0 : (long) Math.floor(profit * Math.clamp(rate, 0.0, 1.0));
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
