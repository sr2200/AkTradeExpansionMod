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

		TradeExpansionConfig.Wallet w = c.wallet;
		w.capacity = clamp("wallet.capacity", w.capacity, 1000, 1_000_000_000L);

		TradeExpansionConfig.Bank b = c.bank;
		b.minMarketLevel = clamp("bank.minMarketLevel", b.minMarketLevel, 1, 5);
		b.interestRatePerDay = clamp("bank.interestRatePerDay", b.interestRatePerDay, 0, 0.01);
		b.cardFee = clamp("bank.cardFee", b.cardFee, 0, 1_000_000L);
		b.cardTransactionFee = clamp("bank.cardTransactionFee", b.cardTransactionFee, 0, 0.1);

		TradeExpansionConfig.Trade t = c.trade;
		t.maxDistance = clamp("trade.maxDistance", t.maxDistance, 2, 32);
		t.priceTolerance = clamp("trade.priceTolerance", t.priceTolerance, 0, 0.2);
		t.maxPerTransaction = clamp("trade.maxPerTransaction", t.maxPerTransaction, 1, 2304);

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

		TradeExpansionConfig.Demand dm = c.demand;
		dm.maxShortages = clamp("demand.maxShortages", dm.maxShortages, 0, 10);
		dm.durationDays = clamp("demand.durationDays", dm.durationDays, 0.5, 30);
		dm.minMultiplier = clamp("demand.minMultiplier", dm.minMultiplier, 1, 5);
		dm.maxMultiplier = clamp("demand.maxMultiplier", dm.maxMultiplier, dm.minMultiplier, 5);

		c.market.newMarketMinBeds = clamp("market.newMarketMinBeds", c.market.newMarketMinBeds, 0, 1000);
		c.market.newMarketMinVillagers = clamp("market.newMarketMinVillagers", c.market.newMarketMinVillagers, 0, 1000);
		c.villager.minGoodsPriceMultiplier = clamp("villager.minGoodsPriceMultiplier", c.villager.minGoodsPriceMultiplier, 0, 10);
		c.villager.emeraldPrice = clamp("villager.emeraldPrice", c.villager.emeraldPrice, 1, 10000);

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
	/** D-2: goods ids normalized ("dandelion" → "trade_expansion:dandelion"), values clamped to the goods JSON ranges, empty entries dropped. */
	private static java.util.Map<String, GoodsOverride> goodsOverrides(java.util.Map<String, GoodsOverride> values) {
		java.util.TreeMap<String, GoodsOverride> result = new java.util.TreeMap<>();
		values.forEach((key, value) -> {
			String raw = key == null ? "" : key.trim();
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(raw.contains(":") ? raw : "trade_expansion:" + raw);
			if (id == null) {
				TradeExpansionConfig.warn("goods: '{}' is not a goods id, ignored", key);
				return;
			}
			GoodsOverride clamped = value.clamped();
			if (!clamped.equals(value)) TradeExpansionConfig.warn("goods.{}: values out of range were corrected", id);
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
