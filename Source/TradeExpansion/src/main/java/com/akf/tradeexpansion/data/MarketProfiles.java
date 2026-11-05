package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.GoodsSelector;
import com.akf.tradeexpansion.data.condition.MarketCondition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Market profiles loaded from {@code data/<ns>/ak_trade_expansion/market_profile/*.json} (design D-2).
 * Profiles whose path starts with {@code default} are only used as the per-dimension fallback.
 */
public final class MarketProfiles {
	private static Map<Identifier, MarketProfile> profiles = Map.of();

	private MarketProfiles() {
	}

	public static MarketProfile get(Identifier id) {
		return id == null ? null : profiles.get(id);
	}

	public static Map<Identifier, MarketProfile> all() {
		return profiles;
	}

	static void replace(Map<Identifier, MarketProfile> loaded) {
		profiles = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	private static boolean isFallback(Identifier id) {
		return id.getPath().startsWith("default");
	}

	/**
	 * D-1 step 2: highest priority among matching profiles (ties: by weight), otherwise the dimension's default.
	 *
	 * @return the chosen profile id, or {@code null} if not even a default exists
	 */
	public static Identifier choose(MarketContext context, Random random) {
		List<Identifier> best = new ArrayList<>();
		int bestPriority = Integer.MIN_VALUE;
		for (Map.Entry<Identifier, MarketProfile> e : profiles.entrySet()) {
			if (isFallback(e.getKey()) || !MarketCondition.all(e.getValue().conditions(), context)) continue;
			int priority = e.getValue().priority();
			if (priority > bestPriority) {
				best.clear();
				bestPriority = priority;
			}
			if (priority == bestPriority) best.add(e.getKey());
		}
		if (!best.isEmpty()) {
			int[] weights = best.stream().mapToInt(id -> profiles.get(id).weight()).toArray();
			return best.get(GoodsSelector.pickIndex(weights, random));
		}
		Identifier fallback = defaultFor(context.world().dimension());
		if (profiles.containsKey(fallback)) return fallback;
		Identifier generic = TradeExpansion.id("default");
		return profiles.containsKey(generic) ? generic : null;
	}

	/** {@code default_overworld} / {@code default_the_nether} / {@code default_the_end} / {@code default}. */
	public static Identifier defaultFor(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) return TradeExpansion.id("default_overworld");
		if (dimension.equals(Level.NETHER)) return TradeExpansion.id("default_the_nether");
		if (dimension.equals(Level.END)) return TradeExpansion.id("default_the_end");
		return TradeExpansion.id("default");
	}
}
