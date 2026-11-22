package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The merchant's ledger (design T, v0.81): what a player bought for how much, and the takings and profit of each day.
 * Pure arithmetic; {@code notebook.Ledger} stores it and {@code notebook.LedgerService} feeds it.
 * <p>
 * Profit of a sale = what it brought − what the sold items cost. The cost is the average price the player paid at the
 * markets for the items of that goods not sold yet (the "basis"); items the player gathered or made cost nothing, so a
 * sale of more items than were bought is profit for the rest.
 */
public final class LedgerMath {
	/** Goods kept on one day's list (the most profitable are shown). */
	public static final int MAX_GOODS_PER_DAY = 64;

	private LedgerMath() {
	}

	/** Items of one goods bought at the markets and not sold yet, and what they cost together (copper). Mutable. */
	public static final class Basis {
		private long items;
		private long cost;

		public Basis(long items, long cost) {
			this.items = Math.max(0, items);
			this.cost = Math.max(0, cost);
		}

		public long items() {
			return items;
		}

		public long cost() {
			return cost;
		}

		public boolean isEmpty() {
			return items <= 0;
		}

		/** Bought {@code count} items for {@code total}. */
		public void add(long count, long total) {
			if (count <= 0) return;
			items = Math.addExact(items, count);
			cost = Math.addExact(cost, Math.max(0, total));
		}

		/**
		 * Sold (or handed over) {@code count} items: takes their share of the cost out of the basis.
		 *
		 * @return what those items cost (0 for the items beyond the basis)
		 */
		public long take(long count) {
			if (count <= 0 || items <= 0) return 0;
			long n = Math.min(count, items);
			long share = n == items ? cost : Math.round(cost * (double) n / items);
			items -= n;
			cost -= share;
			if (items == 0) cost = 0;
			return share;
		}
	}

	/** One goods on a day's list: items sold and the profit made on them. */
	public record GoodsLine(String goods, long items, long sales, long profit) {
		public GoodsLine plus(long moreItems, long moreSales, long moreProfit) {
			return new GoodsLine(goods, items + moreItems, sales + moreSales, profit + moreProfit);
		}
	}

	/**
	 * One day of the ledger (copper). Mutable.
	 * <ul>
	 * <li>sales / purchases: what selling at the markets brought and buying cost</li>
	 * <li>profit: profit of the sales and of the delivered requests (reward − cost of the items handed over)</li>
	 * <li>quests: rewards of delivered requests (part of the profit)</li>
	 * <li>staff: what the player's staff made: their sales − what the sold goods cost − wages and commissions</li>
	 * <li>trades: player trades (buy or sell) made that day</li>
	 * </ul>
	 */
	public static final class Day {
		private final long day;
		private long sales;
		private long purchases;
		private long profit;
		private long quests;
		private long staff;
		private int trades;
		private final Map<String, GoodsLine> goods = new LinkedHashMap<>();

		public Day(long day) {
			this.day = day;
		}

		public Day(long day, long sales, long purchases, long profit, long quests, long staff, int trades, List<GoodsLine> goods) {
			this(day);
			this.sales = sales;
			this.purchases = purchases;
			this.profit = profit;
			this.quests = quests;
			this.staff = staff;
			this.trades = trades;
			goods.forEach(g -> this.goods.put(g.goods(), g));
		}

		public long day() {
			return day;
		}

		public long sales() {
			return sales;
		}

		public long purchases() {
			return purchases;
		}

		public long profit() {
			return profit;
		}

		public long quests() {
			return quests;
		}

		public long staff() {
			return staff;
		}

		public int trades() {
			return trades;
		}

		public List<GoodsLine> goods() {
			return List.copyOf(goods.values());
		}

		/** Profit of the day with the staff's. */
		public long totalProfit() {
			return profit + staff;
		}

		/** Anything happened that day (a report is worth sending). */
		public boolean active() {
			return trades > 0 || quests != 0 || staff != 0 || sales != 0 || purchases != 0;
		}

		public void bought(long total) {
			purchases += total;
			trades++;
		}

		public void sold(String goodsId, long items, long total, long cost) {
			sales += total;
			profit += total - cost;
			trades++;
			addGoods(goodsId, items, total, total - cost);
		}

		public void quest(long reward, long cost) {
			quests += reward;
			profit += reward - cost;
		}

		public void staff(long amount) {
			staff += amount;
		}

		private void addGoods(String goodsId, long items, long total, long goodsProfit) {
			GoodsLine line = goods.get(goodsId);
			if (line != null) {
				goods.put(goodsId, line.plus(items, total, goodsProfit));
				return;
			}
			if (goods.size() >= MAX_GOODS_PER_DAY) {
				// Full: the new goods replaces the least profitable one when it did better.
				GoodsLine worst = goods.values().stream().min(Comparator.comparingLong(GoodsLine::profit)).orElseThrow();
				if (worst.profit() >= goodsProfit) return;
				goods.remove(worst.goods());
			}
			goods.put(goodsId, new GoodsLine(goodsId, items, total, goodsProfit));
		}

		/** Adds another day's numbers (a report covering several days). */
		public void add(Day other) {
			sales += other.sales;
			purchases += other.purchases;
			profit += other.profit;
			quests += other.quests;
			staff += other.staff;
			trades += other.trades;
			other.goods.values().forEach(g -> addGoods(g.goods(), g.items(), g.sales(), g.profit()));
		}
	}

	/** The goods with the most profit, best first (ties: more sales first, then by id). Only goods that made a profit. */
	public static List<GoodsLine> top(List<GoodsLine> lines, int count) {
		List<GoodsLine> list = new ArrayList<>(lines.stream().filter(g -> g.profit() > 0).toList());
		list.sort(Comparator.comparingLong(GoodsLine::profit).reversed()
				.thenComparing(Comparator.comparingLong(GoodsLine::sales).reversed())
				.thenComparing(GoodsLine::goods));
		return list.size() > count ? List.copyOf(list.subList(0, count)) : List.copyOf(list);
	}

	/**
	 * Days a report written on {@code today} covers: the days after the last report up to yesterday, at most
	 * {@code keepDays} of them. @return {first, last} (first &gt; last: nothing to report)
	 */
	public static long[] reportRange(long lastReported, long today, int keepDays) {
		long last = today - 1;
		long first = Math.max(lastReported, today - Math.max(1, keepDays));
		return new long[] {first, last};
	}

	/** The day number of a clock time ({@code ticksPerDay} ticks a day, day 0 starting at time 0). */
	public static long dayOf(long time, long ticksPerDay) {
		return Math.floorDiv(time, ticksPerDay);
	}
}
