package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers payload types (common side) and the server-side receivers.
 * S2C receivers live in the client source set.
 */
public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(MarketSnapshotS2C.TYPE, MarketSnapshotS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TradeResultS2C.TYPE, TradeResultS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeRequestC2S.TYPE, TradeRequestC2S.CODEC);

		// Handlers run on the server thread, so trades never race each other (F-2).
		ServerPlayNetworking.registerGlobalReceiver(TradeRequestC2S.TYPE,
				(payload, context) -> TradeService.handle(context.player(), payload));
	}
}
