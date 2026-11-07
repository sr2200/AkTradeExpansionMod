package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Development tab (design N-6): donate coins / materials, build a facility, take a construction marker.
 *
 * @param amount   DONATE_COINS: copper to donate
 * @param facility DONATE_ITEMS / BUILD: facility id ({@code ak_trade_expansion:warehouse}), empty otherwise
 * @param useCard  DONATE_COINS: pay from the bank account (E-8, no card fee)
 */
public record DevelopmentRequestC2S(long marketId, Action action, long amount, String facility, boolean useCard) implements CustomPacketPayload {
	public enum Action {
		DONATE_COINS, DONATE_ITEMS, BUILD, MARKER
	}

	public static final Type<DevelopmentRequestC2S> TYPE = new Type<>(TradeExpansion.id("development_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DevelopmentRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, DevelopmentRequestC2S::marketId,
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), DevelopmentRequestC2S::action,
			ByteBufCodecs.VAR_LONG, DevelopmentRequestC2S::amount,
			ByteBufCodecs.STRING_UTF8, DevelopmentRequestC2S::facility,
			ByteBufCodecs.BOOL, DevelopmentRequestC2S::useCard,
			DevelopmentRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
