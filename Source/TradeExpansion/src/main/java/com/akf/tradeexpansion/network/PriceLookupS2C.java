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
 * I (v0.47): prices of an item at the markets of the player's dimension that deal in it, nearest first.
 *
 * @param item    the item that was asked for
 * @param radius  only markets within this horizontal distance of the player are listed ({@code board.radius})
 * @param markets at most {@code PriceLookupService.MAX_MARKETS} markets (one entry per goods definition of the item)
 */
public record PriceLookupS2C(Identifier item, int radius, List<Entry> markets) implements CustomPacketPayload {
	public static final Type<PriceLookupS2C> TYPE = new Type<>(TradeExpansion.id("price_lookup_result"));

	/**
	 * @param title    "name (level)" (C-4)
	 * @param distance horizontal distance from the player to the market center, in blocks
	 * @param dx / dz  offset of the market center from the player
	 * @param unit     items per trading unit
	 * @param buy / sell price per trading unit as this player would get it, or -1 when not available (sold out, not bought, ...)
	 * @param stock    current stock
	 * @param shortage the market is short of it (K)
	 */
	public record Entry(long id, Component title, int distance, int dx, int dz, int unit, long buy, long sell, int stock,
			boolean shortage) {
		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.ofMember(Entry::write, Entry::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			ComponentSerialization.STREAM_CODEC.encode(buf, title);
			buf.writeVarInt(distance);
			buf.writeVarInt(dx);
			buf.writeVarInt(dz);
			buf.writeVarInt(unit);
			buf.writeVarLong(buy);
			buf.writeVarLong(sell);
			buf.writeVarInt(stock);
			buf.writeBoolean(shortage);
		}

		private static Entry read(RegistryFriendlyByteBuf buf) {
			return new Entry(buf.readVarLong(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readBoolean());
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, PriceLookupS2C> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, PriceLookupS2C::item,
			ByteBufCodecs.VAR_INT, PriceLookupS2C::radius,
			Entry.CODEC.apply(ByteBufCodecs.list()), PriceLookupS2C::markets,
			PriceLookupS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
