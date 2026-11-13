package com.akf.tradeexpansion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraderPaletteTest {
	private static final int ROBE = 0xFF24395E; // the wandering trader's most common robe colour
	private static final int SKIN = 0xFFB57B67;
	private static final int GREEN_CLOTH = 0xFF283420;

	@Test
	void hsvRoundTrip() {
		for (int rgb : new int[] {0x24395E, 0xB57B67, 0xF2C039, 0x000000, 0xFFFFFF, 0x919191, 0x620E00}) {
			float[] hsv = TraderPalette.toHsv((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
			int back = TraderPalette.toRgb(hsv[0], hsv[1], hsv[2]);
			for (int shift : new int[] {16, 8, 0}) {
				int d = Math.abs(((back >> shift) & 0xFF) - ((rgb >> shift) & 0xFF));
				assertTrue(d <= 1, () -> Integer.toHexString(rgb) + " -> " + Integer.toHexString(back));
			}
		}
	}

	@Test
	void originalVariantKeepsEverything() {
		assertEquals(ROBE, TraderPalette.recolor(ROBE, 0));
		assertEquals(SKIN, TraderPalette.recolor(SKIN, 0));
	}

	@Test
	void onlyRobePixelsChange() {
		for (int v = 1; v < TraderPalette.VARIANT_COUNT; v++) {
			assertNotEquals(ROBE, TraderPalette.recolor(ROBE, v), "variant " + v);
			assertEquals(SKIN, TraderPalette.recolor(SKIN, v));
			assertEquals(GREEN_CLOTH, TraderPalette.recolor(GREEN_CLOTH, v));
			assertEquals(0x00000000, TraderPalette.recolor(0x00000000, v));
		}
	}

	@Test
	void alphaIsKept() {
		assertEquals(0x80, TraderPalette.recolor(0x8024395E, 1) >>> 24);
	}

	@Test
	void redVariantIsRed() {
		int c = TraderPalette.recolor(ROBE, 1);
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		assertTrue(r > g && r > b, Integer.toHexString(c));
	}

	@Test
	void outOfRangeVariantFallsBackToOriginal() {
		assertEquals(0, TraderPalette.clamp(-1));
		assertEquals(0, TraderPalette.clamp(TraderPalette.VARIANT_COUNT));
		assertEquals(ROBE, TraderPalette.recolor(ROBE, 99));
	}
}
