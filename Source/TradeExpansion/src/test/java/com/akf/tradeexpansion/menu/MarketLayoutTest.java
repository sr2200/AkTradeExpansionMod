package com.akf.tradeexpansion.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Trade screen size (A-4, v0.33): grows with the GUI-scaled window, never below 300×240, never past the window. */
class MarketLayoutTest {
	/** 1920×1080 at GUI scale 3 = 640×360: the largest layout (10 rows, +120 wide). */
	@Test
	void largeWindowGivesTheLargestLayout() {
		MarketLayout layout = MarketLayout.fit(640, 360);
		assertEquals(10, layout.rows());
		assertEquals(420, layout.width());
		assertEquals(312, layout.height());
	}

	/** 1920×1080 at GUI scale 4 = 480×270: no room for more rows, but wider. */
	@Test
	void shortWindowKeepsTheRows() {
		MarketLayout layout = MarketLayout.fit(480, 270);
		assertEquals(6, layout.rows());
		assertEquals(240, layout.height());
		assertTrue(layout.width() <= 480 - 16);
	}

	/** Smaller than the base: the base size (as before v0.33). */
	@Test
	void tinyWindowKeepsTheBaseSize() {
		assertEquals(MarketLayout.DEFAULT, MarketLayout.fit(310, 250));
	}

	/** The screen always fits with its margin, and the inventory moves with the list. */
	@Test
	void alwaysFitsAndSlotsFollow() {
		for (int w = 300; w <= 1000; w += 37) {
			for (int h = 240; h <= 600; h += 23) {
				MarketLayout l = MarketLayout.fit(w, h);
				if (l.extraRows() > 0) assertTrue(l.height() + 16 <= h, w + "x" + h);
				if (l.extraWidth() > 0) assertTrue(l.width() + 16 <= w, w + "x" + h);
				assertEquals(156 + l.extraRows() * 18, l.inventoryY());
				assertEquals(0, l.extraWidth() % 2);
			}
		}
	}
}
