package com.akf.tradeexpansion.menu;

/**
 * Size of the trade screen (A-4, v0.33): the base 300×240 grows with the window — more rows in the goods list and a wider
 * list — as far as the GUI-scaled window allows. Decided when the screen opens. Slot positions follow it; they only
 * matter on the client (the server never looks at slot coordinates).
 *
 * @param extraRows  list rows added below the base 6
 * @param extraWidth pixels added to the list width (the name column)
 */
public record MarketLayout(int extraRows, int extraWidth) {
	public static final MarketLayout DEFAULT = new MarketLayout(0, 0);
	public static final int BASE_WIDTH = 300;
	public static final int BASE_HEIGHT = 240;
	public static final int ROW_H = 18;
	public static final int BASE_ROWS = 6;
	public static final int MAX_EXTRA_ROWS = 4;
	public static final int MAX_EXTRA_WIDTH = 120;
	/** Space kept free around the screen. */
	private static final int MARGIN = 16;

	/** The largest layout that fits a GUI-scaled window of {@code width} × {@code height}. */
	public static MarketLayout fit(int width, int height) {
		int rows = Math.clamp((height - MARGIN - BASE_HEIGHT) / ROW_H, 0, MAX_EXTRA_ROWS);
		int extra = Math.clamp((width - MARGIN - BASE_WIDTH) / 2 * 2, 0, MAX_EXTRA_WIDTH); // even, so the inventory stays centred
		return new MarketLayout(rows, extra);
	}

	public int width() {
		return BASE_WIDTH + extraWidth;
	}

	public int height() {
		return BASE_HEIGHT + extraRows * ROW_H;
	}

	public int rows() {
		return BASE_ROWS + extraRows;
	}

	/** Top-left of the player inventory: centred horizontally, below the (taller) list. */
	public int inventoryX() {
		return 70 + extraWidth / 2;
	}

	public int inventoryY() {
		return 156 + extraRows * ROW_H;
	}

	public int sellSlotX() {
		return 26;
	}

	public int sellSlotY() {
		return 176 + extraRows * ROW_H;
	}
}
