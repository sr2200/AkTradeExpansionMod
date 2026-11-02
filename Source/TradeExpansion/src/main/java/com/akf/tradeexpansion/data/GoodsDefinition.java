package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.PriceModel;
import net.minecraft.resources.Identifier;

import java.util.OptionalDouble;

/**
 * One goods definition (design D-2). Optional values fall back to {@link TradeExpansionConfig}.
 *
 * @param item       item id; a good matches only plain stacks of this item (no extra components)
 * @param buy        the player can buy it
 * @param sell       the player can sell it
 */
public record GoodsDefinition(
		Identifier item,
		double basePrice,
		OptionalDouble elasticity,
		double targetStock,
		OptionalDouble maxStock,
		OptionalDouble restockPerDay,
		OptionalDouble spread,
		boolean buy,
		boolean sell
) {
	public static GoodsDefinition of(String item, double basePrice, double elasticity, double targetStock) {
		return new GoodsDefinition(Identifier.parse(item), basePrice, OptionalDouble.of(elasticity), targetStock,
				OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), true, true);
	}

	public GoodsDefinition withMaxStock(double value) {
		return new GoodsDefinition(item, basePrice, elasticity, targetStock, OptionalDouble.of(value), restockPerDay, spread, buy, sell);
	}

	public GoodsDefinition withRestock(double value) {
		return new GoodsDefinition(item, basePrice, elasticity, targetStock, maxStock, OptionalDouble.of(value), spread, buy, sell);
	}

	public GoodsDefinition sellOnly() {
		return new GoodsDefinition(item, basePrice, elasticity, targetStock, maxStock, restockPerDay, spread, false, true);
	}

	/**
	 * Resolves the effective params for a market (D-1):
	 * B = base_price × priceFactor × price.globalMultiplier, with B-6 correction of tau.
	 */
	public GoodParams params(double priceFactor, TradeExpansionConfig config) {
		double t = targetStock;
		GoodParams params = new GoodParams(
				basePrice * priceFactor * config.globalMultiplier(),
				elasticity.orElse(config.defaultElasticity()),
				spread.orElse(config.defaultSpread()),
				t,
				maxStock.orElse(t * config.defaultMaxStockMultiplier()),
				restockPerDay.orElse(t / 2),
				config.minMultiplier(),
				config.maxMultiplier());
		return PriceModel.ensureExploitSafe(params);
	}
}
