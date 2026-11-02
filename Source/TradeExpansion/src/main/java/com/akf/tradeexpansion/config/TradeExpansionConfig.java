package com.akf.tradeexpansion.config;

/**
 * Settings of design chapter G (subset used so far). Values are the design defaults;
 * loading them from {@code config/trade_expansion.json} comes in stage 8.
 */
public record TradeExpansionConfig(
		// price
		double defaultSpread,
		double defaultElasticity,
		double minMultiplier,
		double maxMultiplier,
		double jitter,
		double defaultMaxStockMultiplier,
		double globalMultiplier,
		// recovery
		double halfLifeDays,
		double restockMultiplier,
		// distance
		double mergeRadius,
		double nearRadius,
		double farRadius,
		double falloffExponent,
		double minNewMarketDistance,
		// trade
		double maxDistance,
		double priceTolerance,
		int maxPerTransaction,
		boolean allowCreative,
		// log
		boolean logTrades
) {
	public static final TradeExpansionConfig DEFAULT = new TradeExpansionConfig(
			0.4, 32, 0.25, 3.0, 0.15, 2.0, 1.0,
			1.0, 1.0,
			64, 128, 1536, 2.0, 256,
			8, 0.0, 64, false,
			true
	);

	private static TradeExpansionConfig current = DEFAULT;

	public static TradeExpansionConfig get() {
		return current;
	}

	public static void set(TradeExpansionConfig config) {
		current = config;
	}
}
