package com.akf.tradeexpansion.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Clamps every setting into the range of design chapter G. Out-of-range values are corrected with a warning
 * instead of rejecting the whole file.
 */
public final class ConfigValidator {
	private ConfigValidator() {
	}

	/** Corrects {@code c} in place and returns it. */
	public static TradeExpansionConfig validate(TradeExpansionConfig c) {
		TradeExpansionConfig.Currency cur = c.currency;
		cur.ratios = intList("currency.ratios", cur.ratios, 4, 2, 100, false, List.of(10, 10, 10, 10));
		cur.exchangeFee = clamp("currency.exchangeFee", cur.exchangeFee, 0, 0.5);
		cur.startingMoney = clamp("currency.startingMoney", cur.startingMoney, 0, 1_000_000_000L);
		cur.emeraldCoinsPerBlock = clamp("currency.emeraldCoinsPerBlock", cur.emeraldCoinsPerBlock, 0, 64);

		TradeExpansionConfig.Price p = c.price;
		p.defaultSpread = clamp("price.defaultSpread", p.defaultSpread, 0.05, 0.9);
		p.defaultElasticity = clamp("price.defaultElasticity", p.defaultElasticity, 3, 10000);
		p.minMultiplier = clamp("price.minMultiplier", p.minMultiplier, 0.01, 1);
		p.maxMultiplier = clamp("price.maxMultiplier", p.maxMultiplier, 1, 100);
		p.jitter = clamp("price.jitter", p.jitter, 0, 0.5);
		p.defaultMaxStockMultiplier = clamp("price.defaultMaxStockMultiplier", p.defaultMaxStockMultiplier, 1, 10);
		p.globalMultiplier = clamp("price.globalMultiplier", p.globalMultiplier, 0.01, 100);

		TradeExpansionConfig.Recovery r = c.recovery;
		r.halfLifeDays = clamp("recovery.halfLifeDays", r.halfLifeDays, 0.05, 100);
		r.restockMultiplier = clamp("recovery.restockMultiplier", r.restockMultiplier, 0, 100);
		r.consumableMultiplier = clamp("recovery.consumableMultiplier", r.consumableMultiplier, 0, 10);
		r.consumableHalfLifeDays = clamp("recovery.consumableHalfLifeDays", r.consumableHalfLifeDays, 0.05, 100);

		TradeExpansionConfig.Distance d = c.distance;
		d.mergeRadius = clamp("distance.mergeRadius", d.mergeRadius, 8, 512);
		d.nearRadius = clamp("distance.nearRadius", d.nearRadius, d.mergeRadius, 100000);
		d.farRadius = clamp("distance.farRadius", d.farRadius, d.nearRadius + 1, 100000);
		d.falloffExponent = clamp("distance.falloffExponent", d.falloffExponent, 0.5, 5);
		d.minNewMarketDistance = clamp("distance.minNewMarketDistance", d.minNewMarketDistance, 0, 10000);

		TradeExpansionConfig.Market m = c.market;
		m.minGoods = clamp("market.minGoods", m.minGoods, 1, 100);
		m.maxGoods = clamp("market.maxGoods", m.maxGoods, m.minGoods, 100);
		m.levelThresholds = intList("market.levelThresholds", m.levelThresholds, 4, 0, 100000, true, List.of(5, 10, 20, 40));
		m.villagerThresholds = intList("market.villagerThresholds", m.villagerThresholds, 4, 0, 100000, true, List.of(3, 6, 12, 24));
		m.levelRadius = clamp("market.levelRadius", m.levelRadius, 16, 256);
		m.levelCheckIntervalDays = clamp("market.levelCheckIntervalDays", m.levelCheckIntervalDays, 0, 100);
		m.specialGoods = intList("market.specialGoods", m.specialGoods, 5, 0, 10, false, List.of(1, 1, 2, 2, 2));
		m.specialPriceMultiplier = clamp("market.specialPriceMultiplier", m.specialPriceMultiplier, 0.5, 5);
		m.specialStockRatio = clamp("market.specialStockRatio", m.specialStockRatio, 0.01, 1);

		TradeExpansionConfig.Wallet w = c.wallet;
		w.capacity = clamp("wallet.capacity", w.capacity, 1000, 1_000_000_000L);

		TradeExpansionConfig.Bank b = c.bank;
		b.minMarketLevel = clamp("bank.minMarketLevel", b.minMarketLevel, 1, 5);
		b.interestRatePerDay = clamp("bank.interestRatePerDay", b.interestRatePerDay, 0, 0.01);
		b.cardFee = clamp("bank.cardFee", b.cardFee, 0, 1_000_000L);
		b.cardTransactionFee = clamp("bank.cardTransactionFee", b.cardTransactionFee, 0, 0.1);
		b.interestMaxBalance = clamp("bank.interestMaxBalance", b.interestMaxBalance, 0, 1_000_000_000_000L);

		TradeExpansionConfig.Trade t = c.trade;
		t.maxDistance = clamp("trade.maxDistance", t.maxDistance, 2, 32);
		t.priceTolerance = clamp("trade.priceTolerance", t.priceTolerance, 0, 0.2);
		t.maxPerTransaction = clamp("trade.maxPerTransaction", t.maxPerTransaction, 1, 2304);
		List<TradeExpansionConfig.BulkTier> tiers = new ArrayList<>();
		for (TradeExpansionConfig.BulkTier tier : t.bulkDiscounts) {
			tiers.add(new TradeExpansionConfig.BulkTier(clamp("trade.bulkDiscounts.minQuantity", tier.minQuantity(), 2, 2304),
					clamp("trade.bulkDiscounts.rate", tier.rate(), 0, 0.5)));
		}
		tiers.sort(java.util.Comparator.comparingInt(TradeExpansionConfig.BulkTier::minQuantity));
		t.bulkDiscounts = tiers;

		c.command.permissionLevel = clamp("command.permissionLevel", c.command.permissionLevel, 0, 4);

		c.board.radius = clamp("board.radius", c.board.radius, 64, 4096);
		c.board.maxMarkets = clamp("board.maxMarkets", c.board.maxMarkets, 1, 100);
		c.board.dealThreshold = clamp("board.dealThreshold", c.board.dealThreshold, 0.01, 1);

		TradeExpansionConfig.Quest q = c.quest;
		q.maxPerMarket = clamp("quest.maxPerMarket", q.maxPerMarket, 0, 10);
		q.refreshDays = clamp("quest.refreshDays", q.refreshDays, 0.1, 30);
		q.durationDays = clamp("quest.durationDays", q.durationDays, 0.5, 30);
		q.rewardMultipliers = doubleList("quest.rewardMultipliers", q.rewardMultipliers, 3, 0.1, 10, List.of(1.2, 1.5, 2.0));
		q.difficultyThresholds = intList("quest.difficultyThresholds", q.difficultyThresholds, 2, 0, 1_000_000_000, true, List.of(200, 1000));
		q.transportPerMarket = clamp("quest.transportPerMarket", q.transportPerMarket, 0, 5);
		q.transportMinDistance = clamp("quest.transportMinDistance", q.transportMinDistance, 0, 100_000);
		q.transportMaxDistance = clamp("quest.transportMaxDistance", q.transportMaxDistance, 0, 100_000);
		if (q.transportMaxDistance < q.transportMinDistance) {
			TradeExpansionConfig.warn("quest.transportMaxDistance = {} is below quest.transportMinDistance, using {}", q.transportMaxDistance, q.transportMinDistance);
			q.transportMaxDistance = q.transportMinDistance;
		}
		q.transportBaseMultiplier = clamp("quest.transportBaseMultiplier", q.transportBaseMultiplier, 0, 10);
		q.transportPerThousandBlocks = clamp("quest.transportPerThousandBlocks", q.transportPerThousandBlocks, 0, 10);
		q.transportMaxMultiplier = clamp("quest.transportMaxMultiplier", q.transportMaxMultiplier, 0, 10);

		TradeExpansionConfig.Demand dm = c.demand;
		dm.maxShortages = clamp("demand.maxShortages", dm.maxShortages, 0, 10);
		dm.durationDays = clamp("demand.durationDays", dm.durationDays, 0.5, 30);
		dm.minMultiplier = clamp("demand.minMultiplier", dm.minMultiplier, 1, 5);
		dm.maxMultiplier = clamp("demand.maxMultiplier", dm.maxMultiplier, dm.minMultiplier, 5);

		TradeExpansionConfig.Rivals rv = c.rivals;
		rv.chancePerDay = clamp("rivals.chancePerDay", rv.chancePerDay, 0, 1);
		rv.maxActive = clamp("rivals.maxActive", rv.maxActive, 0, 20);
		rv.minDays = clamp("rivals.minDays", rv.minDays, 1, 30);
		rv.maxDays = clamp("rivals.maxDays", rv.maxDays, rv.minDays, 30);
		rv.minImpact = clamp("rivals.minImpact", rv.minImpact, 0, 2);
		rv.maxImpact = clamp("rivals.maxImpact", rv.maxImpact, rv.minImpact, 2);
		rv.nearRadius = clamp("rivals.nearRadius", rv.nearRadius, 0, 100_000);
		rv.farRadius = clamp("rivals.farRadius", rv.farRadius, rv.nearRadius, 100_000);

		TradeExpansionConfig.Staff st = c.staff;
		st.maxPerPlayer = clamp("staff.maxPerPlayer", st.maxPerPlayer, 0, 50);
		st.minReputationLevel = clamp("staff.minReputationLevel", st.minReputationLevel, 0, 5);
		st.carrierHireFee = clamp("staff.carrierHireFee", st.carrierHireFee, 0, 100_000_000L);
		st.clerkHireFee = clamp("staff.clerkHireFee", st.clerkHireFee, 0, 100_000_000L);
		st.carrierWagePerDay = clamp("staff.carrierWagePerDay", st.carrierWagePerDay, 0, 100_000_000L);
		st.clerkWagePerDay = clamp("staff.clerkWagePerDay", st.clerkWagePerDay, 0, 100_000_000L);
		st.carrierCapacity = clamp("staff.carrierCapacity", st.carrierCapacity, 1, 2304);
		st.clerkCapacity = clamp("staff.clerkCapacity", st.clerkCapacity, 1, 100_000);
		st.carrierBlocksPerDay = clamp("staff.carrierBlocksPerDay", st.carrierBlocksPerDay, 10, 1_000_000);
		st.carrierMaxDistance = clamp("staff.carrierMaxDistance", st.carrierMaxDistance, 0, 1_000_000);
		st.carrierMaxWaitDays = clamp("staff.carrierMaxWaitDays", st.carrierMaxWaitDays, 0, 30);
		st.carrierCommission = clamp("staff.carrierCommission", st.carrierCommission, 0, 1);
		st.carrierSpreadMultiplier = clamp("staff.carrierSpreadMultiplier", st.carrierSpreadMultiplier, 0, 1);
		st.carrierPriceImpact = clamp("staff.carrierPriceImpact", st.carrierPriceImpact, 0.05, 1);
		st.accidentBase = clamp("staff.accidentBase", st.accidentBase, 0, 1);
		st.accidentPerThousandBlocks = clamp("staff.accidentPerThousandBlocks", st.accidentPerThousandBlocks, 0, 1);
		st.accidentMax = clamp("staff.accidentMax", st.accidentMax, 0, 1);
		st.accidentLossMin = clamp("staff.accidentLossMin", st.accidentLossMin, 0, 1);
		st.accidentLossMax = clamp("staff.accidentLossMax", st.accidentLossMax, st.accidentLossMin, 1);
		st.accidentRestDays = clamp("staff.accidentRestDays", st.accidentRestDays, 0, 30);
		st.quitChance = clamp("staff.quitChance", st.quitChance, 0, 1);
		TradeExpansionConfig.Trend tr = c.trend;
		tr.durationDays = clamp("trend.durationDays", tr.durationDays, 0.5, 60);
		tr.popularCount = clamp("trend.popularCount", tr.popularCount, 0, 5);
		tr.unpopularCount = clamp("trend.unpopularCount", tr.unpopularCount, 0, 5);
		tr.popularMin = clamp("trend.popularMin", tr.popularMin, 1, 5);
		tr.popularMax = clamp("trend.popularMax", tr.popularMax, tr.popularMin, 5);
		tr.unpopularMin = clamp("trend.unpopularMin", tr.unpopularMin, 0.1, 1);
		tr.unpopularMax = clamp("trend.unpopularMax", tr.unpopularMax, tr.unpopularMin, 1);
		tr.minGoods = clamp("trend.minGoods", tr.minGoods, 1, 20);

		c.market.newMarketMinBeds = clamp("market.newMarketMinBeds", c.market.newMarketMinBeds, 0, 1000);
		c.market.newMarketMinVillagers = clamp("market.newMarketMinVillagers", c.market.newMarketMinVillagers, 0, 1000);
		c.market.newMarketEnoughBeds = clamp("market.newMarketEnoughBeds", c.market.newMarketEnoughBeds, 0, 1000);
		c.villager.minGoodsPriceMultiplier = clamp("villager.minGoodsPriceMultiplier", c.villager.minGoodsPriceMultiplier, 0, 10);
		c.villager.emeraldPrice = clamp("villager.emeraldPrice", c.villager.emeraldPrice, 1, 10000);

		TradeExpansionConfig.Reputation rp = c.reputation;
		rp.levelThresholds = intList("reputation.levelThresholds", rp.levelThresholds, 5, 1, 1_000_000, true, List.of(50, 150, 400, 1000, 2500));
		rp.halfLifeDays = clamp("reputation.halfLifeDays", rp.halfLifeDays, 0, 1000);
		rp.pointsPerCoin = clamp("reputation.pointsPerCoin", rp.pointsPerCoin, 0, 10);
		rp.shortageMultiplier = clamp("reputation.shortageMultiplier", rp.shortageMultiplier, 1, 10);
		rp.questPoints = intList("reputation.questPoints", rp.questPoints, 3, 0, 100_000, false, List.of(10, 25, 50));
		rp.acceptedQuestMultiplier = clamp("reputation.acceptedQuestMultiplier", rp.acceptedQuestMultiplier, 1, 10);
		rp.villagerTradePoints = clamp("reputation.villagerTradePoints", rp.villagerTradePoints, 0, 1000);
		rp.attackPenalty = clamp("reputation.attackPenalty", rp.attackPenalty, 0, 100_000);
		rp.killPenalty = clamp("reputation.killPenalty", rp.killPenalty, 0, 100_000);
		rp.questFailPenalty = clamp("reputation.questFailPenalty", rp.questFailPenalty, 0, 100_000);
		rp.priceBonusPerLevel = clamp("reputation.priceBonusPerLevel", rp.priceBonusPerLevel, 0, 0.05);
		rp.rewardBonusPerLevel = clamp("reputation.rewardBonusPerLevel", rp.rewardBonusPerLevel, 0, 1);
		rp.hardQuestLevel = clamp("reputation.hardQuestLevel", rp.hardQuestLevel, 0, 5);
		rp.specialBonusLevel = clamp("reputation.specialBonusLevel", rp.specialBonusLevel, 0, 5);
		rp.specialBonusCount = clamp("reputation.specialBonusCount", rp.specialBonusCount, 0, 5);

		TradeExpansionConfig.Development dv = c.development;
		dv.dimensions = itemIds("development.dimensions", dv.dimensions);
		dv.pointsPerCoin = clamp("development.pointsPerCoin", dv.pointsPerCoin, 0, 100);
		dv.materialMultiplier = clamp("development.materialMultiplier", dv.materialMultiplier, 0, 10);
		dv.questRewardRatio = clamp("development.questRewardRatio", dv.questRewardRatio, 0, 10);
		dv.tradePointsPerCoin = clamp("development.tradePointsPerCoin", dv.tradePointsPerCoin, 0, 1);
		dv.halfLifeDays = clamp("development.halfLifeDays", dv.halfLifeDays, 0, 1000);
		dv.levelBonusThresholds = bonusThresholds(dv.levelBonusThresholds);
		dv.buildReputationLevel = clamp("development.buildReputationLevel", dv.buildReputationLevel, 0, 5);
		dv.maxConcurrentBuilds = clamp("development.maxConcurrentBuilds", dv.maxConcurrentBuilds, 1, 4);
		dv.buildDays = clamp("development.buildDays", dv.buildDays, 0, 100);
		dv.siteRadius = clamp("development.siteRadius", dv.siteRadius, 8, 64);
		dv.siteSearchLimit = clamp("development.siteSearchLimit", dv.siteSearchLimit, 16, 4096);
		dv.rebuildCostRatio = clamp("development.rebuildCostRatio", dv.rebuildCostRatio, 0, 1);
		dv.warehouseStockMultiplier = clamp("development.warehouseStockMultiplier", dv.warehouseStockMultiplier, 1, 10);
		dv.warehouseRestockMultiplier = clamp("development.warehouseRestockMultiplier", dv.warehouseRestockMultiplier, 1, 10);
		dv.questHallExtraQuests = clamp("development.questHallExtraQuests", dv.questHallExtraQuests, 0, 10);
		dv.questHallExtraTransport = clamp("development.questHallExtraTransport", dv.questHallExtraTransport, 0, 5);
		dv.innVillagers = clamp("development.innVillagers", dv.innVillagers, 0, 10);
		dv.bankInterestMaxMultiplier = clamp("development.bankInterestMaxMultiplier", dv.bankInterestMaxMultiplier, 1, 100);
		dv.fountainReputationMultiplier = clamp("development.fountainReputationMultiplier", dv.fountainReputationMultiplier, 1, 10);
		dv.parkPointsMultiplier = clamp("development.parkPointsMultiplier", dv.parkPointsMultiplier, 1, 10);
		dv.reputationPerPoint = clamp("development.reputationPerPoint", dv.reputationPerPoint, 0, 10);
		dv.materialCountMultiplier = clamp("development.materialCountMultiplier", dv.materialCountMultiplier, 0.1, 100);

		c.consumables.add = itemIds("consumables.add", c.consumables.add);
		c.consumables.remove = itemIds("consumables.remove", c.consumables.remove);
		c.goods = goodsOverrides(c.goods);
		return c;
	}

