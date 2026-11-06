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
		// A-4 (v0.33): the trade screen grows with the window (decided when it opens).
		com.akf.tradeexpansion.menu.MarketMenu.clientLayout = () -> {
			com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
			return com.akf.tradeexpansion.menu.MarketLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight());
		};
		net.minecraft.client.renderer.entity.EntityRenderers.register(com.akf.tradeexpansion.registry.ModEntities.TRADER, TraderRenderer::new);
		ClientNetworkHandlers.init();
		ItemTooltips.init();

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
