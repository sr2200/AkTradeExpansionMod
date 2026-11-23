package com.akf.tradeexpansion.core;

import com.akf.tradeexpansion.core.FacilityLevelMath.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacilityLevelMathTest {
	private static final List<Double> STEPS = List.of(1.5, 2.0);
	private static final List<Double> COSTS = List.of(1.0, 1.5);

	@Test
	void strengthGrowsWithTheLevel() {
		assertEquals(1.0, FacilityLevelMath.strength(1, Role.STRONG, STEPS), 1e-9);
		assertEquals(1.5, FacilityLevelMath.strength(2, Role.NEUTRAL, STEPS), 1e-9);
		assertEquals(2.0, FacilityLevelMath.strength(3, Role.NEUTRAL, STEPS), 1e-9);
		// the chosen direction: 1.5 times the growth for its effect, half for the other
		assertEquals(1.75, FacilityLevelMath.strength(2, Role.STRONG, STEPS), 1e-9);
		assertEquals(1.25, FacilityLevelMath.strength(2, Role.WEAK, STEPS), 1e-9);
		assertEquals(2.5, FacilityLevelMath.strength(3, Role.STRONG, STEPS), 1e-9);
		assertEquals(1.5, FacilityLevelMath.strength(3, Role.WEAK, STEPS), 1e-9);
		// above the top level: the top level; steps missing from the config: the defaults
		assertEquals(2.0, FacilityLevelMath.strength(7, Role.NEUTRAL, STEPS), 1e-9);
		assertEquals(2.0, FacilityLevelMath.strength(3, Role.NEUTRAL, List.of()), 1e-9);
		// steps below 1 do not weaken the building
		assertEquals(1.0, FacilityLevelMath.strength(2, Role.STRONG, List.of(0.5, 0.5)), 1e-9);
	}

	@Test
	void multipliersKeepTheirDirection() {
		// the warehouse's stock: 1.5 → 1.75 → 2
		assertEquals(1.5, FacilityLevelMath.raise(1.5, 1.0), 1e-9);
		assertEquals(1.75, FacilityLevelMath.raise(1.5, 1.5), 1e-9);
		assertEquals(2.0, FacilityLevelMath.raise(1.5, 2.0), 1e-9);
		// a decrease grows as a power and stays above 0
		assertEquals(Math.pow(0.75, 1.5), FacilityLevelMath.raise(0.75, 1.5), 1e-9);
		assertEquals(0.25, FacilityLevelMath.raise(0.5, 2.0), 1e-9);
		assertTrue(FacilityLevelMath.raise(0.5, 2.5) > 0);
		assertEquals(1.0, FacilityLevelMath.raise(1.0, 2.0), 1e-9);
		assertEquals(0.0, FacilityLevelMath.raise(0.0, 2.0), 1e-9);
	}

	@Test
	void countsAreRounded() {
		assertEquals(2, FacilityLevelMath.count(2, 1.0));
		assertEquals(3, FacilityLevelMath.count(2, 1.5));
		assertEquals(4, FacilityLevelMath.count(2, 1.75));
		assertEquals(5, FacilityLevelMath.count(2, 2.5));
		assertEquals(1, FacilityLevelMath.count(1, 1.25));
		assertEquals(2, FacilityLevelMath.count(1, 1.5));
		assertEquals(0, FacilityLevelMath.count(0, 2.5));
	}

	@Test
	void intervalsGetShorter() {
		assertEquals(5, FacilityLevelMath.interval(5, 1.0, 2));
		assertEquals(3, FacilityLevelMath.interval(5, 1.75, 2));
		assertEquals(2, FacilityLevelMath.interval(5, 2.5, 2));
		assertEquals(2, FacilityLevelMath.interval(3, 2.5, 2));
	}

	@Test
	void perkGrowsPerLevel() {
		assertEquals(1.0, FacilityLevelMath.perk(1, 0.15), 1e-9);
		assertEquals(1.15, FacilityLevelMath.perk(2, 0.15), 1e-9);
		assertEquals(1.3, FacilityLevelMath.perk(3, 0.15), 1e-9);
		assertEquals(1.0, FacilityLevelMath.perk(3, 0), 1e-9);
	}

	@Test
	void costsOfTheLevels() {
		assertEquals(1.0, FacilityLevelMath.upgradeFactor(1, COSTS), 1e-9);
		assertEquals(1.5, FacilityLevelMath.upgradeFactor(2, COSTS), 1e-9);
		assertEquals(0.0, FacilityLevelMath.upgradeFactor(3, COSTS), 1e-9);
		assertEquals(1.0, FacilityLevelMath.totalFactor(1, COSTS), 1e-9);
		assertEquals(2.0, FacilityLevelMath.totalFactor(2, COSTS), 1e-9);
		assertEquals(3.5, FacilityLevelMath.totalFactor(3, COSTS), 1e-9);
		assertEquals(300, FacilityLevelMath.scaled(200, 1.5));
		assertEquals(1, FacilityLevelMath.scaled(1, 0.1));
		assertEquals(0, FacilityLevelMath.scaled(200, 0));
		// rebuilding a level 3 building at half: 3.5 × 0.5 of its cost
		assertEquals(350, FacilityLevelMath.scaled(200, FacilityLevelMath.totalFactor(3, COSTS) * 0.5));
	}
}
