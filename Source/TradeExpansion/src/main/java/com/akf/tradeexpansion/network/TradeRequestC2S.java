package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Buy / sell button (design F-2).
 *
 * @param buy           true = the player buys, false = the player sells
 * @param expectedTotal total shown in the preview; the trade is cancelled if the server's total differs (A-4)
 */
public record TradeRequestC2S(long marketId, Identifier item, boolean buy, int quantity, long expectedTotal) implements CustomPacketPayload {
	public static final Type<TradeRequestC2S> TYPE = new Type<>(TradeExpansion.id("trade_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, TradeRequestC2S::marketId,
			Identifier.STREAM_CODEC, TradeRequestC2S::item,
			ByteBufCodecs.BOOL, TradeRequestC2S::buy,
			ByteBufCodecs.VAR_INT, TradeRequestC2S::quantity,
			ByteBufCodecs.VAR_LONG, TradeRequestC2S::expectedTotal,
			TradeRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
