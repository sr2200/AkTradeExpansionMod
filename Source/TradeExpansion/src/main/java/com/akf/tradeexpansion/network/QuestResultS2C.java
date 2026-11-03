package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Outcome of a {@link QuestDeliverC2S} (design J). Result codes are shared with trades. */
public record QuestResultS2C(TradeResult result, long reward) implements CustomPacketPayload {
	public static final Type<QuestResultS2C> TYPE = new Type<>(TradeExpansion.id("quest_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuestResultS2C> CODEC = StreamCodec.composite(
			TradeResult.STREAM_CODEC, QuestResultS2C::result,
			ByteBufCodecs.VAR_LONG, QuestResultS2C::reward,
			QuestResultS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
