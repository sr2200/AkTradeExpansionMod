package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Deliver" on the request tab (design J). {@code useCard}: the reward goes to the bank account (E-8). */
public record QuestDeliverC2S(long marketId, int requestId, boolean useCard) implements CustomPacketPayload {
	public static final Type<QuestDeliverC2S> TYPE = new Type<>(TradeExpansion.id("quest_deliver"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuestDeliverC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, QuestDeliverC2S::marketId,
			ByteBufCodecs.VAR_INT, QuestDeliverC2S::requestId,
			ByteBufCodecs.BOOL, QuestDeliverC2S::useCard,
			QuestDeliverC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
