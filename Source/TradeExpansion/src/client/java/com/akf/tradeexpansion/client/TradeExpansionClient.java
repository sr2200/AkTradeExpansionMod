package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class TradeExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.MARKET, MarketScreen::new);
		ClientNetworkHandlers.init();
	}
}
