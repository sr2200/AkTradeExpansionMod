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
	private static long seed(ServerLevel level, BlockPos center) {
		return level.getSeed() ^ center.asLong() * 0x9E3779B97F4A7C15L;
	}

	/** Fills a freshly created (empty) market: profile, level, goods (D-1). */
	public static void populate(ServerLevel level, Market market, List<Market> neighbors) {
		populate(level, market, neighbors, MarketLevel.MIN_LEVEL);
	}

	/**
	 * {@code /tradeex market reroll}: profile and goods again with the current data packs. The level is kept at least at
	 * its previous value (Q21); price factors and pressure start over.
	 */
	public static void reroll(ServerLevel level, Market market, List<Market> neighbors) {
		int previous = market.level();
		market.goods().clear();
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
	 * Re-measures the level when the check interval has passed and adds goods on level-up (D-4).
	 *
	 * @return {@code true} if the market grew
	 */
	public static boolean growIfDue(ServerLevel level, Market market, List<Market> neighbors) {
		long now = MarketClock.now(level);
		double interval = TradeExpansionConfig.get().market.levelCheckIntervalDays * PriceModel.TICKS_PER_DAY;
		if (now - market.lastLevelCheckGameTime() < interval) return false;
		market.setLastLevelCheckGameTime(now);

		int measured = measureLevel(level, market.center());
		if (measured <= market.level()) return false; // Q21: the level never goes down
		market.setLevel(measured);

		Identifier profileId = market.profileId() != null ? market.profileId() : MarketProfiles.defaultFor(level.dimension());
		MarketProfile profile = MarketProfiles.get(profileId);
		if (profile != null) {
			TradeExpansionConfig config = TradeExpansionConfig.get();
			int target = MarketLevel.goodsCount(measured, config.market.minGoods, config.market.maxGoods);
			Set<String> existing = new HashSet<>();
			market.goods().keySet().forEach(id -> {
				GoodsDefinition def = GoodsCatalog.get(id);
				if (def == null || def.autoAdd().isEmpty()) existing.add(id.toString()); // auto_add goods are outside N
			});
			Random random = new Random(seed(level, market.center()) ^ measured);
			for (String goodsId : GoodsSelector.grow(existing, profile.selectorPools(), measured, target, random)) {
				addGood(market, Identifier.parse(goodsId), random, neighbors, now);
			}
		}
		TradeExpansion.LOGGER.info("Market #{} grew to level {}", market.id(), measured);
		return true;
	}

	/** D-4: beds around the center, or villagers in the Nether / End where beds explode. */
	public static int measureLevel(ServerLevel level, BlockPos center) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		if (usesVillagers(level)) {
			return MarketLevel.level(countVillagers(level, center), config.market.villagerThresholdArray());
		}
		return MarketLevel.level(countBeds(level, center), config.market.levelThresholdArray());
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
