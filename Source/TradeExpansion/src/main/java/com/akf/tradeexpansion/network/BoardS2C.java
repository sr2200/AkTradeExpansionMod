package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Latest prices of nearby markets for the bulletin board (design I).
 *
 * @param marketId the market whose screen asked
 */
public record BoardS2C(long marketId, List<Entry> markets) implements CustomPacketPayload {
	public static final Type<BoardS2C> TYPE = new Type<>(TradeExpansion.id("board"));

	/**
	 * @param buy / sell price per trading unit, or -1 when not available (sold out, not bought, ...)
	 * @param buyDeal  % cheaper to buy than at the current market (or the standard price when it does not deal in it), 0 = no deal
	 * @param sellDeal % more paid than at the current market (or the standard price), 0 = no deal
	 */
	public record Good(Identifier item, int unit, long buy, long sell, int stock, boolean shortage, int buyDeal, int sellDeal) {
		static final StreamCodec<RegistryFriendlyByteBuf, Good> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Good::item,
				ByteBufCodecs.VAR_INT, Good::unit,
				ByteBufCodecs.VAR_LONG, Good::buy,
				ByteBufCodecs.VAR_LONG, Good::sell,
				ByteBufCodecs.VAR_INT, Good::stock,
				ByteBufCodecs.BOOL, Good::shortage,
				ByteBufCodecs.VAR_INT, Good::buyDeal,
				ByteBufCodecs.VAR_INT, Good::sellDeal,
				Good::new);
	}

	/**
	 * @param title  "name (level)" (C-4)
	 * @param dx / dz offset of the market center from the current market's center
	 */
	public record Entry(long id, Component title, int dx, int dz, List<Good> goods) {
		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Entry::id,
				ComponentSerialization.STREAM_CODEC, Entry::title,
				ByteBufCodecs.VAR_INT, Entry::dx,
				ByteBufCodecs.VAR_INT, Entry::dz,
				Good.CODEC.apply(ByteBufCodecs.list()), Entry::goods,
				Entry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, BoardS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, BoardS2C::marketId,
			Entry.CODEC.apply(ByteBufCodecs.list()), BoardS2C::markets,
			BoardS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
