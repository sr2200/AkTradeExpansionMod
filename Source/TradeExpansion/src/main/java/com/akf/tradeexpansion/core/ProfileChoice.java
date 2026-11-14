package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * D-1 step 2: among the market profiles whose conditions match, the highest priority wins; ties are drawn by weight.
 * Minecraft-independent (the caller checks the conditions and falls back to the dimension's default).
 */
public final class ProfileChoice {
	private ProfileChoice() {
	}

	/** A matching, non-fallback profile. */
	public record Candidate<T>(T id, int priority, int weight) {
	}

	/** @return the chosen id, or {@code null} when there is no candidate */
	public static <T> T choose(List<Candidate<T>> candidates, Random random) {
		List<Candidate<T>> best = new ArrayList<>();
		int bestPriority = Integer.MIN_VALUE;
		for (Candidate<T> c : candidates) {
			if (c.priority() > bestPriority) {
				best.clear();
				bestPriority = c.priority();
			}
			if (c.priority() == bestPriority) best.add(c);
		}
		if (best.isEmpty()) return null;
		int[] weights = best.stream().mapToInt(Candidate::weight).toArray();
		return best.get(GoodsSelector.pickIndex(weights, random)).id();
	}
}
