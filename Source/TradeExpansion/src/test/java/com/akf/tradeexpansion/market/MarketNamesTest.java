package com.akf.tradeexpansion.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** C-4: market names taken from name tags / item names. */
class MarketNamesTest {
	@Test
	void formattingIsRemovedAndSpacesTrimmed() {
		assertEquals("麦の里", MarketNames.sanitize("  §6麦の§r里 "));
	}

	@Test
	void emptyNamesAreRejected() {
		assertNull(MarketNames.sanitize(null));
		assertNull(MarketNames.sanitize("   "));
		assertNull(MarketNames.sanitize("§a"));
	}

	@Test
	void longNamesAreCut() {
		String name = MarketNames.sanitize("あ".repeat(40));
		assertEquals(MarketNames.MAX_LENGTH, name.codePointCount(0, name.length()));
	}
}
