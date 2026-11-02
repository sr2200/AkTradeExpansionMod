package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bank tab button (design E-7 / E-8, F-2). {@code amount} is ignored for ISSUE_CARD. */
public record BankRequestC2S(long marketId, Action action, long amount) implements CustomPacketPayload {
	public enum Action { DEPOSIT, WITHDRAW, ISSUE_CARD }

	public static final Type<BankRequestC2S> TYPE = new Type<>(TradeExpansion.id("bank_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BankRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, BankRequestC2S::marketId,
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), BankRequestC2S::action,
			ByteBufCodecs.VAR_LONG, BankRequestC2S::amount,
			BankRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
