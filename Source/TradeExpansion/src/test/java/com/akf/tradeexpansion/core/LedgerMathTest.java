package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerMathTest {
	/** Bought 10 for 100c, sold 4: they cost 40c; the rest keeps the average price. */
	@Test
	void basisTakesAverageCost() {
		LedgerMath.Basis basis = new LedgerMath.Basis(0, 0);
		basis.add(10, 100);
		assertEquals(40, basis.take(4));
		assertEquals(6, basis.items());
		assertEquals(60, basis.cost());
		basis.add(4, 100); // 10 items, 160c: 16c each
		assertEquals(80, basis.take(5));
	}

	/** Selling more than was bought: the extra items (gathered or made) cost nothing; the basis empties. */
	@Test
	void basisBeyondBoughtIsFree() {
		LedgerMath.Basis basis = new LedgerMath.Basis(3, 31);
		assertEquals(31, basis.take(10)); // all of the basis, even with rounding
		assertTrue(basis.isEmpty());
		assertEquals(0, basis.cost());
		assertEquals(0, basis.take(5));
	}

	/** Rounding never leaves a cost without items. */
	@Test
	void basisRoundingKeepsTotal() {
		LedgerMath.Basis basis = new LedgerMath.Basis(3, 10);
		long taken = basis.take(1) + basis.take(1) + basis.take(1);
		assertEquals(10, taken);
		assertEquals(0, basis.cost());
	}

	/** A day: buying is not a loss; the sale's profit is its price minus the cost. */
	@Test
	void dayTotals() {
		LedgerMath.Day day = new LedgerMath.Day(5);
		day.bought(100);
		day.sold("a:bread", 10, 300, 100);
		day.sold("a:iron", 2, 50, 0);
		day.quest(80, 20);
		day.staff(-15);
		assertEquals(350, day.sales());
		assertEquals(100, day.purchases());
		assertEquals(200 + 50 + 60, day.profit());
		assertEquals(80, day.quests());
		assertEquals(310 - 15, day.totalProfit());
		assertEquals(3, day.trades());
		assertTrue(day.active());
		assertFalse(new LedgerMath.Day(1).active());
	}

	/** The same goods sold twice is one line. */
	@Test
	void dayMergesGoods() {
		LedgerMath.Day day = new LedgerMath.Day(0);
		day.sold("a:bread", 1, 10, 0);
		day.sold("a:bread", 2, 30, 6);
		assertEquals(List.of(new LedgerMath.GoodsLine("a:bread", 3, 40, 34)), day.goods());
	}

	/** A full list keeps the most profitable goods. */
	@Test
	void dayListKeepsBest() {
		LedgerMath.Day day = new LedgerMath.Day(0);
		for (int i = 0; i < LedgerMath.MAX_GOODS_PER_DAY; i++) day.sold("a:g" + i, 1, 10 + i, 0);
		day.sold("a:poor", 1, 1, 0); // worse than every listed goods: dropped
		day.sold("a:rich", 1, 1000, 0); // replaces the worst (a:g0)
		assertEquals(LedgerMath.MAX_GOODS_PER_DAY, day.goods().size());
		assertTrue(day.goods().stream().anyMatch(g -> g.goods().equals("a:rich")));
		assertTrue(day.goods().stream().noneMatch(g -> g.goods().equals("a:poor") || g.goods().equals("a:g0")));
		assertEquals(sumOf(LedgerMath.MAX_GOODS_PER_DAY) + 1 + 1000, day.sales()); // the totals keep every sale
	}

	private static long sumOf(int n) {
		long sum = 0;
		for (int i = 0; i < n; i++) sum += 10 + i;
		return sum;
	}

	/** Several days add up (a report after being away). */
	@Test
	void daysAdd() {
		LedgerMath.Day a = new LedgerMath.Day(1);
		a.sold("a:bread", 1, 10, 4);
		LedgerMath.Day b = new LedgerMath.Day(2);
		b.sold("a:bread", 2, 20, 0);
		b.staff(7);
		a.add(b);
		assertEquals(30, a.sales());
		assertEquals(26 + 7, a.totalProfit());
		assertEquals(List.of(new LedgerMath.GoodsLine("a:bread", 3, 30, 26)), a.goods());
	}

	/** Top goods: by profit, only those that made one. */
	@Test
	void topGoods() {
		List<LedgerMath.GoodsLine> lines = List.of(
				new LedgerMath.GoodsLine("a:x", 1, 10, 5),
				new LedgerMath.GoodsLine("a:y", 1, 90, 50),
				new LedgerMath.GoodsLine("a:z", 1, 10, -3),
				new LedgerMath.GoodsLine("a:w", 1, 20, 5));
		assertEquals(List.of("a:y", "a:w", "a:x"), LedgerMath.top(lines, 5).stream().map(LedgerMath.GoodsLine::goods).toList());
		assertEquals(1, LedgerMath.top(lines, 1).size());
	}

	/** The morning report covers the days since the last one, up to yesterday, at most keepDays. */
	@Test
	void reportRange() {
		assertArrayEquals(new long[] {9, 9}, LedgerMath.reportRange(9, 10, 7)); // reported yesterday morning
		assertArrayEquals(new long[] {5, 9}, LedgerMath.reportRange(5, 10, 7)); // away for days
		assertArrayEquals(new long[] {3, 9}, LedgerMath.reportRange(0, 10, 7)); // too long: the last 7 days
		long[] none = LedgerMath.reportRange(10, 10, 7); // already reported today
		assertTrue(none[0] > none[1]);
	}

	@Test
	void dayOf() {
		assertEquals(0, LedgerMath.dayOf(23_999, 24_000));
		assertEquals(1, LedgerMath.dayOf(24_000, 24_000));
		assertEquals(-1, LedgerMath.dayOf(-1, 24_000));
	}
}
