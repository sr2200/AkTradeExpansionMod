package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Deliver crates" on the request tab (design J-2, v0.35): every cargo crate for this market in the inventory.
 * {@code useCard}: reward and deposit go to the bank account (E-8).
 */
public record CargoDeliverC2S(long marketId, boolean useCard) implements CustomPacketPayload {
	public static final Type<CargoDeliverC2S> TYPE = new Type<>(TradeExpansion.id("cargo_deliver"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CargoDeliverC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, CargoDeliverC2S::marketId,
			ByteBufCodecs.BOOL, CargoDeliverC2S::useCard,
			CargoDeliverC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
