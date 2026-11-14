package com.akf.tradeexpansion.core;

import com.akf.tradeexpansion.core.SettlementMath.Mode;
import com.akf.tradeexpansion.core.SettlementMath.Requirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementMathTest {
	private static Requirement r(Mode mode, int enoughBeds) {
		return new Requirement(mode, 3, 3, enoughBeds);
	}

	@Test
	void beds() {
		Requirement r = r(Mode.BEDS, 5);
		assertTrue(SettlementMath.allows(r, false, 3, 0));
		assertFalse(SettlementMath.allows(r, false, 2, 99));
		// Nether / End: villagers instead of beds
		assertTrue(SettlementMath.allows(r, true, 0, 3));
		assertFalse(SettlementMath.allows(r, true, 99, 2));
		assertFalse(SettlementMath.needsVillagers(Mode.BEDS, false));
		assertTrue(SettlementMath.needsVillagers(Mode.BEDS, true));
	}

	@Test
	void villagers() {
		Requirement r = r(Mode.VILLAGERS, 5);
		assertTrue(SettlementMath.allows(r, false, 0, 3));
		assertFalse(SettlementMath.allows(r, false, 99, 2));
		assertTrue(SettlementMath.needsVillagers(Mode.VILLAGERS, false));
	}

	@Test
	void bedsAndVillagers() {
		Requirement r = r(Mode.BEDS_AND_VILLAGERS, 0);
		assertTrue(SettlementMath.allows(r, false, 3, 3));
		assertTrue(SettlementMath.allows(r, false, 4, 3));
		assertFalse(SettlementMath.allows(r, false, 3, 4)); // more villagers than beds
		assertFalse(SettlementMath.allows(r, false, 2, 2));
		assertFalse(SettlementMath.allows(r, false, 9, 2)); // too few villagers
		assertTrue(SettlementMath.allows(r, true, 0, 3)); // no beds in the Nether
		assertFalse(SettlementMath.allows(r, true, 0, 2));
	}

	@Test
	void enoughBedsWhateverTheVillagers() {
		Requirement r = r(Mode.BEDS_AND_VILLAGERS, 5);
		assertFalse(SettlementMath.allows(r, false, 4, 8));
		assertTrue(SettlementMath.allows(r, false, 5, 8));
		assertTrue(SettlementMath.allows(r, false, 5, 30));
		assertFalse(SettlementMath.allows(r, false, 5, 2)); // still needs the minimum villagers
	}

	@Test
	void enoughBedsDoesNotLowerTheMinimum() {
		Requirement r = new Requirement(Mode.BEDS_AND_VILLAGERS, 6, 3, 4);
		assertFalse(SettlementMath.allows(r, false, 5, 8)); // at least enoughBeds, but fewer than minBeds
		assertTrue(SettlementMath.allows(r, false, 6, 8));
	}
}
