package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Goods definitions loaded from {@code data/<ns>/trade_expansion/goods/*.json} (design D-2), keyed by goods id
 * (the file id, e.g. {@code trade_expansion:bread}). Replaced on every data pack (re)load.
 */
public final class GoodsCatalog {
	private static Map<Identifier, GoodsDefinition> goods = Map.of();
	/** {@code components} decoded with the server registries; filled lazily, cleared on reload. */
	private static final Map<Identifier, DataComponentPatch> PATCHES = new ConcurrentHashMap<>();
	/** Registries of the running server, set on start (components need them to decode). */
	private static volatile HolderLookup.Provider registries;

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
		PATCHES.clear();
	}

	public static void setRegistries(HolderLookup.Provider provider) {
		registries = provider;
		PATCHES.clear();
	}

	public static DataComponentPatch patch(Identifier goodsId) {
		return registries == null ? DataComponentPatch.EMPTY : patch(goodsId, registries);
	}

	public static InventoryUtil.Match match(Identifier goodsId) {
		GoodsDefinition def = goods.get(goodsId);
		Item item = BuiltInRegistries.ITEM.getValue(def.item());
		return new InventoryUtil.Match(item, patch(goodsId), def.acceptsDamaged() && def.unit() == 1);
	}

	/** The components a stack of this good must have (D-2); empty for plain goods or when the JSON is invalid. */
	public static DataComponentPatch patch(Identifier goodsId, HolderLookup.Provider provider) {
		GoodsDefinition def = goods.get(goodsId);
		if (def == null || def.components().isEmpty()) return DataComponentPatch.EMPTY;
		return PATCHES.computeIfAbsent(goodsId, id -> {
			JsonElement json = def.components().get().convert(JsonOps.INSTANCE).getValue();
			return DataComponentPatch.CODEC.parse(provider.createSerializationContext(JsonOps.INSTANCE), json)
					.resultOrPartial(error -> TradeExpansion.LOGGER.warn("Goods {}: invalid components: {}", id, error))
					.orElse(DataComponentPatch.EMPTY);
		});
	}
}
