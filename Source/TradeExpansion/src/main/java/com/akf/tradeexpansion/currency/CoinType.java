package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.core.CoinMath;

import java.util.Arrays;

/**
 * The five coin tiers (design E-1), smallest first. Values follow {@code currency.ratios}.
 */
public enum CoinType {
	COPPER("copper_coin"),
	IRON("iron_coin"),
	GOLD("gold_coin"),
	DIAMOND("diamond_coin"),
	NETHERITE("netherite_coin");

	public static final int[] DEFAULT_RATIOS = {10, 10, 10, 10};

	private static int[] ratios = DEFAULT_RATIOS.clone();
	private static long[] values = CoinMath.values(ratios);

	private final String path;

	CoinType(String path) {
		this.path = path;
	}

	/** Registry path, e.g. {@code copper_coin}. */
	public String path() {
		return path;
	}

	/** Value in copper. */
	public long value() {
		return values[ordinal()];
	}

	/** The next smaller coin, or {@code null} for copper. */
	public CoinType lower() {
		return ordinal() == 0 ? null : values()[ordinal() - 1];
	}

	/** How many {@link #lower()} coins this coin is worth. */
	public int ratioToLower() {
		return ordinal() == 0 ? 1 : ratios[ordinal() - 1];
	}

	/** Copper values of all tiers, indexed by ordinal. */
	public static long[] valueTable() {
		return values.clone();
	}

	public static int[] ratios() {
		return ratios.clone();
	}

	/** Changes {@code currency.ratios} (copper→iron, iron→gold, gold→diamond, diamond→netherite). */
	public static void setRatios(int[] newRatios) {
		if (newRatios.length != values().length - 1) {
			throw new IllegalArgumentException("expected " + (values().length - 1) + " ratios: " + Arrays.toString(newRatios));
		}
		long[] newValues = CoinMath.values(newRatios);
		ratios = newRatios.clone();
		values = newValues;
	}
}
