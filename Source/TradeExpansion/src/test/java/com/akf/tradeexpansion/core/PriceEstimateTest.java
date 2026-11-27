package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Suggested base price for a good added from the config screen. */
class PriceEstimateTest {
	@Test
	void oneKindOfMaterialIsJustTheMaterials() {
		// 3 wheat (5c) → 1 bread: 15c, no bonus (goods design 1-1).
		assertEquals(15, PriceEstimate.fromRecipe(List.of(15.0), 1), 1e-9);
		// 1 log (8c) → 4 planks.
		assertEquals(2, PriceEstimate.fromRecipe(List.of(8.0), 4), 1e-9);
	}

	@Test
	void combinedMaterialsPayForTheWork() {
		// Pumpkin (10c) + sugar (2c) + egg (6c) → pumpkin pie: × 1.25.
		assertEquals(18 * 1.25, PriceEstimate.fromRecipe(List.of(10.0, 2.0, 6.0), 1), 1e-9);
	}

	@Test
	void aMinorMaterialIsNotAKind() {
		// 3 diamonds (300c) + 2 sticks (2c): a tool, no bonus.
		assertEquals(302, PriceEstimate.fromRecipe(List.of(300.0, 2.0), 1), 1e-9);
	}

	@Test
	void nothingToGoBy() {
		assertEquals(-1, PriceEstimate.fromRecipe(List.of(), 1));
		assertEquals(-1, PriceEstimate.fromRecipe(List.of(5.0), 0));
		assertEquals(-1, PriceEstimate.fromRecipe(List.of(0.0), 1));
		assertEquals(-1, PriceEstimate.fromRecipe(List.of(5.0, -1.0), 1));
	}

	@Test
	void traits() {
		assertEquals(4, PriceEstimate.fromTraits(64, 0, 0, 0, 0), 1e-9);
		assertEquals(10, PriceEstimate.fromTraits(16, 0, 0, 0, 0), 1e-9);
		assertEquals(40, PriceEstimate.fromTraits(1, 0, 0, 0, 0), 1e-9);
		// Bread-like food: 5 × 2.5 + 6 × 1.5.
		assertEquals(21.5, PriceEstimate.fromTraits(64, 0, 5, 6f, 0), 1e-9);
		// Durability adds; rarity multiplies.
		assertEquals((40 + 100) * 8, PriceEstimate.fromTraits(1, 2, 0, 0, 400), 1e-9);
		assertEquals(4 * 20, PriceEstimate.fromTraits(64, 9, 0, 0, 0), 1e-9);
	}

	@Test
	void rounding() {
		assertEquals(23, PriceEstimate.round(22.5), 1e-9);
		assertEquals(2.3, PriceEstimate.round(2.26), 1e-9);
		assertEquals(0.01, PriceEstimate.round(0.001), 1e-9);
	}
}
