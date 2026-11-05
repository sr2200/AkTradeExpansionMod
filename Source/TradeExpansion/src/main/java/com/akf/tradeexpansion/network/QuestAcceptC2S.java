package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Accept" on the request tab (design J / M, v0.29): delivering it gives more reputation, letting it expire costs some. */
public record QuestAcceptC2S(long marketId, int requestId) implements CustomPacketPayload {
	public static final Type<QuestAcceptC2S> TYPE = new Type<>(TradeExpansion.id("quest_accept"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuestAcceptC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, QuestAcceptC2S::marketId,
			ByteBufCodecs.VAR_INT, QuestAcceptC2S::requestId,
			QuestAcceptC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
