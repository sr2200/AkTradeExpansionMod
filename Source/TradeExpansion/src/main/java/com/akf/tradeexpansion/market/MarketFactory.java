package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.GoodsSelector;
import com.akf.tradeexpansion.core.MarketLevel;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketContext;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import com.akf.tradeexpansion.data.condition.MarketCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the goods of a market: profile choice, level, goods selection (design D-1 / D-4).
 */
public final class MarketFactory {
	/** Villagers are counted within this height above / below the market center (D-4). */
	private static final int VILLAGER_HEIGHT = 32;

	private MarketFactory() {
	}

	/** Seed of every random choice of a market: same place, same goods (D-1). */
	public static long seed(ServerLevel level, BlockPos center) {
		return level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L;
	}

	/** Fills a freshly created (empty) market: profile, level, goods (D-1). */
	public static void populate(ServerLevel level, Market market, List<Market> neighbors) {
		populate(level, market, neighbors, MarketLevel.MIN_LEVEL);
	}

	/**
	 * {@code /aktradeex market reroll}: profile and goods again with the current data packs, at the measured level (kept at
	 * least at its previous value when {@code market.shrinkOnLevelDown} is off, Q21); price factors and pressure start over.
	 */
	public static void reroll(ServerLevel level, Market market, List<Market> neighbors) {
		int previous = TradeExpansionConfig.get().market.shrinkOnLevelDown ? MarketLevel.MIN_LEVEL : market.level();
		market.goods().clear();
		market.clearShelved();
		market.specials().clear();
		market.bonusSpecials().clear();
		market.setSpecialDay(Long.MIN_VALUE); // D-5: chosen again by the next upkeep
		// Shortages and requests depend on the goods, so they are chosen again too (K).
		market.shortages().clear();
		market.setShortagePeriod(Long.MIN_VALUE);
		market.requests().clear();
		market.setLastRequestRefresh(Long.MIN_VALUE);
		populate(level, market, neighbors, previous);
	}

