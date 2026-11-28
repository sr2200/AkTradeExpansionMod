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
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
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
 * @param staffTab     Q (v0.48.2): show the staff tab: staff is on, or the player still has employees (to take their money and goods back)
 * @param mint         N-4: minting at the market's mint (exchange tab)
 * @param outlook      v0.79: with a school, how long the shortages and trends of the market still last
 */
public record MarketSnapshotS2C(long marketId, BlockPos center, long gameTime, int level, List<GoodView> goods,
		List<RequestView> requests, BulkDiscount bulkDiscount, ReputationView reputation, DevelopmentView development,
		List<TrendView> trends, boolean staffTab, MintView mint, OutlookView outlook) implements CustomPacketPayload {
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
	 * @param shortageMultiplier K: multiplier of B while short (1 = not short)
	 * @param trends        P (v0.46): the market's trends that apply to this good
	 * @param rival         O-5 (v0.46): the rival campaign felt most on this good here
	 * @param shortageLeft  K-2 (v0.78): units still to sell to fulfill the shortage (0 = not short or cannot be fulfilled)
	 * @param rainMultiplier U (v0.82): multiplier of B of a rainy spell (1 = none)
	 * @param event         V: the market event felt most on this good here
	 */
	public record GoodView(Identifier goods, Identifier item, int unit, GoodParams params, double pressure, double stock, boolean buy, boolean sell,
			boolean shortage, boolean special, DataComponentPatch patch, boolean acceptDamaged, List<Double> history,
			double shortageMultiplier, List<TrendView> trends, Optional<RivalView> rival, int shortageLeft, double rainMultiplier,
			Optional<EventView> event) {
		/** Which stacks count as this good (client-side counting and preview), without its valuation (S-7). */
		public InventoryUtil.Match match() {
			return new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(item), patch, acceptDamaged);
		}

		/**
		 * Which stacks this market takes as this good, like {@code TradeService.sellMatch} on the server: with its
		 * valuation (S-7) and without the stacks that the other sellable goods of {@code market} take.
		 */
		public InventoryUtil.Match match(List<GoodView> market) {
			com.akf.tradeexpansion.api.ItemValuation valuation = unit == 1 ? com.akf.tradeexpansion.trade.ItemValuations.get(goods) : null;
			if (valuation == null) return match();
			List<InventoryUtil.Match> others = new java.util.ArrayList<>();
			for (GoodView other : market) {
				if (!other.goods.equals(goods) && other.sell && !com.akf.tradeexpansion.trade.ItemValuations.has(other.goods)) others.add(other.match());
			}
			return new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(item), patch, acceptDamaged, valuation, others);
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
			buf.writeDouble(shortageMultiplier);
			TrendView.CODEC.apply(ByteBufCodecs.list()).encode(buf, trends);
			ByteBufCodecs.optional(RivalView.CODEC).encode(buf, rival);
			buf.writeVarInt(shortageLeft);
			buf.writeDouble(rainMultiplier);
			ByteBufCodecs.optional(EventView.CODEC).encode(buf, event);
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
			double shortageMultiplier = buf.readDouble();
			List<TrendView> trends = TrendView.CODEC.apply(ByteBufCodecs.list()).decode(buf);
			Optional<RivalView> rival = ByteBufCodecs.optional(RivalView.CODEC).decode(buf);
			int shortageLeft = buf.readVarInt();
			double rainMultiplier = buf.readDouble();
			Optional<EventView> event = ByteBufCodecs.optional(EventView.CODEC).decode(buf);
			return new GoodView(goods, item, unit, params, pressure, stock, buy, sell, shortage, special, patch, acceptDamaged, history,
					shortageMultiplier, trends, rival, shortageLeft, rainMultiplier, event);
		}
	}

	/**
	 * V-5: a market event as shown on a goods row.
	 *
	 * @param multiplier the multiplier of B it gives this good here today
	 * @param daysLeft   days after today (0 = ends today)
	 */
	public record EventView(Identifier event, double multiplier, int daysLeft) {
		public static final StreamCodec<RegistryFriendlyByteBuf, EventView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, EventView::event,
				ByteBufCodecs.DOUBLE, EventView::multiplier,
				ByteBufCodecs.VAR_INT, EventView::daysLeft,
				EventView::new);
	}

	/**
	 * O-5: a rival campaign as shown on a goods row.
	 *
	 * @param buying   the guild buys up (prices rise); otherwise it sells off
	 * @param daysLeft days after today (0 = ends today)
	 */
	public record RivalView(Identifier guild, boolean buying, int daysLeft) {
		public static final StreamCodec<RegistryFriendlyByteBuf, RivalView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, RivalView::guild,
				ByteBufCodecs.BOOL, RivalView::buying,
				ByteBufCodecs.VAR_INT, RivalView::daysLeft,
				RivalView::new);
	}

	/** P: one goods group in or out of fashion at a market. */
	public record TrendView(Identifier group, double multiplier) {
		public static final StreamCodec<RegistryFriendlyByteBuf, TrendView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, TrendView::group,
				ByteBufCodecs.DOUBLE, TrendView::multiplier,
				TrendView::new);

		/** In fashion first, then out of fashion, each by multiplier. */
		public static List<TrendView> of(Market market) {
			List<TrendView> list = new ArrayList<>();
			market.trends().forEach((group, m) -> list.add(new TrendView(group, Market.theater(m, market)))); // v0.51 theater
			list.sort(java.util.Comparator.comparingDouble(TrendView::multiplier).reversed());
			return list;
		}

		/** The trends that apply to one good of the market (none for specials and auto_add goods, see Market#trendMultiplier). */
		public static List<TrendView> of(Market market, Identifier goodsId) {
			if (market.trends().isEmpty() || market.isSpecial(goodsId)) return List.of();
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def == null || def.autoAdd().isPresent()) return List.of();
			List<TrendView> list = new ArrayList<>();
			for (Identifier group : com.akf.tradeexpansion.data.GoodsGroups.groupsOf(goodsId)) {
				Double m = market.trends().get(group);
				if (m != null) list.add(new TrendView(group, Market.theater(m, market)));
			}
			return list;
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
			boolean bank, boolean canChoose, int buildLevel, int freeBuilds, List<FacilityView> facilities, int festivalIn, double vigor,
			double vigorMultiplier, int shortagesFulfilled, int villagersEvery, Levels levels) {
		public static final DevelopmentView DISABLED = new DevelopmentView(false, 0, 0, List.of(), 1, 0, false, false, 0, 0, List.of(), -1,
				100, 1, 0, 0, Levels.OFF);

		/**
		 * N-11 (v0.86): the settings of the buildings' levels the screen needs to show how strong they are
		 * ({@code development.upgradeEnabled}, {@code upgradeEffectSteps}, {@code upgradePerkBonus}).
		 */
		public record Levels(boolean enabled, List<Double> steps, double perkBonus) {
			public static final Levels OFF = new Levels(false, List.of(), 0);
			static final StreamCodec<RegistryFriendlyByteBuf, Levels> CODEC = StreamCodec.composite(
					ByteBufCodecs.BOOL, Levels::enabled,
					ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), Levels::steps,
					ByteBufCodecs.DOUBLE, Levels::perkBonus,
					Levels::new);

			/** Strength of effect {@code effect} of {@code kind} at {@code level} in direction {@code branch} (1 = as at level 1). */
			public double strength(com.akf.tradeexpansion.development.FacilityKind kind, int level, String branch, int effect) {
				if (!enabled) return 1.0;
				return com.akf.tradeexpansion.core.FacilityLevelMath.strength(level, kind.role(branch, effect), steps);
			}

			/** Multiplier of the side bonus of direction {@code branch} at {@code level} (1 = none). */
			public double perk(com.akf.tradeexpansion.development.FacilityKind kind, int level, String branch) {
				if (!enabled || kind.perk(branch) == com.akf.tradeexpansion.development.FacilityKind.Perk.NONE) return 1.0;
				return com.akf.tradeexpansion.core.FacilityLevelMath.perk(level, perkBonus);
			}
		}

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
			buf.writeVarInt(festivalIn + 1);
			buf.writeDouble(vigor);
			buf.writeDouble(vigorMultiplier);
			buf.writeVarInt(shortagesFulfilled);
			buf.writeVarInt(villagersEvery);
			Levels.CODEC.encode(buf, levels);
		}

		private static DevelopmentView read(RegistryFriendlyByteBuf buf) {
			return new DevelopmentView(buf.readBoolean(), buf.readDouble(), buf.readDouble(), ContributorView.CODEC.apply(ByteBufCodecs.list()).decode(buf),
					buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					FacilityView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(),
					buf.readVarInt(), buf.readVarInt(), Levels.CODEC.decode(buf));
		}
	}

	/**
	 * N-4: one facility of the development tab.
	 *
	 * @param state 0 = not built, 1 = under construction, 2 = completed, 3 = destroyed
	 * @param cost  development points building it costs now (half for a rebuild)
	 * @param due   market-clock time the construction ends (state 1)
	 * @param materialsReady every wanted material is donated, or materials are not required (v0.42, development.requireMaterials)
	 * @param size          N-6 (v0.58): size of the building in this market's look (x = width, y = height, z = depth); 0 when unknown
	 * @param level         N-11 (v0.86): its level (1..3; a destroyed one keeps the level it is rebuilt at)
	 * @param branch        N-11: the direction chosen at level 2 ({@code a} / {@code b}), empty before
	 * @param upgrading     N-11: a completed building is being raised to the next level ({@code due} is when it is done)
	 * @param tallLook      N-11: its level 3 has a second storey (when the space above is free)
	 */
	public record FacilityView(String id, String kind, Identifier icon, int state, long cost, long due, List<WantedView> wanted,
			boolean materialsReady, BlockPos size, int level, String branch, boolean upgrading, boolean tallLook) {
		public static final int NONE = 0, CONSTRUCTING = 1, COMPLETE = 2, DESTROYED = 3;

		static final StreamCodec<RegistryFriendlyByteBuf, FacilityView> CODEC = StreamCodec.ofMember(FacilityView::write, FacilityView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(id);
			buf.writeUtf(kind);
			Identifier.STREAM_CODEC.encode(buf, icon);
			buf.writeVarInt(state);
			buf.writeVarLong(cost);
			buf.writeVarLong(due);
			WantedView.CODEC.apply(ByteBufCodecs.list()).encode(buf, wanted);
			buf.writeBoolean(materialsReady);
			BlockPos.STREAM_CODEC.encode(buf, size);
			buf.writeVarInt(level);
			buf.writeUtf(branch);
			buf.writeBoolean(upgrading);
			buf.writeBoolean(tallLook);
		}

		private static FacilityView read(RegistryFriendlyByteBuf buf) {
			return new FacilityView(buf.readUtf(), buf.readUtf(), Identifier.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarLong(),
					buf.readVarLong(), WantedView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean(), BlockPos.STREAM_CODEC.decode(buf),
					buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
		}

		/** N-11: the kind as the enum, or {@code null} for an unknown one. */
		public com.akf.tradeexpansion.development.FacilityKind facilityKind() {
			for (com.akf.tradeexpansion.development.FacilityKind k : com.akf.tradeexpansion.development.FacilityKind.values()) {
				if (k.getSerializedName().equals(kind)) return k;
			}
			return null;
		}

		/** N-11: completed, not being raised and below the top level (the levels being on is checked by the caller). */
		public boolean upgradeable() {
			return state == COMPLETE && !upgrading && level < com.akf.tradeexpansion.core.FacilityLevelMath.MAX_LEVEL;
		}
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
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		List<GoodView> views = new ArrayList<>();
		market.goods().forEach((goodsId, state) -> {
			if (!Reputation.canSee(market, uuid, goodsId, gameTime)) return; // M: bonus specials
			GoodParams params = Reputation.params(market, uuid, goodsId, gameTime);
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (params == null || def == null) return;
			Campaign campaign = RivalService.strongest(registry, market, goodsId, gameTime);
			Optional<RivalView> rival = campaign == null ? Optional.empty()
					: Optional.of(new RivalView(campaign.guild(), campaign.buying(), campaign.daysLeft(RivalService.day(gameTime))));
			com.akf.tradeexpansion.event.MarketEvent marketEvent = com.akf.tradeexpansion.event.EventService.strongest(registry, market, goodsId, gameTime);
			Optional<EventView> event = marketEvent == null || market.eventMultiplierFor(goodsId) == 1.0 ? Optional.empty()
					: Optional.of(new EventView(marketEvent.event(), market.eventMultiplierFor(goodsId),
							marketEvent.daysLeft(com.akf.tradeexpansion.event.EventService.day(gameTime))));
			views.add(new GoodView(goodsId, def.item(), def.unit(), params, state.pressure(), state.stock(), def.buy(), def.sell(),
					market.shortages().containsKey(goodsId), market.isSpecial(goodsId), GoodsCatalog.patch(goodsId), def.acceptsDamaged() && def.unit() == 1,
					List.copyOf(state.history()), market.shortages().getOrDefault(goodsId, 1.0), TrendView.of(market, goodsId), rival,
					com.akf.tradeexpansion.quest.DemandService.left(market, goodsId), market.rainMultiplierFor(goodsId), event));
		});
		List<RequestView> requests = new ArrayList<>();
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
				TradeExpansionConfig.get().trade.bulkDiscount(), reputation, developmentView(market, player, gameTime), TrendView.of(market),
				TradeExpansionConfig.get().staff.enabled || registry.employees().stream().anyMatch(e -> e.owner().equals(uuid)), MintView.of(market),
				OutlookView.of(player.level(), market, gameTime));
	}

	/**
	 * v0.79 school: days (rounded up, at least 1) until the market's shortages and trends change; {@code school} false
	 * (and the days 0) when the market has no school.
	 */
	public record OutlookView(boolean school, int shortageDays, int trendDays) {
		public static final OutlookView NONE = new OutlookView(false, 0, 0);
		static final StreamCodec<RegistryFriendlyByteBuf, OutlookView> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, OutlookView::school,
				ByteBufCodecs.VAR_INT, OutlookView::shortageDays,
				ByteBufCodecs.VAR_INT, OutlookView::trendDays,
				OutlookView::new);

		public static OutlookView of(net.minecraft.server.level.ServerLevel level, Market market, long now) {
			if (!market.hasFacility(FacilityKind.SCHOOL)) return NONE;
			return new OutlookView(true, shortageDays(market, now), trendDays(level, market, now));
		}

		/** Days until the shortages of {@code market} are rolled again. */
		public static int shortageDays(Market market, long now) {
			long length = com.akf.tradeexpansion.core.TrendMath.length(TradeExpansionConfig.get().demand.durationDays);
			long end = (Math.floorDiv(now, length) + 1) * length; // QuestMath.period
			return days(end - now);
		}

		/** Days until the trends of {@code market} are chosen again (each market's period is shifted, TrendService). */
		public static int trendDays(net.minecraft.server.level.ServerLevel level, Market market, long now) {
			double duration = TradeExpansionConfig.get().trend.durationDays;
			long length = com.akf.tradeexpansion.core.TrendMath.length(duration);
			long offset = com.akf.tradeexpansion.core.TrendMath.offset(com.akf.tradeexpansion.market.MarketFactory.seed(level, market.center()), duration);
			long end = (Math.floorDiv(now + offset, length) + 1) * length - offset;
			return days(end - now);
		}

		private static int days(long ticks) {
			return (int) Math.max(1, Math.ceil(ticks / (double) com.akf.tradeexpansion.core.PriceModel.TICKS_PER_DAY - 1e-9));
		}
	}

	/**
	 * N-4: the mint of the exchange tab: whether the market has a completed mint, and per metal of
	 * {@code MintService.METALS} its block, the copper value of one minting (9 blocks) and whether it is allowed.
	 */
	public record MintView(boolean built, List<MintOption> options) {
		public static final MintView NONE = new MintView(false, List.of());
		static final StreamCodec<RegistryFriendlyByteBuf, MintView> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, MintView::built,
				MintOption.CODEC.apply(ByteBufCodecs.list()), MintView::options,
				MintView::new);

		static MintView of(Market market) {
			if (!market.hasFacility(FacilityKind.MINT)) return NONE;
			List<MintOption> options = new ArrayList<>();
			for (com.akf.tradeexpansion.currency.MintService.Metal m : com.akf.tradeexpansion.currency.MintService.METALS) {
				options.add(new MintOption(BuiltInRegistries.ITEM.getKey(m.block()), m.mintValue(market), m.enabled()));
			}
			return new MintView(true, options);
		}
	}

	public record MintOption(Identifier block, long value, boolean enabled) {
		static final StreamCodec<RegistryFriendlyByteBuf, MintOption> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, MintOption::block,
				ByteBufCodecs.VAR_LONG, MintOption::value,
				ByteBufCodecs.BOOL, MintOption::enabled,
				MintOption::new);
	}

	/** N: points, contributors (top 5 and the player) of a market that develops. */
	private static DevelopmentView developmentView(Market market, ServerPlayer player, long now) {
		if (!Development.enabled(market)) return DevelopmentView.DISABLED;
		List<ContributorView> top = new ArrayList<>();
		for (Market.Contributor c : Development.top(market, TOP_CONTRIBUTORS)) top.add(new ContributorView(c.name(), c.points()));
		Market.Contributor mine = market.contributors().get(player.getUUID());
		TradeExpansionConfig.Development c = Development.config();
		List<FacilityView> facilities = new ArrayList<>();
		java.util.Map<String, Long> order = new java.util.HashMap<>(); // N-6: the order is by the cost of building (not of the next level)
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		net.minecraft.server.level.ServerLevel marketLevel = server.getLevel(market.dimension());
		String placeStyle = marketLevel == null ? "plains" : FacilityBuilder.style(marketLevel, market.center());
		for (java.util.Map.Entry<Identifier, FacilityDefinition> e : FacilityCatalog.all().entrySet()) {
			FacilityDefinition def = e.getValue();
			Facility f = market.facilities().get(e.getKey());
			// v0.58: the size of the look it has (under construction or built) or would get here (also a rebuild)
			String style = f != null && f.state() != Facility.State.DESTROYED ? f.style() : placeStyle;
			// N-11 (v0.86): the size of the look of its level
			java.util.Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> look = f != null
					? FacilityBuilder.template(server, def, style, f.level(), f.tall(), f.branch()) : FacilityBuilder.template(server, def, style);
			BlockPos size = look.map(t -> new BlockPos(t.getSize().getX(), t.getSize().getY(), t.getSize().getZ())).orElse(BlockPos.ZERO);
			boolean tallLook = FacilityBuilder.template(server, def, style, com.akf.tradeexpansion.core.FacilityLevelMath.MAX_LEVEL, true).isPresent();
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
			boolean upgrading = f != null && f.upgrading();
			facilities.add(new FacilityView(e.getKey().toString(), def.kind().getSerializedName(), def.icon(), state,
					FacilityBuilder.cost(market, e.getKey()), f == null ? 0 : upgrading ? f.upgradeDue() : f.due(), wanted,
					FacilityBuilder.materialsReady(market, e.getKey()), size, f == null ? 1 : f.level(), f == null ? "" : f.branch(), upgrading, tallLook));
			order.put(e.getKey().toString(), def.cost());
		}
		// N-6: cheapest first (ties by id), the order the town can grow in.
		facilities.sort(java.util.Comparator.comparingLong((FacilityView v) -> order.getOrDefault(v.id(), v.cost())).thenComparing(FacilityView::id));
		int buildLevel = c.buildReputationLevel;
		return new DevelopmentView(true, Development.points(market, now), mine == null ? 0 : mine.points(), top, market.measuredLevel(),
				com.akf.tradeexpansion.market.MarketFactory.levelBonus(market), market.hasFacility(FacilityKind.BANK),
				Reputation.level(market, player.getUUID(), now) >= buildLevel, buildLevel,
				(int) Math.max(0, c.maxConcurrentBuilds - FacilityBuilder.constructing(market)), facilities,
				com.akf.tradeexpansion.development.Festival.daysUntil(market, now), market.vigor(), Development.vigorMultiplier(market),
				market.shortagesFulfilled(), com.akf.tradeexpansion.config.TradeExpansionConfig.get().demand.villagersEvery,
				new DevelopmentView.Levels(c.upgradeEnabled, List.copyOf(c.upgradeEffectSteps), c.upgradePerkBonus));
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
		TrendView.CODEC.apply(ByteBufCodecs.list()).encode(buf, trends);
		buf.writeBoolean(staffTab);
		MintView.CODEC.encode(buf, mint);
		OutlookView.CODEC.encode(buf, outlook);
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
		List<TrendView> trends = TrendView.CODEC.apply(ByteBufCodecs.list()).decode(buf);
		boolean staffTab = buf.readBoolean();
		return new MarketSnapshotS2C(marketId, center, gameTime, level, goods, requests, new BulkDiscount(tiers), reputation, development, trends,
				staffTab, MintView.CODEC.decode(buf), OutlookView.CODEC.decode(buf));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
