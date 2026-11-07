package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's bank balance and whether they carry a valid card (design E-7 / E-8, F-2).
 * Sent when a market screen opens and after trades / bank operations.
 *
 * @param result      ordinal of {@code BankService.Result} of the last bank operation, or -1
 * @param interestCap balance up to which interest is paid now (N-4, v0.38: higher while registered with a bank building)
 * @param bankName    name of the market whose bank building the account is registered with, empty for none
 */
public record AccountS2C(long bankBalance, boolean validCard, int result, long interestCap, String bankName) implements CustomPacketPayload {
	public static final Type<AccountS2C> TYPE = new Type<>(TradeExpansion.id("account"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AccountS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, AccountS2C::bankBalance,
			ByteBufCodecs.BOOL, AccountS2C::validCard,
			ByteBufCodecs.VAR_INT, AccountS2C::result,
			ByteBufCodecs.VAR_LONG, AccountS2C::interestCap,
			ByteBufCodecs.STRING_UTF8, AccountS2C::bankName,
			AccountS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
