package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Outcome of a development tab action (design N-6). Result codes are shared with trades.
 *
 * @param amount DONATE_COINS: copper donated; DONATE_ITEMS: items donated
 * @param points development points gained (donations) or spent (BUILD)
 */
public record DevelopmentResultS2C(TradeResult result, DevelopmentRequestC2S.Action action, long amount, long points) implements CustomPacketPayload {
	public static final Type<DevelopmentResultS2C> TYPE = new Type<>(TradeExpansion.id("development_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DevelopmentResultS2C> CODEC = StreamCodec.composite(
			TradeResult.STREAM_CODEC, DevelopmentResultS2C::result,
			ByteBufCodecs.VAR_INT.map(i -> DevelopmentRequestC2S.Action.values()[i], DevelopmentRequestC2S.Action::ordinal), DevelopmentResultS2C::action,
			ByteBufCodecs.VAR_LONG, DevelopmentResultS2C::amount,
			ByteBufCodecs.VAR_LONG, DevelopmentResultS2C::points,
			DevelopmentResultS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
