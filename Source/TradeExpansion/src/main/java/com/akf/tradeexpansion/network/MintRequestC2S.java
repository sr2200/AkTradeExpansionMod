package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Minting at the mint (design N-4, F-2): {@code batches} times 9 blocks of {@code metal} into coins.
 *
 * @param metal   index of {@code MintService.METALS} (0 = copper block, 1 = iron block)
 * @param batches mintings of 9 blocks each (1..64)
 */
public record MintRequestC2S(long marketId, int metal, int batches) implements CustomPacketPayload {
	public static final Type<MintRequestC2S> TYPE = new Type<>(TradeExpansion.id("mint_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MintRequestC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, MintRequestC2S::marketId,
			ByteBufCodecs.VAR_INT, MintRequestC2S::metal,
			ByteBufCodecs.VAR_INT, MintRequestC2S::batches,
			MintRequestC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
