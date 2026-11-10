package com.akf.tradeexpansion.core;

import com.akf.tradeexpansion.core.GoodsSelector.Candidate;
import com.akf.tradeexpansion.core.GoodsSelector.Pool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GoodsSelectorTest {
	private static final List<Pool> POOLS = List.of(
			new Pool(2, 3, List.of(c("bread", 10, 1), c("oak_log", 10, 1), c("apple", 8, 1), c("wool", 8, 1))),
			new Pool(3, 4, commonPool()),
			new Pool(1, 2, List.of(c("iron", 5, 2), c("gold", 4, 2), c("diamond", 2, 4), c("diamond_sword", 1, 5))));

	private static Candidate c(String goods, int weight, int minLevel) {
		return new Candidate(goods, weight, minLevel);
	}

	private static List<Candidate> commonPool() {
		List<Candidate> list = new ArrayList<>();
		list.add(c("bread", 10, 1)); // duplicate of the local pool
		for (int i = 0; i < 18; i++) list.add(c("common" + i, 5, 1));
		list.add(c("book", 3, 2));
		return list;
	}

	@Test
	void selectsExactlyTargetWithoutDuplicates() {
		for (long seed = 0; seed < 200; seed++) {
			for (int level = 1; level <= 5; level++) {
				int target = MarketLevel.goodsCount(level, 10, 20);
				List<String> goods = GoodsSelector.select(List.of("wheat"), POOLS, level, target, new Random(seed));
				assertEquals(target, goods.size(), "seed " + seed + " level " + level);
				assertEquals(goods.size(), new HashSet<>(goods).size());
				assertEquals("wheat", goods.getFirst());
			}
		}
	}

	@Test
	void respectsMinLevel() {
		for (long seed = 0; seed < 200; seed++) {
			List<String> goods = GoodsSelector.select(List.of("wheat"), POOLS, 1, 20, new Random(seed));
			assertFalse(goods.contains("iron"));
			assertFalse(goods.contains("book"));
			assertFalse(goods.contains("diamond_sword"));
		}
	}

	@Test
	void sameSeedSameGoods() {
		assertEquals(GoodsSelector.select(List.of(), POOLS, 3, 15, new Random(42)),
				GoodsSelector.select(List.of(), POOLS, 3, 15, new Random(42)));
	}

	@Test
	void stopsWhenCandidatesRunOut() {
		List<Pool> small = List.of(new Pool(1, 1, List.of(c("a", 1, 1), c("b", 1, 1))));
		assertEquals(Set.of("x", "a", "b"), new HashSet<>(GoodsSelector.select(List.of("x"), small, 1, 10, new Random(1))));
	}

	@Test
	void growAddsOnlyNewGoods() {
		List<String> initial = GoodsSelector.select(List.of("wheat"), POOLS, 1, 10, new Random(7));
		List<String> added = GoodsSelector.grow(new HashSet<>(initial), POOLS, 4, 18, new Random(7 ^ 4));
		assertEquals(8, added.size());
		for (String g : added) assertFalse(initial.contains(g));
		assertFalse(added.contains("diamond_sword"));
	}

	@Test
	void pickIndexSkipsZeroWeights() {
		assertEquals(0, GoodsSelector.pickIndex(new int[]{1, 0}, new Random(3)));
		assertEquals(1, GoodsSelector.pickIndex(new int[]{0, 5}, new Random(3)));
		assertEquals(2, GoodsSelector.pickIndex(new int[]{-4, 0, 1}, new Random(3))); // negative counts as 0
	}

	/** Weights 1 : 3: about a quarter of the picks go to the first element. */
	@Test
	void pickIndexFollowsWeights() {
		Random random = new Random(99);
		int[] counts = new int[2];
		for (int i = 0; i < 40_000; i++) counts[GoodsSelector.pickIndex(new int[]{1, 3}, random)]++;
		assertEquals(0.25, counts[0] / 40_000.0, 0.01);
	}

	/** Nothing to choose from (no weights, or all 0 / negative): the first element. */
	@Test
	void pickIndexWithoutWeights() {
		assertEquals(0, GoodsSelector.pickIndex(new int[0], new Random(1)));
		assertEquals(0, GoodsSelector.pickIndex(new int[]{0, 0}, new Random(1)));
		assertEquals(0, GoodsSelector.pickIndex(new int[]{-1, -2}, new Random(1)));
	}

	/** "always" goods are never trimmed, even when they alone exceed the target. */
	@Test
	void alwaysGoodsAreKeptOverTheTarget() {
		List<String> always = List.of("a", "b", "c", "d");
		assertEquals(always, GoodsSelector.select(always, POOLS, 5, 2, new Random(5)));
	}

	/** A candidate with weight 0 is never chosen, not even to fill up to the target. */
	@Test
	void zeroWeightIsNeverChosen() {
		List<Pool> pools = List.of(new Pool(3, 3, List.of(c("a", 1, 1), c("b", 0, 1), c("c", 1, 1))));
		for (long seed = 0; seed < 50; seed++) {
			List<String> goods = GoodsSelector.select(List.of(), pools, 1, 10, new Random(seed));
			assertEquals(Set.of("a", "c"), new HashSet<>(goods));
		}
	}

	/** Weighted picks follow the weights: a candidate 9 times as heavy is picked first about 90% of the time. */
	@Test
	void selectFollowsWeights() {
		List<Pool> pools = List.of(new Pool(1, 1, List.of(c("heavy", 9, 1), c("light", 1, 1))));
		int heavy = 0;
		for (long seed = 0; seed < 5_000; seed++) {
			if (GoodsSelector.select(List.of(), pools, 1, 1, new Random(seed)).equals(List.of("heavy"))) heavy++;
		}
		assertEquals(0.9, heavy / 5_000.0, 0.02);
	}

	/** grow never removes goods and adds nothing when the market already holds enough. */
	@Test
	void growWithEnoughGoodsAddsNothing() {
		Set<String> existing = Set.of("bread", "oak_log", "apple");
		assertEquals(List.of(), GoodsSelector.grow(existing, POOLS, 5, 3, new Random(1)));
		assertEquals(List.of(), GoodsSelector.grow(existing, POOLS, 5, 1, new Random(1)));
	}
}
