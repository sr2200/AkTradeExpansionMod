package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Outcome of an {@link ExchangeRequestC2S} (design E-3). */
public record ExchangeResultS2C(TradeResult result) implements CustomPacketPayload {
	public static final Type<ExchangeResultS2C> TYPE = new Type<>(TradeExpansion.id("exchange_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeResultS2C> CODEC =
			TradeResult.STREAM_CODEC.map(ExchangeResultS2C::new, ExchangeResultS2C::result).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
