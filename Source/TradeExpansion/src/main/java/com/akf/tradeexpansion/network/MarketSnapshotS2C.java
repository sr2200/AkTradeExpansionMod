package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.BulkDiscount;
import com.akf.tradeexpansion.core.ReputationMath;
import com.akf.tradeexpansion.market.Reputation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
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
 * @param bulkDiscount the server's {@code trade.bulkDiscounts} (B-5), so the preview matches the server
 * @param reputation   the receiving player's reputation with the market (M, v0.29); prices, rewards and the goods list
 *                     are already those of that player
 */
public record MarketSnapshotS2C(long marketId, BlockPos center, long gameTime, int level, List<GoodView> goods,
		List<RequestView> requests, BulkDiscount bulkDiscount, ReputationView reputation) implements CustomPacketPayload {
	public static final Type<MarketSnapshotS2C> TYPE = new Type<>(TradeExpansion.id("market_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MarketSnapshotS2C> CODEC = StreamCodec.ofMember(MarketSnapshotS2C::write, MarketSnapshotS2C::read);

	/**
	 * @param goods    goods id (key of the trade request)
	 * @param item     item shown and moved
	 * @param unit     items per trading unit; params, stock and quantities count units (B-5)
	 * @param shortage the good is short in this market (K); its B already includes the multiplier
	 * @param special  today's special (D-5); its B already includes market.specialPriceMultiplier
	 * @param patch         components a stack must have (D-2), empty for plain goods
	 * @param acceptDamaged damaged items are bought at reduced prices (A-4)
	 * @param history       X of the last days, oldest first, for the price graph (A-4)
	 */
	public record GoodView(Identifier goods, Identifier item, int unit, GoodParams params, double pressure, double stock, boolean buy, boolean sell,
			boolean shortage, boolean special, DataComponentPatch patch, boolean acceptDamaged, List<Double> history) {
		/** Which stacks count as this good (client-side counting and preview). */
		public InventoryUtil.Match match() {
			return new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(item), patch, acceptDamaged);
		}

		public ItemStack displayStack() {
			return new ItemStack(BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(item)), 1, patch);
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
			buf.writeBoolean(special);
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
			boolean special = buf.readBoolean();
			DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
			boolean acceptDamaged = buf.readBoolean();
			List<Double> history = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()).decode(buf);
			return new GoodView(goods, item, unit, params, pressure, stock, buy, sell, shortage, special, patch, acceptDamaged, history);
		}
	}

	/**
	 * A request with the item to show (J). The reward includes the player's reputation bonus (M).
	 *
	 * @param accepted the player accepted it
	 * @param locked   3-star request the player cannot deliver yet (reputation.hardQuestLevel)
	 */
	public record RequestView(Request request, Identifier item, boolean accepted, boolean locked) {
		static final StreamCodec<RegistryFriendlyByteBuf, RequestView> CODEC = StreamCodec.composite(
				Request.STREAM_CODEC, RequestView::request,
				Identifier.STREAM_CODEC, RequestView::item,
				ByteBufCodecs.BOOL, RequestView::accepted,
				ByteBufCodecs.BOOL, RequestView::locked,
				RequestView::new);
	}

	/**
	 * The receiving player's reputation with the market (M).
	 *
	 * @param next        points needed for the next level, -1 at the top
	 * @param priceBonus  nominal price bonus of the level (capped per good)
	 * @param rewardBonus request reward bonus of the level
	 */
	public record ReputationView(boolean enabled, int level, double points, int next, double priceBonus, double rewardBonus,
			int hardQuestLevel, int specialBonusLevel) {
		static final StreamCodec<RegistryFriendlyByteBuf, ReputationView> CODEC = StreamCodec.ofMember(ReputationView::write, ReputationView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(enabled);
			buf.writeVarInt(level);
			buf.writeDouble(points);
			buf.writeVarInt(next);
			buf.writeDouble(priceBonus);
			buf.writeDouble(rewardBonus);
			buf.writeVarInt(hardQuestLevel);
			buf.writeVarInt(specialBonusLevel);
		}

		private static ReputationView read(RegistryFriendlyByteBuf buf) {
			return new ReputationView(buf.readBoolean(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readDouble(),
					buf.readDouble(), buf.readVarInt(), buf.readVarInt());
		}
	}

	/** Builds the snapshot {@code player} sees; the caller must have refreshed the market to {@code gameTime}. */
	public static MarketSnapshotS2C of(Market market, ServerPlayer player, long gameTime) {
		UUID uuid = player.getUUID();
		List<GoodView> views = new ArrayList<>();
		market.goods().forEach((goodsId, state) -> {
			if (!Reputation.canSee(market, uuid, goodsId, gameTime)) return; // M: bonus specials
			GoodParams params = Reputation.params(market, uuid, goodsId, gameTime);
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (params == null || def == null) return;
			views.add(new GoodView(goodsId, def.item(), def.unit(), params, state.pressure(), state.stock(), def.buy(), def.sell(),
					market.shortages().containsKey(goodsId), market.isSpecial(goodsId), GoodsCatalog.patch(goodsId), def.acceptsDamaged() && def.unit() == 1,
					List.copyOf(state.history())));
		});
		List<RequestView> requests = new ArrayList<>();
		for (Request r : market.requests()) {
			GoodsDefinition def = GoodsCatalog.get(r.goods());
			if (def == null) continue;
			requests.add(new RequestView(r.withReward(Reputation.reward(market, uuid, r, gameTime)), def.item(), r.accepted().contains(uuid),
					!Reputation.canDeliver(market, uuid, r, gameTime)));
		}
		TradeExpansionConfig.Reputation rc = TradeExpansionConfig.get().reputation;
		int level = Reputation.level(market, uuid, gameTime);
		ReputationView reputation = new ReputationView(rc.enabled, level, Reputation.points(market, uuid, gameTime),
				ReputationMath.nextThreshold(level, rc.levelThresholdArray()), Reputation.priceBonus(level), Reputation.rewardBonus(level),
				rc.hardQuestLevel, rc.specialBonusLevel);
		return new MarketSnapshotS2C(market.id(), market.center(), gameTime, market.level(), views, requests,
				TradeExpansionConfig.get().trade.bulkDiscount(), reputation);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(marketId);
		BlockPos.STREAM_CODEC.encode(buf, center);
		buf.writeVarLong(gameTime);
		buf.writeVarInt(level);
		buf.writeVarInt(goods.size());
		goods.forEach(g -> g.write(buf));
		RequestView.CODEC.apply(ByteBufCodecs.list()).encode(buf, requests);
		buf.writeVarInt(bulkDiscount.tiers().size());
		for (BulkDiscount.Tier tier : bulkDiscount.tiers()) {
			buf.writeVarInt(tier.minQuantity());
			buf.writeDouble(tier.rate());
		}
		ReputationView.CODEC.encode(buf, reputation);
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
		int tierCount = buf.readVarInt();
		List<BulkDiscount.Tier> tiers = new ArrayList<>(tierCount);
		for (int i = 0; i < tierCount; i++) tiers.add(new BulkDiscount.Tier(buf.readVarInt(), buf.readDouble()));
		ReputationView reputation = ReputationView.CODEC.decode(buf);
		return new MarketSnapshotS2C(marketId, center, gameTime, level, goods, requests, new BulkDiscount(tiers), reputation);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
