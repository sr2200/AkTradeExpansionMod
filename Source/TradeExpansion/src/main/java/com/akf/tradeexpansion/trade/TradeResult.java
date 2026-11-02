package com.akf.tradeexpansion.trade;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** Result codes of a trade (design A-4 / B-5). */
public enum TradeResult {
	SUCCESS,
	INVALID_REQUEST,
	TOO_FAR,
	CREATIVE_NOT_ALLOWED,
	NOT_AVAILABLE,
	INSUFFICIENT_STOCK,
	INSUFFICIENT_FUNDS,
	INSUFFICIENT_ITEMS,
	INVENTORY_FULL,
	STOCK_FULL,
	PRICE_CHANGED;

	public static final StreamCodec<ByteBuf, TradeResult> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(i -> values()[i], TradeResult::ordinal);

	public String translationKey() {
		return "trade.trade_expansion.result." + name().toLowerCase(Locale.ROOT);
	}
}