	private static void populate(ServerLevel level, Market market, List<Market> neighbors, int minLevel) {
		long now = MarketClock.now(level);
		Random random = new Random(seed(level, market.center()));
		MarketContext context = new MarketContext(level, market.center(), market.origin());

		market.setLevel(Math.max(minLevel, measureLevel(level, market.center())));
		market.setLastLevelCheckGameTime(now);
		market.setProfileId(MarketProfiles.choose(context, random));

		TradeExpansionConfig config = TradeExpansionConfig.get();
		int target = MarketLevel.goodsCount(market.level(), config.market.minGoods, config.market.maxGoods);
		MarketProfile profile = MarketProfiles.get(market.profileId());
		List<String> selected;
		if (profile != null) {
			selected = GoodsSelector.select(profile.alwaysGoods(), profile.selectorPools(), market.level(), target, random);
		} else {
			TradeExpansion.LOGGER.warn("No market profile for market #{}; using the first {} goods", market.id(), target);
			selected = GoodsCatalog.all().entrySet().stream()
					.filter(e -> e.getValue().autoAdd().isEmpty())
					.limit(target).map(e -> e.getKey().toString()).toList();
		}
		if (selected.size() < target) {
			TradeExpansion.LOGGER.warn("Market #{} ({}): only {} of {} goods available", market.id(), market.profileId(), selected.size(), target);
		}
		for (String goodsId : selected) addGood(market, Identifier.parse(goodsId), random, neighbors, now);

		// D-1 step 0: goods with auto_add conditions (e.g. emerald coin in village markets), outside N.
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			List<MarketCondition> conditions = e.getValue().autoAdd().orElse(null);
			if (conditions != null && market.good(e.getKey()) == null && MarketCondition.all(conditions, context)) {
				addGood(market, e.getKey(), random, neighbors, now);
			}
		}
	}

	/**
	 * Re-measures the level when the check interval has passed (D-4). Growing brings back shelved goods first, then adds
	 * new ones; shrinking (v0.27, Q21) shelves the most recently listed goods, keeping their state.
	 *
	 * @return {@code 1} if the market grew, {@code -1} if it shrank, {@code 0} otherwise
	 */
	public static int resizeIfDue(ServerLevel level, Market market, List<Market> neighbors) {
		long now = MarketClock.now(level);
		TradeExpansionConfig config = TradeExpansionConfig.get();
		double interval = config.market.levelCheckIntervalDays * PriceModel.TICKS_PER_DAY;
		if (now - market.lastLevelCheckGameTime() < interval) return 0;
		market.setLastLevelCheckGameTime(now);

		int measured = measureLevel(level, market.center());
		if (measured == market.level() || measured < market.level() && !config.market.shrinkOnLevelDown) return 0;
		boolean grew = measured > market.level();
		market.refresh(now); // shelved / restored goods carry their state as of now
		market.setLevel(measured);
		int target = MarketLevel.goodsCount(measured, config.market.minGoods, config.market.maxGoods);
		if (grew) {
			grow(level, market, neighbors, target, now);
		} else {
			List<Identifier> regular = market.regularGoods();
			for (int i = regular.size() - 1; i >= target; i--) market.shelve(regular.get(i));
		}
		TradeExpansion.LOGGER.info("Market #{} {} to level {}", market.id(), grew ? "grew" : "shrank", measured);
		return grew ? 1 : -1;
	}

	private static void grow(ServerLevel level, Market market, List<Market> neighbors, int target, long now) {
		// Goods shelved when the market shrank come back first (last shelved, first back), with their old state.
		List<Identifier> shelved = market.shelvedGoods();
		for (int i = shelved.size() - 1; i >= 0 && market.regularGoods().size() < target; i--) {
			Identifier goodsId = shelved.get(i);
			if (market.isSpecial(goodsId)) market.removeGood(goodsId); // listed today as a special: the shelved state wins
			market.unshelve(goodsId);
		}
		Identifier profileId = market.profileId() != null ? market.profileId() : MarketProfiles.defaultFor(level.dimension());
		MarketProfile profile = MarketProfiles.get(profileId);
		if (profile == null) return;
		Set<String> existing = new HashSet<>();
		market.regularGoods().forEach(id -> existing.add(id.toString())); // auto_add goods and specials are outside N
		Random random = new Random(seed(level, market.center()) ^ market.level());
		for (String goodsId : GoodsSelector.grow(existing, profile.selectorPools(), market.level(), target, random)) {
			Identifier id = Identifier.parse(goodsId);
			// A special chosen for good becomes a regular good with its current state (D-5).
			market.bonusSpecials().remove(id);
			if (!market.specials().remove(id)) addGood(market, id, random, neighbors, now);
		}
	}

	/**
	 * D-4: beds around the center (and, with {@code market.levelUsesVillagers}, villagers too: the lower level wins), or
	 * villagers alone in the Nether / End where beds explode.
	 */
	public static int measureLevel(ServerLevel level, BlockPos center) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		if (usesVillagers(level)) {
			return MarketLevel.level(countVillagers(level, center), config.market.villagerThresholdArray());
		}
		if (!config.market.levelUsesVillagers) return MarketLevel.level(countBeds(level, center), config.market.levelThresholdArray());
		// v0.27.5: beds and villagers both count; the lower level wins.
		return MarketLevel.levelOfBoth(countBeds(level, center), config.market.levelThresholdArray(),
				countVillagers(level, center), config.market.villagerThresholdArray());
	}

	public static boolean usesVillagers(ServerLevel level) {
		return level.dimension().equals(Level.NETHER) || level.dimension().equals(Level.END);
	}

	/** Beds within the horizontal radius, at any height (D-4). */
	public static long countBeds(ServerLevel level, BlockPos center) {
		int radius = TradeExpansionConfig.get().market.levelRadius;
		return level.getPoiManager().getInSquare(h -> h.is(PoiTypes.HOME), center, radius, PoiManager.Occupancy.ANY)
				.filter(r -> DistanceModel.horizontalDistance(r.getPos().getX(), r.getPos().getZ(), center.getX(), center.getZ()) <= radius)
				.count();
	}

	public static long countVillagers(ServerLevel level, BlockPos center) {
		int radius = TradeExpansionConfig.get().market.levelRadius;
		AABB box = new AABB(center).inflate(radius, VILLAGER_HEIGHT, radius);
		return level.getEntitiesOfClass(Villager.class, box,
				v -> DistanceModel.horizontalDistance(v.getX(), v.getZ(), center.getX() + 0.5, center.getZ() + 0.5) <= radius).size();
	}

	/**
	 * D-5: once per in-game day, replaces today's specials: goods outside the market's list, a few per level, with a
	 * higher price and a small stock. They are not counted in N, not shortages and gone the next day.
	 *
	 * @return {@code true} if they changed
	 */
	public static boolean refreshSpecials(ServerLevel level, Market market, long now) {
		long day = Math.floorDiv(now, PriceModel.TICKS_PER_DAY);
		if (day == market.specialDay()) return false;
		market.setSpecialDay(day);
		for (Identifier old : List.copyOf(market.specials())) market.removeGood(old);

		TradeExpansionConfig config = TradeExpansionConfig.get();
		int regular = config.market.specialGoodsAt(market.level());
		// M: the last specialBonusCount specials are shown only to players with a good reputation.
		int bonus = config.reputation.enabled ? config.reputation.specialBonusCount : 0;
		int count = regular + bonus;
		List<Identifier> candidates = new ArrayList<>();
		Set<Identifier> shelved = new HashSet<>(market.shelvedGoods());
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			GoodsDefinition def = e.getValue();
			// Buyable plain goods only; tokens such as the emerald coin (auto_add / unit > 1) are excluded.
			if (!def.buy() || def.unit() != 1 || def.autoAdd().isPresent()) continue;
			if (market.good(e.getKey()) != null || shelved.contains(e.getKey())) continue;
			candidates.add(e.getKey());
		}
		candidates.sort(null); // stable order for the seeded choice
		Random random = new Random(seed(level, market.center()) ^ (day * 0x2545F4914F6CDD1DL));
		for (int i = 0; i < count && !candidates.isEmpty(); i++) {
			Identifier pick = candidates.remove(random.nextInt(candidates.size()));
			double priceFactor = 1 + (random.nextDouble() * 2 - 1) * config.price.jitter;
			market.addGood(pick, new GoodState(priceFactor, 0, 0, now));
			market.specials().add(pick);
			if (i >= regular) market.bonusSpecials().add(pick);
			GoodParams params = market.params(pick);
			market.good(pick).setStock(Math.max(1, Math.floor(params.targetStock() * config.market.specialStockRatio)));
		}
		return true;
	}

	/** Adds one good with a rolled priceFactor, the C-1 initial pressure and stock T. */
	private static void addGood(Market market, Identifier goodsId, Random random, List<Market> neighbors, long now) {
		if (GoodsCatalog.get(goodsId) == null || market.good(goodsId) != null) return;
		TradeExpansionConfig config = TradeExpansionConfig.get();
		double priceFactor = 1 + (random.nextDouble() * 2 - 1) * config.price.jitter;
		market.addGood(goodsId, new GoodState(priceFactor, 0, 0, now));
		GoodParams params = market.params(goodsId);
		GoodState state = market.good(goodsId);
		state.setPressure(params.clampPressure(initialPressure(goodsId, market, neighbors, now, config)));
		state.setStock(params.targetStock());
	}

	/** C-1: start from the distance-weighted average of nearby markets' X instead of 0. */
	private static double initialPressure(Identifier goodsId, Market market, List<Market> others, long now, TradeExpansionConfig config) {
		List<DistanceModel.Neighbor> neighbors = new ArrayList<>();
		for (Market other : others) {
			if (other == market) continue;
			double d = other.horizontalDistanceTo(market.center());
			if (d >= config.distance.farRadius || other.good(goodsId) == null) continue;
			other.refresh(now);
			neighbors.add(new DistanceModel.Neighbor(d, other.good(goodsId).pressure()));
		}
		return DistanceModel.initialPressure(neighbors, config.distance.nearRadius, config.distance.farRadius, config.distance.falloffExponent);
	}
}
