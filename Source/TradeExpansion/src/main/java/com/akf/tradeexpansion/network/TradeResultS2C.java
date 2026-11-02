package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Outcome of a {@link TradeRequestC2S} (design F-2). */
public record TradeResultS2C(TradeResult result, boolean buy, int quantity, long total) implements CustomPacketPayload {
	public static final Type<TradeResultS2C> TYPE = new Type<>(TradeExpansion.id("trade_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeResultS2C> CODEC = StreamCodec.composite(
			TradeResult.STREAM_CODEC, TradeResultS2C::result,
			ByteBufCodecs.BOOL, TradeResultS2C::buy,
			ByteBufCodecs.VAR_INT, TradeResultS2C::quantity,
			ByteBufCodecs.VAR_LONG, TradeResultS2C::total,
			TradeResultS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
