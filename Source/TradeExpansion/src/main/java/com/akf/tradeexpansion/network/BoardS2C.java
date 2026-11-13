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
 * @param radius   the radius actually used, in blocks ({@code board.radius} of the server, times the tavern's multiplier)
 */
public record BoardS2C(long marketId, int radius, List<Entry> markets, List<News> news) implements CustomPacketPayload {
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
	 * @param center  the market center (v0.34: for map waypoints)
	 * @param trends  P (v0.46): the market's trends
	 */
	public record Entry(long id, Component title, int dx, int dz, net.minecraft.core.BlockPos center, List<Good> goods,
			List<MarketSnapshotS2C.TrendView> trends) {
		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Entry::id,
				ComponentSerialization.STREAM_CODEC, Entry::title,
				ByteBufCodecs.VAR_INT, Entry::dx,
				ByteBufCodecs.VAR_INT, Entry::dz,
				net.minecraft.core.BlockPos.STREAM_CODEC, Entry::center,
				Good.CODEC.apply(ByteBufCodecs.list()), Entry::goods,
				MarketSnapshotS2C.TrendView.CODEC.apply(ByteBufCodecs.list()), Entry::trends,
				Entry::new);
	}

	/**
	 * O-5 (v0.46): a running rival campaign.
	 *
	 * @param market   name of the center market
	 * @param marketId the center market (to open it on the board when it is listed)
	 * @param dx / dz  offset of the center market from the current market, 0 / 0 when it is the current market
	 * @param daysLeft days after today (0 = ends today)
	 * @param felt     prices of the current market are moved by it
	 */
	public record News(Identifier guild, boolean buying, Identifier item, Component market, long marketId, int dx, int dz, int daysLeft,
			boolean felt) {
		static final StreamCodec<RegistryFriendlyByteBuf, News> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, News::guild,
				ByteBufCodecs.BOOL, News::buying,
				Identifier.STREAM_CODEC, News::item,
				ComponentSerialization.STREAM_CODEC, News::market,
				ByteBufCodecs.VAR_LONG, News::marketId,
				ByteBufCodecs.VAR_INT, News::dx,
				ByteBufCodecs.VAR_INT, News::dz,
				ByteBufCodecs.VAR_INT, News::daysLeft,
				ByteBufCodecs.BOOL, News::felt,
				News::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, BoardS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, BoardS2C::marketId,
			ByteBufCodecs.VAR_INT, BoardS2C::radius,
			Entry.CODEC.apply(ByteBufCodecs.list()), BoardS2C::markets,
			News.CODEC.apply(ByteBufCodecs.list()), BoardS2C::news,
			BoardS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
