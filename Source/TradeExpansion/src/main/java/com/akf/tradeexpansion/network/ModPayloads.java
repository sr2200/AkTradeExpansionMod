package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.board.BoardService;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.currency.ExchangeService;
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
		PayloadTypeRegistry.clientboundPlay().register(BoardS2C.TYPE, BoardS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillagerPricesS2C.TYPE, VillagerPricesS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ExchangeResultS2C.TYPE, ExchangeResultS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ExchangeRequestC2S.TYPE, ExchangeRequestC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestResultS2C.TYPE, QuestResultS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BoardRequestC2S.TYPE, BoardRequestC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestDeliverC2S.TYPE, QuestDeliverC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestAcceptC2S.TYPE, QuestAcceptC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeRequestC2S.TYPE, TradeRequestC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BankRequestC2S.TYPE, BankRequestC2S.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WalletWithdrawC2S.TYPE, WalletWithdrawC2S.CODEC);

		// Handlers run on the server thread, so trades never race each other (F-2).
		ServerPlayNetworking.registerGlobalReceiver(TradeRequestC2S.TYPE,
				(payload, context) -> TradeService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BankRequestC2S.TYPE,
				(payload, context) -> BankService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ExchangeRequestC2S.TYPE,
				(payload, context) -> ExchangeService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BoardRequestC2S.TYPE,
				(payload, context) -> BoardService.handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(QuestDeliverC2S.TYPE,
				(payload, context) -> QuestService.deliver(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(QuestAcceptC2S.TYPE,
				(payload, context) -> QuestService.accept(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WalletWithdrawC2S.TYPE,
				(payload, context) -> WalletService.withdraw(context.player(), payload.offhand(), payload.coin(), payload.count()));
	}
}
