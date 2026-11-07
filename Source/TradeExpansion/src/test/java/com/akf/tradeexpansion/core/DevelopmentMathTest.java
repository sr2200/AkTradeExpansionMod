package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevelopmentMathTest {
	@Test
	void noDecayByDefault() {
		assertEquals(1000, DevelopmentMath.decay(1000, 30, 0), 1e-9);
		assertEquals(500, DevelopmentMath.decay(1000, 60, 60), 1e-9);
		assertEquals(1000, DevelopmentMath.decay(1000, 0, 60), 1e-9);
	}

	@Test
	void bonusFromCompletedFacilities() {
		int[] thresholds = {2, 4};
		assertEquals(0, DevelopmentMath.levelBonus(0, thresholds));
		assertEquals(0, DevelopmentMath.levelBonus(1, thresholds));
		assertEquals(1, DevelopmentMath.levelBonus(2, thresholds));
		assertEquals(1, DevelopmentMath.levelBonus(3, thresholds));
		assertEquals(2, DevelopmentMath.levelBonus(4, thresholds));
		assertEquals(0, DevelopmentMath.levelBonus(4, new int[0]));
	}

	@Test
	void levelWithBonusStopsAtFive() {
		assertEquals(4, DevelopmentMath.effectiveLevel(2, 2));
		assertEquals(5, DevelopmentMath.effectiveLevel(4, 2));
		assertEquals(1, DevelopmentMath.effectiveLevel(1, 0));
	}

	@Test
	void materialsUpToTheRequiredCount() {
		assertEquals(64, DevelopmentMath.acceptable(64, 0, 100));
		assertEquals(14, DevelopmentMath.acceptable(64, 50, 20));
		assertEquals(0, DevelopmentMath.acceptable(64, 64, 5));
		assertEquals(150, DevelopmentMath.materialPoints(10, 10, 1.5), 1e-9);
	}

	@Test
	void siteOffsetsNearestFirstWithinTheRadius() {
		java.util.List<int[]> offsets = DevelopmentMath.siteOffsets(4, 2, 1000);
		assertEquals(12, offsets.size()); // (±2,0) (0,±2) (±2,±2) (±4,0) (0,±4); (±4,±2) is outside
		assertEquals(2, Math.abs(offsets.get(0)[0]) + Math.abs(offsets.get(0)[1]));
		for (int i = 1; i < offsets.size(); i++) {
			int[] a = offsets.get(i - 1), b = offsets.get(i);
			org.junit.jupiter.api.Assertions.assertTrue(a[0] * a[0] + a[1] * a[1] <= b[0] * b[0] + b[1] * b[1]);
		}
		assertEquals(3, DevelopmentMath.siteOffsets(32, 2, 3).size());
		org.junit.jupiter.api.Assertions.assertTrue(offsets.stream().noneMatch(o -> o[0] == 0 && o[1] == 0));
	}

	@Test
	void materialCountsScaleWithTheSetting() {
		assertEquals(512, DevelopmentMath.materialCount(128, 4.0));
		assertEquals(13, DevelopmentMath.materialCount(25, 0.5)); // 12.5 rounds up
		assertEquals(1, DevelopmentMath.materialCount(1, 0.1));
	}

	@Test
	void rebuildCostsAShare() {
		assertEquals(2500, DevelopmentMath.cost(2500, false, 0.5));
		assertEquals(1250, DevelopmentMath.cost(2500, true, 0.5));
		assertEquals(751, DevelopmentMath.cost(1501, true, 0.5)); // 750.5 rounds up
	}
}
