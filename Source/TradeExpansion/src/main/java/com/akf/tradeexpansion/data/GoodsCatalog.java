package com.akf.tradeexpansion.data;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Goods definitions loaded from {@code data/<ns>/trade_expansion/goods/*.json} (design D-2), keyed by goods id
 * (the file id, e.g. {@code trade_expansion:bread}). Replaced on every data pack (re)load.
 */
public final class GoodsCatalog {
	private static Map<Identifier, GoodsDefinition> goods = Map.of();

	private GoodsCatalog() {
	}

	public static GoodsDefinition get(Identifier goodsId) {
		return goods.get(goodsId);
	}

	public static Map<Identifier, GoodsDefinition> all() {
		return goods;
	}

	static void replace(Map<Identifier, GoodsDefinition> loaded) {
		goods = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}
}
