package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * "Sell all" on the sell tab (A-4, v0.32): the goods and trading units the confirmation showed and the total the player
 * saw. The server sells all of them or none (same rule as single trades: a different total cancels).
 */
public record SellAllC2S(long marketId, List<Line> lines, long expectedTotal, boolean useCard) implements CustomPacketPayload {
	public static final Type<SellAllC2S> TYPE = new Type<>(TradeExpansion.id("sell_all"));

	/** One good: trading units to sell (B-5). */
	public record Line(Identifier goods, int units) {
		static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Line::goods,
				ByteBufCodecs.VAR_INT, Line::units,
				Line::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, SellAllC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, SellAllC2S::marketId,
			Line.CODEC.apply(ByteBufCodecs.list()), SellAllC2S::lines,
			ByteBufCodecs.VAR_LONG, SellAllC2S::expectedTotal,
			ByteBufCodecs.BOOL, SellAllC2S::useCard,
			SellAllC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
