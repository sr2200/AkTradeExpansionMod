package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestMathTest {
	private static final int[] THRESHOLDS = {200, 1000};
	private static final double[] MULTIPLIERS = {1.2, 1.5, 2.0};

	@Test
	void difficultyByValue() {
		assertEquals(1, QuestMath.difficulty(0, THRESHOLDS));
		assertEquals(1, QuestMath.difficulty(199.9, THRESHOLDS));
		assertEquals(2, QuestMath.difficulty(200, THRESHOLDS));
		assertEquals(2, QuestMath.difficulty(999, THRESHOLDS));
		assertEquals(3, QuestMath.difficulty(1000, THRESHOLDS));
		assertEquals(3, QuestMath.difficulty(1e9, THRESHOLDS));
	}

	@Test
	void rewardByDifficulty() {
		assertEquals(120, QuestMath.reward(100, 1, MULTIPLIERS));
		assertEquals(750, QuestMath.reward(500, 2, MULTIPLIERS));
		assertEquals(2000, QuestMath.reward(1000, 3, MULTIPLIERS));
		assertEquals(25, QuestMath.reward(20.4, 1, MULTIPLIERS)); // 24.48 rounds up
	}

	/** A difficulty outside 1..3 uses the nearest multiplier instead of failing. */
	@Test
	void rewardClampsTheDifficulty() {
		assertEquals(120, QuestMath.reward(100, 0, MULTIPLIERS));
		assertEquals(200, QuestMath.reward(100, 4, MULTIPLIERS));
		assertEquals(0, QuestMath.reward(0, 2, MULTIPLIERS));
		assertEquals(24, QuestMath.reward(20, 1, MULTIPLIERS)); // 20 × 1.2 = 24.000000000000004 stays 24
	}

	@Test
	void difficultyWithoutThresholds() {
		assertEquals(1, QuestMath.difficulty(1e9, new int[0]));
		assertEquals(3, QuestMath.difficulty(1e9, new int[] {1, 2, 3, 4})); // never above ★3
	}

	@Test
	void periodLastsAtLeastOneTick() {
		assertEquals(500, QuestMath.period(500, 0));
		assertEquals(1, QuestMath.period(24_000, 1));
	}

	@Test
	void transportMultiplierGrowsWithDistanceUpToTheCap() {
		assertEquals(0.2, QuestMath.transportMultiplier(0, 0.2, 0.8, 2.0), 1e-12);
		assertEquals(0.44, QuestMath.transportMultiplier(300, 0.2, 0.8, 2.0), 1e-12);
		assertEquals(1.0, QuestMath.transportMultiplier(1000, 0.2, 0.8, 2.0), 1e-12);
		assertEquals(2.0, QuestMath.transportMultiplier(5000, 0.2, 0.8, 2.0), 1e-12); // capped
		assertEquals(0.2, QuestMath.transportMultiplier(-50, 0.2, 0.8, 2.0), 1e-12); // negative distance counts as 0
	}

	@Test
	void periods() {
		assertEquals(0, QuestMath.period(0, 3));
		assertEquals(0, QuestMath.period(71_999, 3));
		assertEquals(1, QuestMath.period(72_000, 3));
		assertEquals(-1, QuestMath.period(-1, 3));
	}

	@Test
	void multiplierRange() {
		assertEquals(1.5, QuestMath.shortageMultiplier(0, 1.5, 2.0), 1e-12);
		assertEquals(1.75, QuestMath.shortageMultiplier(0.5, 1.5, 2.0), 1e-12);
	}
}
