package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import net.minecraft.core.BlockPos;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.trade.InventoryUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * @param requests open delivery requests of the market (J)
 */
public record MarketSnapshotS2C(long marketId, BlockPos center, long gameTime, int level, List<GoodView> goods,
		List<RequestView> requests) implements CustomPacketPayload {
	public static final Type<MarketSnapshotS2C> TYPE = new Type<>(TradeExpansion.id("market_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketSnapshotS2C> CODEC = StreamCodec.ofMember(MarketSnapshotS2C::write, MarketSnapshotS2C::read);

	/**
	 * @param goods    goods id (key of the trade request)
	 * @param item     item shown and moved
	 * @param unit     items per trading unit; params, stock and quantities count units (B-5)
	 * @param shortage the good is short in this market (K); its B already includes the multiplier
	 * @param patch         components a stack must have (D-2), empty for plain goods
	 * @param acceptDamaged damaged items are bought at reduced prices (A-4)
	 * @param history       X of the last days, oldest first, for the price graph (A-4)
	 */
	public record GoodView(Identifier goods, Identifier item, int unit, GoodParams params, double pressure, double stock, boolean buy, boolean sell,
			boolean shortage, DataComponentPatch patch, boolean acceptDamaged, List<Double> history) {
		/** Which stacks count as this good (client-side counting and preview). */
		public InventoryUtil.Match match() {
			return new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(item), patch, acceptDamaged);
		}

		public ItemStack displayStack() {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(item).builtInRegistryHolder(), 1, patch);
		}

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
			buf.writeDouble(params.consumePerDay());
			buf.writeDouble(params.halfLifeDays());
			buf.writeDouble(pressure);
			buf.writeDouble(stock);
			buf.writeBoolean(buy);
			buf.writeBoolean(sell);
			buf.writeBoolean(shortage);
			DataComponentPatch.STREAM_CODEC.encode(buf, patch);
			buf.writeBoolean(acceptDamaged);
			ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()).encode(buf, history);
		}

		private static GoodView read(RegistryFriendlyByteBuf buf) {
			Identifier goods = Identifier.STREAM_CODEC.decode(buf);
			Identifier item = Identifier.STREAM_CODEC.decode(buf);
			int unit = buf.readVarInt();
			GoodParams params = new GoodParams(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
			double pressure = buf.readDouble();
			double stock = buf.readDouble();
			boolean buy = buf.readBoolean();
			boolean sell = buf.readBoolean();
			boolean shortage = buf.readBoolean();
			DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
			boolean acceptDamaged = buf.readBoolean();
			List<Double> history = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()).decode(buf);
			return new GoodView(goods, item, unit, params, pressure, stock, buy, sell, shortage, patch, acceptDamaged, history);
		}
	}

	/** A request with the item to show (J). */
	public record RequestView(Request request, Identifier item) {
		static final StreamCodec<RegistryFriendlyByteBuf, RequestView> CODEC = StreamCodec.composite(
				Request.STREAM_CODEC, RequestView::request,
				Identifier.STREAM_CODEC, RequestView::item,
				RequestView::new);
	}

	/** Builds a snapshot; the caller must have refreshed the market to {@code gameTime}. */
	public static MarketSnapshotS2C of(Market market, long gameTime) {
		List<GoodView> views = new ArrayList<>();
		market.goods().forEach((goodsId, state) -> {
			GoodParams params = market.params(goodsId);
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (params == null || def == null) return;
			views.add(new GoodView(goodsId, def.item(), def.unit(), params, state.pressure(), state.stock(), def.buy(), def.sell(),
					market.shortages().containsKey(goodsId), GoodsCatalog.patch(goodsId), def.acceptsDamaged() && def.unit() == 1,
					List.copyOf(state.history())));
		});
		List<RequestView> requests = new ArrayList<>();
		for (Request r : market.requests()) {
			GoodsDefinition def = GoodsCatalog.get(r.goods());
			if (def != null) requests.add(new RequestView(r, def.item()));
		}
		return new MarketSnapshotS2C(market.id(), market.center(), gameTime, market.level(), views, requests);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(marketId);
		BlockPos.STREAM_CODEC.encode(buf, center);
		buf.writeVarLong(gameTime);
		buf.writeVarInt(level);
		buf.writeVarInt(goods.size());
		goods.forEach(g -> g.write(buf));
		RequestView.CODEC.apply(ByteBufCodecs.list()).encode(buf, requests);
	}

	private static MarketSnapshotS2C read(RegistryFriendlyByteBuf buf) {
		long marketId = buf.readVarLong();
		BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
		long gameTime = buf.readVarLong();
		int level = buf.readVarInt();
		int size = buf.readVarInt();
		List<GoodView> goods = new ArrayList<>(size);
		for (int i = 0; i < size; i++) goods.add(GoodView.read(buf));
		List<RequestView> requests = RequestView.CODEC.apply(ByteBufCodecs.list()).decode(buf);
		return new MarketSnapshotS2C(marketId, center, gameTime, level, goods, requests);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
