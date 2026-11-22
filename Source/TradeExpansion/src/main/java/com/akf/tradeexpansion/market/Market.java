package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.MarketLevel;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
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

	/** K: one shortage; {@code sold} (v0.78, K-2) = units sold to it in its period. */
	private record Shortage(Identifier goods, double multiplier, int sold) {
		static final Codec<Shortage> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("goods").forGetter(Shortage::goods),
				Codec.DOUBLE.fieldOf("multiplier").forGetter(Shortage::multiplier),
				Codec.INT.optionalFieldOf("sold", 0).forGetter(Shortage::sold)
		).apply(i, Shortage::new));
	}

	/** K-2 (v0.78): the village's vigor and how many shortages were fulfilled (fields after the two full records). */
	private record Growth(double vigor, int fulfilled) {
		static final MapCodec<Growth> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("vigor", com.akf.tradeexpansion.core.DemandMath.VIGOR_MAX).forGetter(Growth::vigor),
				Codec.INT.optionalFieldOf("shortages_fulfilled", 0).forGetter(Growth::fulfilled)
		).apply(i, Growth::new));
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

	/** P (v0.46): a goods group in fashion (multiplier &gt; 1) or out of fashion (&lt; 1) at this market. */
	private record TrendEntry(Identifier group, double multiplier) {
		static final Codec<TrendEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("group").forGetter(TrendEntry::group),
				Codec.DOUBLE.fieldOf("multiplier").forGetter(TrendEntry::multiplier)
		).apply(i, TrendEntry::new));
	}

	/**
	 * Fields added after the 16-field limit of RecordCodecBuilder: shelved goods and today's specials (v0.27), reputation
	 * and the specials shown only to players with a good reputation (v0.29), development (v0.36), rival guilds and
	 * trends (v0.46), the number of rerolls (v0.77). This record is full (16 fields): add the next fields elsewhere.
	 */
	private record Assortment(List<Shelved> shelved, List<Identifier> specials, long specialDay, List<Standing> reputation,
			List<Identifier> bonusSpecials, double development, long developmentSince, List<Contributor> contributors,
			List<Facility> facilities, Map<Identifier, Map<String, Integer>> donations, List<BlockPos> markers, int measuredLevel,
			long rivalDay, List<TrendEntry> trends, long trendPeriod, int rerolls) {
		static final MapCodec<Assortment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Shelved.CODEC.listOf().optionalFieldOf("shelved", List.of()).forGetter(Assortment::shelved),
				Identifier.CODEC.listOf().optionalFieldOf("specials", List.of()).forGetter(Assortment::specials),
				Codec.LONG.optionalFieldOf("special_day", Long.MIN_VALUE).forGetter(Assortment::specialDay),
				Standing.CODEC.listOf().optionalFieldOf("reputation", List.of()).forGetter(Assortment::reputation),
				Identifier.CODEC.listOf().optionalFieldOf("bonus_specials", List.of()).forGetter(Assortment::bonusSpecials),
				Codec.DOUBLE.optionalFieldOf("development", 0.0).forGetter(Assortment::development),
				Codec.LONG.optionalFieldOf("development_since", Long.MIN_VALUE).forGetter(Assortment::developmentSince),
				Contributor.CODEC.listOf().optionalFieldOf("contributors", List.of()).forGetter(Assortment::contributors),
				Facility.CODEC.listOf().optionalFieldOf("facilities", List.of()).forGetter(Assortment::facilities),
				Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING, Codec.INT))
						.optionalFieldOf("facility_donations", Map.of()).forGetter(Assortment::donations),
				BlockPos.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(Assortment::markers),
				Codec.INT.optionalFieldOf("measured_level", 0).forGetter(Assortment::measuredLevel),
				Codec.LONG.optionalFieldOf("rival_day", Long.MIN_VALUE).forGetter(Assortment::rivalDay),
				TrendEntry.CODEC.listOf().optionalFieldOf("trends", List.of()).forGetter(Assortment::trends),
				Codec.LONG.optionalFieldOf("trend_period", Long.MIN_VALUE).forGetter(Assortment::trendPeriod),
				Codec.INT.optionalFieldOf("rerolls", 0).forGetter(Assortment::rerolls)
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

	public static final Codec<Market> CODEC = Codec.mapPair(MAIN_CODEC, Codec.mapPair(Assortment.CODEC, Growth.CODEC)).xmap(pair -> {
		Market m = pair.getFirst();
		Assortment a = pair.getSecond().getFirst();
		Growth growth = pair.getSecond().getSecond();
		m.vigor = Math.clamp(growth.vigor(), 0, com.akf.tradeexpansion.core.DemandMath.VIGOR_MAX);
		m.shortagesFulfilled = Math.max(0, growth.fulfilled());
		a.shelved().forEach(sh -> m.shelved.put(sh.goods(), sh));
		m.specials.addAll(a.specials());
		m.specialDay = a.specialDay();
		a.reputation().forEach(st -> m.reputation.put(st.player(), st));
		m.bonusSpecials.addAll(a.bonusSpecials());
		m.development = a.development();
		m.developmentSince = a.developmentSince();
		a.contributors().forEach(c -> m.contributors.put(c.player(), c));
		a.facilities().forEach(f -> m.facilities.put(f.id(), f));
		a.donations().forEach((id, counts) -> m.donations.put(id, new LinkedHashMap<>(counts)));
		a.markers().forEach(p -> m.markers.add(p.immutable()));
		m.measuredLevel = a.measuredLevel() > 0 ? a.measuredLevel() : m.level; // markets saved before v0.38: no bonus yet
		m.rivalDay = a.rivalDay();
		a.trends().forEach(t -> m.trends.put(t.group(), t.multiplier()));
		m.trendPeriod = a.trendPeriod();
		m.rerolls = Math.max(0, a.rerolls());
		return m;
	}, m -> Pair.of(m, Pair.of(new Assortment(List.copyOf(m.shelved.values()), List.copyOf(m.specials), m.specialDay,
			List.copyOf(m.reputation.values()), List.copyOf(m.bonusSpecials), m.development, m.developmentSince,
			List.copyOf(m.contributors.values()), List.copyOf(m.facilities.values()), Map.copyOf(m.donations), List.copyOf(m.markers),
			m.measuredLevel, m.rivalDay, m.trendEntries(), m.trendPeriod, m.rerolls), new Growth(m.vigor, m.shortagesFulfilled)))).codec();

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
	/** K-2 (v0.78): units sold to each current shortage in its period, the village's vigor and the shortages fulfilled. */
	private final Map<Identifier, Integer> shortageSold = new LinkedHashMap<>();
	private double vigor = com.akf.tradeexpansion.core.DemandMath.VIGOR_MAX;
	private int shortagesFulfilled;
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
	/** N-4 (v0.38): facilities under construction, completed or destroyed, by facility id. */
	private final Map<Identifier, Facility> facilities = new LinkedHashMap<>();
	/** N-2: materials donated per facility id and wanted key (item id or #tag), reset when the facility is completed. */
	private final Map<Identifier, Map<String, Integer>> donations = new LinkedHashMap<>();
	/** N-5: building site stakes placed near the market, oldest first. */
	private final List<BlockPos> markers = new ArrayList<>();
	/** D-4 / N-3: the level measured from beds and villagers, before the facility bonus. */
	private int measuredLevel = MarketLevel.MIN_LEVEL;
	/** O-4 (v0.46): the last market-clock day whose rival pushes this market has received. */
	private long rivalDay = Long.MIN_VALUE;
	/** P (v0.46): goods groups in / out of fashion (group id → multiplier of B) and the period they belong to. */
	private final Map<Identifier, Double> trends = new LinkedHashMap<>();
	private long trendPeriod = Long.MIN_VALUE;
	/** D-1 (v0.77): how many times {@code market reroll} drew the goods again; mixed into the draw so each reroll differs. */
	private int rerolls;

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
		shortages.forEach(sh -> {
			this.shortages.put(sh.goods(), sh.multiplier());
			if (sh.sold() > 0) this.shortageSold.put(sh.goods(), sh.sold());
		});
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
		shortages.forEach((goodsId, m) -> list.add(new Shortage(goodsId, m, shortageSold.getOrDefault(goodsId, 0))));
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

	/** K-2: units sold to each current shortage (mutable; cleared with the shortages). */
	public Map<Identifier, Integer> shortageSold() {
		return shortageSold;
	}

	/** Removes every shortage and what was sold to them. */
	public void clearShortages() {
		shortages.clear();
		shortageSold.clear();
	}

	/** K-2: the village's vigor (0〜100). */
	public double vigor() {
		return vigor;
	}

	public void setVigor(double vigor) {
		this.vigor = vigor;
	}

	/** K-2: shortages fulfilled at this market so far. */
	public int shortagesFulfilled() {
		return shortagesFulfilled;
	}

	public void setShortagesFulfilled(int count) {
		this.shortagesFulfilled = count;
	}

	private List<TrendEntry> trendEntries() {
		List<TrendEntry> list = new ArrayList<>();
		trends.forEach((group, m) -> list.add(new TrendEntry(group, m)));
		return list;
	}

	/** O-4: last day of rival pushes received. */
	public long rivalDay() {
		return rivalDay;
	}

	public void setRivalDay(long day) {
		this.rivalDay = day;
	}

	/** P: trends (goods group id → multiplier of B), in fashion first (mutable). */
	public Map<Identifier, Double> trends() {
		return trends;
	}

	public long trendPeriod() {
		return trendPeriod;
	}

	public void setTrendPeriod(long period) {
		this.trendPeriod = period;
	}

	/** D-1 (v0.77): the number of {@code market reroll}s (0 = the goods drawn when the market was created). */
	public int rerolls() {
		return rerolls;
	}

	public void addReroll() {
		rerolls++;
	}

	/** P: product of the trend multipliers of every group the good is in; 1 for specials and auto_add goods. */
	/**
	 * v0.51 theater: a trend in fashion raises B by {@code theaterTrendMultiplier} times its bonus, one out of fashion
	 * lowers it by only 1 / that of its loss.
	 */
	public static double theater(double m, boolean theater) {
		return theater ? com.akf.tradeexpansion.core.TrendMath.theater(m, TradeExpansionConfig.get().development.theaterTrendMultiplier) : m;
	}

	public double trendMultiplier(Identifier goodsId) {
		if (trends.isEmpty() || specials.contains(goodsId)) return 1.0;
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null || def.autoAdd().isPresent()) return 1.0;
		double m = 1.0;
		boolean theater = hasFacility(FacilityKind.THEATER);
		for (Identifier group : GoodsGroups.groupsOf(goodsId)) m *= theater(trends.getOrDefault(group, 1.0), theater);
		return m;
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
		shortageSold.remove(goodsId);
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
		shortageSold.remove(goodsId);
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

	/** N-4: facilities by id (mutable; callers mark the registry dirty). */
	public Map<Identifier, Facility> facilities() {
		return facilities;
	}

	/** N-4: a completed facility of this kind (its effect is on). */
	public boolean hasFacility(FacilityKind kind) {
		for (Facility f : facilities.values()) {
			if (f.kind() == kind && f.complete()) return true;
		}
		return false;
	}

	public int completedFacilities() {
		return (int) facilities.values().stream().filter(Facility::complete).count();
	}

	/** N-2: donated counts of one facility (mutable). */
	public Map<String, Integer> donations(Identifier facility) {
		return donations.computeIfAbsent(facility, k -> new LinkedHashMap<>());
	}

	public void clearDonations(Identifier facility) {
		donations.remove(facility);
	}

	/** N-5: building site stakes (mutable). */
	public List<BlockPos> markers() {
		return markers;
	}

	/** D-4: the level from beds and villagers alone; {@link #level()} adds the facility bonus (N-3). */
	public int measuredLevel() {
		return measuredLevel;
	}

	public void setMeasuredLevel(int measuredLevel) {
		this.measuredLevel = measuredLevel;
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
		multiplier *= trendMultiplier(goodsId); // P: trends
		if (specials.contains(goodsId)) multiplier *= TradeExpansionConfig.get().market.specialPriceMultiplier; // D-5
		GoodParams params = def.params(state.priceFactor(), multiplier, TradeExpansionConfig.get());
		if (hasFacility(FacilityKind.WAREHOUSE)) params = warehouse(params);
		if (hasFacility(FacilityKind.CLOCK_TOWER)) params = clockTower(params);
		return params;
	}

	/** v0.51: a clock tower shortens the recovery half-life H of every good. */
	private static GoodParams clockTower(GoodParams p) {
		double k = Math.clamp(TradeExpansionConfig.get().development.clockTowerRecoveryMultiplier, 0.1, 1.0);
		return new GoodParams(p.basePrice(), p.elasticity(), p.spread(), p.targetStock(), p.maxStock(), p.restockPerDay(),
				p.minMultiplier(), p.maxMultiplier(), p.consumePerDay(), p.halfLifeDays() * k);
	}

	/** N-4: a warehouse raises the stock cap Scap and the restock speed r (unlimited stock stays unlimited). */
	private static GoodParams warehouse(GoodParams p) {
		TradeExpansionConfig.Development c = TradeExpansionConfig.get().development;
		double cap = p.maxStock() >= 1e9 ? p.maxStock() : Math.max(p.targetStock(), Math.floor(p.maxStock() * c.warehouseStockMultiplier));
		return new GoodParams(p.basePrice(), p.elasticity(), p.spread(), p.targetStock(), cap, p.restockPerDay() * c.warehouseRestockMultiplier,
				p.minMultiplier(), p.maxMultiplier(), p.consumePerDay(), p.halfLifeDays());
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
