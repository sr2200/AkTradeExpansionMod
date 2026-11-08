package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.ConfigSyncS2C;
import com.akf.tradeexpansion.network.ExchangeResultS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.network.TradeResultS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** S2C receivers (design 4-2: only the receiving side lives in the client source set). */
public final class ClientNetworkHandlers {
	private ClientNetworkHandlers() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MarketSnapshotS2C.TYPE, (payload, context) -> {
			ClientMarketCache.put(payload);
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onSnapshot(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TradeResultS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onResult(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AccountS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onAccount(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(BoardS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onBoard(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(QuestResultS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onQuestResult(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.akf.tradeexpansion.network.DevelopmentResultS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onDevelopmentResult(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ExchangeResultS2C.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof MarketScreen screen) {
				screen.onExchangeResult(payload);
			}
		});
		// Server settings needed for previews (design G); local settings come back on disconnect.
		// Stage 14a (design H): coin prices of the villager trade screen; an empty array means coin trades are off.
		ClientPlayNetworking.registerGlobalReceiver(com.akf.tradeexpansion.network.VillagerPricesS2C.TYPE, (payload, context) -> {
			java.util.UUID id = context.player().getUUID();
			if (payload.prices().length == 0) com.akf.tradeexpansion.villager.VillagerCoins.clear(id);
			else com.akf.tradeexpansion.villager.VillagerCoins.put(id, new com.akf.tradeexpansion.villager.VillagerCoins.Prices(payload.containerId(), payload.prices()));
			if (context.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu) menu.updateSellItem();
		});
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2C.TYPE, (payload, context) -> payload.applyOnClient());
		// I (v0.47): recipe viewer pages
		ClientPlayNetworking.registerGlobalReceiver(com.akf.tradeexpansion.network.TradableItemsS2C.TYPE,
				(payload, context) -> PriceLookupCache.setTradable(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.akf.tradeexpansion.network.PriceLookupS2C.TYPE,
				(payload, context) -> PriceLookupCache.put(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientMarketCache.clear();
			PriceLookupCache.clear();
			ConfigManager.load();
		});
	}
}
