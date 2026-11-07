package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.BankCardItem;
import com.akf.tradeexpansion.item.CargoCrateItem;
import com.akf.tradeexpansion.item.EmeraldCoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public final class ModItems {
	private static final Map<CoinType, CoinItem> COINS = new EnumMap<>(CoinType.class);

	public static final CoinItem COPPER_COIN = registerCoin(CoinType.COPPER);
	public static final CoinItem IRON_COIN = registerCoin(CoinType.IRON);
	public static final CoinItem GOLD_COIN = registerCoin(CoinType.GOLD);
	public static final CoinItem DIAMOND_COIN = registerCoin(CoinType.DIAMOND);
	public static final CoinItem NETHERITE_COIN = registerCoin(CoinType.NETHERITE);

	/** Token coin of design E-5 (not part of the wallet). */
	public static final EmeraldCoinItem EMERALD_COIN = register("emerald_coin", EmeraldCoinItem::new, 64);

	/** Wallet (E-6) and bank card (E-8). */
	public static final WalletItem WALLET = register("wallet", WalletItem::new, 1);
	public static final BankCardItem BANK_CARD = register("bank_card", BankCardItem::new, 1);

	/** J-2 (v0.35): a transport request's goods, carried from one market to another. Not craftable, cannot be opened. */
	public static final CargoCrateItem CARGO_CRATE = register("cargo_crate", CargoCrateItem::new, 1);

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

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, int maxStack) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradeExpansion.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key).stacksTo(maxStack)));
	}

	/** Forces class loading so the static fields above get registered. */
	public static void init() {
	}
}
