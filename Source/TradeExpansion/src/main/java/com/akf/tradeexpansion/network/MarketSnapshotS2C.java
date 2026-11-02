package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Full state of one market, sent when a screen opens and whenever the market changes (design F-2).
 * Carries the resolved params so the client can preview totals with the same {@code PriceModel} as the server.
 *
 * @param gameTime server game time the state was refreshed to; the client extrapolates recovery from it
 * @param level    market level (D-4); the bank tab shows from {@code bank.minMarketLevel} (E-7)
 */
public record MarketSnapshotS2C(long marketId, BlockPos center, long gameTime, int level, List<GoodView> goods) implements CustomPacketPayload {
	public static final Type<MarketSnapshotS2C> TYPE = new Type<>(TradeExpansion.id("market_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketSnapshotS2C> CODEC = StreamCodec.ofMember(MarketSnapshotS2C::write, MarketSnapshotS2C::read);

	/**
	 *  goods goods id (key of the trade request)
	 *  item  item shown and moved
	 *  unit  items per trading unit; params, stock and quantities count units (B-5)
	 */
	public record GoodView(Identifier goods, Identifier item, int unit, GoodParams params, double pressure, double stock, boolean buy, boolean sell) {
		public GoodState state() {
			return new GoodState(1.0, pressure, stock, 0);
		}

		private void write(RegistryFriendlyByteBuf buf) {
			Identifier.STREAM_CODEC.encode(buf, goods);
			Identifier.STREAM_CODEC.encode(buf, item);
			buf.writeVarInt(unit);
			buf.writeDouble(params.basePrice());
			buf.writeDouble(params.elasticity());
			buf.writeDouble(params.spread());
			buf.writeDouble(params.targetStock());
			buf.writeDouble(params.maxStock());
			buf.writeDouble(params.restockPerDay());
			buf.writeDouble(params.minMultiplier());
			buf.writeDouble(params.maxMultiplier());
			buf.writeDouble(pressure);
			buf.writeDouble(stock);
			buf.writeBoolean(buy);
			buf.writeBoolean(sell);
		}

		private static GoodView read(RegistryFriendlyByteBuf buf) {
			Identifier goods = Identifier.STREAM_CODEC.decode(buf);
			Identifier item = Identifier.STREAM_CODEC.decode(buf);
			int unit = buf.readVarInt();
			GoodParams params = new GoodParams(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
			return new GoodView(goods, item, unit, params, buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean());
		}
	}

	/** Builds a snapshot; the caller must have refreshed the market to {@code gameTime}. */
	public static MarketSnapshotS2C of(Market market, long gameTime) {
		List<GoodView> views = new ArrayList<>();
		market.goods().forEach((goodsId, state) -> {
			GoodParams params = market.params(goodsId);
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (params == null || def == null) return;
			views.add(new GoodView(goodsId, def.item(), def.unit(), params, state.pressure(), state.stock(), def.buy(), def.sell()));
		});
		return new MarketSnapshotS2C(market.id(), market.center(), gameTime, market.level(), views);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(marketId);
		BlockPos.STREAM_CODEC.encode(buf, center);
		buf.writeVarLong(gameTime);
		buf.writeVarInt(level);
		buf.writeVarInt(goods.size());
		goods.forEach(g -> g.write(buf));
	}

	private static MarketSnapshotS2C read(RegistryFriendlyByteBuf buf) {
		long marketId = buf.readVarLong();
		BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
		long gameTime = buf.readVarLong();
		int level = buf.readVarInt();
		int size = buf.readVarInt();
		List<GoodView> goods = new ArrayList<>(size);
		for (int i = 0; i < size; i++) goods.add(GoodView.read(buf));
		return new MarketSnapshotS2C(marketId, center, gameTime, level, goods);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
