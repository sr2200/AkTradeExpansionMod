package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.network.MarketSnapshotS2C;
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientMarketCache.clear());
	}
}
