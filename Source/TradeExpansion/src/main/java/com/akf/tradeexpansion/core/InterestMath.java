package com.akf.tradeexpansion.core;

/**
 * Deposit interest (design E-7, v0.29.1). Minecraft-independent. Interest is compounded every in-game day on the part of
 * the balance up to {@code cap}; above the cap the balance grows only by the interest of the cap.
 */
public final class InterestMath {
	private InterestMath() {
	}

	/**
	 * Balance (with fractions of a copper) after {@code days} at {@code ratePerDay}.
	 *
	 * @param cap balance up to which interest is paid; 0 or less = no cap
	 */
	public static double accrue(double balance, double days, double ratePerDay, double cap) {
		if (days <= 0 || ratePerDay <= 0 || balance <= 0) return balance;
		double growth = Math.log1p(ratePerDay);
		if (cap <= 0) return balance * Math.exp(growth * days);
		if (balance >= cap) return balance + cap * ratePerDay * days;
		// Compounding until the balance reaches the cap, then the interest of the cap per day.
		double daysToCap = Math.log(cap / balance) / growth;
		if (daysToCap >= days) return balance * Math.exp(growth * days);
		return cap + cap * ratePerDay * (days - daysToCap);
	}
}