	private static double clamp(String key, double value, double min, double max) {
		if (Double.isNaN(value) || value < min || value > max) {
			double fixed = Double.isNaN(value) ? min : Math.clamp(value, min, max);
			TradeExpansionConfig.warn("{} = {} is out of range [{}, {}], using {}", key, value, min, max, fixed);
			return fixed;
		}
		return value;
	}

	private static long clamp(String key, long value, long min, long max) {
		if (value < min || value > max) {
			long fixed = Math.clamp(value, min, max);
			TradeExpansionConfig.warn("{} = {} is out of range [{}, {}], using {}", key, value, min, max, fixed);
			return fixed;
		}
		return value;
	}

	private static int clamp(String key, int value, int min, int max) {
		return (int) clamp(key, (long) value, min, max);
	}

	/** Fixed-size double list, each element clamped; wrong size → defaults. */
	/** D-2: goods ids normalized ("dandelion" → "ak_trade_expansion:dandelion"), values clamped to the goods JSON ranges, empty entries dropped. */
	private static java.util.Map<String, GoodsOverride> goodsOverrides(java.util.Map<String, GoodsOverride> values) {
		java.util.TreeMap<String, GoodsOverride> result = new java.util.TreeMap<>();
		values.forEach((key, value) -> {
			String raw = key == null ? "" : key.trim();
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(raw.contains(":") ? raw : "ak_trade_expansion:" + raw);
			if (id == null) {
				TradeExpansionConfig.warn("goods: '{}' is not a goods id, ignored", key);
				return;
			}
			// v0.27: "item" of a config-added good, normalized like the consumables lists.
			GoodsOverride clamped = value.clamped().withItem(value.item().map(String::trim).map(s -> s.contains(":") ? s : "minecraft:" + s)
					.filter(s -> net.minecraft.resources.Identifier.tryParse(s) != null));
			if (!value.clamped().equals(value)) TradeExpansionConfig.warn("goods.{}: values out of range were corrected", id);
			if (!clamped.isEmpty()) result.put(id.toString(), clamped);
		});
		return result;
	}

