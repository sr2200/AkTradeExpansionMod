package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.core.GoodsSelector;
import com.akf.tradeexpansion.data.condition.MarketCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code data/<ns>/ak_trade_expansion/market_profile/<name>.json} (design D-2): which goods a kind of market deals in.
 */
public record MarketProfile(
		Optional<Component> displayName,
		int priority,
		int weight,
		List<MarketCondition> conditions,
		List<Pool> pools,
		List<Identifier> always,
		List<PriceModifier> priceModifiers
) {
	public static final Codec<MarketProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.optionalFieldOf("display_name").forGetter(MarketProfile::displayName),
			Codec.INT.optionalFieldOf("priority", 0).forGetter(MarketProfile::priority),
			Codec.intRange(0, 10000).optionalFieldOf("weight", 1).forGetter(MarketProfile::weight),
			MarketCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(MarketProfile::conditions),
			Pool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(MarketProfile::pools),
			Identifier.CODEC.listOf().optionalFieldOf("always", List.of()).forGetter(MarketProfile::always),
			PriceModifier.CODEC.listOf().optionalFieldOf("price_modifiers", List.of()).forGetter(MarketProfile::priceModifiers)
	).apply(i, MarketProfile::new));

	/** {@code "rolls": 3} or {@code "rolls": {"min": 2, "max": 4}}. */
	public record Rolls(int min, int max) {
		private static final Codec<Rolls> RANGE = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 100).fieldOf("min").forGetter(Rolls::min),
				Codec.intRange(0, 100).fieldOf("max").forGetter(Rolls::max)
		).apply(i, Rolls::new));
		public static final Codec<Rolls> CODEC = Codec.withAlternative(RANGE, Codec.intRange(0, 100), n -> new Rolls(n, n))
				.validate(r -> r.min <= r.max ? DataResult.success(r) : DataResult.error(() -> "rolls min > max"));
	}

	/**
	 * @param minLevel            the good is a candidate only in markets of at least this level (D-4)
	 * @param basePriceMultiplier multiplies the base price in markets of this profile (D-1 step 5)
	 */
	public record Entry(Identifier goods, int weight, int minLevel, double basePriceMultiplier) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(Entry::goods),
				Codec.intRange(0, 10000).optionalFieldOf("weight", 1).forGetter(Entry::weight),
				Codec.intRange(1, 5).optionalFieldOf("min_level", 1).forGetter(Entry::minLevel),
				Codec.doubleRange(0.01, 100).optionalFieldOf("base_price_multiplier", 1.0).forGetter(Entry::basePriceMultiplier)
		).apply(i, Entry::new));
	}

	public record Pool(Rolls rolls, List<Entry> entries) {
		public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
				Rolls.CODEC.optionalFieldOf("rolls", new Rolls(1, 1)).forGetter(Pool::rolls),
				Entry.CODEC.listOf().fieldOf("entries").forGetter(Pool::entries)
		).apply(i, Pool::new));
	}

	/** Exactly one of {@code item_tag} ("#minecraft:logs") or {@code item} ("minecraft:cactus"), v0.7. */
	public record PriceModifier(Optional<TagKey<Item>> itemTag, Optional<Identifier> item, double multiplier) {
		public static final Codec<PriceModifier> CODEC = RecordCodecBuilder.<PriceModifier>create(i -> i.group(
				TagKey.hashedCodec(Registries.ITEM).optionalFieldOf("item_tag").forGetter(PriceModifier::itemTag),
				Identifier.CODEC.optionalFieldOf("item").forGetter(PriceModifier::item),
				Codec.doubleRange(0.01, 100).fieldOf("multiplier").forGetter(PriceModifier::multiplier)
		).apply(i, PriceModifier::new)).validate(m -> m.itemTag.isPresent() != m.item.isPresent()
				? DataResult.success(m)
				: DataResult.error(() -> "price_modifiers entry needs exactly one of item_tag / item"));

		boolean matches(Identifier itemId) {
			if (item.isPresent()) return item.get().equals(itemId);
			Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(itemId));
			return holder.is(itemTag.get());
		}
	}

	/** Profile price factor of one good: the first entry's base_price_multiplier × every matching price_modifier. */
	public double multiplier(Identifier goodsId, Identifier itemId) {
		double m = pools.stream().flatMap(p -> p.entries().stream())
				.filter(e -> e.goods().equals(goodsId))
				.findFirst()
				.map(Entry::basePriceMultiplier)
				.orElse(1.0);
		for (PriceModifier modifier : priceModifiers) {
			if (modifier.matches(itemId)) m *= modifier.multiplier();
		}
		return m;
	}

	/** Pools converted for {@link GoodsSelector}, keeping only goods that have a definition. */
	public List<GoodsSelector.Pool> selectorPools() {
		List<GoodsSelector.Pool> list = new ArrayList<>();
		for (Pool pool : pools) {
			List<GoodsSelector.Candidate> candidates = new ArrayList<>();
			for (Entry e : pool.entries()) {
				if (GoodsCatalog.get(e.goods()) != null) {
					candidates.add(new GoodsSelector.Candidate(e.goods().toString(), e.weight(), e.minLevel()));
				}
			}
			list.add(new GoodsSelector.Pool(pool.rolls().min(), pool.rolls().max(), candidates));
		}
		return list;
	}

	/** {@code always} goods that have a definition. */
	public List<String> alwaysGoods() {
		return always.stream().filter(id -> GoodsCatalog.get(id) != null).map(Identifier::toString).toList();
	}
}
