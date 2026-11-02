package com.akf.tradeexpansion.data;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All known goods definitions. Built-in list for now; stage 7 replaces it with the
 * {@code trade_expansion/goods} data pack loader.
 */
public final class GoodsCatalog {
	private static final Map<Identifier, GoodsDefinition> GOODS = new LinkedHashMap<>();

	static {
		List.of(
				GoodsDefinition.of("minecraft:bread", 20, 32, 16),
				GoodsDefinition.of("minecraft:wheat", 4, 128, 64),
				GoodsDefinition.of("minecraft:cooked_beef", 30, 32, 16),
				GoodsDefinition.of("minecraft:apple", 12, 64, 32),
				GoodsDefinition.of("minecraft:torch", 3, 128, 64),
				GoodsDefinition.of("minecraft:iron_ingot", 60, 32, 16),
				GoodsDefinition.of("minecraft:emerald", 100, 32, 8),
				GoodsDefinition.of("minecraft:golden_apple", 450, 16, 4),
				GoodsDefinition.of("minecraft:diamond_sword", 900, 16, 8).withMaxStock(24).withRestock(4),
				GoodsDefinition.of("minecraft:rotten_flesh", 2, 128, 64).sellOnly()
		).forEach(def -> GOODS.put(def.item(), def));
	}

	private GoodsCatalog() {
	}

	public static GoodsDefinition get(Identifier item) {
		return GOODS.get(item);
	}

	public static Collection<GoodsDefinition> all() {
		return Collections.unmodifiableCollection(GOODS.values());
	}
}
