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

	/** Characters outside the BMP (emoji) count as one each and are never cut in half. */
	@Test
	void longNamesKeepWholeCharacters() {
		String emoji = "🌾"; // 🌾, a surrogate pair
		String name = MarketNames.sanitize(emoji.repeat(40));
		assertEquals(MarketNames.MAX_LENGTH, name.codePointCount(0, name.length()));
		assertEquals(emoji.repeat(MarketNames.MAX_LENGTH), name);
		assertFalse(Character.isHighSurrogate(name.charAt(name.length() - 1)));
	}

	/** Exactly the limit is kept; a cut that ends in a space is trimmed again. */
	@Test
	void cutAtTheLimit() {
		String exact = "a".repeat(MarketNames.MAX_LENGTH);
		assertEquals(exact, MarketNames.sanitize(exact));
		assertEquals("a".repeat(MarketNames.MAX_LENGTH - 1), MarketNames.sanitize("a".repeat(MarketNames.MAX_LENGTH - 1) + " bcd"));
	}
}
