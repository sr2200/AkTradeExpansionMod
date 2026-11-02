package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.item.CoinItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
	private static final Map<CoinType, CoinItem> COINS = new EnumMap<>(CoinType.class);

	public static final CoinItem COPPER_COIN = registerCoin(CoinType.COPPER);
	public static final CoinItem IRON_COIN = registerCoin(CoinType.IRON);
	public static final CoinItem GOLD_COIN = registerCoin(CoinType.GOLD);
	public static final CoinItem DIAMOND_COIN = registerCoin(CoinType.DIAMOND);
	public static final CoinItem NETHERITE_COIN = registerCoin(CoinType.NETHERITE);

	private ModItems() {
	}

	public static CoinItem coin(CoinType type) {
		return COINS.get(type);
	}

	private static CoinItem registerCoin(CoinType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradeExpansion.id(type.path()));
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(64);
		if (type == CoinType.NETHERITE) {
			properties.fireResistant();
		}
		CoinItem item = Registry.register(BuiltInRegistries.ITEM, key, new CoinItem(type, properties));
		COINS.put(type, item);
		return item;
	}

	/** Forces class loading so the static fields above get registered. */
	public static void init() {
	}
}
