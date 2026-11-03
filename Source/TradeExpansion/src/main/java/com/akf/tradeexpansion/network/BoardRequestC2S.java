package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Opening the bulletin board tab (design I). */
public record BoardRequestC2S(long marketId) implements CustomPacketPayload {
	public static final Type<BoardRequestC2S> TYPE = new Type<>(TradeExpansion.id("board_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BoardRequestC2S> CODEC =
			ByteBufCodecs.VAR_LONG.map(BoardRequestC2S::new, BoardRequestC2S::marketId).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
