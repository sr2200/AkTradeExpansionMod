package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.InteractionResult;

public class TradeExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.MARKET, MarketScreen::new);
		ClientNetworkHandlers.init();

		// Using a wallet opens its screen on the client (E-6); the server side of the use does nothing.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && player.getItemInHand(hand).getItem() instanceof WalletItem) {
				Minecraft.getInstance().gui.setScreen(new WalletScreen(hand));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}
}
