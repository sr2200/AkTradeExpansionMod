package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Exchange tab button (design E-3, F-2).
 *
 * @param coin  {@code CoinType} ordinal of the coins given (down) or the tier below the coins wanted (up)
 * @param up    true: lower coins into {@code count} coins of the next tier; false: {@code count} coins into lower ones
 */
public record ExchangeRequestC2S(long marketId, int coin, boolean up, int count) implements CustomPacketPayload {
	public static final Type<ExchangeRequestC2S> TYPE = new Type<>(TradeExpansion.id("exchange_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, ExchangeRequestC2S::marketId,
			ByteBufCodecs.VAR_INT, ExchangeRequestC2S::coin,
			ByteBufCodecs.BOOL, ExchangeRequestC2S::up,
			ByteBufCodecs.VAR_INT, ExchangeRequestC2S::count,
			ExchangeRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
