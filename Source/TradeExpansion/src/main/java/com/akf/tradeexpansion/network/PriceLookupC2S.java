package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** I (v0.47): a recipe viewer page asks for the prices of an item at the markets nearest to the player. */
public record PriceLookupC2S(Identifier item) implements CustomPacketPayload {
	public static final Type<PriceLookupC2S> TYPE = new Type<>(TradeExpansion.id("price_lookup"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PriceLookupC2S> CODEC =
			Identifier.STREAM_CODEC.map(PriceLookupC2S::new, PriceLookupC2S::item).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
