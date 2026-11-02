package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Picks the goods of a market from its profile (design D-1 step 3 / D-4). Minecraft-independent;
 * goods are identified by their id string.
 */
public final class GoodsSelector {
	private GoodsSelector() {
	}

	public record Candidate(String goods, int weight, int minLevel) {
	}

	public record Pool(int minRolls, int maxRolls, List<Candidate> candidates) {
	}

	/**
	 * Initial selection: {@code always}, then each pool's rolls, then fill / trim to {@code target}.
	 *
	 * @return chosen goods in display order; fewer than {@code target} if the candidates run out
	 */
	public static List<String> select(List<String> always, List<Pool> pools, int level, int target, Random random) {
		Set<String> chosen = new LinkedHashSet<>(always);
		for (Pool pool : pools) {
			int rolls = pool.minRolls() + random.nextInt(Math.max(0, pool.maxRolls() - pool.minRolls()) + 1);
			for (int i = 0; i < rolls; i++) {
				String pick = pickWeighted(eligible(pool.candidates(), level, chosen), random);
				if (pick == null) break;
				chosen.add(pick);
			}
		}
		fill(chosen, pools, level, target, random);

		// Too many: drop random goods that are not in "always".
		List<String> removable = new ArrayList<>(chosen);
		removable.removeAll(always);
		while (chosen.size() > target && !removable.isEmpty()) {
			chosen.remove(removable.remove(random.nextInt(removable.size())));
		}
		return List.copyOf(chosen);
	}

	/**
	 * Level-up: adds goods to {@code existing} until it holds {@code target} goods.
	 *
	 * @return only the newly added goods
	 */
	public static List<String> grow(Set<String> existing, List<Pool> pools, int level, int target, Random random) {
		Set<String> chosen = new LinkedHashSet<>(existing);
		fill(chosen, pools, level, target, random);
		List<String> added = new ArrayList<>(chosen);
		added.removeAll(existing);
		return added;
	}

	/** Adds weighted picks from every pool (duplicates merged, first weight wins) until {@code target}. */
	private static void fill(Set<String> chosen, List<Pool> pools, int level, int target, Random random) {
		Map<String, Candidate> all = new LinkedHashMap<>();
		for (Pool pool : pools) {
			for (Candidate c : pool.candidates()) all.putIfAbsent(c.goods(), c);
		}
		List<Candidate> merged = List.copyOf(all.values());
		while (chosen.size() < target) {
			String pick = pickWeighted(eligible(merged, level, chosen), random);
			if (pick == null) return;
			chosen.add(pick);
		}
	}

	private static List<Candidate> eligible(List<Candidate> candidates, int level, Set<String> chosen) {
		List<Candidate> list = new ArrayList<>();
		for (Candidate c : candidates) {
			if (c.minLevel() <= level && c.weight() > 0 && !chosen.contains(c.goods())) list.add(c);
		}
		return list;
	}

	private static String pickWeighted(List<Candidate> candidates, Random random) {
		int total = 0;
		for (Candidate c : candidates) total += c.weight();
		if (total <= 0) return null;
		int r = random.nextInt(total);
		for (Candidate c : candidates) {
			r -= c.weight();
			if (r < 0) return c.goods();
		}
		return null;
	}

	/** Weighted pick of one element by index (profile choice among equal priorities). */
	public static int pickIndex(int[] weights, Random random) {
		int total = 0;
		for (int w : weights) total += Math.max(0, w);
		if (total <= 0) return 0;
		int r = random.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			r -= Math.max(0, weights[i]);
			if (r < 0) return i;
		}
		return 0;
	}
}
