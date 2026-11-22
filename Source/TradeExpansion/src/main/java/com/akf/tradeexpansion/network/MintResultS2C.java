package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Outcome of a {@link MintRequestC2S} (design N-4); {@code value} is the copper value of the coins minted. */
public record MintResultS2C(TradeResult result, long value) implements CustomPacketPayload {
	public static final Type<MintResultS2C> TYPE = new Type<>(TradeExpansion.id("mint_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MintResultS2C> CODEC = StreamCodec.composite(
			TradeResult.STREAM_CODEC.cast(), MintResultS2C::result,
			ByteBufCodecs.VAR_LONG, MintResultS2C::value,
			MintResultS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
