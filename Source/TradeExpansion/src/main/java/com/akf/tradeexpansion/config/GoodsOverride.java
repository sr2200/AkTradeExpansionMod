package com.akf.tradeexpansion.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Per-goods values set in the config file (design D-2 / G, {@code goods}): they replace the data pack's goods
 * definition, key by key, without editing a data pack. Keys and ranges are the same as in the goods JSON.
 * {@code buy: false} and {@code sell: false} together stop a market from dealing in the good.
 */
public record GoodsOverride(
		Optional<Double> basePrice,
		Optional<Double> elasticity,
		Optional<Double> targetStock,
		Optional<Double> maxStock,
		Optional<Double> restockPerDay,
		Optional<Double> consumePerDay,
		Optional<Double> halfLifeDays,
		Optional<Double> spread,
		Optional<Boolean> buy,
		Optional<Boolean> sell
) {
	public static final GoodsOverride EMPTY = new GoodsOverride(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

	public static final Codec<GoodsOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("base_price").forGetter(GoodsOverride::basePrice),
			Codec.DOUBLE.optionalFieldOf("elasticity").forGetter(GoodsOverride::elasticity),
			Codec.DOUBLE.optionalFieldOf("target_stock").forGetter(GoodsOverride::targetStock),
			Codec.DOUBLE.optionalFieldOf("max_stock").forGetter(GoodsOverride::maxStock),
			Codec.DOUBLE.optionalFieldOf("restock_per_day").forGetter(GoodsOverride::restockPerDay),
			Codec.DOUBLE.optionalFieldOf("consume_per_day").forGetter(GoodsOverride::consumePerDay),
			Codec.DOUBLE.optionalFieldOf("half_life_days").forGetter(GoodsOverride::halfLifeDays),
			Codec.DOUBLE.optionalFieldOf("spread").forGetter(GoodsOverride::spread),
			Codec.BOOL.optionalFieldOf("buy").forGetter(GoodsOverride::buy),
			Codec.BOOL.optionalFieldOf("sell").forGetter(GoodsOverride::sell)
	).apply(i, GoodsOverride::new));

	/** Number keys, their JSON names and allowed ranges (same as the goods JSON, D-2). */
	public enum NumberKey {
		BASE_PRICE("base_price", 0.01, 1e9),
		ELASTICITY("elasticity", 0.01, 1e6),
		TARGET_STOCK("target_stock", 0, 1e6),
		MAX_STOCK("max_stock", 0, 1e6),
		RESTOCK_PER_DAY("restock_per_day", 0, 1e6),
		CONSUME_PER_DAY("consume_per_day", 0, 1e6),
		HALF_LIFE_DAYS("half_life_days", 0.05, 100),
		SPREAD("spread", 0, 1.9);

		public final String json;
		public final double min;
		public final double max;

		NumberKey(String json, double min, double max) {
			this.json = json;
			this.min = min;
			this.max = max;
		}
	}

	public Optional<Double> get(NumberKey key) {
		return switch (key) {
			case BASE_PRICE -> basePrice;
			case ELASTICITY -> elasticity;
			case TARGET_STOCK -> targetStock;
			case MAX_STOCK -> maxStock;
			case RESTOCK_PER_DAY -> restockPerDay;
			case CONSUME_PER_DAY -> consumePerDay;
			case HALF_LIFE_DAYS -> halfLifeDays;
			case SPREAD -> spread;
		};
	}

	public GoodsOverride with(NumberKey key, Optional<Double> value) {
		return new GoodsOverride(
				key == NumberKey.BASE_PRICE ? value : basePrice,
				key == NumberKey.ELASTICITY ? value : elasticity,
				key == NumberKey.TARGET_STOCK ? value : targetStock,
				key == NumberKey.MAX_STOCK ? value : maxStock,
				key == NumberKey.RESTOCK_PER_DAY ? value : restockPerDay,
				key == NumberKey.CONSUME_PER_DAY ? value : consumePerDay,
				key == NumberKey.HALF_LIFE_DAYS ? value : halfLifeDays,
				key == NumberKey.SPREAD ? value : spread,
				buy, sell);
	}

	public GoodsOverride withBuy(Optional<Boolean> value) {
		return new GoodsOverride(basePrice, elasticity, targetStock, maxStock, restockPerDay, consumePerDay, halfLifeDays, spread, value, sell);
	}

	public GoodsOverride withSell(Optional<Boolean> value) {
		return new GoodsOverride(basePrice, elasticity, targetStock, maxStock, restockPerDay, consumePerDay, halfLifeDays, spread, buy, value);
	}

	public boolean isEmpty() {
		return equals(EMPTY);
	}

	/** Values clamped to the allowed ranges (logged by the caller). */
	public GoodsOverride clamped() {
		GoodsOverride result = this;
		for (NumberKey key : NumberKey.values()) {
			result = result.with(key, get(key).map(v -> Math.clamp(v, key.min, key.max)));
		}
		return result;
	}
}
