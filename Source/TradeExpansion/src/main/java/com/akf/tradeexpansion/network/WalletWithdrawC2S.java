package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Take coins out of the wallet held in a hand (design E-6, F-2).
 *
 * @param offhand the wallet is in the off hand
 * @param coin    {@code CoinType} ordinal
 */
public record WalletWithdrawC2S(boolean offhand, int coin, int count) implements CustomPacketPayload {
	public static final Type<WalletWithdrawC2S> TYPE = new Type<>(TradeExpansion.id("wallet_withdraw"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WalletWithdrawC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, WalletWithdrawC2S::offhand,
			ByteBufCodecs.VAR_INT, WalletWithdrawC2S::coin,
			ByteBufCodecs.VAR_INT, WalletWithdrawC2S::count,
			WalletWithdrawC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