	/** Normalizes item ids ("carrot" → "minecraft:carrot"), dropping malformed and duplicate entries. Unknown items are kept (other mods may load later). */
	private static List<String> itemIds(String key, List<String> values) {
		java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
		for (String value : values) {
			net.minecraft.resources.Identifier id = value == null ? null : net.minecraft.resources.Identifier.tryParse(value.trim());
			if (id == null) TradeExpansionConfig.warn("{}: '{}' is not an item id, ignored", key, value);
			else ids.add(id.toString());
		}
		return new java.util.ArrayList<>(ids);
	}

	private static List<Double> doubleList(String key, List<Double> values, int size, double min, double max, List<Double> defaults) {
		if (values == null || values.size() != size) {
			TradeExpansionConfig.warn("{} needs {} values, using {}", key, size, defaults);
			return new ArrayList<>(defaults);
		}
		List<Double> fixed = new ArrayList<>(size);
		for (int i = 0; i < size; i++) fixed.add(clamp(key + "[" + i + "]", values.get(i), min, max));
		return fixed;
	}

	/** N-3: 0..4 ascending counts of completed facilities, each 1..4 (there are four facilities). */
	private static List<Integer> bonusThresholds(List<Integer> values) {
		String key = "development.levelBonusThresholds";
		if (values == null || values.size() > 4) {
			TradeExpansionConfig.warn("{} takes at most 4 values, using [2, 4]", key);
			return new ArrayList<>(List.of(2, 4));
		}
		List<Integer> fixed = new ArrayList<>(values.size());
		int previous = 1;
		for (int i = 0; i < values.size(); i++) {
			int v = clamp(key + "[" + i + "]", values.get(i), 1, 4);
			if (v < previous) {
				TradeExpansionConfig.warn("{} must be ascending, {} raised to {}", key, v, previous);
				v = previous;
			}
			fixed.add(v);
			previous = v;
		}
		return fixed;
	}

	/** Fixed-size int list; each element clamped; optionally forced ascending. Wrong size → defaults. */
	private static List<Integer> intList(String key, List<Integer> values, int size, int min, int max, boolean ascending, List<Integer> defaults) {
		if (values == null || values.size() != size) {
			TradeExpansionConfig.warn("{} needs {} values, using {}", key, size, defaults);
			return new ArrayList<>(defaults);
		}
		List<Integer> fixed = new ArrayList<>(size);
		int previous = Integer.MIN_VALUE;
		for (int i = 0; i < size; i++) {
			int v = clamp(key + "[" + i + "]", values.get(i), min, max);
			if (ascending && v < previous) {
				TradeExpansionConfig.warn("{} must be ascending, {} raised to {}", key, v, previous);
				v = previous;
			}
			fixed.add(v);
			previous = v;
		}
		return fixed;
	}
}
