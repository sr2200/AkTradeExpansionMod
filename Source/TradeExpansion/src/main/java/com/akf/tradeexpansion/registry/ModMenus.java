package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.menu.MarketMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
	public static final ExtendedMenuType<MarketMenu, MarketMenu.OpenData> MARKET = Registry.register(
			BuiltInRegistries.MENU, TradeExpansion.id("market"),
			new ExtendedMenuType<>(MarketMenu::new, MarketMenu.OpenData.STREAM_CODEC));

	private ModMenus() {
	}

	public static void init() {
	}
}
