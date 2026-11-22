package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemandMathTest {
	@Test
	void unitsLeft() {
		assertEquals(16, DemandMath.left(0, 16));
		assertEquals(1, DemandMath.left(15, 16));
		assertEquals(0, DemandMath.left(16, 16));
		assertEquals(0, DemandMath.left(40, 16));
		assertEquals(Integer.MAX_VALUE, DemandMath.left(40, 0)); // fulfilling is off
	}

	@Test
	void vigorStaysInRange() {
		assertEquals(90, DemandMath.vigor(100, -10));
		assertEquals(100, DemandMath.vigor(95, 10));
		assertEquals(0, DemandMath.vigor(3, -5));
	}

	/** Full points from vigor 50 up; at 0 they are lowered by the penalty, in a straight line between. */
	@Test
	void lowVigorLowersPoints() {
		assertEquals(1, DemandMath.pointsMultiplier(100, 0.1));
		assertEquals(1, DemandMath.pointsMultiplier(50, 0.1));
		assertEquals(0.95, DemandMath.pointsMultiplier(25, 0.1), 1e-9);
		assertEquals(0.9, DemandMath.pointsMultiplier(0, 0.1), 1e-9);
		assertEquals(1, DemandMath.pointsMultiplier(0, 0)); // penalty off
	}

	@Test
	void villagerEveryThirdShortage() {
		assertFalse(DemandMath.villagerDue(1, 3));
		assertFalse(DemandMath.villagerDue(2, 3));
		assertTrue(DemandMath.villagerDue(3, 3));
		assertTrue(DemandMath.villagerDue(6, 3));
		assertFalse(DemandMath.villagerDue(3, 0)); // villagers off
		assertFalse(DemandMath.villagerDue(0, 3));
	}

	@Test
	void pointsForFulfilling() {
		assertEquals(320, DemandMath.fulfillPoints(20, 16, 1.0), 1e-9);
		assertEquals(160, DemandMath.fulfillPoints(20, 16, 0.5), 1e-9);
	}
}
