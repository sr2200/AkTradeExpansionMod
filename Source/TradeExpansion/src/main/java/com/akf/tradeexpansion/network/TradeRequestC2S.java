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
 * @param goods         goods id (not the item id)
 * @param buy           true = the player buys, false = the player sells
 * @param quantity      number of trading units (B-5)
 * @param expectedTotal total shown in the preview; the trade is cancelled if the server's total differs (A-4)
 * @param useCard       pay / receive through the bank account instead of cash (E-8)
 * @param all           Shift: up to the largest possible quantity, beyond {@code trade.maxPerTransaction} (A-4, Q13)
 */
public record TradeRequestC2S(long marketId, Identifier goods, boolean buy, int quantity, long expectedTotal, boolean useCard, boolean all) implements CustomPacketPayload {
	public static final Type<TradeRequestC2S> TYPE = new Type<>(TradeExpansion.id("trade_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, TradeRequestC2S::marketId,
			Identifier.STREAM_CODEC, TradeRequestC2S::goods,
			ByteBufCodecs.BOOL, TradeRequestC2S::buy,
			ByteBufCodecs.VAR_INT, TradeRequestC2S::quantity,
			ByteBufCodecs.VAR_LONG, TradeRequestC2S::expectedTotal,
			ByteBufCodecs.BOOL, TradeRequestC2S::useCard,
			ByteBufCodecs.BOOL, TradeRequestC2S::all,
			TradeRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
