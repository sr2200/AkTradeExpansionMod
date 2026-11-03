package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.MarketLevel;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.quest.Request;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * One market: a group of stalls sharing stock and prices (design C-1 / D-4 / F-1).
 * Goods are keyed by goods id ({@code trade_expansion:bread}), not by item id.
 */
public final class Market {
	private static final Codec<GoodState> GOOD_STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("price_factor").forGetter(GoodState::priceFactor),
			Codec.DOUBLE.fieldOf("pressure").forGetter(GoodState::pressure),
			Codec.DOUBLE.fieldOf("stock").forGetter(GoodState::stock),
			Codec.LONG.fieldOf("last_trade").forGetter(GoodState::lastTradeGameTime),
			Codec.DOUBLE.listOf().optionalFieldOf("history", List.of()).forGetter(GoodState::history),
			Codec.LONG.optionalFieldOf("last_sample_day", Long.MIN_VALUE).forGetter(GoodState::lastSampleDay)
	).apply(i, GoodState::new));

	/** Days of price history kept for the graph (A-4). */
	public static final int HISTORY_SIZE = 14;

	/** The NBT field stays "item" (schema 1 name) although it holds the goods id since schema 2. */
	private record GoodEntry(Identifier item, GoodState state) {
		static final Codec<GoodEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(GoodEntry::item),
				GOOD_STATE_CODEC.fieldOf("state").forGetter(GoodEntry::state)
		).apply(i, GoodEntry::new));
	}

	private record Shortage(Identifier goods, double multiplier) {
		static final Codec<Shortage> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(Shortage::goods),
				Codec.DOUBLE.fieldOf("multiplier").forGetter(Shortage::multiplier)
		).apply(i, Shortage::new));
	}

	public static final Codec<Market> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Market::id),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Market::dimension),
			BlockPos.CODEC.fieldOf("center").forGetter(Market::center),
			MarketOrigin.CODEC.fieldOf("origin").forGetter(Market::origin),
			Identifier.CODEC.optionalFieldOf("profile").forGetter(m -> Optional.ofNullable(m.profileId)),
			Codec.intRange(MarketLevel.MIN_LEVEL, MarketLevel.MAX_LEVEL).optionalFieldOf("level", MarketLevel.MIN_LEVEL).forGetter(Market::level),
			Codec.LONG.optionalFieldOf("last_level_check", 0L).forGetter(Market::lastLevelCheckGameTime),
			BlockPos.CODEC.listOf().fieldOf("stalls").forGetter(m -> List.copyOf(m.stalls)),
			Codec.LONG.fieldOf("last_update").forGetter(Market::lastUpdateGameTime),
			GoodEntry.CODEC.listOf().fieldOf("goods").forGetter(Market::goodEntries),
			Request.CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(m -> List.copyOf(m.requests)),
			Codec.LONG.optionalFieldOf("last_request_refresh", Long.MIN_VALUE).forGetter(m -> m.lastRequestRefresh),
			Codec.INT.optionalFieldOf("next_request_id", 1).forGetter(m -> m.nextRequestId),
			Shortage.CODEC.listOf().optionalFieldOf("shortages", List.of()).forGetter(Market::shortageEntries),
			Codec.LONG.optionalFieldOf("shortage_period", Long.MIN_VALUE).forGetter(m -> m.shortagePeriod)
	).apply(i, Market::new));

	private final long id;
	private final ResourceKey<Level> dimension;
	private final BlockPos center;
	private final MarketOrigin origin;
	private Identifier profileId;
	private int level;
	private long lastLevelCheckGameTime;
	private final Set<BlockPos> stalls;
	private long lastUpdateGameTime;
	/** Insertion order = display order. */
	private final Map<Identifier, GoodState> goods;
	/** J: open delivery requests. */
	private final List<Request> requests = new ArrayList<>();
	private long lastRequestRefresh;
	private int nextRequestId;
	/** K: current shortages (goods id → base price multiplier) and the period they belong to. */
	private final Map<Identifier, Double> shortages = new LinkedHashMap<>();
	private long shortagePeriod;

	private Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, Optional<Identifier> profileId,
			int level, long lastLevelCheckGameTime, List<BlockPos> stalls, long lastUpdateGameTime, List<GoodEntry> goods,
			List<Request> requests, long lastRequestRefresh, int nextRequestId, List<Shortage> shortages, long shortagePeriod) {
		this.id = id;
		this.dimension = dimension;
		this.center = center;
		this.origin = origin;
		this.profileId = profileId.orElse(null);
		this.level = level;
		this.lastLevelCheckGameTime = lastLevelCheckGameTime;
		this.stalls = new HashSet<>(stalls);
		this.lastUpdateGameTime = lastUpdateGameTime;
		this.goods = new LinkedHashMap<>();
		goods.forEach(e -> this.goods.put(e.item(), e.state()));
		this.requests.addAll(requests);
		this.lastRequestRefresh = lastRequestRefresh;
		this.nextRequestId = nextRequestId;
		shortages.forEach(sh -> this.shortages.put(sh.goods(), sh.multiplier()));
		this.shortagePeriod = shortagePeriod;
	}

	public Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, long gameTime) {
		this(id, dimension, center, origin, Optional.empty(), MarketLevel.MIN_LEVEL, gameTime, List.of(), gameTime, List.of(),
				List.of(), Long.MIN_VALUE, 1, List.of(), Long.MIN_VALUE);
	}

	private List<Shortage> shortageEntries() {
		List<Shortage> list = new ArrayList<>();
		shortages.forEach((goodsId, m) -> list.add(new Shortage(goodsId, m)));
		return list;
	}

	/** J: open requests (mutable; callers mark the registry dirty). */
	public List<Request> requests() {
		return requests;
	}

	public long lastRequestRefresh() {
		return lastRequestRefresh;
	}

	public void setLastRequestRefresh(long gameTime) {
		this.lastRequestRefresh = gameTime;
	}

	public int takeRequestId() {
		return nextRequestId++;
	}

	/** K: shortages (goods id → multiplier of B). */
	public Map<Identifier, Double> shortages() {
		return shortages;
	}

	public long shortagePeriod() {
		return shortagePeriod;
	}

	public void setShortagePeriod(long period) {
		this.shortagePeriod = period;
	}

	private List<GoodEntry> goodEntries() {
		List<GoodEntry> list = new ArrayList<>(goods.size());
		goods.forEach((goodsId, state) -> list.add(new GoodEntry(goodsId, state)));
		return list;
	}

	public long id() {
		return id;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos center() {
		return center;
	}

	public MarketOrigin origin() {
		return origin;
	}

	/** The market profile (D-1 step 2), or {@code null} for markets created before stage 7. */
	public Identifier profileId() {
		return profileId;
	}

	public void setProfileId(Identifier profileId) {
		this.profileId = profileId;
	}

	/** Market level 1..5 (D-4). Never decreases (Q21). */
	public int level() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public long lastLevelCheckGameTime() {
		return lastLevelCheckGameTime;
	}

	public void setLastLevelCheckGameTime(long gameTime) {
		this.lastLevelCheckGameTime = gameTime;
	}

	public Set<BlockPos> stalls() {
		return stalls;
	}

	public long lastUpdateGameTime() {
		return lastUpdateGameTime;
	}

	/** Every good this market deals in, including ones whose definition was removed (F-1: callers hide those via {@link #params} == null). */
	public Map<Identifier, GoodState> goods() {
		return goods;
	}

	public GoodState good(Identifier goodsId) {
		return goods.get(goodsId);
	}

	public void addGood(Identifier goodsId, GoodState state) {
		goods.put(goodsId, state);
	}

	/** Schema 1 → 2: goods were keyed by item id; the built-in goods ids share the item's path. */
	void remapGoods(UnaryOperator<Identifier> mapping) {
		Map<Identifier, GoodState> old = new LinkedHashMap<>(goods);
		goods.clear();
		old.forEach((key, state) -> goods.put(mapping.apply(key), state));
	}

	/** Effective params of one good, or {@code null} if it has no definition any more. */
	public GoodParams params(Identifier goodsId) {
		GoodState state = goods.get(goodsId);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (state == null || def == null) return null;
		double multiplier = profileMultiplier(goodsId, def) * shortages.getOrDefault(goodsId, 1.0); // K: shortage raises B
		return def.params(state.priceFactor(), multiplier, TradeExpansionConfig.get());
	}

	private double profileMultiplier(Identifier goodsId, GoodsDefinition def) {
		MarketProfile profile = MarketProfiles.get(profileId);
		return profile == null ? 1.0 : profile.multiplier(goodsId, def.item());
	}

	/**
	 * J: base price B of any good as seen by this market. Goods it does not deal in have no price factor, so 1 is used
	 * (design J, v0.12.1).
	 */
	public double basePriceFor(Identifier goodsId) {
		GoodParams p = params(goodsId);
		if (p != null) return p.basePrice();
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null) return 0;
		return def.basePrice() * profileMultiplier(goodsId, def) * TradeExpansionConfig.get().price.globalMultiplier;
	}

	/** Lazy recovery of every good up to {@code gameTime} (B-3). */
	public void refresh(long gameTime) {
		double days = (gameTime - lastUpdateGameTime) / (double) PriceModel.TICKS_PER_DAY;
		TradeExpansionConfig config = TradeExpansionConfig.get();
		if (days > 0) {
			goods.forEach((goodsId, state) -> {
				GoodParams p = params(goodsId);
				if (p != null) PriceModel.recover(p, state, days, config.recovery.halfLifeDays, config.recovery.restockMultiplier);
			});
		}
		lastUpdateGameTime = gameTime;
		long day = Math.floorDiv(gameTime, PriceModel.TICKS_PER_DAY);
		goods.values().forEach(state -> state.sample(day, HISTORY_SIZE));
	}

	/** Applies {@code days} of recovery without waiting (test command, design 5-2). */
	public void elapse(double days) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		goods.forEach((goodsId, state) -> {
			GoodParams p = params(goodsId);
			if (p != null) PriceModel.recover(p, state, days, config.recovery.halfLifeDays, config.recovery.restockMultiplier);
		});
	}

	public double horizontalDistanceTo(BlockPos pos) {
		double dx = center.getX() - pos.getX();
		double dz = center.getZ() - pos.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
