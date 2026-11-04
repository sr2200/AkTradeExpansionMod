package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.condition.MarketCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * One goods definition: {@code data/<ns>/trade_expansion/goods/<name>.json} (design D-2).
 * Optional values fall back to {@link TradeExpansionConfig}. Prices, stock and pressure count in units of {@code unit} items.
 *
 * @param item          item id; a good matches only plain stacks of this item (no extra components)
 * @param buy           the player can buy it
 * @param sell          the player can sell it
 * @param acceptDamaged reduced-price buying of damaged items (stored; the trade logic comes later, A-4)
 * @param unit          items per trading unit (B-5, v0.6)
 * @param autoAdd       markets matching these conditions deal in this good outside the profile (D-1 step 0)
 * @param link          propagation factor of X to other markets (C-2, default 1)
 * @param components    item components a stack must have exactly (D-2); raw JSON, resolved with the server registries
 * @param consumePerDay drain speed of stock above target; default r / 2, or T × {@code recovery.consumableMultiplier}
 *                      for items in {@link #CONSUMABLES} (B-3)
 * @param halfLifeDays  half-life of X; default {@code recovery.halfLifeDays}, or {@code recovery.consumableHalfLifeDays}
 *                      for consumables (B-3)
 */
public record GoodsDefinition(
		Identifier item,
		double basePrice,
		Optional<Double> elasticity,
		double targetStock,
		Optional<Double> maxStock,
		Optional<Double> restockPerDay,
		Optional<Double> spread,
		boolean buy,
		boolean sell,
		Optional<Boolean> acceptDamaged,
		int unit,
		Optional<List<MarketCondition>> autoAdd,
		double link,
		Optional<Dynamic<?>> components,
		Optional<Double> consumePerDay,
		Optional<Double> halfLifeDays
) {
	/** Consumables the market uses up (food, torches, fuel, logs and planks): stock and buying capacity recover quickly (B-3). */
	public static final TagKey<Item> CONSUMABLES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("trade_expansion", "consumables"));

	public static final Codec<GoodsDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(GoodsDefinition::item),
			Codec.doubleRange(0.01, 1e9).fieldOf("base_price").forGetter(GoodsDefinition::basePrice),
			Codec.doubleRange(0.01, 1e6).optionalFieldOf("elasticity").forGetter(GoodsDefinition::elasticity),
			Codec.doubleRange(0, 1e6).optionalFieldOf("target_stock", 16.0).forGetter(GoodsDefinition::targetStock),
			Codec.doubleRange(0, 1e6).optionalFieldOf("max_stock").forGetter(GoodsDefinition::maxStock),
			Codec.doubleRange(0, 1e6).optionalFieldOf("restock_per_day").forGetter(GoodsDefinition::restockPerDay),
			Codec.doubleRange(0, 1.9).optionalFieldOf("spread").forGetter(GoodsDefinition::spread),
			Codec.BOOL.optionalFieldOf("buy", true).forGetter(GoodsDefinition::buy),
			Codec.BOOL.optionalFieldOf("sell", true).forGetter(GoodsDefinition::sell),
			Codec.BOOL.optionalFieldOf("accept_damaged").forGetter(GoodsDefinition::acceptDamaged),
			Codec.intRange(1, 64).optionalFieldOf("unit", 1).forGetter(GoodsDefinition::unit),
			MarketCondition.CODEC.listOf().optionalFieldOf("auto_add").forGetter(GoodsDefinition::autoAdd),
			Codec.doubleRange(0, 10).optionalFieldOf("link", 1.0).forGetter(GoodsDefinition::link),
			Codec.PASSTHROUGH.optionalFieldOf("components").forGetter(GoodsDefinition::components),
			Codec.doubleRange(0, 1e6).optionalFieldOf("consume_per_day").forGetter(GoodsDefinition::consumePerDay),
			Codec.doubleRange(0.05, 100).optionalFieldOf("half_life_days").forGetter(GoodsDefinition::halfLifeDays)
	).apply(i, GoodsDefinition::new));

	/** A-4: reduced-price buying of damaged items, falling back to {@code trade.acceptDamaged}. */
	public boolean acceptsDamaged() {
		return acceptDamaged.orElse(TradeExpansionConfig.get().trade.acceptDamaged);
	}

	/** D-2 / G: this definition with the config file's values for this good. */
	public GoodsDefinition with(com.akf.tradeexpansion.config.GoodsOverride o) {
		return new GoodsDefinition(item, o.basePrice().orElse(basePrice), o.elasticity().or(() -> elasticity), o.targetStock().orElse(targetStock),
				o.maxStock().or(() -> maxStock), o.restockPerDay().or(() -> restockPerDay), o.spread().or(() -> spread),
				o.buy().orElse(buy), o.sell().orElse(sell), acceptDamaged, unit, autoAdd, link, components,
				o.consumePerDay().or(() -> consumePerDay), o.halfLifeDays().or(() -> halfLifeDays));
	}

	/** v0.27: a good added by the config file ({@code goods.<id>.item} and {@code base_price}); other values are the defaults. */
	public static GoodsDefinition fromConfig(Identifier item, com.akf.tradeexpansion.config.GoodsOverride o) {
		return new GoodsDefinition(item, o.basePrice().orElse(1.0), Optional.empty(), 16.0, Optional.empty(), Optional.empty(), Optional.empty(),
				true, true, Optional.empty(), 1, Optional.empty(), 1.0, Optional.empty(), Optional.empty(), Optional.empty()).with(o);
	}

	/** B-3: whether this good is a consumable (see {@link #isConsumable}). */
	public boolean consumable(TradeExpansionConfig config) {
		return isConsumable(item, config);
	}

	/** B-3: {@link #CONSUMABLES} plus {@code consumables.add}, minus {@code consumables.remove} (the tag is empty until tags load). */
	public static boolean isConsumable(Identifier item, TradeExpansionConfig config) {
		String id = item.toString();
		if (config.consumables.remove.contains(id)) return false;
		return config.consumables.add.contains(id) || BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(item)).is(CONSUMABLES);
	}

	/**
	 * Resolves the effective params for a market (D-1):
	 * B = base_price × profile multiplier × priceFactor × price.globalMultiplier, with B-6 correction of tau.
	 */
	public GoodParams params(double priceFactor, double profileMultiplier, TradeExpansionConfig config) {
		double t = targetStock;
		double restock = restockPerDay.orElse(t / 2);
		boolean consumable = consumable(config);
		GoodParams params = new GoodParams(
				basePrice * profileMultiplier * priceFactor * config.price.globalMultiplier,
				elasticity.orElse(config.price.defaultElasticity),
				spread.orElse(config.price.defaultSpread),
				t,
				maxStock.orElse(t * config.price.defaultMaxStockMultiplier),
				restock,
				config.price.minMultiplier,
				config.price.maxMultiplier,
				consumePerDay.orElse(consumable ? t * config.recovery.consumableMultiplier : restock / 2),
				halfLifeDays.orElse(consumable ? config.recovery.consumableHalfLifeDays : config.recovery.halfLifeDays));
		return PriceModel.ensureExploitSafe(params);
	}
}
