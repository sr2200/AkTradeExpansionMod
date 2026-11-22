package com.akf.tradeexpansion.data;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * U (v0.82): goods that get cheaper in a rainy spell, loaded from {@code data/<ns>/ak_trade_expansion/rain_goods/*.json}
 * (same format as a goods group; every file is added together).
 */
public final class RainGoods {
	private static Set<Identifier> goods = Set.of();

	private RainGoods() {
	}

	public static boolean contains(Identifier goodsId) {
		return goods.contains(goodsId);
	}

	/** In load order. */
	public static Set<Identifier> all() {
		return goods;
	}

	static void replace(Set<Identifier> loaded) {
		goods = Collections.unmodifiableSet(new LinkedHashSet<>(loaded));
	}
}
