package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.BulkDiscount;
import com.akf.tradeexpansion.core.ReputationMath;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.Level;
import java.util.Optional;
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
 * @param development  N (v0.36): development points and facilities of the market
 */
public record MarketSnapshotS2C(long marketId, BlockPos center, long gameTime, int level, List<GoodView> goods,
		List<RequestView> requests, BulkDiscount bulkDiscount, ReputationView reputation, DevelopmentView development) implements CustomPacketPayload {
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
	 * @param accepted  the player accepted it (J-2: took its cargo crate)
	 * @param locked    3-star request the player cannot deliver yet (reputation.hardQuestLevel)
	 * @param transport J-2 (v0.35): where a transport request goes
	 */
	public record RequestView(Request request, Identifier item, boolean accepted, boolean locked, Optional<TransportView> transport) {
		static final StreamCodec<RegistryFriendlyByteBuf, RequestView> CODEC = StreamCodec.composite(
				Request.STREAM_CODEC, RequestView::request,
				Identifier.STREAM_CODEC, RequestView::item,
				ByteBufCodecs.BOOL, RequestView::accepted,
				ByteBufCodecs.BOOL, RequestView::locked,
				ByteBufCodecs.optional(TransportView.CODEC), RequestView::transport,
				RequestView::new);
	}

	/**
	 * J-2: destination of a transport request, seen from this market.
	 *
	 * @param dx       east-west offset in Overworld blocks (Nether ×8 when the dimensions are linked)
	 * @param dz       north-south offset, likewise
	 * @param distance horizontal distance in the same blocks
	 */
	public record TransportView(Component destinationName, int dx, int dz, int distance, long deposit) {
		static final StreamCodec<RegistryFriendlyByteBuf, TransportView> CODEC = StreamCodec.composite(
				ComponentSerialization.STREAM_CODEC, TransportView::destinationName,
				ByteBufCodecs.VAR_INT, TransportView::dx,
				ByteBufCodecs.VAR_INT, TransportView::dz,
				ByteBufCodecs.VAR_INT, TransportView::distance,
				ByteBufCodecs.VAR_LONG, TransportView::deposit,
				TransportView::new);

		static Optional<TransportView> of(MarketRegistry registry, Market source, Request request) {
			if (request.transport().isEmpty()) return Optional.empty();
			Market destination = registry.get(request.transport().get().destination());
			if (destination == null) return Optional.empty();
			boolean sameDimension = source.dimension().equals(destination.dimension());
			int ss = !sameDimension && source.dimension().equals(Level.NETHER) ? 8 : 1;
			int sd = !sameDimension && destination.dimension().equals(Level.NETHER) ? 8 : 1;
			int dx = destination.center().getX() * sd - source.center().getX() * ss;
			int dz = destination.center().getZ() * sd - source.center().getZ() * ss;
			return Optional.of(new TransportView(MarketNames.name(destination), dx, dz,
					(int) Math.round(MarketRegistry.linkedDistance(source, destination)), request.transport().get().deposit()));
		}
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

	/**
	 * N (v0.37 / v0.38): development of the market as {@code player} sees it.
	 *
	 * @param enabled       the market's dimension develops (the tab is shown)
	 * @param mine          points the receiving player brought
	 * @param top           contributors with the most points
	 * @param measuredLevel level from beds and villagers (D-4); {@code level} of the snapshot adds {@code bonus}
	 * @param bank          the market has a bank building: bank tab at any level, no card fee (N-4)
	 * @param canChoose     the player's reputation allows choosing a facility ({@code development.buildReputationLevel})
	 * @param buildLevel    the reputation level needed for that
	 * @param freeBuilds    constructions that may still start ({@code development.maxConcurrentBuilds} minus those under way)
	 */
	public record DevelopmentView(boolean enabled, double points, double mine, List<ContributorView> top, int measuredLevel, int bonus,
			boolean bank, boolean canChoose, int buildLevel, int freeBuilds, List<FacilityView> facilities) {
		public static final DevelopmentView DISABLED = new DevelopmentView(false, 0, 0, List.of(), 1, 0, false, false, 0, 0, List.of());

		static final StreamCodec<RegistryFriendlyByteBuf, DevelopmentView> CODEC = StreamCodec.ofMember(DevelopmentView::write, DevelopmentView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(enabled);
			buf.writeDouble(points);
			buf.writeDouble(mine);
			ContributorView.CODEC.apply(ByteBufCodecs.list()).encode(buf, top);
			buf.writeVarInt(measuredLevel);
			buf.writeVarInt(bonus);
			buf.writeBoolean(bank);
			buf.writeBoolean(canChoose);
			buf.writeVarInt(buildLevel);
			buf.writeVarInt(freeBuilds);
			FacilityView.CODEC.apply(ByteBufCodecs.list()).encode(buf, facilities);
		}

		private static DevelopmentView read(RegistryFriendlyByteBuf buf) {
			return new DevelopmentView(buf.readBoolean(), buf.readDouble(), buf.readDouble(), ContributorView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
					buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					FacilityView.CODEC.apply(ByteBufCodecs.list()).decode(buf));
		}
	}

	/**
	 * N-4: one facility of the development tab.
	 *
	 * @param state 0 = not built, 1 = under construction, 2 = completed, 3 = destroyed
	 * @param cost  development points building it costs now (half for a rebuild)
	 * @param due   market-clock time the construction ends (state 1)
	 * @param materialsReady every wanted material is donated, or materials are not required (v0.42, development.requireMaterials)
	 */
	public record FacilityView(String id, String kind, Identifier icon, int state, long cost, long due, List<WantedView> wanted,
			boolean materialsReady) {
		public static final int NONE = 0, CONSTRUCTING = 1, COMPLETE = 2, DESTROYED = 3;

		static final StreamCodec<RegistryFriendlyByteBuf, FacilityView> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, FacilityView::id,
				ByteBufCodecs.STRING_UTF8, FacilityView::kind,
				Identifier.STREAM_CODEC, FacilityView::icon,
				ByteBufCodecs.VAR_INT, FacilityView::state,
				ByteBufCodecs.VAR_LONG, FacilityView::cost,
				ByteBufCodecs.VAR_LONG, FacilityView::due,
				WantedView.CODEC.apply(ByteBufCodecs.list()), FacilityView::wanted,
				ByteBufCodecs.BOOL, FacilityView::materialsReady,
				FacilityView::new);
	}

	/**
	 * N-2: one wanted material.
	 *
	 * @param item item shown (the first item of a tag)
	 * @param tag  item tag id when any item of the tag counts, empty otherwise
	 */
	public record WantedView(Identifier item, String tag, int required, int donated) {
		static final StreamCodec<RegistryFriendlyByteBuf, WantedView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, WantedView::item,
				ByteBufCodecs.STRING_UTF8, WantedView::tag,
				ByteBufCodecs.VAR_INT, WantedView::required,
				ByteBufCodecs.VAR_INT, WantedView::donated,
				WantedView::new);
	}

	public record ContributorView(String name, double points) {
		static final StreamCodec<RegistryFriendlyByteBuf, ContributorView> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ContributorView::name,
				ByteBufCodecs.DOUBLE, ContributorView::points,
				ContributorView::new);
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
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		for (Request r : market.requests()) {
			GoodsDefinition def = GoodsCatalog.get(r.goods());
			if (def == null) continue;
			requests.add(new RequestView(r.withReward(Reputation.reward(market, uuid, r, gameTime)), def.item(), r.accepted().contains(uuid),
					!Reputation.canDeliver(market, uuid, r, gameTime), TransportView.of(registry, market, r)));
		}
		TradeExpansionConfig.Reputation rc = TradeExpansionConfig.get().reputation;
		int level = Reputation.level(market, uuid, gameTime);
		ReputationView reputation = new ReputationView(rc.enabled, level, Reputation.points(market, uuid, gameTime),
				ReputationMath.nextThreshold(level, rc.levelThresholdArray()), Reputation.priceBonus(level), Reputation.rewardBonus(level),
				rc.hardQuestLevel, rc.specialBonusLevel);
		return new MarketSnapshotS2C(market.id(), market.center(), gameTime, market.level(), views, requests,
				TradeExpansionConfig.get().trade.bulkDiscount(), reputation, developmentView(market, player, gameTime));
	}

	/** N: points, contributors (top 5 and the player) of a market that develops. */
	private static DevelopmentView developmentView(Market market, ServerPlayer player, long now) {
		if (!Development.enabled(market)) return DevelopmentView.DISABLED;
		List<ContributorView> top = new ArrayList<>();
		for (Market.Contributor c : Development.top(market, TOP_CONTRIBUTORS)) top.add(new ContributorView(c.name(), c.points()));
		Market.Contributor mine = market.contributors().get(player.getUUID());
		TradeExpansionConfig.Development c = Development.config();
		List<FacilityView> facilities = new ArrayList<>();
		for (java.util.Map.Entry<Identifier, FacilityDefinition> e : FacilityCatalog.all().entrySet()) {
			FacilityDefinition def = e.getValue();
			Facility f = market.facilities().get(e.getKey());
			int state = f == null ? FacilityView.NONE : switch (f.state()) {
				case CONSTRUCTING -> FacilityView.CONSTRUCTING;
				case COMPLETE -> FacilityView.COMPLETE;
				case DESTROYED -> FacilityView.DESTROYED;
			};
			java.util.Map<String, Integer> donated = market.donations(e.getKey());
			List<WantedView> wanted = new ArrayList<>();
			for (FacilityDefinition.Wanted w : def.wanted()) {
				wanted.add(new WantedView(BuiltInRegistries.ITEM.getKey(w.displayItem()), w.tag().map(Identifier::toString).orElse(""), FacilityBuilder.required(market, e.getKey(), w),
						donated.getOrDefault(w.key(), 0)));
			}
			facilities.add(new FacilityView(e.getKey().toString(), def.kind().getSerializedName(), def.icon(), state,
					FacilityBuilder.cost(market, e.getKey()), f == null ? 0 : f.due(), wanted, FacilityBuilder.materialsReady(market, e.getKey())));
		}
		int buildLevel = c.buildReputationLevel;
		return new DevelopmentView(true, Development.points(market, now), mine == null ? 0 : mine.points(), top, market.measuredLevel(),
				com.akf.tradeexpansion.market.MarketFactory.levelBonus(market), market.hasFacility(FacilityKind.BANK),
				Reputation.level(market, player.getUUID(), now) >= buildLevel, buildLevel,
				(int) Math.max(0, c.maxConcurrentBuilds - FacilityBuilder.constructing(market)), facilities);
	}

	/** N-6: contributors listed on the development tab. */
	private static final int TOP_CONTRIBUTORS = 5;

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
		DevelopmentView.CODEC.encode(buf, development);
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
		DevelopmentView development = DevelopmentView.CODEC.decode(buf);
		return new MarketSnapshotS2C(marketId, center, gameTime, level, goods, requests, new BulkDiscount(tiers), reputation, development);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
