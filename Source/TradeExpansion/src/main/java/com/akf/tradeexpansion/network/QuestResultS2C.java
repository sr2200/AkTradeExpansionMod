package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Outcome of a request action (design J / J-2). Result codes are shared with trades.
 *
 * @param amount DELIVER: the reward; TAKE_CARGO: the deposit paid; DELIVER_CARGO: reward + deposit paid out
 * @param count  DELIVER_CARGO: crates delivered
 */
public record QuestResultS2C(TradeResult result, Kind kind, long amount, int count) implements CustomPacketPayload {
	public enum Kind {
		DELIVER, TAKE_CARGO, DELIVER_CARGO
	}

	public static final Type<QuestResultS2C> TYPE = new Type<>(TradeExpansion.id("quest_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuestResultS2C> CODEC = StreamCodec.composite(
			TradeResult.STREAM_CODEC, QuestResultS2C::result,
			ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), QuestResultS2C::kind,
			ByteBufCodecs.VAR_LONG, QuestResultS2C::amount,
			ByteBufCodecs.VAR_INT, QuestResultS2C::count,
			QuestResultS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
