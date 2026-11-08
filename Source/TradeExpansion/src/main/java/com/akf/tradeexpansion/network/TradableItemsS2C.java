package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * I (v0.47): the items that some goods definition trades, for the recipe viewer pages (JEI / REI). Sent on join, after a
 * data pack reload and after {@code /aktradeex reload}.
 */
public record TradableItemsS2C(List<Identifier> items) implements CustomPacketPayload {
	public static final Type<TradableItemsS2C> TYPE = new Type<>(TradeExpansion.id("tradable_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradableItemsS2C> CODEC =
			Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(TradableItemsS2C::new, TradableItemsS2C::items).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
