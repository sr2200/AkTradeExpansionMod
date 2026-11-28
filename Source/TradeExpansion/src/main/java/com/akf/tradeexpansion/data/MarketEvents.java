package com.akf.tradeexpansion.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market events (design V-1) loaded from {@code data/<ns>/ak_trade_expansion/market_event/*.json}, in id order: for a
 * few days the base price of some goods goes down or up around one market ("a good harvest", "a mine accident").
 */
public final class MarketEvents {
	/**
	 * @param goods      goods the event moves
	 * @param groups     goods groups (O-1) whose goods it also moves
	 * @param minMultiplier / maxMultiplier range of the multiplier of B at the center (both below 1, or both above)
	 * @param minDays / maxDays range of the length in days
	 * @param weight     how often it is chosen (0 = never)
	 * @param profiles   market profiles its center may have (empty = any)
	 * @param dimensions dimensions its center may be in (empty = any)
	 */
	public record Definition(List<Identifier> goods, List<Identifier> groups, double minMultiplier, double maxMultiplier, int minDays,
			int maxDays, int weight, List<Identifier> profiles, List<Identifier> dimensions) {
		public static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.listOf().optionalFieldOf("goods", List.of()).forGetter(Definition::goods),
				Identifier.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(Definition::groups),
				Codec.doubleRange(0.05, 10).fieldOf("min_multiplier").forGetter(Definition::minMultiplier),
				Codec.doubleRange(0.05, 10).fieldOf("max_multiplier").forGetter(Definition::maxMultiplier),
				Codec.intRange(1, 60).optionalFieldOf("min_days", 3).forGetter(Definition::minDays),
				Codec.intRange(1, 60).optionalFieldOf("max_days", 5).forGetter(Definition::maxDays),
				Codec.intRange(0, 1000).optionalFieldOf("weight", 1).forGetter(Definition::weight),
				Identifier.CODEC.listOf().optionalFieldOf("profiles", List.of()).forGetter(Definition::profiles),
				Identifier.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(Definition::dimensions)
		).apply(i, Definition::new));

		/** Prices go up (the multiplier is above 1). */
		public boolean raises() {
			return minMultiplier + maxMultiplier > 2;
		}

		/** Both ends of the multiplier are on the same side of 1 and in order. */
		public boolean valid() {
			return minMultiplier <= maxMultiplier && minDays <= maxDays
					&& (minMultiplier >= 1 && maxMultiplier > 1 || maxMultiplier <= 1 && minMultiplier < 1);
		}

		/** The goods it moves: {@link #goods} then the goods of {@link #groups}, without duplicates, in that order. */
		public List<Identifier> allGoods() {
			List<Identifier> list = new ArrayList<>(goods);
			for (Identifier group : groups) {
				GoodsGroups.Group g = GoodsGroups.get(group);
				if (g == null) continue;
				for (Identifier id : g.goods()) if (!list.contains(id)) list.add(id);
			}
			return list;
		}
	}

	private static Map<Identifier, Definition> events = Map.of();

	private MarketEvents() {
	}

	public static Map<Identifier, Definition> all() {
		return events;
	}

	public static Definition get(Identifier id) {
		return id == null ? null : events.get(id);
	}

	/** Translation key of the event: {@code market_event.<ns>.<path>} (+ {@code .desc}, {@code .forecast}). */
	public static String key(Identifier event) {
		return "market_event." + event.getNamespace() + "." + event.getPath().replace('/', '.');
	}

	/** Display name ("豊作"). */
	public static Component name(Identifier event) {
		return Component.translatable(key(event));
	}

	static void replace(Map<Identifier, Definition> loaded) {
		Map<Identifier, Definition> sorted = new LinkedHashMap<>();
		loaded.keySet().stream().sorted().forEach(id -> sorted.put(id, loaded.get(id)));
		events = Collections.unmodifiableMap(sorted);
	}
}
