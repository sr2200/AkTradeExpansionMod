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
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * One market: a group of stalls sharing stock and prices (design C-1 / D-4 / F-1).
 * Goods are keyed by goods id ({@code ak_trade_expansion:bread}), not by item id.
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

	/** C-4: name and namer (founder of a crafted market, or whoever named it first). */
	private record Naming(Optional<String> name, Optional<UUID> namer) {
		static final Codec<Naming> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name").forGetter(Naming::name),
				UUIDUtil.CODEC.optionalFieldOf("namer").forGetter(Naming::namer)
		).apply(i, Naming::new));
	}

	/** D-4 (v0.27): a good taken off the shelves when the market shrank, kept with its state until the market grows again. */
	private record Shelved(Identifier goods, GoodState state, long since) {
		static final Codec<Shelved> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(Shelved::goods),
				GOOD_STATE_CODEC.fieldOf("state").forGetter(Shelved::state),
				Codec.LONG.fieldOf("since").forGetter(Shelved::since)
		).apply(i, Shelved::new));
	}

	/** M (Q39): one player's reputation points and the market-clock time they were last brought up to date. */
	public record Standing(UUID player, double points, long since) {
		static final Codec<Standing> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Standing::player),
				Codec.DOUBLE.fieldOf("points").forGetter(Standing::points),
				Codec.LONG.fieldOf("since").forGetter(Standing::since)
		).apply(i, Standing::new));
	}

	/** N (v0.36): development points one player brought to the market (donations, requests, trades), never decayed. */
	public record Contributor(UUID player, String name, double points) {
		static final Codec<Contributor> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Contributor::player),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Contributor::name),
				Codec.DOUBLE.fieldOf("points").forGetter(Contributor::points)
		).apply(i, Contributor::new));
	}

	/**
	 * Fields added after the 16-field limit of RecordCodecBuilder: shelved goods and today's specials (v0.27), reputation
	 * and the specials shown only to players with a good reputation (v0.29), development (v0.36).
	 */
	private record Assortment(List<Shelved> shelved, List<Identifier> specials, long specialDay, List<Standing> reputation,
			List<Identifier> bonusSpecials, double development, long developmentSince, List<Contributor> contributors) {
		static final MapCodec<Assortment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Shelved.CODEC.listOf().optionalFieldOf("shelved", List.of()).forGetter(Assortment::shelved),
				Identifier.CODEC.listOf().optionalFieldOf("specials", List.of()).forGetter(Assortment::specials),
				Codec.LONG.optionalFieldOf("special_day", Long.MIN_VALUE).forGetter(Assortment::specialDay),
				Standing.CODEC.listOf().optionalFieldOf("reputation", List.of()).forGetter(Assortment::reputation),
				Identifier.CODEC.listOf().optionalFieldOf("bonus_specials", List.of()).forGetter(Assortment::bonusSpecials),
				Codec.DOUBLE.optionalFieldOf("development", 0.0).forGetter(Assortment::development),
				Codec.LONG.optionalFieldOf("development_since", Long.MIN_VALUE).forGetter(Assortment::developmentSince),
				Contributor.CODEC.listOf().optionalFieldOf("contributors", List.of()).forGetter(Assortment::contributors)
		).apply(i, Assortment::new));
	}

	private static final MapCodec<Market> MAIN_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
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
			Codec.LONG.optionalFieldOf("shortage_period", Long.MIN_VALUE).forGetter(m -> m.shortagePeriod),
			Naming.CODEC.optionalFieldOf("naming", new Naming(Optional.empty(), Optional.empty()))
					.forGetter(m -> new Naming(Optional.ofNullable(m.name), Optional.ofNullable(m.namer)))
	).apply(i, Market::new));

	public static final Codec<Market> CODEC = Codec.mapPair(MAIN_CODEC, Assortment.CODEC).xmap(pair -> {
		Market m = pair.getFirst();
		Assortment a = pair.getSecond();
		a.shelved().forEach(sh -> m.shelved.put(sh.goods(), sh));
		m.specials.addAll(a.specials());
		m.specialDay = a.specialDay();
		a.reputation().forEach(st -> m.reputation.put(st.player(), st));
		m.bonusSpecials.addAll(a.bonusSpecials());
		m.development = a.development();
		m.developmentSince = a.developmentSince();
		a.contributors().forEach(c -> m.contributors.put(c.player(), c));
		return m;
	}, m -> Pair.of(m, new Assortment(List.copyOf(m.shelved.values()), List.copyOf(m.specials), m.specialDay,
			List.copyOf(m.reputation.values()), List.copyOf(m.bonusSpecials), m.development, m.developmentSince,
			List.copyOf(m.contributors.values())))).codec();

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
	/** C-4: custom name ({@code null} = automatic) and the player allowed to change it. */
	private String name;
	private UUID namer;
	/** D-4: goods taken off when the level went down, in the order they were taken off. */
	private final Map<Identifier, Shelved> shelved = new LinkedHashMap<>();
	/** D-5: today's specials (goods outside the profile, kept for one in-game day) and the day they belong to. */
	private final Set<Identifier> specials = new LinkedHashSet<>();
	private long specialDay = Long.MIN_VALUE;
	/** D-5 / M: the part of {@link #specials} shown only to players with a good reputation. */
	private final Set<Identifier> bonusSpecials = new LinkedHashSet<>();
	/** M (Q39): reputation of each player with this market (decay applied lazily, see {@code Reputation}). */
	private final Map<UUID, Standing> reputation = new LinkedHashMap<>();
	/** N (v0.36): development points (decay applied lazily when development.halfLifeDays > 0) and who brought them. */
	private double development;
	private long developmentSince = Long.MIN_VALUE;
	private final Map<UUID, Contributor> contributors = new LinkedHashMap<>();

	private Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, Optional<Identifier> profileId,
			int level, long lastLevelCheckGameTime, List<BlockPos> stalls, long lastUpdateGameTime, List<GoodEntry> goods,
			List<Request> requests, long lastRequestRefresh, int nextRequestId, List<Shortage> shortages, long shortagePeriod,
			Naming naming) {
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
		this.name = naming.name().orElse(null);
		this.namer = naming.namer().orElse(null);
	}

	public Market(long id, ResourceKey<Level> dimension, BlockPos center, MarketOrigin origin, long gameTime) {
		this(id, dimension, center, origin, Optional.empty(), MarketLevel.MIN_LEVEL, gameTime, List.of(), gameTime, List.of(),
				List.of(), Long.MIN_VALUE, 1, List.of(), Long.MIN_VALUE, new Naming(Optional.empty(), Optional.empty()));
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

	/** C-4: custom name, or {@code null} for the automatic one (see {@link MarketNames}). */
	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UUID namer() {
		return namer;
	}

	public void setNamer(UUID namer) {
		this.namer = namer;
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

	/** Market level 1..5 (D-4): the measured size; it goes down too since v0.27 (Q21). */
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

	/** Goods counted in N (D-4): not auto_add (e.g. the emerald coin) and not today's specials, in display order. */
	public List<Identifier> regularGoods() {
		List<Identifier> list = new ArrayList<>();
		for (Identifier goodsId : goods.keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (specials.contains(goodsId) || def != null && def.autoAdd().isPresent()) continue;
			list.add(goodsId);
		}
		return list;
	}

	/** D-4: takes a good off the shelves, keeping its state (the market must be refreshed to now). */
	public void shelve(Identifier goodsId) {
		GoodState state = goods.remove(goodsId);
		if (state == null) return;
		shortages.remove(goodsId);
		shelved.put(goodsId, new Shelved(goodsId, state, lastUpdateGameTime));
	}

	/** Shelved goods, most recently shelved last. */
	public List<Identifier> shelvedGoods() {
		return List.copyOf(shelved.keySet());
	}

	/** D-4: puts a shelved good back; its stock and pressure recover for the time it was away (B-3). */
	public void unshelve(Identifier goodsId) {
		Shelved entry = shelved.remove(goodsId);
		if (entry == null || goods.containsKey(goodsId)) return;
		goods.put(goodsId, entry.state());
		GoodParams p = params(goodsId);
		double days = (lastUpdateGameTime - entry.since()) / (double) PriceModel.TICKS_PER_DAY;
		if (p != null) PriceModel.recover(p, entry.state(), days, TradeExpansionConfig.get().recovery.restockMultiplier);
	}

	/** Drops every shelved good ({@code market reroll}). */
	public void clearShelved() {
		shelved.clear();
	}

	/** D-5: today's specials (mutable). */
	public Set<Identifier> specials() {
		return specials;
	}

	public boolean isSpecial(Identifier goodsId) {
		return specials.contains(goodsId);
	}

	public long specialDay() {
		return specialDay;
	}

	public void setSpecialDay(long day) {
		this.specialDay = day;
	}

	/** Removes a good (today's special at the end of its day). */
	public void removeGood(Identifier goodsId) {
		goods.remove(goodsId);
		shortages.remove(goodsId);
		specials.remove(goodsId);
		bonusSpecials.remove(goodsId);
	}

	/** D-5 / M: specials shown only to players with a good reputation (a subset of {@link #specials()}, mutable). */
	public Set<Identifier> bonusSpecials() {
		return bonusSpecials;
	}

	/** M: stored standing of a player, or {@code null}. */
	public Standing standing(UUID player) {
		return reputation.get(player);
	}

	public void setStanding(Standing standing) {
		reputation.put(standing.player(), standing);
	}

	/** N: stored development points (use {@code Development.points} for the value with decay applied). */
	public double development() {
		return development;
	}

	public long developmentSince() {
		return developmentSince;
	}

	public void setDevelopment(double points, long since) {
		this.development = Math.max(0, points);
		this.developmentSince = since;
	}

	/** N: contributors in the order they first contributed. */
	public Map<UUID, Contributor> contributors() {
		return contributors;
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
		if (specials.contains(goodsId)) multiplier *= TradeExpansionConfig.get().market.specialPriceMultiplier; // D-5
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
				if (p != null) PriceModel.recover(p, state, days, config.recovery.restockMultiplier);
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
			if (p != null) PriceModel.recover(p, state, days, config.recovery.restockMultiplier);
		});
	}

	public double horizontalDistanceTo(BlockPos pos) {
		double dx = center.getX() - pos.getX();
		double dz = center.getZ() - pos.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
