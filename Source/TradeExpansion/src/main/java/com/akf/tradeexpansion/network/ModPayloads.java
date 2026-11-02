package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.currency.WalletService;
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
		PayloadTypeRegistry.clientboundPlay().register(ConfigSyncS2C.TYPE, ConfigSyncS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AccountS2C.TYPE, AccountS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeRequestC2S.TYPE, TradeRequestC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BankRequestC2S.TYPE, BankRequestC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WalletWithdrawC2S.TYPE, WalletWithdrawC2S.CODEC);

		// Handlers run on the server thread, so trades never race each other (F-2).
		ServerPlayNetworking.registerGlobalReceiver(TradeRequestC2S.TYPE,
				(payload, context) -> TradeService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BankRequestC2S.TYPE,
				(payload, context) -> BankService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WalletWithdrawC2S.TYPE,
				(payload, context) -> WalletService.withdraw(context.player(), payload.offhand(), payload.coin(), payload.count()));
	}
}
