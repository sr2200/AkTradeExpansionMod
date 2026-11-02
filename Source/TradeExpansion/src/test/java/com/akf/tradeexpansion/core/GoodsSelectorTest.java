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
	void pickIndexFollowsWeights() {
		assertEquals(0, GoodsSelector.pickIndex(new int[]{1, 0}, new Random(3)));
		assertEquals(1, GoodsSelector.pickIndex(new int[]{0, 5}, new Random(3)));
	}
}
